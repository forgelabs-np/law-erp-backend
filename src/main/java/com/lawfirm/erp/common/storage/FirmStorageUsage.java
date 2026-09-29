package com.lawfirm.erp.common.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per firm: how much space it has been allocated and how much it is using.
 *
 * <p>Quota and usage share a row deliberately. Both are read and written together inside the
 * same locked transaction, and keeping the allocation here rather than in {@code firm_configs}
 * means a firm admin cannot raise their own limit through the firm settings API — allocation
 * is a platform decision.
 */
@Entity
@Table(name = "firm_storage_usage", uniqueConstraints = {
        @UniqueConstraint(name = "uq_firm_storage_usage_firm", columnNames = "firm_id")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FirmStorageUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firm_id", nullable = false)
    private UUID firmId;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;

    /**
     * Allocated bytes. {@link StorageQuotaService#UNLIMITED} (0) disables the check — a firm
     * with no allocation configured is not silently blocked from working.
     */
    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
