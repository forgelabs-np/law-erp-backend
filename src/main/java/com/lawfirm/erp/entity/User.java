package com.lawfirm.erp.entity;

import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"email", "firm_id"}),
                @UniqueConstraint(columnNames = {"username", "firm_id"})
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends ActiveAuditableEntity implements UserDetails {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id")
    private Firm firm;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    private String mobileNo;
    private String password;
    private String fullName;
    private String profilePhotoUrl;

    @Builder.Default
    private Boolean isEmailVerified = false;

    @Builder.Default
    private Boolean isMobileVerified = false;

    @Builder.Default
    private Boolean isBlocked = false;

    @Builder.Default
    private Integer loginAttempts = 0;

    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    @Builder.Default
    private Boolean portalAccessEnabled = false;

    @Column(name = "permission_version")
    private Integer permissionVersion = 0;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return !Boolean.TRUE.equals(isBlocked); }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isActive(); }

    public UUID getFirmId() {
        return firm != null ? firm.getId() : null;
    }

    public boolean isSuperAdmin() {
        return userType == UserType.SUPER_ADMIN;
    }
}