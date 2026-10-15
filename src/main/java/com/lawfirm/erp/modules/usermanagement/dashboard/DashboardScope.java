package com.lawfirm.erp.modules.usermanagement.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DashboardScope {

    private final UUID userId;

    private final UUID firmId;

    private final String userType;

    private final String roleCode;

    private final LocalDateTime now;

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(userType);
    }

    public boolean isFirmAdmin() {
        return "FIRM".equals(userType);
    }

    public boolean isFirmUser() {
        return "FIRM_USER".equals(userType);
    }

    public boolean isClient() {
        return "CLIENT".equals(userType);
    }
}
