package com.lawfirm.erp.common.storage;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.firm.repository.FirmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageQuotaServiceTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Mock private FirmStorageUsageRepository repository;
    @Mock private FirmRepository firmRepository;

    private StorageQuotaService service;
    private final UUID firmId = UUID.randomUUID();

    /** Stands in for the single row the database holds for this firm. */
    private final java.util.concurrent.atomic.AtomicReference<FirmStorageUsage> storedRow =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setDefaultQuotaBytes(5 * GB);
        service = new StorageQuotaService(repository, firmRepository, properties);
        when(firmRepository.existsById(any())).thenReturn(true);
        when(repository.save(any(FirmStorageUsage.class))).thenAnswer(invocation -> {
            storedRow.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        // Reads see whatever was last written, so a row created on first use is visible afterwards.
        when(repository.findForUpdate(firmId)).thenAnswer(invocation -> Optional.ofNullable(storedRow.get()));
    }

    private FirmStorageUsage row(long used, long quota) {
        return FirmStorageUsage.builder()
                .id(1L).firmId(firmId).usedBytes(used).quotaBytes(quota)
                .updatedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("reserving space increments the counter")
    void reserveIncrementsUsage() {
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(row(1 * GB, 5 * GB)));

        service.reserve(firmId, 2 * GB);

        assertEquals(3 * GB, repository.findForUpdate(firmId).orElseThrow().getUsedBytes());
    }

    @Test
    @DisplayName("an upload that does not fit is refused with a readable message")
    void reserveRefusesWhenTheAllocationIsExhausted() {
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(row(4 * GB, 5 * GB)));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.reserve(firmId, 2 * GB));

        assertTrue(error.getMessage().contains("4.0 GB"), error.getMessage());
        assertTrue(error.getMessage().contains("5.0 GB"), error.getMessage());
        assertTrue(error.getMessage().contains("2.0 GB"), error.getMessage());
    }

    @Test
    @DisplayName("exactly filling the allocation is allowed")
    void reserveAllowsFillingToTheLastByte() {
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(row(4 * GB, 5 * GB)));

        service.reserve(firmId, 1 * GB);

        assertEquals(5 * GB, repository.findForUpdate(firmId).orElseThrow().getUsedBytes());
    }

    @Test
    @DisplayName("an allocation of zero means unlimited")
    void zeroAllocationIsUnlimited() {
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(row(900 * GB, StorageQuotaService.UNLIMITED)));

        service.reserve(firmId, 500 * GB);

        assertEquals(1400 * GB, repository.findForUpdate(firmId).orElseThrow().getUsedBytes());
    }

    @Test
    @DisplayName("a firm with no row yet gets the default allocation and is never refused")
    void firstUseCreatesTheRowWithTheDefaultAllocation() {
        // No row exists yet — the default answer returns empty until a save happens.
        service.reserve(firmId, 1 * GB);

        FirmStorageUsage created = repository.findForUpdate(firmId).orElseThrow();
        assertEquals(1 * GB, created.getUsedBytes());
        assertEquals(5 * GB, created.getQuotaBytes(), "the configured default allocation should apply");
    }

    @Test
    @DisplayName("reserving repeatedly accumulates against the same allocation")
    void reserveAccumulatesAcrossCalls() {
        FirmStorageUsage usage = row(0, 5 * GB);
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(usage));

        service.reserve(firmId, 1 * GB);

        assertEquals(1 * GB, usage.getUsedBytes());
        // The pre-flight check reads the counter without taking the lock.
        when(repository.findByFirmId(firmId)).thenReturn(Optional.of(usage));
        assertTrue(service.canFit(firmId, 4 * GB));
        assertFalse(service.canFit(firmId, 5 * GB), "the last byte of the allocation is the limit");
    }

    @Test
    @DisplayName("releasing space never drives the counter below zero")
    void releaseNeverGoesNegative() {
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(row(1 * GB, 5 * GB)));

        service.release(firmId, 4 * GB);

        assertEquals(0L, repository.findForUpdate(firmId).orElseThrow().getUsedBytes());
    }

    @Test
    @DisplayName("the platform can allocate different sizes to different firms")
    void setQuotaAllocatesSpace() {
        when(repository.findForUpdate(firmId)).thenReturn(Optional.of(row(0, 5 * GB)));

        StorageUsageView view = service.setQuota(firmId, 10 * GB);

        assertEquals(10 * GB, view.quotaBytes());
        assertEquals(10 * GB, repository.findForUpdate(firmId).orElseThrow().getQuotaBytes());
    }

    @Test
    void setQuotaRejectsANegativeAllocation() {
        assertThrows(BusinessRuleException.class, () -> service.setQuota(firmId, -1L));
    }

    @Test
    void setQuotaRejectsAnUnknownFirm() {
        when(firmRepository.existsById(firmId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.setQuota(firmId, GB));
    }

    @Test
    @DisplayName("usage view reports what is left and how full the firm is")
    void usageViewReportsAvailableSpace() {
        when(repository.findByFirmId(firmId)).thenReturn(Optional.of(row(5 * GB, 10 * GB)));

        StorageUsageView view = service.usage(firmId);

        assertEquals(5 * GB, view.usedBytes());
        assertEquals(5 * GB, view.availableBytes());
        assertEquals(50d, view.usedPercent());
        assertFalse(view.unlimited());
    }

    @Test
    @DisplayName("usedPercent is capped at 100 even when the firm is over its allocation")
    void usedPercentIsCapped() {
        StorageUsageView view = StorageUsageView.of(20 * GB, 10 * GB);

        assertEquals(100d, view.usedPercent());
        assertEquals(0L, view.availableBytes());
    }

    @Test
    @DisplayName("an unlimited firm reports no percentage")
    void unlimitedFirmReportsUnlimited() {
        StorageUsageView view = StorageUsageView.of(3 * GB, StorageQuotaService.UNLIMITED);

        assertTrue(view.unlimited());
        assertEquals(0d, view.usedPercent());
    }

    @Test
    void bytesAreFormattedForHumans() {
        assertEquals("5.0 GB", StorageQuotaService.humanReadable(5 * GB));
        assertEquals("50.0 MB", StorageQuotaService.humanReadable(50L * 1024 * 1024));
        assertEquals("512.0 B", StorageQuotaService.humanReadable(512));
        assertEquals("unlimited", StorageQuotaService.humanReadable(0));
    }
}
