package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReadScopeGuard {

    private final CurrentUserResolver currentUserResolver;

    public boolean isClientScope() {
        AuthenticatedUser user = currentUserResolver.getCurrentUser();
        return user != null && "CLIENT".equalsIgnoreCase(user.getUserType());
    }

    public boolean isAssignmentScope() {
        AuthenticatedUser user = currentUserResolver.getCurrentUser();
        if (user == null || user.isSuperAdmin() || user.isFirmAdmin()) {
            return false;
        }
        return !"CLIENT".equalsIgnoreCase(user.getUserType());
    }

    public UUID currentUserId() {
        return currentUserResolver.getCurrentUserId();
    }

    public void requireOwnClientRecord(UUID ownerClientUserId, String what) {
        if (!isClientScope()) {
            return;
        }
        UUID me = currentUserId();
        if (ownerClientUserId == null || !ownerClientUserId.equals(me)) {
            throw new ForbiddenException("You do not have access to " + what);
        }
    }
}
