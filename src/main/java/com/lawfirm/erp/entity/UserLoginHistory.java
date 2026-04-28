// entity/UserLoginHistory.java
package com.lawfirm.erp.entity;

import com.lawfirm.erp.enums.LoginStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_login_history")
public class UserLoginHistory {
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