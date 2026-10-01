package com.lawfirm.erp.common.storage;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.firm.repository.FirmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Allocates storage per firm and keeps its usage counter honest.
 *
 * <p>Every mutation goes through the row lock in {@link FirmStorageUsageRepository#findForUpdate},
 * so concurrent uploads against the same firm serialize instead of double-spending the last
 * of the allocation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaService {

    /** Sentinel allocation meaning "no limit enforced". */
    public static final long UNLIMITED = 0L;

    private final FirmStorageUsageRepository repository;
    private final FirmRepository firmRepository;
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public StorageUsageView usage(UUID firmId) {
        return repository.findByFirmId(firmId)
                .map(row -> StorageUsageView.of(row.getUsedBytes(), row.getQuotaBytes()))
                .orElseGet(() -> StorageUsageView.of(0L, defaultQuota()));
    }

    /** Cheap pre-flight check for the upload-ticket step, so an over-quota upload is refused before it starts. */
    @Transactional(readOnly = true)
    public boolean canFit(UUID firmId, long bytes) {
        return repository.findByFirmId(firmId)
                .map(row -> fits(row, bytes))
                .orElse(true);
    }

    /**
     * Reserves {@code bytes} for the firm. Throws when that would exceed the allocation.
     * The caller must already hold the bytes in storage and clean them up if this throws.
     */
    @Transactional
    public void reserve(UUID firmId, long bytes) {
        FirmStorageUsage usage = lockOrCreate(firmId);
        if (!fits(usage, bytes)) {
            throw new BusinessRuleException(String.format(
                    "Storage allocation exceeded: %s of %s used, this file needs %s. "
                            + "Ask your platform administrator for more space.",
                    humanReadable(usage.getUsedBytes()),
                    humanReadable(usage.getQuotaBytes()),
                    humanReadable(bytes)));
        }
        usage.setUsedBytes(usage.getUsedBytes() + bytes);
        usage.setUpdatedAt(LocalDateTime.now());
        repository.save(usage);
    }

    /** Gives bytes back — called when a document is archived. */
    @Transactional
    public void release(UUID firmId, long bytes) {
        FirmStorageUsage usage = lockOrCreate(firmId);
        usage.setUsedBytes(Math.max(0L, usage.getUsedBytes() - bytes));
        usage.setUpdatedAt(LocalDateTime.now());
        repository.save(usage);
    }

    /** Platform-side allocation change: firm A 5 GB, firm B 10 GB. */
    @Transactional
    public StorageUsageView setQuota(UUID firmId, long quotaBytes) {
        if (quotaBytes < 0) {
            throw new BusinessRuleException("Storage allocation cannot be negative");
        }
        if (!firmRepository.existsById(firmId)) {
            throw new ResourceNotFoundException("Firm not found: " + firmId);
        }
        FirmStorageUsage usage = lockOrCreate(firmId);
        usage.setQuotaBytes(quotaBytes);
        usage.setUpdatedAt(LocalDateTime.now());
        FirmStorageUsage saved = repository.save(usage);
        log.info("Storage allocation for firm {} set to {}", firmId, humanReadable(quotaBytes));
        return StorageUsageView.of(saved.getUsedBytes(), saved.getQuotaBytes());
    }

    /**
     * Creates the firm's row on first use.
     *
     * <p>Existing firms are backfilled at startup by {@link StorageUsageBootstrap}, so in
     * practice this only covers a firm created after boot, whose first upload is a single
     * admin action rather than a concurrent burst.
     */
    private FirmStorageUsage lockOrCreate(UUID firmId) {
        return repository.findForUpdate(firmId).orElseGet(() -> repository.save(
                FirmStorageUsage.builder()
                        .firmId(firmId)
                        .usedBytes(0L)
                        .quotaBytes(defaultQuota())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private boolean fits(FirmStorageUsage usage, long bytes) {
        return usage.getQuotaBytes() <= UNLIMITED
                || usage.getUsedBytes() + bytes <= usage.getQuotaBytes();
    }

    private long defaultQuota() {
        return systemConfigService.storageDefaultQuotaBytes();
    }

    /** "5 GB" rather than "5368709120", for messages a human reads. */
    public static String humanReadable(long bytes) {
        if (bytes <= 0) {
            return "unlimited";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        double value = bytes;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }
}
