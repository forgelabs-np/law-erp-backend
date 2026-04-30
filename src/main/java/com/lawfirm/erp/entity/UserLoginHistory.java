package com.lawfirm.erp.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.enums.LoginStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_login_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginHistory extends ActiveAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    private LoginStatus status;

    private LocalDateTime loginTime;
    private String ipAddress;
    private String deviceInfo;
    private String failureReason;
    private Integer loginCount = 1;
    private LocalDateTime lastLoginTime;
    private LocalDateTime lastFailedAttempt;
}