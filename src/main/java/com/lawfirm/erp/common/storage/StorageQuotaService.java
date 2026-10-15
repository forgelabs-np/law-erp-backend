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

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaService {

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

    @Transactional(readOnly = true)
    public boolean canFit(UUID firmId, long bytes) {
        return repository.findByFirmId(firmId)
                .map(row -> fits(row, bytes))
                .orElse(true);
    }

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

    @Transactional
    public void release(UUID firmId, long bytes) {
        FirmStorageUsage usage = lockOrCreate(firmId);
        usage.setUsedBytes(Math.max(0L, usage.getUsedBytes() - bytes));
        usage.setUpdatedAt(LocalDateTime.now());
        repository.save(usage);
    }

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
