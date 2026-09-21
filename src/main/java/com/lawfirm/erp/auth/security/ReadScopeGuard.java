package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Row-level read scoping for client accounts.
 *
 * <p>The permission matrix gives the CLIENT role <em>ACCESS + VIEW</em> on case management,
 * calendar, documents and projects. Those permission rows are identical to the ones an
 * ADVOCATE holds, so the permission check alone cannot express "own records only" — the
 * {@code PermissionScope.OWN} documentation never had an implementation behind it.
 *
 * <p>Instead of inventing a second permission set, callers ask this guard whether the
 * current request is client-scoped, then constrain the query (or reject the record). That
 * mirrors how the project portal already behaves: the login identity <em>is</em> the scope.
 */
@Component
@RequiredArgsConstructor
public class ReadScopeGuard {

    private final CurrentUserResolver currentUserResolver;

    /** True when the caller is a client-portal account and must be limited to its own rows. */
    public boolean isClientScope() {
        AuthenticatedUser user = currentUserResolver.getCurrentUser();
        return user != null && "CLIENT".equalsIgnoreCase(user.getUserType());
    }

    public UUID currentUserId() {
        return currentUserResolver.getCurrentUserId();
    }

    /**
     * Reject the request unless the caller is allowed to see the record that belongs to
     * {@code ownerClientUserId}. Staff (any non-CLIENT user type) pass through untouched.
     */
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
