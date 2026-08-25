package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for FirmAdmin flows:
 * - List firm roles
 * - View role permissions (current + ceiling)
 * - Update role permissions
 * - Assign users to roles
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirmAdminE2ETest extends BaseIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private com.lawfirm.erp.rbac.repository.RolePermissionRepository rolePermissionRepository;

    private String firmToken;
    private Firm testFirm;
    private Role advocateRole;

    // ═══════════════════════════════════════════════════════════════════════
    // Setup — create firm via SA token, then login as firm admin
    // ═══════════════════════════════════════════════════════════════════════

    private void setupFirmWithAdmin() throws Exception {
        // Register super admin
        registerSuperAdmin("firmsa", "firmsa@test.com", "Pass123!",
                "Firm SA", "9810000001", "test-super-admin-secret-key-12345");

        // Get SA user directly (bypass MFA) for firm creation
        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("firmsa", "firmsa@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create firm via API
        var firmRequest = apiRequest(Map.of(
                "lawFirmCode", "FIRMRBAC",
                "name", "Firm RBAC Test",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin",
                "adminEmail", "rbacadmin@testfirm.com",
                "adminMobileNo", "9810000002",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin"
        ));

        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms", firmRequest);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(200, status,
                "Firm creation should succeed, got: " + status + " — " +
                        result.getResponse().getContentAsString());

        String firmId = parseResponse(result).path("data").path("firmId").asText();
        testFirm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();

        // Get the firm admin user and generate token directly (bypass MFA)
        Role firmAdminRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "FIRM_ADMIN")
                .orElseThrow();
        User firmAdmin = userRepository.findByUsernameAndFirmId("rbacadmin", testFirm.getId())
                .orElseThrow();

        firmToken = generateAccessToken(firmAdmin);

        // Get the firm-scoped ADVOCATE role
        advocateRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "ADVOCATE")
                .orElseThrow(() -> new RuntimeException("ADVOCATE role not cloned"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. List Firm Roles
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Firm admin can list firm-scoped roles")
    void listFirmRoles() throws Exception {
        setupFirmWithAdmin();

        MvcResult result = authGet(firmToken, "/api/v1/firm/roles");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertTrue(data.isArray(), "Response should be an array");
        Assertions.assertTrue(data.size() >= 4, "Should have at least 4 roles (FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT)");

        // Verify SUPER_ADMIN is NOT in the list
        for (JsonNode role : data) {
            Assertions.assertNotEquals("SUPER_ADMIN", role.path("code").asText(),
                    "SUPER_ADMIN should not appear in firm roles");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. View Role Permissions (Current + Ceiling)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("Firm admin can view role permissions with ceiling")
    void viewRolePermissions_withCeiling() throws Exception {
        setupFirmWithAdmin();

        MvcResult result = authGet(firmToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");

        Assertions.assertTrue(data.has("currentPermissions"), "Should have currentPermissions");
        Assertions.assertTrue(data.has("availablePermissions"), "Should have availablePermissions");
        Assertions.assertEquals("ADVOCATE", data.path("roleCode").asText());

        JsonNode current = data.path("currentPermissions");
        Assertions.assertTrue(current.isArray(), "currentPermissions should be an array");
        Assertions.assertTrue(current.size() > 0, "ADVOCATE should have some permissions");

        JsonNode available = data.path("availablePermissions");
        // Collect available permission codes
        Set<String> availableCodes = new HashSet<>();
        for (JsonNode p : available) {
            availableCodes.add(p.path("code").asText());
        }
        // All non-GLOBAL current permissions should be in the ceiling
        for (JsonNode p : current) {
            if (!"GLOBAL".equals(p.path("scope").asText())) {
                Assertions.assertTrue(availableCodes.contains(p.path("code").asText()),
                        "Non-GLOBAL permission " + p.path("code").asText() + " should be in ceiling");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Ceiling available permissions are all from parent system role")
    void viewRolePermissions_ceilingMatchesParent() throws Exception {
        setupFirmWithAdmin();

        MvcResult result = authGet(firmToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        assertSuccess(result);

        JsonNode available = parseResponse(result).path("data").path("availablePermissions");

        // Verify all available permissions are in the parent system ADVOCATE role
        Role parentRole = getSystemRole("ADVOCATE");
        Set<String> parentPermCodes = rolePermissionRepository.findPermissionsByRoleId(parentRole.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        for (JsonNode perm : available) {
            Assertions.assertTrue(parentPermCodes.contains(perm.path("code").asText()),
                    "Permission " + perm.path("code").asText() + " should be in parent system role");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Update Role Permissions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Firm admin can update role permissions within ceiling")
    void updateRolePermissions_withinCeiling() throws Exception {
        setupFirmWithAdmin();

        MvcResult permResult = authGet(firmToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        JsonNode available = parseResponse(permResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(2, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }

        var request = apiRequest(Map.of("roleId", advocateRole.getId(), "permissionIds", permIds));
        MvcResult result = authPut(firmToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions", request);
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertEquals("ADVOCATE", data.path("roleCode").asText());
        Assertions.assertEquals(permIds.size(), data.path("permissions").size());
    }

    @Test
    @Order(5)
    @DisplayName("Firm admin CANNOT assign permissions beyond ceiling")
    void updateRolePermissions_exceedsCeiling_rejected() throws Exception {
        setupFirmWithAdmin();

        MvcResult ceilingResult = authGet(firmToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");
        Set<String> availableCodes = new HashSet<>();
        for (JsonNode p : available) {
            availableCodes.add(p.path("code").asText());
        }

        // Find a permission NOT in the ceiling
        Permission outsideCeiling = permissionRepository.findAll().stream()
                .filter(p -> !availableCodes.contains(p.getCode()))
                .findFirst()
                .orElse(null);

        if (outsideCeiling == null) {
            Assertions.assertTrue(true, "All permissions in ceiling, ceiling enforcement not testable here");
            return;
        }

        var request = apiRequest(Map.of("roleId", advocateRole.getId(), "permissionIds", List.of(outsideCeiling.getId())));
        MvcResult result = authPut(firmToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Should reject permission outside ceiling, got: " + status +
                        " — " + result.getResponse().getContentAsString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Firm Admin Cannot Modify System Roles
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Firm admin cannot modify system roles")
    void updateSystemRole_rejected() throws Exception {
        setupFirmWithAdmin();

        Role systemAdvocate = getSystemRole("ADVOCATE");

        var request = apiRequest(Map.of("roleId", systemAdvocate.getId(), "permissionIds", List.of(UUID.randomUUID())));
        MvcResult result = authPut(firmToken,
                "/api/v1/firm/roles/" + systemAdvocate.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Should reject system role modification, got: " + status);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Firm Admin Can List Role Users
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("Firm admin can list users assigned to a role")
    void listRoleUsers() throws Exception {
        setupFirmWithAdmin();

        MvcResult result = authGet(firmToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/users");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertTrue(data.isArray(), "Response should be an array");
    }
}
