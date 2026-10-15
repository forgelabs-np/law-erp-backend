package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regression probes for defects found during the full-lifecycle E2E pass.
 * Each test asserts the behaviour the product documents; a failure here means
 * the defect is present.
 */
@Transactional
class E2eSecurityConfigProbeTest extends QaBaseTest {

    @Autowired
    private SystemConfigService systemConfigService;

    // ═══════════════════════════════════════════════════════════════════════
    // FINDING 1 — "Only one super admin allowed" is not enforced
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FINDING-1: a second super admin with different details must be rejected")
    void onlyOneSuperAdminMayRegister() throws Exception {
        MvcResult first = registerSuperAdminApi("probe_sa_one", "probe_sa_one@e2e.test", "9800007001");
        assertEquals(200, status(first), "first SA registration should succeed: " + raw(first));

        MvcResult second = registerSuperAdminApi("probe_sa_two", "probe_sa_two@e2e.test", "9800007002");
        assertEquals(400, status(second),
                "the product documents a single super admin, but a second one registered: " + raw(second));

        assertEquals(1, userRepository.findByUserType(UserType.SUPER_ADMIN).size(),
                "exactly one SUPER_ADMIN row must exist after registration");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FINDING 2 — DB-configured login lockout is ignored
    // ═══════════════════════════════════════════════════════════════════════

    private static final String LOCKOUT_FIRM_CODE = "PROBELOCK";
    private static final String LOCKOUT_ADMIN = "probe_lock_admin";

    /**
     * Committed fixture for the lockout probe, built from the repositories rather than through the
     * super-admin API. Registering a super admin here would commit (the probe is
     * non-transactional) and then FINDING-1 and every other SA-dependent test would see two.
     * Idempotent, so re-running the class in one JVM reuses the same firm and account.
     */
    private User committedLockoutFixtureUser() {
        Firm firm = firmRepository.findByLawFirmCode(LOCKOUT_FIRM_CODE).orElseGet(() -> {
            Firm created = createFirm(LOCKOUT_FIRM_CODE, LOCKOUT_FIRM_CODE + " Law Firm");
            Role role = new Role();
            role.setFirm(created);
            role.setRoleName("FIRM_ADMIN");
            role.setRoleCode("FIRM_ADMIN");
            role.setIsSystem(false);
            role.setActive(true);
            roleRepository.save(role);
            return created;
        });

        User admin = userRepository.findByUsernameAndFirmId(LOCKOUT_ADMIN, firm.getId())
                .orElseGet(() -> createUser(LOCKOUT_ADMIN, LOCKOUT_ADMIN + "@probelock.test",
                        ADMIN_PWD, UserType.FIRM, firmRole(firm, "FIRM_ADMIN"), firm));

        admin.setLoginAttempts(0);
        admin.setLockedUntil(null);
        return userRepository.save(admin);
    }

    /**
     * This probe deliberately runs OUTSIDE the test transaction.
     *
     * <p>A lockout only exists if the failed-attempt write <em>commits</em>. Inside the class-level
     * transaction every write joins the test's own transaction, so the test reads back its own
     * uncommitted change and passes even when the service rolls that write back on the exception —
     * exactly how a shattered lockout counter stayed green here while production let an account be
     * guessed at forever. Non-transactional, the read below only sees committed state.
     *
     * <p>Because it commits, the config it overrides is restored in a finally block so the rest of
     * the suite still runs against the seeded {@code LOGIN_MAX_ATTEMPTS}.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("FINDING-2: login lockout must honour LOGIN_MAX_ATTEMPTS from system config")
    void loginLockoutHonoursDbConfiguredMaxAttempts() throws Exception {
        UUID adminId = committedLockoutFixtureUser().getId();

        int seededMaxAttempts = systemConfigService.loginMaxAttempts();
        try {
            systemConfigService.setGlobal(SystemConfigService.KEY_LOGIN_MAX_ATTEMPTS, "1");
            assertEquals(1, systemConfigService.loginMaxAttempts(),
                    "precondition: system config must hold LOGIN_MAX_ATTEMPTS=1");

            MvcResult first = badLogin(LOCKOUT_FIRM_CODE, LOCKOUT_ADMIN, "WrongPass1!");
            assertEquals(401, status(first), raw(first));

            // Asserted on the row, not the response body — the surfaced wording is locked down
            // separately by FINDING-4a so this test does not break when the copy changes.
            assertNotNull(userRepository.findById(adminId).orElseThrow().getLockedUntil(),
                    "with LOGIN_MAX_ATTEMPTS=1 the account must be locked after one failed login");

            MvcResult second = badLogin(LOCKOUT_FIRM_CODE, LOCKOUT_ADMIN, "WrongPass2!");
            assertEquals(401, status(second), raw(second));
            assertEquals(1, userRepository.findById(adminId).orElseThrow().getLoginAttempts(),
                    "a locked account must not accrue further failed-attempt counts");
        } finally {
            systemConfigService.setGlobal(SystemConfigService.KEY_LOGIN_MAX_ATTEMPTS,
                    String.valueOf(seededMaxAttempts));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FINDING 3 — the first-login password-change token is replayable
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FINDING-3: a password-change token must not be usable twice")
    void passwordChangeTokenIsSingleUse() throws Exception {
        String sa = token(superAdmin("probe_token_sa"));
        createFirm(sa, "PROBETOKEN", "probe_token_admin");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", "PROBETOKEN",
                                "username", "probe_token_admin",
                                "password", ADMIN_PWD)))))
                .andReturn();
        assertEquals(200, status(loginResult), raw(loginResult));
        String token = json(loginResult).path("data").path("passwordChangeToken").asText();
        assertFalse(token.isBlank(), "firm admin first login must return a password-change token");

        MvcResult first = changePassword(token, "FirstRotate123!");
        assertEquals(200, status(first), "first rotation should succeed: " + raw(first));

        MvcResult replay = changePassword(token, "SecondRotate123!");
        assertEquals(401, status(replay),
                "replaying the same password-change token must be refused, but got: " + raw(replay));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FINDING 4 (D6) — login names the real reason, but only after the password is proved
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FINDING-4a: a locked account is told it is locked, and for how long")
    void lockedAccountIsToldTheReason() throws Exception {
        String sa = token(superAdmin("probe_msg_sa"));
        createFirm(sa, "PROBEMSG", "probe_msg_admin");
        systemConfigService.setGlobal(SystemConfigService.KEY_LOGIN_MAX_ATTEMPTS, "1");

        assertEquals(401, status(badLogin("PROBEMSG", "probe_msg_admin", "WrongPass1!")),
                "the first failure must be refused");

        MvcResult locked = badLogin("PROBEMSG", "probe_msg_admin", "WrongPass2!");
        assertEquals(401, status(locked), raw(locked));
        assertTrue(raw(locked).contains("Account locked"),
                "a locked account must be told it is locked, got: " + raw(locked));
    }

    @Test
    @DisplayName("FINDING-4b: account state is never revealed without the password")
    void accountStateRequiresThePassword() throws Exception {
        String sa = token(superAdmin("probe_oracle_sa"));
        createFirm(sa, "PROBEORACLE", "probe_oracle_admin");
        Firm firm = firmByCode("PROBEORACLE");
        String admin = grantEverythingAndToken(sa, firm, "probe_oracle_admin");

        // Created through the API: only there is the password hashed (the direct-insert
        // helpers in the QA harness store it raw and can never password-authenticate).
        assertAllowed(authPost(admin, "/api/v1/firm/clients", apiRequest(Map.of(
                "username", "probe_oracle_client",
                "email", "probe_oracle_client@probeoracle.test",
                "mobileNo", "9800009088",
                "password", CLIENT_PWD,
                "fullName", "Probe Oracle Client",
                "portalAccessEnabled", false))),
                "firm admin creates a portal-disabled client");

        MvcResult wrong = clientLogin("PROBEORACLE", "probe_oracle_client", "WrongPass9!");
        assertEquals(401, status(wrong), raw(wrong));
        assertFalse(raw(wrong).contains("portal"),
                "a wrong password must not name the portal state: " + raw(wrong));

        MvcResult right = clientLogin("PROBEORACLE", "probe_oracle_client", CLIENT_PWD);
        assertEquals(401, status(right), raw(right));
        assertTrue(raw(right).contains("portal access is disabled"),
                "with the right password the portal reason must be shown: " + raw(right));
    }

    @Test
    @DisplayName("FINDING-4c: an inactive account is named only after the password is proved")
    void inactiveAccountReasonRequiresThePassword() throws Exception {
        String sa = token(superAdmin("probe_inactive_sa"));
        createFirm(sa, "PROBEINACT", "probe_inactive_admin");
        Firm firm = firmByCode("PROBEINACT");
        String admin = grantEverythingAndToken(sa, firm, "probe_inactive_admin");

        EmployeeFixture employee = createEmployee(firm, admin, "probe_inactive_emp");
        User row = userRepository.findByUsernameAndFirmId(employee.username(), firm.getId())
                .orElseThrow();
        row.setActive(false);
        userRepository.save(row);

        MvcResult wrong = badLogin("PROBEINACT", employee.username(), "WrongPass9!");
        assertEquals(401, status(wrong), raw(wrong));
        assertFalse(raw(wrong).contains("inactive"),
                "a wrong password must not name the account state: " + raw(wrong));

        MvcResult right = badLogin("PROBEINACT", employee.username(), employee.password());
        assertEquals(401, status(right), raw(right));
        assertTrue(raw(right).contains("inactive"),
                "with the right password the inactive reason must be shown: " + raw(right));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FINDING 5 (D7) — a client must rotate the password the firm admin chose
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FINDING-5: a client's first portal login must force a password rotation")
    void newClientMustRotateTheAdminSetPassword() throws Exception {
        String sa = token(superAdmin("probe_d7_sa"));
        createFirm(sa, "PROBED7", "probe_d7_admin");
        Firm firm = firmByCode("PROBED7");
        String admin = grantEverythingAndToken(sa, firm, "probe_d7_admin");

        MvcResult created = authPost(admin, "/api/v1/firm/clients", apiRequest(Map.of(
                "username", "probe_d7_client",
                "email", "probe_d7_client@probed7.test",
                "mobileNo", "9800009099",
                "password", CLIENT_PWD,
                "fullName", "Probe D7 Client",
                "portalAccessEnabled", true)));
        assertAllowed(created, "firm admin creates client");
        String username = json(created).path("data").path("username").asText();

        MvcResult firstLogin = clientLogin("PROBED7", username, CLIENT_PWD);
        assertEquals(200, status(firstLogin), raw(firstLogin));
        JsonNode data = json(firstLogin).path("data");
        assertEquals("PASSWORD_CHANGE_REQUIRED", data.path("status").asText(),
                "a client must be forced to rotate the admin-set password: " + raw(firstLogin));
        String changeToken = data.path("passwordChangeToken").asText();
        assertFalse(changeToken.isBlank(), "no password-change token issued: " + raw(firstLogin));

        MvcResult changed = changePassword(changeToken, "ClientRotated123!");
        assertEquals(200, status(changed), raw(changed));
        assertFalse(json(changed).path("data").path("accessToken").asText().isBlank(),
                "rotating must return a usable token: " + raw(changed));

        MvcResult secondLogin = clientLogin("PROBED7", username, "ClientRotated123!");
        assertEquals(200, status(secondLogin), raw(secondLogin));
        assertFalse(json(secondLogin).path("data").path("accessToken").asText().isBlank(),
                "the rotated password must sign in: " + raw(secondLogin));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private MvcResult clientLogin(String firmCode, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/client/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", firmCode,
                                "username", username,
                                "password", password)))))
                .andReturn();
    }

    private MvcResult registerSuperAdminApi(String username, String email, String mobile) throws Exception {
        return mockMvc.perform(post("/api/v1/super-admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "fullName", username + " Full",
                                "username", username,
                                "email", email,
                                "mobileNo", mobile,
                                "password", "ProbePass123!",
                                "secretKey", SA_SECRET)))))
                .andReturn();
    }

    private MvcResult badLogin(String firmCode, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", firmCode,
                                "username", username,
                                "password", password)))))
                .andReturn();
    }

    private MvcResult changePassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "passwordChangeToken", token,
                                "newPassword", newPassword,
                                "confirmPassword", newPassword)))))
                .andReturn();
    }
}
