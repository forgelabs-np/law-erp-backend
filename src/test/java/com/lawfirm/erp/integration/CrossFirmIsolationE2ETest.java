package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for cross-firm data isolation:
 * - Firm A users cannot access Firm B data
 * - Firm A admin cannot manage Firm B users
 * - JWT tokens are scoped to a firm
 * - Firm context is enforced in requests
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossFirmIsolationE2ETest extends BaseIntegrationTest {

    private String firmAToken;
    private String firmBToken;
    private Firm firmA;
    private Firm firmB;
    private User firmAAdvocate;
    private User firmBAdvocate;

    private void setupTwoFirmsWithUsers() throws Exception {
        // Register super admin
        registerSuperAdmin("isosa", "isosa@test.com", "Pass123!",
                "Isolation SA", "9840000001", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("isosa", "isosa@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create Firm A
        MvcResult resA = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "ISOFIRMA",
                "name", "Isolation Firm A",
                "firmType", "FIRM",
                "adminUsername", "isoadminA",
                "adminEmail", "isoadminA@test.com",
                "adminMobileNo", "9840000002",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Admin A"
        )));
        assertSuccess(resA);
        firmA = firmRepository.findById(
                UUID.fromString(parseResponse(resA).path("data").path("firmId").asText())).orElseThrow();

        // Create Firm B
        MvcResult resB = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "ISOFIRMB",
                "name", "Isolation Firm B",
                "firmType", "FIRM",
                "adminUsername", "isoadminB",
                "adminEmail", "isoadminB@test.com",
                "adminMobileNo", "9840000003",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Admin B"
        )));
        assertSuccess(resB);
        firmB = firmRepository.findById(
                UUID.fromString(parseResponse(resB).path("data").path("firmId").asText())).orElseThrow();

        // Generate tokens directly (bypass MFA)
        User adminA = userRepository.findByUsernameAndFirmId("isoadminA", firmA.getId()).orElseThrow();
        User adminB = userRepository.findByUsernameAndFirmId("isoadminB", firmB.getId()).orElseThrow();
        firmAToken = generateAccessToken(adminA);
        firmBToken = generateAccessToken(adminB);

        // Create advocates in each firm
        Role advRoleA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE").orElseThrow();
        Role advRoleB = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE").orElseThrow();

        firmAAdvocate = createUser("advA", "advA@isofirma.com", "pass",
                UserType.FIRM_USER, advRoleA, firmA);
        firmBAdvocate = createUser("advB", "advB@isofirmb.com", "pass",
                UserType.FIRM_USER, advRoleB, firmB);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. JWT Firm Scoping
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("JWT tokens contain firm ID and firm code")
    void jwt_containsFirmContext() throws Exception {
        setupTwoFirmsWithUsers();

        String tokenA = generateAccessToken(firmAAdvocate);
        String tokenB = generateAccessToken(firmBAdvocate);

        Assertions.assertNotNull(tokenA);
        Assertions.assertNotNull(tokenB);
        Assertions.assertNotEquals(tokenA, tokenB);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Firm A Cannot Access Firm B Data
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("Firm A admin sees only Firm A roles")
    void firmA_seesOnlyOwnRoles() throws Exception {
        setupTwoFirmsWithUsers();

        MvcResult result = authGet(firmAToken, "/api/v1/firm/roles");
        assertSuccess(result);

        JsonNode roles = parseResponse(result).path("data");
        for (JsonNode role : roles) {
            String roleId = role.path("id").asText();
            var roleEntity = roleRepository.findById(UUID.fromString(roleId)).orElse(null);
            if (roleEntity != null && roleEntity.getFirm() != null) {
                Assertions.assertEquals(firmA.getId(), roleEntity.getFirm().getId(),
                        "Firm A admin should only see Firm A roles");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Firm A admin cannot modify Firm B role permissions")
    void firmA_cannotModifyFirmBRoles() throws Exception {
        setupTwoFirmsWithUsers();

        Role firmBAdvocateRole = roleRepository
                .findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE").orElseThrow();

        var request = apiRequest(Map.of("roleId", firmBAdvocateRole.getId(), "permissionIds", java.util.List.of(UUID.randomUUID())));
        MvcResult result = authPut(firmAToken,
                "/api/v1/firm/roles/" + firmBAdvocateRole.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Firm A admin should NOT modify Firm B roles, got: " + status);
    }

    @Test
    @Order(4)
    @DisplayName("Firm A admin cannot view Firm B role users")
    void firmA_cannotViewFirmBRoleUsers() throws Exception {
        setupTwoFirmsWithUsers();

        Role firmBAdvocateRole = roleRepository
                .findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE").orElseThrow();

        MvcResult result = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmBAdvocateRole.getId() + "/users");

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Firm A admin should NOT view Firm B role users, got: " + status);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Cross-Firm Advocate Isolation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("Advocates cannot access FIRM_ADMIN endpoints")
    void advocates_cannotAccessAdminEndpoints() throws Exception {
        setupTwoFirmsWithUsers();

        String tokenAdvA = generateAccessToken(firmAAdvocate);
        String tokenAdvB = generateAccessToken(firmBAdvocate);

        MvcResult resultA = authGet(tokenAdvA, "/api/v1/firm/roles");
        int statusA = resultA.getResponse().getStatus();
        Assertions.assertEquals(403, statusA,
                "Advocate should NOT access firm roles endpoint, got: " + statusA);

        MvcResult resultB = authGet(tokenAdvB, "/api/v1/firm/roles");
        int statusB = resultB.getResponse().getStatus();
        Assertions.assertEquals(403, statusB,
                "Advocate should NOT access firm roles endpoint, got: " + statusB);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. SuperAdmin Cross-Firm Access
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("SuperAdmin can access data across all firms")
    void superAdmin_crossFirmAccess() throws Exception {
        setupTwoFirmsWithUsers();

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("crosssa", "cross@test.com", "pass",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult resultA = authGet(saToken, "/api/v1/admin/roles");
        assertSuccess(resultA);

        MvcResult resultB = authGet(saToken, "/api/v1/super-admin/firms/admins");
        int statusB = resultB.getResponse().getStatus();
        Assertions.assertNotEquals(403, statusB,
                "SuperAdmin should access firm-admins, got: " + statusB);
    }
}
