package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.document.support.DocumentTestFiles;
import com.lawfirm.erp.modules.document.support.FakeStorageService;
import com.lawfirm.erp.qa.QaBaseTest;
import com.lawfirm.erp.rbac.entity.Module;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * The whole product journey, driven exclusively through the public HTTP API —
 * no directly-minted JWTs and no hand-written rows for the flow itself.
 *
 * <pre>
 *   super admin registers  → logs in → MFA setup confirmed
 *     → creates a firm (firm admin born)
 *       → enables the firm's modules
 *       → grants the FIRM_ADMIN role permissions
 *         → firm admin first login (forced rotation → MFA setup)
 *           → grants the ADVOCATE role permissions
 *           → creates an employee
 *           → creates a client
 *             → employee first login (forced rotation)
 *             → client portal login
 * </pre>
 *
 * Then exercises the feature surfaces each actor should reach. The value over the
 * existing suites is that every token here comes from a real login, so the
 * registration → MFA → rotation → access-token chain is under test end to end.
 */
@Transactional
class FullLifecycleE2ETest extends QaBaseTest {

    /** Replaces MinIO for this test class only, so document upload needs no running store. */
    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        StorageService fakeStorageService() {
            return new FakeStorageService();
        }
    }

    private static final String SA_USERNAME = "e2e_super";
    private static final String SA_EMAIL = "e2e_super@e2e.test";
    private static final String SA_MOBILE = "9800009001";
    private static final String SA_PASSWORD = "SuperPass123!";

    private static final String FIRM_CODE = "E2EFIRM";
    private static final String ADMIN_USERNAME = "e2e_admin";
    private static final String ADMIN_ROTATED = "AdminRotated123!";
    private static final String EMP_ROTATED = "EmployeeRot123!";
    private static final String CLIENT_ROTATED = "ClientRot123!";

    // ═══════════════════════════════════════════════════════════════════════
    // Journey
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: super admin → firm/firm admin → employee → client, real HTTP only")
    void fullLifecycle() throws Exception {
        // ── 1. Super Admin registers through the real endpoint ────────────────
        MvcResult registered = mockMvc.perform(post("/api/v1/super-admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                "fullName", "E2E Super Admin",
                                "username", SA_USERNAME,
                                "email", SA_EMAIL,
                                "mobileNo", SA_MOBILE,
                                "password", SA_PASSWORD,
                                "secretKey", SA_SECRET)))))
                .andReturn();
        assertEquals(200, status(registered), "SA registration failed: " + raw(registered));
        assertFalse(json(registered).path("data").path("userId").asText().isBlank(),
                "registration must return the new user id");

        // ── 2. Super Admin login → MFA setup → access token ───────────────────
        JsonNode saLogin = loginResponse("/api/v1/super-admin/login", null, SA_USERNAME, SA_PASSWORD);
        assertEquals("MFA_SETUP_REQUIRED", saLogin.path("status").asText(),
                "a freshly registered super admin must be forced through MFA setup, got: " + saLogin);
        assertFalse(saLogin.path("mfaQrCodeUri").asText().isBlank(), "MFA setup must return a QR URI");
        String saToken = finishLogin(saLogin, null);

        // ── 3. SA creates the firm (which creates its admin) ──────────────────
        JsonNode firmData = createFirm(saToken, FIRM_CODE, ADMIN_USERNAME);
        Firm firm = firmByCode(FIRM_CODE);
        assertEquals(FIRM_CODE, firmData.path("lawFirmCode").asText());
        assertEquals(ADMIN_USERNAME, firmData.path("adminUsername").asText());
        assertTrue(firmAdmin(firm, ADMIN_USERNAME).getMustChangePassword(),
                "the firm admin must be forced to rotate the seeded password");

        // ── 4. SA enables every module via the real API ───────────────────────
        for (Module module : moduleRepository.findAll()) {
            assertAllowed(authPost(saToken, "/api/v1/super-admin/firms/" + firm.getId() + "/modules",
                    apiRequest(Map.of("moduleId", module.getId(), "isEnabled", true))),
                    "enable module " + module.getCode());
        }

        // ── 5. SA grants the FIRM_ADMIN role every permission ─────────────────
        assertAllowed(grantRolePermissions(saToken, firm, firmRole(firm, "FIRM_ADMIN"), everyPermissionId()),
                "SA grants FIRM_ADMIN every permission");

        // ── 6. Firm admin first login: forced rotation then MFA setup ─────────
        JsonNode adminLogin = loginResponse("/api/v1/auth/login", FIRM_CODE, ADMIN_USERNAME, ADMIN_PWD);
        assertEquals("PASSWORD_CHANGE_REQUIRED", adminLogin.path("status").asText(),
                "firm admin's first login must require a password change, got: " + adminLogin);
        String admin = finishLogin(adminLogin, ADMIN_ROTATED);
        assertFalse(admin.isBlank(), "firm admin must end up with an access token");

        // The rotated password now works and yields an MFA challenge (not a raw token).
        JsonNode relogin = loginResponse("/api/v1/auth/login", FIRM_CODE, ADMIN_USERNAME, ADMIN_ROTATED);
        assertEquals("MFA_REQUIRED", relogin.path("status").asText(),
                "after setup, a firm admin login must challenge for MFA, got: " + relogin);
        String adminViaMfa = finishLogin(relogin, null);
        assertFalse(adminViaMfa.isBlank(), "MFA validation must produce an access token");

        // ── 7. Firm admin's own surface ───────────────────────────────────────
        assertAllowed(authGet(admin, "/api/v1/firm/profile"), "firm admin reads firm profile");
        assertAllowed(authGet(admin, "/api/v1/firm/modules"), "firm admin reads enabled modules");
        assertAllowed(authGet(admin, "/api/v1/firm/roles"), "firm admin lists roles");
        assertAllowed(authGet(admin, "/api/v1/modules/users?page=0&size=10"), "firm admin lists users");

        // ── 8. Firm admin grants the ADVOCATE role a working permission set ────
        assertAllowed(assignRolePermissions(admin, firmRole(firm, "ADVOCATE"),
                        permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE",
                                "CALENDAR:ACCESS", "CALENDAR:VIEW",
                                "DASHBOARD_MANAGEMENT:VIEW",
                                "PROJECT_MANAGEMENT:ACCESS", "PROJECT_MANAGEMENT:VIEW",
                                "DOCUMENT_MANAGEMENT:ACCESS", "DOCUMENT_MANAGEMENT:VIEW")),
                "firm admin grants ADVOCATE permissions");

        // ── 9. Firm admin creates an employee through the API ─────────────────
        MvcResult employeeCreated = authPost(admin, "/api/v1/firm/employees", apiRequest(Map.of(
                "username", "e2e_employee",
                "email", "e2e_employee@" + FIRM_CODE.toLowerCase() + ".test",
                "mobileNo", "9800009002",
                "password", EMP_PWD,
                "fullName", "E2E Employee",
                "roleId", firmRole(firm, "ADVOCATE").getId(),
                "designation", "Advocate")));
        assertAllowed(employeeCreated, "firm admin creates employee");
        JsonNode empData = json(employeeCreated).path("data");
        String empUsername = empData.path("username").asText();
        String empId = empData.path("id").asText();
        assertFalse(empUsername.isBlank(), "employee creation must return the generated username");

        // ── 10. Employee first login: forced rotation (advocates have no MFA) ─
        JsonNode empLogin = loginResponse("/api/v1/auth/login", FIRM_CODE, empUsername, EMP_PWD);
        assertEquals("PASSWORD_CHANGE_REQUIRED", empLogin.path("status").asText(),
                "employee's first login must require a password change, got: " + empLogin);
        String employee = finishLogin(empLogin, EMP_ROTATED);

        // ── 11. Employee features ─────────────────────────────────────────────
        // Employees do NOT read /firm/modules (FIRM_ADMIN-only); /me carries their module
        // access so the app can build navigation.
        MvcResult me = authGet(employee, "/api/v1/me");
        assertAllowed(me, "employee reads own identity");
        JsonNode meModules = json(me).path("data").path("modules");
        assertTrue(meModules.size() > 0, "employee /me must expose module access");
        boolean caseModuleVisible = false;
        for (JsonNode module : meModules) {
            if ("CASE_MANAGEMENT".equals(module.path("moduleCode").asText())) {
                caseModuleVisible = true;
                assertTrue(module.path("enabled").asBoolean(),
                        "the enabled CASE_MANAGEMENT module must be reported enabled");
            }
        }
        assertTrue(caseModuleVisible, "the employee's granted CASE_MANAGEMENT must appear in /me modules");
        assertDenied(authGet(employee, "/api/v1/firm/modules"),
                "employee reading the admin-only firm module list");
        assertAllowed(authGet(employee, "/api/v1/firm/dashboard"), "employee reads dashboard");
        assertAllowed(authGet(employee, "/api/v1/firm/calendar/today"), "employee reads today's calendar");
        assertAllowed(authGet(employee, "/api/v1/firm/matters?page=0&size=5"), "employee lists matters");

        MvcResult matterCreated = authPost(employee, "/api/v1/firm/matters", apiRequest(
                matterBody("E2E employee matter")));
        assertAllowed(matterCreated, "employee creates a matter");
        String matterNumber = json(matterCreated).path("data").path("matterNumber").asText();
        assertFalse(matterNumber.isBlank(), "matter creation must return a matter number");

        // Employee must not reach administrative surfaces.
        assertDenied(authGet(employee, "/api/v1/firm/employees?page=0&size=5"),
                "advocate listing employees");
        assertDenied(authGet(employee, "/api/v1/firm/roles"), "advocate listing roles");

        // ── 12. Firm admin assigns the employee to the matter ─────────────────
        assertAllowed(authPost(admin, "/api/v1/firm/matters/" + matterNumber + "/assignments",
                apiRequest(Map.of("userId", empId, "assignmentRole", "PRIMARY_ADVOCATE"))),
                "firm admin assigns employee");
        assertAllowed(authGet(admin, "/api/v1/firm/matters/" + matterNumber + "/assignments"),
                "firm admin lists assignments");

        // The employee's scoped matter list now shows the assigned case.
        JsonNode empMatters = json(authGet(employee, "/api/v1/firm/matters?page=0&size=20"))
                .path("data").path("content");
        boolean seesAssigned = false;
        for (JsonNode m : empMatters) {
            if (matterNumber.equals(m.path("matterNumber").asText())) seesAssigned = true;
        }
        assertTrue(seesAssigned, "the assigned employee should see the matter in their list");

        // ── 13. Document upload on the matter (storage faked) ─────────────────
        JsonNode document = uploadDocument(admin, matterNumber, "petition.pdf", "application/pdf",
                DocumentTestFiles.pdf("e2e petition"));
        assertEquals("ACTIVE", document.path("status").asText(), "upload must finish ACTIVE in one call");
        assertEquals(matterNumber, document.path("matterNumber").asText());

        // ── 14. Firm admin creates a client through the API ───────────────────
        MvcResult clientCreated = authPost(admin, "/api/v1/firm/clients", apiRequest(Map.of(
                "username", "e2e_client",
                "email", "e2e_client@" + FIRM_CODE.toLowerCase() + ".test",
                "mobileNo", "9800009003",
                "password", CLIENT_PWD,
                "fullName", "E2E Client",
                "portalAccessEnabled", true)));
        assertAllowed(clientCreated, "firm admin creates client");
        String clientUsername = json(clientCreated).path("data").path("username").asText();

        // Link a project to the client so the portal has something to show.
        assertAllowed(authPost(admin, "/api/v1/projects", Map.of(
                "name", "E2E client project",
                "clientName", "E2E Client",
                "description", "Portal project",
                "startDate", LocalDate.now().toString(),
                "targetEndDate", LocalDate.now().plusMonths(3).toString(),
                "clientUserId", UUID.fromString(json(clientCreated).path("data").path("id").asText()))),
                "firm admin creates a client project");

        // ── 15. Client portal login and own-data access ───────────────────────
        JsonNode clientLogin = loginResponse("/api/v1/auth/client/login", FIRM_CODE, clientUsername, CLIENT_PWD);
        String client = finishLogin(clientLogin, CLIENT_ROTATED);
        assertAllowed(authGet(client, "/api/v1/me"), "client reads own identity");
        assertAllowed(authGet(client, "/api/v1/client/matters"), "client lists own matters");
        assertAllowed(authGet(client, "/api/v1/client/projects"), "client lists own projects");

        // ── 16. Super admin can still see the firm it created ─────────────────
        assertAllowed(authGet(saToken, "/api/v1/super-admin/firms"), "SA lists firms");
        assertAllowed(authGet(saToken, "/api/v1/super-admin/firms/" + firm.getId() + "/admins"),
                "SA lists the firm's admins");
        assertAllowed(authGet(saToken, "/api/v1/super-admin/firms/" + firm.getId() + "/roles"),
                "SA lists the firm's roles");
        assertAllowed(authGet(saToken, "/api/v1/super-admin/users?page=0&size=10"), "SA lists users");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> matterBody(String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("matterType", "CIVIL");
        body.put("title", title);
        body.put("originatingCourtLevel", "DISTRICT");
        body.put("courtName", "Kathmandu District Court");
        return body;
    }

    private JsonNode loginResponse(String endpoint, String firmCode, String username, String password)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (firmCode != null) body.put("lawFirmCode", firmCode);
        body.put("username", username);
        body.put("password", password);

        MvcResult result = mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest(body))))
                .andReturn();
        assertEquals(200, status(result), "login to " + endpoint + " failed: " + raw(result));
        return json(result).path("data");
    }

    /**
     * Drives a login response to a usable access token: rotates an admin-issued
     * password when forced, then confirms MFA setup when a QR challenge is returned.
     * Returns the access token.
     */
    private String finishLogin(JsonNode loginData, String newPassword) throws Exception {
        if ("PASSWORD_CHANGE_REQUIRED".equals(loginData.path("status").asText())) {
            assertNotNull(newPassword, "a forced rotation needs the new password");
            MvcResult changed = mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                    "passwordChangeToken", loginData.path("passwordChangeToken").asText(),
                                    "newPassword", newPassword,
                                    "confirmPassword", newPassword)))))
                    .andReturn();
            assertEquals(200, status(changed), "change-password failed: " + raw(changed));
            loginData = json(changed).path("data");
        }

        String status = loginData.path("status").asText();
        if ("MFA_SETUP_REQUIRED".equals(status) || "MFA_REQUIRED".equals(status)) {
            if ("MFA_SETUP_REQUIRED".equals(status)) {
                assertFalse(loginData.path("mfaQrCodeUri").asText().isBlank(),
                        "MFA setup must return a QR URI");
            }
            String endpoint = "MFA_SETUP_REQUIRED".equals(status)
                    ? "/api/v1/auth/mfa/setup/confirm"
                    : "/api/v1/auth/mfa/validate";
            MvcResult confirmed = mockMvc.perform(post(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(apiRequest(Map.of(
                                    "mfaToken", loginData.path("mfaToken").asText(),
                                    "totpCode", "123456")))))
                    .andReturn();
            assertEquals(200, status(confirmed), "MFA step failed: " + raw(confirmed));
            loginData = json(confirmed).path("data");
        }

        String token = loginData.path("accessToken").asText();
        assertFalse(token.isBlank(), "login produced no access token — status="
                + loginData.path("status").asText() + ", body=" + loginData);
        return token;
    }

    /** One multipart POST to the document API: the bytes go through the server. */
    private JsonNode uploadDocument(String token, String matterNumber, String filename,
                                    String contentType, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        MockMultipartHttpServletRequestBuilder request =
                multipart("/api/v1/firm/documents").file(file);
        if (matterNumber != null) request.param("matterNumber", matterNumber);

        MvcResult result = mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
        assertAllowed(result, "upload " + filename);
        return json(result).path("data");
    }
}
