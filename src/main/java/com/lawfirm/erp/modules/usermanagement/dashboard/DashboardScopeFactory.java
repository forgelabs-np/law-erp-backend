package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.auth.security.AuthenticatedUser;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Builds a {@link DashboardScope} from the authenticated user.
 * One scope per request, passed to the appropriate dashboard service.
 */
@Component
@RequiredArgsConstructor
public class DashboardScopeFactory {

    private final CurrentUserResolver currentUserResolver;

    public DashboardScope build() {
        AuthenticatedUser user = currentUserResolver.getCurrentUser();
        if (user == null) {
            throw new ForbiddenException("No authenticated user");
        }

        return DashboardScope.builder()
                .userId(user.getId())
                .firmId(user.getFirmId())
                .userType(user.getUserType())
                .roleCode(user.getRoles() != null && !user.getRoles().isEmpty()
                        ? user.getRoles().get(0) : null)
                .now(LocalDateTime.now())
                .build();
    }
}
