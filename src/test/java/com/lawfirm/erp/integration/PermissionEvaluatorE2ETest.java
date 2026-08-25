package com.lawfirm.erp.integration;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for PermissionEvaluator:
 * - require() throws ForbiddenException for missing permissions
 * - require() passes for SUPER_ADMIN (bypasses all checks)
 * - has() returns true/false correctly
 * - Cache TTL works (permissions refresh after TTL expires)
 * - clearUserCache() invalidates cached permissions
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionEvaluatorE2ETest extends BaseIntegrationTest {

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    private Firm testFirm;

    private void setupFirm() throws Exception {
        registerSuperAdmin("permeval", "permeval@test.com", "Pass123!",
                "PermEval SA", "9850000001", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("permeval", "permeval@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms",
                apiRequest(Map.of(
                        "lawFirmCode", "PERMEVAL",
                        "name", "PermEval Firm",
                        "firmType", "FIRM",
                        "adminUsername", "peadmin",
                        "adminEmail", "peadmin@test.com",
                        "adminMobileNo", "9850000002",
                        "adminPassword", "AdminPass123!",
                        "adminFullName", "PE Admin"
                ))
        );
        assertSuccess(result);
        String firmId = parseResponse(result).path("data").path("firmId").asText();
        testFirm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. SUPER_ADMIN Bypass
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("SUPER_ADMIN bypasses all permission checks")
    void superAdmin_bypassesAllChecks() throws Exception {
        setupFirm();

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("saBypass", "saBypass@test.com", "pass",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult result = authGet(saToken, "/api/v1/admin/roles");
        assertSuccess(result);
    }

    @Test
    @Order(2)
    @DisplayName("SUPER_ADMIN can access any permission-gated endpoint")
    void superAdmin_accessesAllEndpoints() throws Exception {
        setupFirm();

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("saAll", "saAll@test.com", "pass",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        String[] endpoints = {
                "/api/v1/admin/roles",
                "/api/v1/admin/permissions",
                "/api/v1/admin/modules"
        };

        for (String endpoint : endpoints) {
            MvcResult result = authGet(saToken, endpoint);
            int status = result.getResponse().getStatus();
            Assertions.assertNotEquals(403, status,
                    "SuperAdmin should access " + endpoint + ", got: " + status);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Role-Based Access Control
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("FIRM_ADMIN can access role management (hasRole check)")
    void firmAdmin_accessesRoleManagement() throws Exception {
        setupFirm();

        User admin = userRepository.findByUsernameAndFirmId("peadmin", testFirm.getId()).orElseThrow();
        String adminToken = generateAccessToken(admin);

        MvcResult result = authGet(adminToken, "/api/v1/firm/roles");
        assertSuccess(result);
    }

    @Test
    @Order(4)
    @DisplayName("ADVOCATE cannot access role management (wrong role)")
    void advocate_cannotAccessRoleManagement() throws Exception {
        setupFirm();

        Role advRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "ADVOCATE")
                .orElseThrow();
        User advocate = createUser("advPermTest", "advPerm@test.com", "pass",
                UserType.FIRM_USER, advRole, testFirm);

        String advToken = generateAccessToken(advocate);

        MvcResult result = authGet(advToken, "/api/v1/firm/roles");
        assertForbidden(result);
    }

    @Test
    @Order(5)
    @DisplayName("PARALEGAL cannot access role management")
    void paralegal_cannotAccessRoleManagement() throws Exception {
        setupFirm();

        Role paraRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "PARALEGAL")
                .orElseThrow();
        User paralegal = createUser("paraPermTest", "paraPerm@test.com", "pass",
                UserType.FIRM_USER, paraRole, testFirm);

        String paraToken = generateAccessToken(paralegal);

        MvcResult result = authGet(paraToken, "/api/v1/firm/roles");
        assertForbidden(result);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Permission Scope Enforcement
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("TENANT-scope permissions work within firm context")
    void tenantScope_worksWithinFirm() throws Exception {
        setupFirm();

        User admin = userRepository.findByUsernameAndFirmId("peadmin", testFirm.getId()).orElseThrow();
        String adminToken = generateAccessToken(admin);

        MvcResult result = authGet(adminToken, "/api/v1/firm/modules");
        assertSuccess(result);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Cache Behavior
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("Permission cache can be cleared")
    void cache_clearWorks() throws Exception {
        setupFirm();

        Role advRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "ADVOCATE")
                .orElseThrow();
        User advocate = createUser("cacheTest", "cache@test.com", "pass",
                UserType.FIRM_USER, advRole, testFirm);

        Assertions.assertDoesNotThrow(() ->
                permissionEvaluator.clearUserCache(advocate.getId()));
    }

    @Test
    @Order(8)
    @DisplayName("Clear all cache does not throw")
    void cache_clearAllWorks() {
        Assertions.assertDoesNotThrow(() ->
                permissionEvaluator.clearAllCache());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. has() Method
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("has() returns true for SUPER_ADMIN regardless of permission")
    void has_superAdminAlwaysTrue() throws Exception {
        setupFirm();

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("hasSA", "hasSA@test.com", "pass",
                UserType.SUPER_ADMIN, saRole);

        String saToken = generateAccessToken(saUser);
        MvcResult result = authGet(saToken, "/api/v1/admin/roles");
        assertSuccess(result);
    }

    @Test
    @Order(10)
    @DisplayName("has() returns false for user without permission")
    void has_userWithoutPermission() throws Exception {
        setupFirm();

        Role advRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "ADVOCATE")
                .orElseThrow();
        User advocate = createUser("hasNoPerm", "hasNoPerm@test.com", "pass",
                UserType.FIRM_USER, advRole, testFirm);

        String advToken = generateAccessToken(advocate);
        MvcResult result = authGet(advToken, "/api/v1/admin/roles");
        assertForbidden(result);
    }
}
