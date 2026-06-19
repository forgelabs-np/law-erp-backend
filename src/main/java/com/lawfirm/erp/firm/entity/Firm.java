package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "firms")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Firm extends ActiveAuditableEntity {

    @Column(unique = true, nullable = false, updatable = false)
    private String lawFirmCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FirmType firmType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FirmStatus status;

    private String email;
    private String phone;
    private String address;
    private String jurisdiction;
    private String logoUrl;

    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    @Column(columnDefinition = "TEXT")
    private String settings;
}