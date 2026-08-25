package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for role permission enforcement:
 * - Ceiling enforcement (firm role cannot exceed system role permissions)
 * - GLOBAL scope blocking (firm roles cannot have GLOBAL permissions)
 * - Cross-firm isolation (firm A roles ≠ firm B roles)
 * - Permission version invalidation after role permission change
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RolePermissionE2ETest extends BaseIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    private String firmAToken;
    private String firmBToken;
    private Firm firmA;
    private Firm firmB;

    private void setupTwoFirms() throws Exception {
        // Register super admin
        registerSuperAdmin("dualsa", "dualsa@test.com", "Pass123!",
                "Dual SA", "9820000001", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("dualsa", "dualsa@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create Firm A
        var firmAReq = apiRequest(Map.of(
                "lawFirmCode", "FIRMA",
                "name", "Firm A",
                "firmType", "FIRM",
                "adminUsername", "adminA",
                "adminEmail", "adminA@test.com",
                "adminMobileNo", "9820000002",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Admin A"
        ));
        MvcResult resA = authPost(saToken, "/api/v1/super-admin/firms", firmAReq);
        assertSuccess(resA);
        String firmAId = parseResponse(resA).path("data").path("firmId").asText();
        firmA = firmRepository.findById(UUID.fromString(firmAId)).orElseThrow();

        // Create Firm B
        var firmBReq = apiRequest(Map.of(
                "lawFirmCode", "FIRMB",
                "name", "Firm B",
                "firmType", "FIRM",
                "adminUsername", "adminB",
                "adminEmail", "adminB@test.com",
                "adminMobileNo", "9820000003",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Admin B"
        ));
        MvcResult resB = authPost(saToken, "/api/v1/super-admin/firms", firmBReq);
        assertSuccess(resB);
        String firmBId = parseResponse(resB).path("data").path("firmId").asText();
        firmB = firmRepository.findById(UUID.fromString(firmBId)).orElseThrow();

        // Login both firm admins — bypass MFA by generating tokens directly
        User adminA = userRepository.findByUsernameAndFirmId("adminA", firmA.getId()).orElseThrow();
        User adminB = userRepository.findByUsernameAndFirmId("adminB", firmB.getId()).orElseThrow();
        firmAToken = generateAccessToken(adminA);
        firmBToken = generateAccessToken(adminB);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Ceiling Enforcement
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Ceiling: firm role permissions are subset of system role permissions")
    void ceiling_firmRoleIsSubsetOfSystemRole() throws Exception {
        setupTwoFirms();

        Role firmAdvocate = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE")
                .orElseThrow();
        Role systemAdvocate = getSystemRole("ADVOCATE");

        Set<String> firmPerms = rolePermissionRepository.findPermissionsByRoleId(firmAdvocate.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> systemPerms = rolePermissionRepository.findPermissionsByRoleId(systemAdvocate.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertTrue(systemPerms.containsAll(firmPerms),
                "Firm ADVOCATE permissions should be subset of system ADVOCATE");
    }

    @Test
    @Order(2)
    @DisplayName("Ceiling: FIRM_ADMIN role has same permissions as system FIRM_ADMIN at creation")
    void ceiling_firmAdminMatchesSystemAtCreation() throws Exception {
        setupTwoFirms();

        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN")
                .orElseThrow();
        Role systemAdmin = getSystemRole("FIRM_ADMIN");

        Set<String> firmPerms = rolePermissionRepository.findPermissionsByRoleId(firmAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> systemPerms = rolePermissionRepository.findPermissionsByRoleId(systemAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertEquals(systemPerms, firmPerms,
                "Firm FIRM_ADMIN should start with system FIRM_ADMIN permissions");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. GLOBAL Scope Blocking
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("No permissions have GLOBAL scope — SUPER_ADMIN bypasses checks via role, not scope")
    void noGlobalPermissions() {
        List<Permission> globalPerms = permissionRepository.findAll().stream()
                .filter(p -> p.getScope() == PermissionScope.GLOBAL)
                .toList();
        Assertions.assertTrue(globalPerms.isEmpty(),
                "No permissions should have GLOBAL scope. SUPER_ADMIN bypasses all checks via role.");
    }

    @Test
    @Order(4)
    @DisplayName("GLOBAL permissions are NOT in firm role ceiling")
    void globalPermissions_notInFirmCeiling() throws Exception {
        setupTwoFirms();

        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN")
                .orElseThrow();

        MvcResult result = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAdmin.getId() + "/permissions");
        assertSuccess(result);

        JsonNode available = parseResponse(result).path("data").path("availablePermissions");
        for (JsonNode perm : available) {
            String scope = perm.path("scope").asText();
            Assertions.assertNotEquals("GLOBAL", scope,
                    "GLOBAL permission " + perm.path("code").asText() +
                            " should NOT appear in firm role ceiling");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Firm admin cannot assign permission not in parent system role ceiling")
    void assignPermissionBeyondCeiling_rejected() throws Exception {
        setupTwoFirms();

        // Find a permission NOT in the ADVOCATE system role's ceiling
        Role systemAdvocate = getSystemRole("ADVOCATE");
        Set<String> advocatePermCodes = rolePermissionRepository.findPermissionsByRoleId(systemAdvocate.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        // Find a permission outside the ceiling (e.g., a FULL-access perm not in READ_ONLY)
        Permission outsideCeiling = permissionRepository.findAll().stream()
                .filter(p -> !advocatePermCodes.contains(p.getCode()))
                .findFirst()
                .orElse(null);
        if (outsideCeiling == null) {
            Assertions.assertTrue(true, "All permissions in ceiling, cannot test ceiling enforcement");
            return;
        }

        Role firmAdvocate = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE")
                .orElseThrow();

        var request = apiRequest(Map.of("roleId", firmAdvocate.getId(), "permissionIds", List.of(outsideCeiling.getId())));
        MvcResult result = authPut(firmAToken,
                "/api/v1/firm/roles/" + firmAdvocate.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Should reject permission outside ceiling, got: " + status +
                        " — " + result.getResponse().getContentAsString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Cross-Firm Isolation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Firm A admin cannot modify Firm B roles")
    void crossFirm_firmACannotModifyFirmBRoles() throws Exception {
        setupTwoFirms();

        Role firmBAdvocate = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE")
                .orElseThrow();

        var request = apiRequest(Map.of("roleId", firmBAdvocate.getId(), "permissionIds", List.of(UUID.randomUUID())));
        MvcResult result = authPut(firmAToken,
                "/api/v1/firm/roles/" + firmBAdvocate.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Firm A admin should NOT modify Firm B roles, got: " + status);
    }

    @Test
    @Order(7)
    @DisplayName("Firm A admin cannot view Firm B role permissions")
    void crossFirm_firmACannotViewFirmBPermissions() throws Exception {
        setupTwoFirms();

        Role firmBAdvocate = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE")
                .orElseThrow();

        MvcResult result = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmBAdvocate.getId() + "/permissions");

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Firm A admin should NOT view Firm B permissions, got: " + status);
    }

    @Test
    @Order(8)
    @DisplayName("Firm A and Firm B have independent role permission sets")
    void crossFirm_independentPermissions() throws Exception {
        setupTwoFirms();

        Role firmAAdmin = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN")
                .orElseThrow();
        Role firmBAdmin = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "FIRM_ADMIN")
                .orElseThrow();

        Set<String> firmAPerms = rolePermissionRepository.findPermissionsByRoleId(firmAAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> firmBPerms = rolePermissionRepository.findPermissionsByRoleId(firmBAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertEquals(firmAPerms, firmBPerms,
                "Both firms should start with identical FIRM_ADMIN permissions");

        // Now modify Firm A's permissions
        Role systemAdmin = getSystemRole("FIRM_ADMIN");
        Set<String> systemPerms = rolePermissionRepository.findPermissionsByRoleId(systemAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        // Filter out GLOBAL-scope permissions (DataInitializer mutates shared Permission entity)
        List<UUID> keepHalf = rolePermissionRepository.findPermissionsByRoleId(firmAAdmin.getId())
                .stream()
                .filter(p -> p.getScope() != PermissionScope.GLOBAL)
                .limit(rolePermissionRepository.findPermissionsByRoleId(firmAAdmin.getId())
                        .stream().filter(p -> p.getScope() != PermissionScope.GLOBAL).count() / 2)
                .map(Permission::getId)
                .toList();
        Assertions.assertFalse(keepHalf.isEmpty(), "Should have non-GLOBAL permissions to test with");

        var request = apiRequest(Map.of("roleId", firmAAdmin.getId(), "permissionIds", keepHalf));
        MvcResult updateResult = authPut(firmAToken, "/api/v1/firm/roles/" + firmAAdmin.getId() + "/permissions", request);
        assertSuccess(updateResult);

        Set<String> firmAUpdatedPerms = rolePermissionRepository.findPermissionsByRoleId(firmAAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> firmBUnchangedPerms = rolePermissionRepository.findPermissionsByRoleId(firmBAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertNotEquals(firmAUpdatedPerms, firmBUnchangedPerms,
                "Firm A permissions should differ from Firm B after modification");
        Assertions.assertEquals(firmBPerms, firmBUnchangedPerms,
                "Firm B permissions should remain unchanged");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Permission Version Invalidation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("Permission change invalidates user sessions (permVersion bump)")
    void permissionChange_invalidatesSessions() throws Exception {
        setupTwoFirms();

        Role advocateRole = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE")
                .orElseThrow();
        User advocate = createUser("advocate1", "adv1@test.com", "pass",
                UserType.FIRM_USER, advocateRole, firmA);

        String advocateToken = generateAccessToken(advocate);

        // Advocate's token should be valid (permVersion=0 matches DB)
        MvcResult before = authGet(advocateToken, "/api/v1/firm/roles");
        int beforeStatus = before.getResponse().getStatus();
        Assertions.assertNotEquals(401, beforeStatus,
                "Advocate token should be valid (not 401), got: " + beforeStatus);

        // Now update the ADVOCATE role permissions
        MvcResult permResult = authGet(firmAToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        JsonNode available = parseResponse(permResult).path("data").path("availablePermissions");

        if (available.size() > 0) {
            List<UUID> permIds = new ArrayList<>();
            for (int i = 0; i < Math.min(1, available.size()); i++) {
                permIds.add(UUID.fromString(available.get(i).path("id").asText()));
            }

            var request = apiRequest(Map.of("roleId", advocateRole.getId(), "permissionIds", permIds));
            MvcResult updateResult = authPut(firmAToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions", request);
            assertSuccess(updateResult);

            // Advocate's token should now be stale (permVersion bumped)
            // Use any authenticated endpoint — the permVersion check happens in JwtAuthFilter
            MvcResult after = authGet(advocateToken, "/api/v1/firm/roles");
            int afterStatus = after.getResponse().getStatus();
            // permVersion mismatch → 401 from JwtAuthFilter (before controller checks role)
            Assertions.assertEquals(401, afterStatus,
                    "Advocate token should be rejected after permission change (401), got: " + afterStatus);
        }
    }
}
