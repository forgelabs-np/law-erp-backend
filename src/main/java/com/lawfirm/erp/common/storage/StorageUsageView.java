package com.lawfirm.erp.common.storage;

public record StorageUsageView(
        long usedBytes,
        long quotaBytes,
        long availableBytes,
        double usedPercent,
        boolean unlimited
) {

    public static StorageUsageView of(long usedBytes, long quotaBytes) {
        if (quotaBytes <= StorageQuotaService.UNLIMITED) {
            return new StorageUsageView(usedBytes, quotaBytes, 0L, 0d, true);
        }
        long available = Math.max(0L, quotaBytes - usedBytes);
        double percent = Math.min(100d, (usedBytes * 100d) / (double) quotaBytes);
        return new StorageUsageView(usedBytes, quotaBytes, available, percent, false);
    }
}
