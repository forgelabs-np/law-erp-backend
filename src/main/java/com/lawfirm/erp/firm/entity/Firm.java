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

    /** Logo uploads stay off until the firm opts in via PATCH /firm/brand/logo/allowed. */
    @Column(name = "logo_allowed")
    private Boolean logoAllowed = false;

    @Column(name = "brand_primary_hex", length = 7)
    private String brandPrimaryHex;

    @Column(name = "brand_secondary_hex", length = 7)
    private String brandSecondaryHex;

    @Column(columnDefinition = "TEXT")
    private String settings;

    @Column(name = "is_trial")
    private Boolean isTrial = false;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "trial_started_at")
    private LocalDateTime trialStartedAt;

    @Column(name = "trial_expires_at")
    private LocalDateTime trialExpiresAt;
}