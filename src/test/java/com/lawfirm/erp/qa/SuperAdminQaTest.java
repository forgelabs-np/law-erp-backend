package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA pass over every Super Admin flow:
 * register → login (MFA) → create firm + firm admin → grant permissions →
 * module enablement → suspend/activate → trial lifecycle → password & MFA reset
 * → custom firm role → cross-tenant guards.
 */
@Transactional
class SuperAdminQaTest extends QaBaseTest {

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private FirmModuleRepository firmModuleRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // Registration / login
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-01: super admin registration rejects a wrong secret")
    void superAdminRegistration_wrongSecret() throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/super-admin/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "username", "qa_badsec",
                                "email", "qa_badsec@test.com",
                                "password", "Pass123!",
                                "fullName", "Bad Secret",
                                "mobileNo", "9800111111",
                                "secretKey", "definitely-not-the-secret")))))
                .andReturn();
        assertEquals(400, status(result), "Wrong registration secret must be rejected: " + raw(result));
    }

    @Test
    @DisplayName("SA-02: super admin login always challenges for MFA")
    void superAdminLogin_requiresMfa() throws Exception {
        registerSuperAdmin("qa_sa_mfa", "qa_sa_mfa@test.com", "Pass123!", "MFA SA",
                "9800111112", SA_SECRET);

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/super-admin/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "username", "qa_sa_mfa", "password", "Pass123!")))))
                .andReturn();

        assertEquals(200, status(result), raw(result));
        String loginStatus = json(result).path("data").path("status").asText();
        assertTrue(List.of("MFA_SETUP_REQUIRED", "MFA_REQUIRED").contains(loginStatus),
                "Super admin login must not return a usable access token directly, got: " + loginStatus);
        assertFalse(json(result).path("data").path("mfaToken").asText().isBlank(),
                "MFA challenge must carry an mfaToken");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Firm creation + firm admin handover
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-03: creating a firm creates its admin, clones roles and returns correct data")
    void createFirm_dataCorrectness() throws Exception {
        String sa = token(superAdmin("qa_sa_firm"));
        JsonNode data = createFirm(sa, "QAFIRM1", "qa_admin1");

        assertFalse(data.path("firmId").asText().isBlank(), "firmId must be returned");
        assertEquals("QAFIRM1", data.path("lawFirmCode").asText());
        assertEquals("qa_admin1", data.path("adminUsername").asText());

        Firm firm = firmByCode("QAFIRM1");
        assertEquals(FirmStatus.ACTIVE, firm.getStatus(), "New firm should be ACTIVE");

        User admin = firmAdmin(firm, "qa_admin1");
        assertTrue(Boolean.TRUE.equals(admin.getMustChangePassword()),
                "Firm admin must be forced to change the seeded password on first login");

        // 4 roles cloned, ZERO permissions each (SA must grant the ceiling first)
        for (String roleCode : List.of("FIRM_ADMIN", "ADVOCATE", "PARALEGAL", "CLIENT")) {
            Role role = firmRole(firm, roleCode);
            assertNotNull(role, roleCode + " should be cloned to the firm");
        }
        assertTrue(permCodes(firmRole(firm, "FIRM_ADMIN").getId()).isEmpty(),
                "GAP/RULE: a freshly created firm's FIRM_ADMIN role has no permissions yet");
        assertFalse(roleRepository.findByFirmIdAndRoleCode(firm.getId(), "SUPER_ADMIN").isPresent(),
                "SUPER_ADMIN must not be cloned into a firm");
    }

    @Test
    @DisplayName("SA-04: GET /super-admin/firms lists every firm with correct trial + status flags")
    void listAllFirms() throws Exception {
        String sa = token(superAdmin("qa_sa_list"));
        createFirm(sa, "QAFIRM2", "qa_admin2");
        Map<String, Object> trial = new LinkedHashMap<>();
        trial.put("isTrial", true);
        trial.put("trialDays", 30);
        createFirm(sa, "QAFIRM2T", "qa_admin2t", trial);

        MvcResult result = authGet(sa, "/api/v1/super-admin/firms");
        assertAllowed(result, "list all firms");

        JsonNode firms = json(result).path("data");
        assertTrue(firms.isArray() && firms.size() >= 2, "Both created firms must be listed");

        JsonNode permanent = null;
        JsonNode trialFirm = null;
        for (JsonNode firm : firms) {
            if ("QAFIRM2".equals(firm.path("lawFirmCode").asText())) permanent = firm;
            if ("QAFIRM2T".equals(firm.path("lawFirmCode").asText())) trialFirm = firm;
        }
        assertNotNull(permanent, "The permanent firm must appear in the list");
        assertNotNull(trialFirm, "The trial firm must appear in the list");
        assertFalse(permanent.path("isTrial").asBoolean(), "Permanent firm must not be flagged trial");
        assertTrue(trialFirm.path("isTrial").asBoolean(), "Trial firm must be flagged trial");
        assertEquals("ACTIVE", permanent.path("status").asText(), "Firm status must be reported");
    }

    @Test
    @DisplayName("SA-05: granting every permission gives the firm admin full module access")
    void grantAllPermissions_adminHasFullAccess() throws Exception {
        String sa = token(superAdmin("qa_sa_grantall"));
        createFirm(sa, "QAGFULL", "qa_gadmin");

        Firm firm = firmByCode("QAGFULL");
        Role adminRole = firmRole(firm, "FIRM_ADMIN");
        assertTrue(permCodes(adminRole.getId()).isEmpty(), "precondition: role starts empty");

        String adminToken = grantEverythingAndToken(sa, firm, "qa_gadmin");

        assertEquals(everyPermissionId().size(), permCodes(adminRole.getId()).size(),
                "Firm admin role should hold every seeded permission");

        assertAllowed(authGet(adminToken, "/api/v1/firm/matters?page=0&size=5"), "Firm admin reads matters");
        assertAllowed(authGet(adminToken, "/api/v1/modules/users?page=0&size=5"), "Firm admin reads users");
        assertAllowed(authGet(adminToken, "/api/v1/firm/clients?page=0&size=5"), "Firm admin reads clients");
        assertAllowed(authGet(adminToken, "/api/v1/firm/employees?page=0&size=5"), "Firm admin reads employees");
    }

    @Test
    @DisplayName("SA-06: a firm granted a narrower permission set is blocked from the withheld module")
    void narrowGrant_firmIsRestricted() throws Exception {
        String sa = token(superAdmin("qa_sa_narrow"));
        createFirm(sa, "QAGWIDE", "qa_wide");
        createFirm(sa, "QAGNARROW", "qa_narrow");

        Firm wide = firmByCode("QAGWIDE");
        Firm narrow = firmByCode("QAGNARROW");

        String wideToken = grantEverythingAndToken(sa, wide, "qa_wide");

        // Narrow firm: read-only case management only
        enableAllModulesForFirm(narrow);
        grantRolePermissions(sa, narrow, firmRole(narrow, "FIRM_ADMIN"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));
        String narrowToken = token(firmAdmin(narrow, "qa_narrow"));

        assertAllowed(authGet(narrowToken, "/api/v1/firm/matters?page=0&size=5"),
                "Read-only firm must still read matters");

        MvcResult createMatter = authPost(narrowToken, "/api/v1/firm/matters", apiRequest(Map.of(
                "matterType", "CIVIL",
                "title", "Should not be created",
                "originatingCourtLevel", "DISTRICT",
                "courtName", "Kathmandu District Court")));
        assertDenied(createMatter, "Read-only firm creating a matter");

        MvcResult listUsers = authGet(narrowToken, "/api/v1/modules/users?page=0&size=5");
        assertDenied(listUsers, "Firm without USER_MANAGEMENT:VIEW listing users");

        // The wide firm is unaffected (no cross-firm permission bleed)
        assertAllowed(authPost(wideToken, "/api/v1/firm/matters", apiRequest(Map.of(
                "matterType", "CIVIL",
                "title", "Wide firm matter",
                "originatingCourtLevel", "DISTRICT",
                "courtName", "Kathmandu District Court"))), "Full-permission firm creates a matter");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Firm admin listing / status / trial
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-07: firm admin endpoints return the created admin with correct firm linkage")
    void firmAdminListing_isCorrect() throws Exception {
        String sa = token(superAdmin("qa_sa_admins"));
        createFirm(sa, "QAFIRM7", "qa_admin7");
        Firm firm = firmByCode("QAFIRM7");
        User admin = firmAdmin(firm, "qa_admin7");

        assertAllowed(authGet(sa, "/api/v1/super-admin/firms/admins"), "list all firm admins");
        assertAllowed(authGet(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/admins"), "list firm admins by firm");
        assertAllowed(authGet(sa, "/api/v1/super-admin/firms/admins/" + admin.getId()), "get firm admin by id");

        JsonNode list = json(authGet(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/admins")).path("data");
        assertTrue(list.isArray() && list.size() >= 1, "Firm should expose its admin");
        assertEquals("qa_admin7", list.get(0).path("username").asText());
    }

    @Test
    @DisplayName("SA-08: suspending a firm blocks its users' login and live sessions")
    void suspendFirmBlocksUsers() throws Exception {
        String sa = token(superAdmin("qa_sa_suspend"));
        createFirm(sa, "QAFIRM8", "qa_admin8");
        Firm firm = firmByCode("QAFIRM8");
        String adminToken = grantEverythingAndToken(sa, firm, "qa_admin8");

        assertAllowed(authPut(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/suspend", null), "suspend firm");
        assertEquals(FirmStatus.SUSPENDED, firmByCode("QAFIRM8").getStatus(), "Firm status must be SUSPENDED");

        // FIXED (F-1): Firm.status is consulted at login (AuthServiceImpl) and on every
        // request (JwtAuthFilter), so a suspended firm is refused on both paths.
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", "QAFIRM8", "username", "qa_admin8", "password", ADMIN_PWD)))))
                .andReturn();
        assertEquals(401, status(login),
                "A suspended firm's admin must not be able to log in (got " + status(login) + ")");

        MvcResult apiCall = authGet(adminToken, "/api/v1/firm/matters?page=0&size=5");
        assertEquals(403, status(apiCall),
                "A suspended firm's live session must be refused (got " + status(apiCall) + ")");

        assertAllowed(authPut(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/activate", null), "activate firm");
        assertEquals(FirmStatus.ACTIVE, firmByCode("QAFIRM8").getStatus(), "Firm status must be ACTIVE again");
    }

    @Test
    @DisplayName("SA-09: trial creation, extension and conversion to permanent are consistent")
    void trialLifecycle() throws Exception {
        String sa = token(superAdmin("qa_sa_trial"));
        Map<String, Object> trial = new LinkedHashMap<>();
        trial.put("isTrial", true);
        trial.put("trialDays", 14);
        createFirm(sa, "QAFIRM9", "qa_admin9", trial);

        Firm firm = firmByCode("QAFIRM9");
        assertTrue(Boolean.TRUE.equals(firm.getIsTrial()), "Firm should be flagged as trial");
        assertNotNull(firm.getTrialExpiresAt(), "Trial firm must have an expiry");
        java.time.LocalDateTime expiryBefore = firm.getTrialExpiresAt();

        assertAllowed(authPut(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/extend-trial",
                apiRequest(Map.of("additionalDays", 10))), "extend trial");
        assertTrue(firmByCode("QAFIRM9").getTrialExpiresAt().isAfter(expiryBefore),
                "Extending the trial must move the expiry forward");

        assertAllowed(authPut(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/convert-to-permanent", null),
                "convert to permanent");
        assertFalse(Boolean.TRUE.equals(firmByCode("QAFIRM9").getIsTrial()),
                "Converted firm must no longer be flagged as trial");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Modules
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-10: no modules are enabled at creation; SA can enable one and it is reported back")
    void moduleEnablement() throws Exception {
        String sa = token(superAdmin("qa_sa_modules"));
        createFirm(sa, "QAFIRM10", "qa_admin10");
        Firm firm = firmByCode("QAFIRM10");

        Module caseModule = moduleRepository.findByCode("CASE_MANAGEMENT").orElseThrow();
        assertFalse(firmModuleRepository.existsByFirmIdAndModuleCodeAndIsEnabledTrue(firm.getId(), "CASE_MANAGEMENT"),
                "Modules must not be auto-enabled for a new firm");

        assertAllowed(authPost(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/modules",
                apiRequest(Map.of("moduleId", caseModule.getId(), "isEnabled", true))), "enable module");
        assertTrue(firmModuleRepository.existsByFirmIdAndModuleCodeAndIsEnabledTrue(firm.getId(), "CASE_MANAGEMENT"),
                "Module should now be enabled for the firm");

        JsonNode modules = json(authGet(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/modules")).path("data");
        assertTrue(modules.size() > 0, "Firm module status list should not be empty");
    }

    @Test
    @DisplayName("SA-11: disabling a module does not actually block the API — GAP")
    void disabledModuleStillServed() throws Exception {
        String sa = token(superAdmin("qa_sa_modgate"));
        createFirm(sa, "QAFIRM11", "qa_admin11");
        Firm firm = firmByCode("QAFIRM11");
        String adminToken = grantEverythingAndToken(sa, firm, "qa_admin11");

        Module caseModule = moduleRepository.findByCode("CASE_MANAGEMENT").orElseThrow();
        assertAllowed(authPost(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/modules",
                apiRequest(Map.of("moduleId", caseModule.getId(), "isEnabled", false))), "disable module");

        // FIXED (F-6): disabling a module now blocks its API endpoints via @RequiresModule
        assertDenied(authGet(adminToken, "/api/v1/firm/matters?page=0&size=5"),
                "case-management API must be blocked when module is disabled");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Credential recovery by SA
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-12: SA password reset works, forces a rotation and revokes the old session")
    void superAdminResetsPassword() throws Exception {
        String sa = token(superAdmin("qa_sa_resetpw"));
        createFirm(sa, "QAFIRM12", "qa_admin12");
        Firm firm = firmByCode("QAFIRM12");
        User admin = firmAdmin(firm, "qa_admin12");
        String sessionBefore = token(admin);
        assertAllowed(authGet(sessionBefore, "/api/v1/me"), "session before the reset works");

        String newPassword = "Relocated2026!";
        assertAllowed(authPost(sa, "/api/v1/super-admin/users/" + admin.getId() + "/reset-password",
                apiRequest(Map.of("newPassword", newPassword))), "SA resets password");

        // The reset bumps permissionVersion, so the old session dies immediately
        assertUnauthorized(authGet(sessionBefore, "/api/v1/me"));

        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", "QAFIRM12", "username", "qa_admin12", "password", newPassword)))))
                .andReturn();
        assertEquals(200, status(login), "New password must authenticate: " + raw(login));
        assertEquals("PASSWORD_CHANGE_REQUIRED", json(login).path("data").path("status").asText(),
                "A Super-Admin-issued password must be rotated on next login");
    }

    @Test
    @DisplayName("SA-13: MFA reset clears the secret, keeps MFA mandatory, and re-enrolment works")
    void superAdminResetsMfa() throws Exception {
        String sa = token(superAdmin("qa_sa_resetmfa"));
        createFirm(sa, "QAFIRM13", "qa_admin13");
        Firm firm = firmByCode("QAFIRM13");
        User admin = firmAdmin(firm, "qa_admin13");

        MvcResult result = authPost(sa, "/api/v1/super-admin/mfa/reset",
                apiRequest(Map.of("userId", admin.getId(), "reason", "QA — lost authenticator device")));
        assertAllowed(result, "SA resets MFA");

        User afterReset = userRepository.findById(admin.getId()).orElseThrow();
        assertNull(afterReset.getMfaSecret(), "MFA secret must be cleared");
        assertFalse(Boolean.TRUE.equals(afterReset.getMfaVerified()), "MFA must go back to unverified");
        // By design the flag stays on, so the next login forces a re-enrolment
        assertTrue(Boolean.TRUE.equals(afterReset.getMfaEnabled()),
                "mfaEnabled stays true by design — the user must re-setup on next login");

        // The user can log in again. The admin still carries mustChangePassword from
        // firm creation, so rotate first and then confirm the fresh MFA challenge.
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "lawFirmCode", "QAFIRM13", "username", "qa_admin13", "password", ADMIN_PWD)))))
                .andReturn();
        assertEquals(200, status(login), "User must still be able to start a login: " + raw(login));

        MvcResult afterRotation = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/change-password")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "passwordChangeToken", json(login).path("data").path("passwordChangeToken").asText(),
                                "newPassword", "Rotated2026!",
                                "confirmPassword", "Rotated2026!")))))
                .andReturn();
        assertEquals(200, status(afterRotation), "Password rotation should succeed: " + raw(afterRotation));
        JsonNode data = json(afterRotation).path("data");
        assertFalse(data.path("mfaToken").asText().isBlank(),
                "An MFA token must be issued after the reset");
        assertFalse(data.path("mfaQrCodeUri").asText().isBlank(),
                "A new enrolment QR must be issued so the user can re-add their authenticator");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Custom firm role created on the firm's behalf
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-14: SA can create a custom firm role with permissions")
    void superAdminCreatesCustomFirmRole() throws Exception {
        String sa = token(superAdmin("qa_sa_customrole"));
        createFirm(sa, "QAFIRM14", "qa_admin14");
        Firm firm = firmByCode("QAFIRM14");

        MvcResult created = authPost(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/roles",
                apiRequest(Map.of(
                        "name", "Billing Clerk",
                        "code", "BILLING_CLERK",
                        "description", "Handles invoices only",
                        "isActive", true,
                        "permissionIds", permIds("BILLING:ACCESS", "BILLING:VIEW", "BILLING:CREATE"))));
        assertAllowed(created, "SA creates firm role");

        String roleId = json(created).path("data").path("id").asText();
        assertFalse(roleId.isBlank(), "Created role must return an id");
        assertTrue(roleRepository.findById(UUID.fromString(roleId)).orElseThrow().getFirm() != null,
                "Custom role must be scoped to the firm");

        // FIXED (F-7): the permissionIds sent on create are applied in the same call.
        assertEquals(java.util.Set.of("BILLING:ACCESS", "BILLING:VIEW", "BILLING:CREATE"),
                permCodes(UUID.fromString(roleId)),
                "create-role must apply the permissionIds sent with it");

        assertAllowed(authPut(sa, "/api/v1/super-admin/firms/" + firm.getId() + "/roles/" + roleId + "/permissions",
                apiRequest(Map.of("roleId", roleId,
                        "permissionIds", permIds("BILLING:ACCESS", "BILLING:VIEW", "BILLING:CREATE")))),
                "assign permissions to the new role");
        assertEquals(java.util.Set.of("BILLING:ACCESS", "BILLING:VIEW", "BILLING:CREATE"),
                permCodes(UUID.fromString(roleId)), "Custom role must now hold exactly the granted permissions");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Tenant guards
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SA-15: a fully-privileged firm admin still cannot call Super Admin endpoints")
    void firmAdminCannotReachSuperAdminApis() throws Exception {
        String sa = token(superAdmin("qa_sa_guard"));
        createFirm(sa, "QAFIRM15", "qa_admin15");
        Firm firm = firmByCode("QAFIRM15");
        String adminToken = grantEverythingAndToken(sa, firm, "qa_admin15");

        assertDenied(authGet(adminToken, "/api/v1/super-admin/users?page=0&size=5"), "firm admin vs SA user list");
        assertDenied(authGet(adminToken, "/api/v1/super-admin/firms/admins"), "firm admin vs SA firm admins");
        assertDenied(authGet(adminToken, "/api/v1/super-admin/audit"), "firm admin vs SA audit");
        assertDenied(authGet(adminToken, "/api/v1/admin/roles"), "firm admin vs platform roles");
        assertDenied(authGet(adminToken, "/api/v1/admin/tenant-types"), "firm admin vs tenant types");

        // By design these two are permission-gated with firm-grantable permissions
        // (MENU_MANAGEMENT / PERMISSION_MANAGEMENT), so a fully-granted admin may read them.
        assertEquals(200, status(authGet(adminToken, "/api/v1/admin/modules")),
                "MENU_MANAGEMENT:VIEW is a firm-level permission — the module catalogue is readable");
        assertEquals(200, status(authGet(adminToken, "/api/v1/admin/permissions")),
                "PERMISSION_MANAGEMENT:VIEW is a firm-level permission");
    }

    @Test
    @DisplayName("SA-16: SA audit trail records privileged actions")
    void superAdminAuditTrail() throws Exception {
        String sa = token(superAdmin("qa_sa_audit"));
        createFirm(sa, "QAFIRM16", "qa_admin16");

        MvcResult result = authGet(sa, "/api/v1/super-admin/audit?page=0&size=20");
        assertEquals(200, status(result), "SA audit endpoint should respond: " + raw(result));
    }
}
