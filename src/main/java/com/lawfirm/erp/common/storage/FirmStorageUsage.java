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

    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
