package com.lawfirm.erp.auth.service;

import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.MfaSetupConfirmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the broken login lockout.
 *
 * <p>{@code authenticateInternalUser}, {@code authenticateClient} and {@code confirmMfaSetup} record
 * a failed attempt and <em>then</em> throw {@link BadCredentialsException}. With Spring's default
 * rollback that write was discarded, so {@code login_attempts} never persisted and an account could
 * be brute-forced indefinitely.
 *
 * <p>{@code E2eSecurityConfigProbeTest} is itself {@code @Transactional}, so its login calls join
 * the test's transaction and it reads the uncommitted row — it cannot observe the rollback. This
 * test asserts the transaction contract directly, which is the part that actually broke.
 */
class LoginTransactionContractTest {

    @Test
    @DisplayName("a rejected login must not roll back the recorded failed attempt")
    void loginKeepsFailedAttempts() throws Exception {
        assertNoRollbackFor(AuthServiceImpl.class.getMethod("authenticateInternalUser", LoginRequest.class));
        assertNoRollbackFor(AuthServiceImpl.class.getMethod("authenticateClient", LoginRequest.class));
        assertNoRollbackFor(AuthServiceImpl.class.getMethod("confirmMfaSetup", MfaSetupConfirmRequest.class));
    }

    private void assertNoRollbackFor(Method method) {
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx, method.getName() + " must stay @Transactional");
        assertTrue(Arrays.asList(tx.noRollbackFor()).contains(BadCredentialsException.class),
                method.getName() + " must declare noRollbackFor = BadCredentialsException.class; "
                        + "without it a rejected login silently discards the lockout counter");
    }
}
