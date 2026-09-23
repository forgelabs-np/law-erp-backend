package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.auth.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Authentication and security-behaviour QA: password recovery (what exists and
 * what does not), first-login rotation, lockout, token handling, MFA limited
 * tokens, client-portal access revocation, and baseline input hardening.
 */
@Transactional
class AuthSecurityQaTest extends QaBaseTest {

    @Autowired
    private JwtUtil jwtUtil;

    private record Setup(Firm firm, String saToken, String adminToken, String adminUsername) {}

    private Setup setupFirm(String firmCode, String adminUsername) throws Exception {
        String sa = token(superAdmin("qa_sa_" + firmCode.toLowerCase()));
        createFirm(sa, firmCode, adminUsername);
        Firm firm = firmByCode(firmCode);
        String adminToken = grantEverythingAndToken(sa, firm, adminUsername);
        return new Setup(firm, sa, adminToken, adminUsername);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Raw auth calls (no bearer token)
    // ═══════════════════════════════════════════════════════════════════════

    private MvcResult loginRaw(String firmCode, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", firmCode, "username", username, "password", password)))))
                .andReturn();
    }

    private MvcResult clientLoginRaw(String firmCode, String mobileNo, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/client/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", firmCode, "username", mobileNo, "password", password)))))
                .andReturn();
    }

    private MvcResult changePasswordRaw(String passwordChangeToken, String newPassword, String confirm) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("passwordChangeToken", passwordChangeToken);
        body.put("newPassword", newPassword);
        body.put("confirmPassword", confirm != null ? confirm : newPassword);
        return mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(body))))
                .andReturn();
    }

    private MvcResult forgotRaw(String firmCode, String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", firmCode, "username", username)))))
                .andReturn();
    }

    private MvcResult resetRaw(String token, String newPassword, String confirm) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("newPassword", newPassword);
        body.put("confirmPassword", confirm != null ? confirm : newPassword);
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(body))))
                .andReturn();
    }

    /** Full first-login rotation: temp password in, chosen password + tokens out. */
    private JsonNode rotatePassword(String firmCode, String username, String tempPassword, String newPassword)
            throws Exception {
        MvcResult login = loginRaw(firmCode, username, tempPassword);
        assertEquals(200, status(login), "Temporary password must authenticate: " + raw(login));
        assertEquals("PASSWORD_CHANGE_REQUIRED", json(login).path("data").path("status").asText(),
                "A reset password must force rotation on next login");
        MvcResult changed = changePasswordRaw(json(login).path("data").path("passwordChangeToken").asText(),
                newPassword, null);
        assertEquals(200, status(changed), "Password rotation should succeed: " + raw(changed));
        return json(changed).path("data");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Password recovery
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AUTH-01: forgot-password is public, generic and non-enumerating")
    void forgotPasswordIsGeneric() throws Exception {
        Setup s = setupFirm("QAAUTH1", "qa_auth1_admin");

        MvcResult knownAccount = forgotRaw("QAAUTH1", s.adminUsername());
        MvcResult unknownAccount = forgotRaw("QAAUTH1", "ghost_user_does_not_exist");
        MvcResult unknownFirm = forgotRaw("NOSUCHFIRM", s.adminUsername());

        assertAllowed(knownAccount, "forgot-password for a real account");
        assertAllowed(unknownAccount, "forgot-password for an unknown account");
        assertAllowed(unknownFirm, "forgot-password for an unknown firm");

        // Identical answers — nothing here tells an attacker which accounts exist
        assertEquals(raw(knownAccount), raw(unknownAccount),
                "Known and unknown accounts must produce the same response");
        assertEquals(raw(knownAccount), raw(unknownFirm),
                "Known and unknown firms must produce the same response");

        // The route is public (no bearer token was sent) but the reset needs a real token
        assertNotEquals(200, status(resetRaw("made-up-token", "Whatever2026!", null)),
                "A forged reset token must be refused");
    }

    @Test
    @DisplayName("AUTH-01b: the self-service reset link rotates the password, clears lockout and kills sessions")
    void selfServiceResetFlow() throws Exception {
        Setup s = setupFirm("QAAUTH1B", "qa_auth1b_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth1b_emp");

        String live = rotatePassword("QAAUTH1B", employee.username(), EMP_PWD, "Live2026!")
                .path("accessToken").asText();
        assertAllowed(authGet(live, "/api/v1/me"), "session before the reset works");

        // Bulk permVersion bumps bypass the persistence context — refresh so the link is minted
        // against the account's real current version (same pattern as QaBaseTest.freshToken).
        entityManager.flush();
        entityManager.clear();
        User user = userRepository.findById(employee.id()).orElseThrow();
        String resetToken = jwtUtil.generatePasswordResetToken(user);

        // Rejections first
        assertRejected(resetRaw(resetToken, "short", "short"), "weak password on reset");
        assertRejected(resetRaw(resetToken, "Recovered2026!", "Different2026!"), "mismatched confirmation");
        assertRejected(resetRaw(resetToken, "Live2026!", "Live2026!"), "reusing the current password");
        assertNotEquals(200, status(resetRaw("not.a.real.token", "Whatever2026!", null)), "forged token");
        assertNotEquals(200, status(resetRaw(null, "Whatever2026!", null)), "missing token");

        // A reset token is a limited-scope token — it is not a bearer credential
        assertUnauthorized(authGet(resetToken, "/api/v1/me"));
        assertUnauthorized(authGet(resetToken, "/api/v1/firm/matters?page=0&size=5"));

        // Happy path
        assertAllowed(resetRaw(resetToken, "Recovered2026!", null), "reset with a valid link");

        // The link is single-use: the redeeming reset bumped permVersion, so a replay is refused
        entityManager.flush();
        entityManager.clear();
        assertNotEquals(200, status(resetRaw(resetToken, "Replayed2026!", null)),
                "A redeemed reset link must not be usable a second time");

        // Existing sessions are revoked and the old password is dead
        assertUnauthorized(authGet(live, "/api/v1/me"));
        assertNotEquals(200, status(loginRaw("QAAUTH1B", employee.username(), "Live2026!")),
                "The previous password must stop working");

        MvcResult fresh = loginRaw("QAAUTH1B", employee.username(), "Recovered2026!");
        assertEquals(200, status(fresh), "The recovered password must work: " + raw(fresh));
        assertFalse(json(fresh).path("data").path("accessToken").asText().isBlank(),
                "Recovery should leave the account able to sign in normally");
    }

    @Test
    @DisplayName("AUTH-01c: a self-service reset also clears a brute-force lockout")
    void selfServiceResetClearsLockout() throws Exception {
        Setup s = setupFirm("QAAUTH1C", "qa_auth1c_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth1c_emp");

        for (int attempt = 1; attempt <= 5; attempt++) {
            loginRaw("QAAUTH1C", employee.username(), "Wrong" + attempt + "Pass!");
        }
        assertNotNull(userRepository.findById(employee.id()).orElseThrow().getLockedUntil(),
                "Precondition: the account is locked");

        String resetToken = jwtUtil.generatePasswordResetToken(
                userRepository.findById(employee.id()).orElseThrow());
        assertAllowed(resetRaw(resetToken, "Unlocked2026!", null), "reset while locked out");

        assertEquals(200, status(loginRaw("QAAUTH1C", employee.username(), "Unlocked2026!")),
                "Recovery must lift the lockout so the user can get back in");
    }

    @Test
    @DisplayName("AUTH-02: recovery works via admin reset + forced first-login rotation")
    void employeePasswordRecoveryFlow() throws Exception {
        Setup s = setupFirm("QAAUTH2", "qa_auth2_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth2_emp");

        // 1. A forgotten password cannot log in
        assertNotEquals(200, status(loginRaw("QAAUTH2", employee.username(), "forgotten!")),
                "Wrong password must not authenticate");

        // 2. The admin issues a temporary password
        String tempPassword = "TempPass2026!";
        assertAllowed(authPost(s.adminToken(), "/api/v1/modules/users/" + employee.id() + "/reset-password",
                apiRequest(Map.of("newPassword", tempPassword))), "admin resets the password");

        // 3. Temporary password works, but a rotation is forced
        MvcResult login = loginRaw("QAAUTH2", employee.username(), tempPassword);
        assertEquals(200, status(login), "Temporary password must authenticate: " + raw(login));
        String changeToken = json(login).path("data").path("passwordChangeToken").asText();
        assertEquals("PASSWORD_CHANGE_REQUIRED", json(login).path("data").path("status").asText());
        assertFalse(changeToken.isBlank(), "A password-change token must be issued");

        // 4. Mismatched confirmation and weak passwords are refused
        assertNotEquals(200, status(changePasswordRaw(changeToken, "Chosen2026!", "Different2026!")),
                "Password confirmation mismatch must be rejected");
        assertNotEquals(200, status(changePasswordRaw(changeToken, "short", "short")),
                "A weak password must be rejected");

        // 5. Rotation succeeds and returns a usable session
        MvcResult changed = changePasswordRaw(changeToken, "Chosen2026!", null);
        assertEquals(200, status(changed), "Password change should succeed: " + raw(changed));
        String accessToken = json(changed).path("data").path("accessToken").asText();
        assertFalse(accessToken.isBlank(), "Password change must return an access token");
        assertAllowed(authGet(accessToken, "/api/v1/me"), "the new session works");

        // 6. The temporary password is dead, the chosen one lives
        assertNotEquals(200, status(loginRaw("QAAUTH2", employee.username(), tempPassword)),
                "The temporary password must stop working");
        MvcResult withChosen = loginRaw("QAAUTH2", employee.username(), "Chosen2026!");
        assertEquals(200, status(withChosen), "The chosen password must work: " + raw(withChosen));
        assertNotEquals("PASSWORD_CHANGE_REQUIRED", json(withChosen).path("data").path("status").asText(),
                "Rotation is complete, so no further rotation should be demanded");
        assertFalse(json(withChosen).path("data").path("accessToken").asText().isBlank(),
                "A rotated employee gets a normal session on the next login");
    }

    @Test
    @DisplayName("AUTH-03: an admin password reset immediately revokes existing sessions")
    void passwordResetRevokesSessions() throws Exception {
        Setup s = setupFirm("QAAUTH3", "qa_auth3_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth3_emp");

        // Employee establishes a live session
        String session = rotatePassword("QAAUTH3", employee.username(), EMP_PWD, "Live2026!")
                .path("accessToken").asText();
        assertAllowed(authGet(session, "/api/v1/me"), "pre-reset session works");

        // Admin resets the password (lost-credentials / offboarding case)
        assertAllowed(authPost(s.adminToken(), "/api/v1/modules/users/" + employee.id() + "/reset-password",
                apiRequest(Map.of("newPassword", "AdminSet2026!"))), "admin resets the password");

        // resetPassword() bumps permissionVersion, so the old token is refused
        assertUnauthorized(authGet(session, "/api/v1/me"));

        // …and the admin-issued password must be rotated before it can be used
        MvcResult withAdminPassword = loginRaw("QAAUTH3", employee.username(), "AdminSet2026!");
        assertEquals(200, status(withAdminPassword), "The reset password must authenticate: " + raw(withAdminPassword));
        assertEquals("PASSWORD_CHANGE_REQUIRED",
                json(withAdminPassword).path("data").path("status").asText(),
                "An admin-issued password always forces a rotation, even for an existing user");
        assertFalse(json(withAdminPassword).path("data").path("passwordChangeToken").asText().isBlank(),
                "A password-change token must be issued");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lockout / enumeration
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AUTH-04: five failed logins lock the account and the correct password is then refused")
    void accountLockout() throws Exception {
        Setup s = setupFirm("QAAUTH4", "qa_auth4_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth4_emp");

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertEquals(401, status(loginRaw("QAAUTH4", employee.username(), "WrongPass" + attempt + "!")),
                    "Failed attempt " + attempt + " should return 401");
        }

        User user = userRepository.findById(employee.id()).orElseThrow();
        assertTrue(user.getLoginAttempts() >= 5, "Failed attempts must be counted, got " + user.getLoginAttempts());
        assertNotNull(user.getLockedUntil(), "Account must be locked after the attempt limit");

        MvcResult lockedAttempt = loginRaw("QAAUTH4", employee.username(), EMP_PWD);
        assertNotEquals(200, status(lockedAttempt),
                "FINDING: a locked account authenticated with the correct password — " + raw(lockedAttempt));
    }

    @Test
    @DisplayName("AUTH-05: unknown user, wrong password and unknown firm are indistinguishable")
    void noUserEnumeration() throws Exception {
        Setup s = setupFirm("QAAUTH5", "qa_auth5_admin");

        MvcResult unknown = loginRaw("QAAUTH5", "ghost_user_does_not_exist", "Whatever1!");
        MvcResult wrongPassword = loginRaw("QAAUTH5", s.adminUsername(), "NotThePassword1!");
        MvcResult unknownFirm = loginRaw("NOSUCHFIRM", s.adminUsername(), ADMIN_PWD);

        assertEquals(401, status(unknown), "Unknown user must be 401");
        assertEquals(401, status(wrongPassword), "Wrong password must be 401");
        assertEquals(401, status(unknownFirm), "Unknown firm code must be 401");

        for (MvcResult result : new MvcResult[]{unknown, wrongPassword, unknownFirm}) {
            String body = raw(result).toLowerCase();
            assertFalse(body.contains("user not found"), "Response must not confirm the account exists: " + raw(result));
            assertFalse(body.contains("no user"), "Response must not confirm the account exists: " + raw(result));
            assertFalse(body.contains("firm not found"), "Response must not confirm the firm exists: " + raw(result));
        }
    }

    @Test
    @DisplayName("AUTH-06: logins are firm-scoped — firm A credentials cannot open a firm B session")
    void loginIsFirmScoped() throws Exception {
        Setup a = setupFirm("QAAUTH6A", "qa_auth6_admin");
        createFirm(a.saToken(), "QAAUTH6B", "qa_auth6b_admin");

        assertNotEquals(200, status(loginRaw("QAAUTH6B", "qa_auth6_admin", ADMIN_PWD)),
                "Firm A credentials must not authenticate against Firm B");
        assertNotEquals(200, status(loginRaw("QAAUTH6A", "qa_auth6b_admin", ADMIN_PWD)),
                "Firm B credentials must not authenticate against Firm A");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Token handling
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AUTH-07: tampered, unsigned and limited-scope tokens are all rejected")
    void tokenHardening() throws Exception {
        Setup s = setupFirm("QAAUTH7", "qa_auth7_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth7_emp");

        MvcResult login = loginRaw("QAAUTH7", employee.username(), EMP_PWD);
        String limitedToken = json(login).path("data").path("passwordChangeToken").asText();
        assertFalse(limitedToken.isBlank(), "A limited-scope token must be issued for the rotation step");

        // Password-change tokens may only be used on the auth endpoints
        assertUnauthorized(authGet(limitedToken, "/api/v1/me"));
        assertUnauthorized(authGet(limitedToken, "/api/v1/firm/matters?page=0&size=5"));
        assertUnauthorized(authGet(limitedToken, "/api/v1/modules/users?page=0&size=5"));

        // Structurally broken bearer tokens
        assertUnauthorized(authGet("aaa.bbb.ccc", "/api/v1/me"));
        assertUnauthorized(authGet(UUID.randomUUID().toString(), "/api/v1/me"));

        String valid = json(changePasswordRaw(limitedToken, "Valid2026!", null)).path("data").path("accessToken").asText();
        assertAllowed(authGet(valid, "/api/v1/me"), "a real session token works");

        String[] parts = valid.split("\\.");
        assertEquals(3, parts.length, "Expected a compact JWS");
        String tamperedPayload = parts[0] + "." + parts[1].substring(0, parts[1].length() - 4) + "AAAA." + parts[2];
        assertUnauthorized(authGet(tamperedPayload, "/api/v1/me"));

        String signatureSwapped = parts[0] + "." + parts[1] + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        assertUnauthorized(authGet(signatureSwapped, "/api/v1/me"));
    }

    @Test
    @DisplayName("AUTH-08: refresh rotates the session and access tokens are not accepted as refresh tokens")
    void refreshTokenHandling() throws Exception {
        Setup s = setupFirm("QAAUTH8", "qa_auth8_admin");
        EmployeeFixture employee = createEmployee(s.firm(), s.adminToken(), "qa_auth8_emp");

        JsonNode session = rotatePassword("QAAUTH8", employee.username(), EMP_PWD, "Refresh2026!");
        String accessToken = session.path("accessToken").asText();
        String refreshToken = session.path("refreshToken").asText();
        assertFalse(refreshToken.isBlank(), "Login must issue a refresh token");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of("refreshToken", refreshToken)))))
                .andReturn();
        assertEquals(200, status(refreshed), "Refresh must succeed: " + raw(refreshed));
        assertFalse(json(refreshed).path("data").path("accessToken").asText().isBlank(),
                "Refresh must return a new access token");

        MvcResult misuse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of("refreshToken", accessToken)))))
                .andReturn();
        assertNotEquals(200, status(misuse), "Access token accepted as a refresh token: " + raw(misuse));

        MvcResult garbage = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of("refreshToken", "not.a.token")))))
                .andReturn();
        assertNotEquals(200, status(garbage), "Garbage refresh token must be rejected");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Client portal access
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AUTH-09: portal access gates client login, live portal sessions and the internal login")
    void portalAccessGatesClientLogin() throws Exception {
        Setup s = setupFirm("QAAUTH9", "qa_auth9_admin");
        String mobile = "9812349999";

        MvcResult created = authPost(s.adminToken(), "/api/v1/firm/clients", apiRequest(Map.of(
                "username", "qa_auth9_client",
                "email", "qa_auth9_client@qaauth9.test",
                "mobileNo", mobile,
                "password", CLIENT_PWD,
                "fullName", "Portal Client",
                "portalAccessEnabled", true)));
        assertAllowed(created, "create client with portal access");
        String clientId = json(created).path("data").path("id").asText();

        MvcResult enabledLogin = clientLoginRaw("QAAUTH9", mobile, CLIENT_PWD);
        assertEquals(200, status(enabledLogin), "Client login while enabled: " + raw(enabledLogin));
        String liveToken = json(enabledLogin).path("data").path("accessToken").asText();
        assertAllowed(authGet(liveToken, "/api/v1/client/projects"), "portal works while enabled");

        // Admin revokes portal access — the documented purpose of the flag
        assertAllowed(authPatch(s.adminToken(), "/api/v1/firm/clients/" + clientId + "/portal-access",
                apiRequest(Boolean.FALSE)), "revoke portal access");
        assertFalse(Boolean.TRUE.equals(userRepository.findById(UUID.fromString(clientId)).orElseThrow()
                .getPortalAccessEnabled()), "Flag must be persisted as false");

        // The live session dies immediately (permissionVersion bump) and login is refused
        assertUnauthorized(authGet(liveToken, "/api/v1/client/projects"));
        assertEquals(401, status(clientLoginRaw("QAAUTH9", mobile, CLIENT_PWD)),
                "A revoked client must not be able to sign in");

        // Portal-only accounts cannot slip in through the internal login endpoint
        assertNotEquals(200, status(loginRaw("QAAUTH9", "qa_auth9_client", CLIENT_PWD)),
                "A client must not authenticate through the staff login endpoint");

        // Switching portal access back on restores the account
        assertAllowed(authPatch(s.adminToken(), "/api/v1/firm/clients/" + clientId + "/portal-access",
                apiRequest(Boolean.TRUE)), "re-enable portal access");
        MvcResult restored = clientLoginRaw("QAAUTH9", mobile, CLIENT_PWD);
        assertEquals(200, status(restored), "Re-enabling portal access must restore the login: " + raw(restored));
        assertAllowed(authGet(json(restored).path("data").path("accessToken").asText(),
                "/api/v1/client/projects"), "portal works again");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Input hardening / error hygiene
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AUTH-09b: a client can sign into the portal with the username the firm created")
    void clientPortalAcceptsTheUsernameToo() throws Exception {
        Setup s = setupFirm("QAAUTH19", "qa_auth19_admin");
        String mobile = "9812341919";

        MvcResult created = authPost(s.adminToken(), "/api/v1/firm/clients", apiRequest(Map.of(
                "username", "qa_auth19_client",
                "email", "qa_auth19_client@qaauth19.test",
                "mobileNo", mobile,
                "password", CLIENT_PWD,
                "fullName", "Portal Client",
                "portalAccessEnabled", true)));
        assertAllowed(created, "create client with portal access");

        UUID clientId = UUID.fromString(json(created).path("data").path("id").asText());
        String username = userRepository.findById(clientId).orElseThrow().getUsername();
        assertNotNull(username, "A portal account must carry a username to type");

        // The portal has one identifier field, labelled username. A client handed a username must
        // not be turned away because the lookup only ever knew about mobile numbers.
        MvcResult byUsername = clientLoginRaw("QAAUTH19", username, CLIENT_PWD);
        assertEquals(200, status(byUsername), "Client portal login by username: " + raw(byUsername));

        MvcResult byMobile = clientLoginRaw("QAAUTH19", mobile, CLIENT_PWD);
        assertEquals(200, status(byMobile), "Client portal login by mobile: " + raw(byMobile));
    }

    @Test
    @DisplayName("AUTH-10: injection-style input fails closed without 5xx or auth bypass")
    void injectionAttempts() throws Exception {
        Setup s = setupFirm("QAAUTH10", "qa_auth10_admin");

        String[] payloads = {
                "' OR '1'='1",
                "admin'--",
                "\" OR 1=1 --",
                "'; DROP TABLE users; --",
                "<script>alert(1)</script>"
        };
        for (String payload : payloads) {
            MvcResult login = loginRaw("QAAUTH10", payload, payload);
            assertEquals(401, status(login),
                    "Injection payload must be a plain auth failure, got " + status(login) + " — " + raw(login));

            MvcResult search = authGet(s.adminToken(), "/api/v1/firm/matters?search="
                    + java.net.URLEncoder.encode(payload, java.nio.charset.StandardCharsets.UTF_8) + "&page=0&size=5");
            assertTrue(status(search) < 500,
                    "Search with an injection payload must not 5xx, got " + status(search) + " — " + raw(search));
        }

        // The users table is still there and still guarded normally
        assertAllowed(authGet(s.adminToken(), "/api/v1/modules/users?page=0&size=5"), "users still queryable");
    }

    @Test
    @DisplayName("AUTH-11: error responses do not leak stack traces, SQL or internals")
    void errorHygiene() throws Exception {
        Setup s = setupFirm("QAAUTH11", "qa_auth11_admin");

        MvcResult notFound = authGet(s.adminToken(), "/api/v1/firm/matters/NO-SUCH-MATTER-123");
        String body = raw(notFound).toLowerCase();
        assertTrue(status(notFound) < 500, "Missing resource should not 5xx: " + status(notFound));
        assertFalse(body.contains("stacktrace"), "Response must not contain a stack trace");
        assertFalse(body.contains("at com.lawfirm"), "Response must not contain internal frames");
        assertFalse(body.contains("org.hibernate"), "Response must not leak ORM internals");
        assertFalse(body.contains("select "), "Response must not leak SQL: " + raw(notFound));

        MvcResult malformed = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"username\":\"x\"}"))
                .andReturn();
        assertTrue(status(malformed) < 500, "Malformed JSON must be a 4xx, got " + status(malformed));

        MvcResult wrongShape = authPost(s.adminToken(), "/api/v1/firm/matters", Map.of("data", "not-an-object"));
        assertTrue(status(wrongShape) < 500, "Wrong payload shape must be a 4xx, got " + status(wrongShape));
    }

    @Test
    @DisplayName("AUTH-12: /me returns the caller's own identity and requires a token")
    void meEndpoint() throws Exception {
        Setup s = setupFirm("QAAUTH12", "qa_auth12_admin");

        MvcResult me = authGet(s.adminToken(), "/api/v1/me");
        assertAllowed(me, "read own profile");
        JsonNode data = json(me).path("data");
        assertEquals(s.adminUsername(), data.path("username").asText(), "Must return the caller's own record");
        assertEquals("FIRM_ADMIN", data.path("role").path("code").asText(), "Must return the caller's role");
        assertEquals(s.firm().getLawFirmCode(), data.path("firm").path("lawFirmCode").asText(),
                "Must return the caller's firm context");
        assertTrue(data.path("permissions").isArray() && data.path("permissions").size() > 0,
                "Must return the caller's effective permissions");

        MvcResult noToken = mockMvc.perform(get("/api/v1/me").contentType(MediaType.APPLICATION_JSON)).andReturn();
        assertUnauthorized(noToken);
    }

    @Test
    @DisplayName("AUTH-13: cross-site requests from an unlisted origin get no CORS grant")
    void corsDefaultIsRestrictive() throws Exception {
        Setup s = setupFirm("QAAUTH13", "qa_auth13_admin");

        MvcResult preflight = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/v1/firm/matters")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Authorization", "Bearer " + s.adminToken()))
                .andReturn();

        String allowedOrigin = preflight.getResponse().getHeader("Access-Control-Allow-Origin");
        assertNotEquals("*", allowedOrigin, "Wildcard CORS must not be configured");
        assertNotEquals("https://evil.example.com", allowedOrigin,
                "An unlisted origin must not be reflected in Access-Control-Allow-Origin");
    }
}
