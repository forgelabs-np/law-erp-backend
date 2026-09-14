package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for SuperAdmin flow:
 * - Register super admin (with secret key)
 * - Login super admin (with MFA handling)
 * - Create firm (which also creates firm admin)
 * - Verify firm admin can login
 * - Verify RBAC system roles are cloned to firm
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SuperAdminE2ETest extends BaseIntegrationTest {

    private static final String SUPER_SECRET = "test-super-admin-secret-key-12345";
    private static final String SA_USERNAME = "testsuperadmin";
    private static final String SA_EMAIL = "sa@test.com";
    private static final String SA_PASSWORD = "TestPass123!";

    // ═══════════════════════════════════════════════════════════════════════
    // 1. SuperAdmin Registration
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("SuperAdmin registration succeeds with valid secret")
    void registerSuperAdmin_validSecret() throws Exception {
        JsonNode response = registerSuperAdmin(
                SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET
        );

        Assertions.assertTrue(response.path("success").asBoolean(),
                "Registration should succeed: " + response.path("message").asText());
        Assertions.assertNotNull(response.path("data").path("userId").asText());
    }

    @Test
    @Order(2)
    @DisplayName("SuperAdmin registration fails with wrong secret")
    void registerSuperAdmin_wrongSecret() throws Exception {
        var request = Map.of("data", Map.of(
                "username", "anotheradmin",
                "email", "another@test.com",
                "password", SA_PASSWORD,
                "fullName", "Another Admin",
                "mobileNo", "9800000002",
                "secretKey", "wrong-secret-key"
        ));

        // RuntimeException → GlobalExceptionHandler returns 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/super-admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("SuperAdmin registration fails with duplicate username")
    void registerSuperAdmin_duplicateUsername() throws Exception {
        // First registration
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        // Second registration with same username
        var request = Map.of("data", Map.of(
                "username", SA_USERNAME,
                "email", "different@test.com",
                "password", SA_PASSWORD,
                "fullName", "Duplicate Admin",
                "mobileNo", "9800000003",
                "secretKey", SUPER_SECRET
        ));

        // RuntimeException → 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/super-admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. SuperAdmin Login
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("SuperAdmin login returns MFA challenge (MFA is mandatory)")
    void loginSuperAdmin_mandatoryMfa() throws Exception {
        // Register first
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        // Login — MFA is mandatory for super admin, so this returns MFA_SETUP_REQUIRED
        var request = Map.of("data", Map.of(
                "username", SA_USERNAME,
                "password", SA_PASSWORD
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/super-admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = parseResponse(result);
        String status = json.path("data").path("status").asText();
        // MFA is mandatory → should return MFA_SETUP_REQUIRED or MFA_REQUIRED
        Assertions.assertTrue(
                "MFA_SETUP_REQUIRED".equals(status) || "MFA_REQUIRED".equals(status),
                "SuperAdmin login should require MFA, got status: " + status
        );
        Assertions.assertNotNull(json.path("data").path("mfaToken").asText());
    }

    @Test
    @Order(5)
    @DisplayName("SuperAdmin login fails with wrong password")
    void loginSuperAdmin_wrongPassword() throws Exception {
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        var request = Map.of("data", Map.of(
                "username", SA_USERNAME,
                "password", "WrongPassword123!"
        ));

        mockMvc.perform(post("/api/v1/super-admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("SuperAdmin login fails with non-existent user")
    void loginSuperAdmin_nonExistentUser() throws Exception {
        var request = Map.of("data", Map.of(
                "username", "nonexistent",
                "password", "password"
        ));

        mockMvc.perform(post("/api/v1/super-admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Create Firm (SuperAdmin → Firm + FirmAdmin)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("SuperAdmin creates firm with admin — full flow")
    void createFirm_fullFlow() throws Exception {
        // Register super admin
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        // Generate token directly (bypassing MFA for test)
        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create firm via API
        var firmRequest = apiRequest(Map.of(
                "lawFirmCode", "TESTFIRM",
                "name", "Test Law Firm",
                "firmType", "FIRM",
                "email", "info@testfirm.com",
                "phone", "9800000010",
                "adminUsername", "firmadmin",
                "adminEmail", "admin@testfirm.com",
                "adminMobileNo", "9800000011",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Firm Admin User"
        ));

        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms", firmRequest);

        // Log the response for debugging if it fails
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        Assertions.assertEquals(200, status,
                "Firm creation should return 200, got: " + status + " — " + body);

        JsonNode json = parseResponse(result);
        Assertions.assertTrue(json.path("success").asBoolean(),
                "Firm creation should succeed: " + json.path("message").asText());

        JsonNode data = json.path("data");
        Assertions.assertEquals("TESTFIRM", data.path("lawFirmCode").asText());
        Assertions.assertEquals("firmadmin", data.path("adminUsername").asText());
    }

    @Test
    @Order(8)
    @DisplayName("Firm admin can login after firm creation")
    void firmAdmin_canLogin() throws Exception {
        // Setup: register SA, create firm
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        var firmRequest = apiRequest(Map.of(
                "lawFirmCode", "LOGINTEST",
                "name", "Login Test Firm",
                "firmType", "FIRM",
                "adminUsername", "logintestadmin",
                "adminEmail", "logintest@testfirm.com",
                "adminMobileNo", "9800000012",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Login Test Admin"
        ));
        authPost(saToken, "/api/v1/super-admin/firms", firmRequest);

        // Firm admin login — mustChangePassword is true, so returns PASSWORD_CHANGE_REQUIRED
        var loginRequest = Map.of("data", Map.of(
                "lawFirmCode", "LOGINTEST",
                "username", "logintestadmin",
                "password", "AdminPass123!"
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = parseResponse(loginResult);
        String loginStatus = loginJson.path("data").path("status").asText();

        // mustChangePassword=true → PASSWORD_CHANGE_REQUIRED
        Assertions.assertEquals("PASSWORD_CHANGE_REQUIRED", loginStatus,
                "First login should require password change, got: " + loginStatus);
        Assertions.assertNotNull(loginJson.path("data").path("passwordChangeToken").asText());
    }

    @Test
    @Order(9)
    @DisplayName("Firm creation clones system roles to firm")
    void createFirm_clonesSystemRoles() throws Exception {
        // Register + login SA
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create firm
        var firmRequest = apiRequest(Map.of(
                "lawFirmCode", "ROLETEST",
                "name", "Role Test Firm",
                "firmType", "FIRM",
                "adminUsername", "roletestadmin",
                "adminEmail", "roletest@testfirm.com",
                "adminMobileNo", "9800000013",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Role Test Admin"
        ));

        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms", firmRequest);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(200, status,
                "Firm creation should return 200, got: " + status + " — " +
                        result.getResponse().getContentAsString());

        // Verify firm was created in DB
        String firmId = parseResponse(result).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(java.util.UUID.fromString(firmId)).orElseThrow();

        // Verify roles were cloned (firm should have non-system roles)
        var firmRoles = roleRepository.findByFirmIdAndIsSystemFalse(firm.getId());
        Assertions.assertFalse(firmRoles.isEmpty(), "Firm should have cloned roles");

        // Verify key roles exist
        var roleCodes = firmRoles.stream().map(Role::getRoleCode).toList();
        Assertions.assertTrue(roleCodes.contains("FIRM_ADMIN"), "FIRM_ADMIN role should be cloned");
        Assertions.assertTrue(roleCodes.contains("ADVOCATE"), "ADVOCATE role should be cloned");
        Assertions.assertTrue(roleCodes.contains("PARALEGAL"), "PARALEGAL role should be cloned");
        Assertions.assertTrue(roleCodes.contains("CLIENT"), "CLIENT role should be cloned");

        // Verify SUPER_ADMIN is NOT cloned (it's platform-level only)
        Assertions.assertFalse(roleCodes.contains("SUPER_ADMIN"),
                "SUPER_ADMIN should NOT be cloned to firm");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. SuperAdmin Global Permissions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("SuperAdmin has GLOBAL scope permissions")
    void superAdmin_globalPermissions() throws Exception {
        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("globaladmin", "global@test.com", "pass",
                UserType.SUPER_ADMIN, saRole);
        String token = generateAccessToken(saUser);

        MvcResult result = authGet(token, "/api/v1/admin/roles");
        assertSuccess(result);
    }

    @Test
    @Order(11)
    @DisplayName("SuperAdmin can access firm admin listing")
    void superAdmin_accessFirmAdminEndpoints() throws Exception {
        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("saendpoint", "saendpoint@test.com", "pass",
                UserType.SUPER_ADMIN, saRole);
        String token = generateAccessToken(saUser);

        MvcResult result = authGet(token, "/api/v1/super-admin/firms/admins");
        int status = result.getResponse().getStatus();
        Assertions.assertNotEquals(403, status,
                "SuperAdmin should access firm-admins endpoint, got: " + status);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Non-SuperAdmin Cannot Access SuperAdmin Endpoints
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @DisplayName("Firm admin cannot access SuperAdmin-only endpoints")
    void firmAdmin_cannotAccessSuperAdminEndpoints() throws Exception {
        // Create a firm with admin
        registerSuperAdmin(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                "Test Super Admin", "9800000001", SUPER_SECRET);

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser(SA_USERNAME, SA_EMAIL, SA_PASSWORD,
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        var firmRequest = apiRequest(Map.of(
                "lawFirmCode", "ISOLATION",
                "name", "Isolation Test Firm",
                "firmType", "FIRM",
                "adminUsername", "isoadmin",
                "adminEmail", "iso@testfirm.com",
                "adminMobileNo", "9800000014",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Isolation Admin"
        ));
        authPost(saToken, "/api/v1/super-admin/firms", firmRequest);

        // Firm admin tries to access role management (SUPER_ADMIN only via @PreAuthorize)
        // Firm admin has FIRM_USER type with FIRM_ADMIN role — should get 403
        Firm isoFirm = firmRepository.findByLawFirmCode("ISOLATION").orElseThrow();
        User firmAdmin = userRepository.findByUsernameAndFirmId("isoadmin", isoFirm.getId())
                .orElseThrow();

        String adminToken = generateAccessToken(firmAdmin);

        MvcResult result = authGet(adminToken, "/api/v1/admin/roles");
        assertForbidden(result);
    }

    @Test
    @Order(13)
    @DisplayName("Unauthenticated request is rejected")
    void unauthenticatedRequest_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(14)
    @DisplayName("Invalid JWT token is rejected")
    void invalidToken_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", "Bearer invalid-token-here")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
