package com.lawfirm.erp.dto.auth;

import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Data
@Builder
public class AuthenticatedDetail implements UserDetails {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String userType;
    private String firmId;
    private String firmCode;
    private String accessToken;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role != null) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return null;  // Token-based auth doesn't need password
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}