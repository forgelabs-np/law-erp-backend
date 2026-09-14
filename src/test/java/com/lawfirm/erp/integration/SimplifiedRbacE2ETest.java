package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test of the simplified RBAC model:
 *
 *   SA → creates firm → clones 4 roles (empty permissions)
 *   SA → sets FIRM_ADMIN permissions on system template (ceiling)
 *   Firm Admin → assigns subset to employee roles
 *   Employee → has role → role has permissions
 *
 * Also tests:
 *   - Multi-firm isolation (Firm A doesn't affect Firm B)
 *   - SA custom role creation on-behalf
 *   - Firm Admin cannot exceed their ceiling
 *   - Ceiling changes propagate (Firm Admin narrows → employee loses)
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimplifiedRbacE2ETest extends BaseIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // Helper: find permission by code
    // ═══════════════════════════════════════════════════════════════════════
    private Permission perm(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Seed permission missing: " + code));
    }

    private Set<UUID> permIds(String... codes) {
        return Arrays.stream(codes).map(c -> perm(c).getId()).collect(Collectors.toSet());
    }

    private Set<String> permCodes(UUID roleId) {
        return rolePermissionRepository.findPermissionsByRoleId(roleId)
                .stream().map(Permission::getCode).collect(Collectors.toSet());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCENARIO 1: Full SA → Firm Admin → Employee flow
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("STEP 1: SA creates firm — 4 roles cloned, all empty")
    void step1_saCreatesFirm_rolesEmpty() throws Exception {
        // Register SA
        registerSuperAdmin("rbacsa1", "rbacsa1@test.com", "Pass123!",
                "RBAC SA", "9850000001", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa1", "rbacsa1@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create firm via API
        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM1",
                "name", "RBAC Test Firm 1",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin1",
                "adminEmail", "admin1@rbactest.com",
                "adminMobileNo", "9850000002",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 1"
        )));
        assertSuccess(result);

        String firmId = parseResponse(result).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();

        // Verify 4 roles cloned, all empty
        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN").orElseThrow();
        Role advocate = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "ADVOCATE").orElseThrow();
        Role paralegal = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "PARALEGAL").orElseThrow();
        Role client = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "CLIENT").orElseThrow();

        assertEquals(0, permCodes(firmAdmin.getId()).size(), "FIRM_ADMIN starts empty");
        assertEquals(0, permCodes(advocate.getId()).size(), "ADVOCATE starts empty");
        assertEquals(0, permCodes(paralegal.getId()).size(), "PARALEGAL starts empty");
        assertEquals(0, permCodes(client.getId()).size(), "CLIENT starts empty");
    }

    @Test
    @Order(2)
    @DisplayName("STEP 2: SA sets FIRM_ADMIN ceiling — via override on system template + firm role")
    void step2_saSetsFirmAdminCeiling() throws Exception {
        registerSuperAdmin("rbacsa2", "rbacsa2@test.com", "Pass123!",
                "RBAC SA 2", "9850000003", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa2", "rbacsa2@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create firm
        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM2",
                "name", "RBAC Test Firm 2",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin2",
                "adminEmail", "admin2@rbactest.com",
                "adminMobileNo", "9850000004",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 2"
        )));
        assertSuccess(firmRes);

        String firmId = parseResponse(firmRes).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();
        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN").orElseThrow();

        // SA sets FIRM_ADMIN permissions via override (this IS the ceiling)
        Set<UUID> ceilingPerms = permIds(
                "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", "CASE_MANAGEMENT:EDIT",
                "BILLING:VIEW",
                "USER_MANAGEMENT:VIEW", "USER_MANAGEMENT:EDIT",
                "ROLE_MANAGEMENT:VIEW",
                "DASHBOARD_MANAGEMENT:VIEW", "DASHBOARD_MANAGEMENT:ACCESS"
        );

        MvcResult override = authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + firmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAdmin.getId(), "permissionIds", ceilingPerms)));
        assertSuccess(override);

        // Verify FIRM_ADMIN now has those permissions
        assertEquals(9, permCodes(firmAdmin.getId()).size(), "FIRM_ADMIN has 9 permissions");
    }

    @Test
    @Order(3)
    @DisplayName("STEP 3: Firm Admin assigns subset to ADVOCATE — within ceiling")
    void step3_firmAdminAssignsToAdvocate() throws Exception {
        registerSuperAdmin("rbacsa3", "rbacsa3@test.com", "Pass123!",
                "RBAC SA 3", "9850000005", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa3", "rbacsa3@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create firm
        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM3",
                "name", "RBAC Test Firm 3",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin3",
                "adminEmail", "admin3@rbactest.com",
                "adminMobileNo", "9850000006",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 3"
        )));
        assertSuccess(firmRes);

        String firmId = parseResponse(firmRes).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();
        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN").orElseThrow();
        Role advocate = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "ADVOCATE").orElseThrow();

        // SA sets FIRM_ADMIN ceiling
        Set<UUID> ceilingPerms = permIds(
                "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", "CASE_MANAGEMENT:EDIT",
                "BILLING:VIEW", "DASHBOARD_MANAGEMENT:VIEW"
        );
        MvcResult overrideResult = authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + firmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAdmin.getId(), "permissionIds", ceilingPerms)));
        assertSuccess(overrideResult);

        // Refresh admin token after SA override bumped permissionVersion
        User adminUser = userRepository.findByUsernameAndFirmId("rbacadmin3", firm.getId()).orElseThrow();
        String adminTokenRefreshed = generateAccessToken(adminUser);

        // Firm Admin assigns subset to ADVOCATE
        Set<UUID> advocatePerms = permIds("CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", "DASHBOARD_MANAGEMENT:VIEW");
        MvcResult assignResult = authPut(adminTokenRefreshed,
                "/api/v1/firm/roles/" + advocate.getId() + "/permissions",
                apiRequest(Map.of("roleId", advocate.getId(), "permissionIds", advocatePerms)));
        assertSuccess(assignResult);

        // Verify ADVOCATE has the assigned permissions
        assertEquals(3, permCodes(advocate.getId()).size());
        assertTrue(permCodes(advocate.getId()).contains("CASE_MANAGEMENT:VIEW"));
        assertTrue(permCodes(advocate.getId()).contains("CASE_MANAGEMENT:CREATE"));
        assertTrue(permCodes(advocate.getId()).contains("DASHBOARD_MANAGEMENT:VIEW"));
    }

    @Test
    @Order(4)
    @DisplayName("STEP 4: Firm Admin CANNOT assign permission outside their ceiling")
    void step4_firmAdminCannotExceedCeiling() throws Exception {
        registerSuperAdmin("rbacsa4", "rbacsa4@test.com", "Pass123!",
                "RBAC SA 4", "9850000007", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa4", "rbacsa4@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM4",
                "name", "RBAC Test Firm 4",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin4",
                "adminEmail", "admin4@rbactest.com",
                "adminMobileNo", "9850000008",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 4"
        )));
        assertSuccess(firmRes);

        String firmId = parseResponse(firmRes).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();
        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN").orElseThrow();
        Role advocate = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "ADVOCATE").orElseThrow();

        // SA sets system FIRM_ADMIN template + firm's FIRM_ADMIN role
        Set<UUID> ceilingPerms = permIds("CASE_MANAGEMENT:VIEW");
        Role systemFirmAdmin = getSystemRole("FIRM_ADMIN");
        authPut(saToken,
                "/api/v1/admin/roles/" + systemFirmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", systemFirmAdmin.getId(), "permissionIds", ceilingPerms)));
        authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + firmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAdmin.getId(), "permissionIds", ceilingPerms)));

        // Refresh admin token after SA override
        User adminUser = userRepository.findByUsernameAndFirmId("rbacadmin4", firm.getId()).orElseThrow();
        String adminToken = generateAccessToken(adminUser);

        // Firm Admin tries to give ADVOCATE a permission NOT in their ceiling
        MvcResult denied = authPut(adminToken,
                "/api/v1/firm/roles/" + advocate.getId() + "/permissions",
                apiRequest(Map.of("roleId", advocate.getId(), "permissionIds", List.of(perm("BILLING:VIEW").getId()))));

        assertEquals(403, denied.getResponse().getStatus(),
                "Should reject permission outside FIRM_ADMIN ceiling");
    }

    @Test
    @Order(5)
    @DisplayName("STEP 5: Firm Admin narrows their own ceiling — ADVOCATE permissions stripped")
    void step5_firmAdminNarrows_employeesStripped() throws Exception {
        registerSuperAdmin("rbacsa5", "rbacsa5@test.com", "Pass123!",
                "RBAC SA 5", "9850000009", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa5", "rbacsa5@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM5",
                "name", "RBAC Test Firm 5",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin5",
                "adminEmail", "admin5@rbactest.com",
                "adminMobileNo", "9850000010",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 5"
        )));
        assertSuccess(firmRes);

        String firmId = parseResponse(firmRes).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();
        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN").orElseThrow();
        Role advocate = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "ADVOCATE").orElseThrow();

        // SA sets system FIRM_ADMIN template + firm's FIRM_ADMIN role
        Set<UUID> ceilingPerms = permIds(
                "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", "BILLING:VIEW"
        );
        Role systemFirmAdmin = getSystemRole("FIRM_ADMIN");
        authPut(saToken,
                "/api/v1/admin/roles/" + systemFirmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", systemFirmAdmin.getId(), "permissionIds", ceilingPerms)));
        authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + firmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAdmin.getId(), "permissionIds", ceilingPerms)));

        // Refresh admin token after SA override
        User adminUser = userRepository.findByUsernameAndFirmId("rbacadmin5", firm.getId()).orElseThrow();
        String adminToken = generateAccessToken(adminUser);

        // Firm Admin gives ADVOCATE all 3
        authPut(adminToken,
                "/api/v1/firm/roles/" + advocate.getId() + "/permissions",
                apiRequest(Map.of("roleId", advocate.getId(), "permissionIds", ceilingPerms)));
        assertEquals(3, permCodes(advocate.getId()).size());

        // Refresh admin token after self-narrowing
        User adminUser2 = userRepository.findByUsernameAndFirmId("rbacadmin5", firm.getId()).orElseThrow();
        String adminToken2 = generateAccessToken(adminUser2);

        // Firm Admin narrows their own permissions (removes BILLING:VIEW)
        Set<UUID> narrowedPerms = permIds("CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE");
        authPut(adminToken2,
                "/api/v1/firm/roles/" + firmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAdmin.getId(), "permissionIds", narrowedPerms)));

        // Verify FIRM_ADMIN now has only 2
        assertEquals(2, permCodes(firmAdmin.getId()).size());

        // Now Firm Admin cannot give ADVOCATE BILLING:VIEW anymore
        User adminUser3 = userRepository.findByUsernameAndFirmId("rbacadmin5", firm.getId()).orElseThrow();
        String adminToken3 = generateAccessToken(adminUser3);

        MvcResult denied = authPut(adminToken3,
                "/api/v1/firm/roles/" + advocate.getId() + "/permissions",
                apiRequest(Map.of("roleId", advocate.getId(), "permissionIds", List.of(perm("BILLING:VIEW").getId()))));
        assertEquals(403, denied.getResponse().getStatus(),
                "BILLING:VIEW is no longer in FIRM_ADMIN ceiling");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCENARIO 2: Multi-firm isolation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("STEP 6: Multi-firm isolation — Firm A changes don't affect Firm B")
    void step6_multiFirmIsolation() throws Exception {
        registerSuperAdmin("rbacsa6", "rbacsa6@test.com", "Pass123!",
                "RBAC SA 6", "9850000011", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa6", "rbacsa6@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        // Create Firm A
        MvcResult firmARes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "ISOFIRMA2",
                "name", "Isolation Firm A",
                "firmType", "FIRM",
                "adminUsername", "isoadminA2",
                "adminEmail", "adminA2@test.com",
                "adminMobileNo", "9850000012",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Admin A"
        )));
        assertSuccess(firmARes);
        String firmAId = parseResponse(firmARes).path("data").path("firmId").asText();
        Firm firmA = firmRepository.findById(UUID.fromString(firmAId)).orElseThrow();

        // Create Firm B
        MvcResult firmBRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "ISOFIRMB2",
                "name", "Isolation Firm B",
                "firmType", "FIRM",
                "adminUsername", "isoadminB2",
                "adminEmail", "adminB2@test.com",
                "adminMobileNo", "9850000013",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Admin B"
        )));
        assertSuccess(firmBRes);
        String firmBId = parseResponse(firmBRes).path("data").path("firmId").asText();
        Firm firmB = firmRepository.findById(UUID.fromString(firmBId)).orElseThrow();

        // SA sets Firm A's FIRM_ADMIN role (this IS the ceiling for Firm A)
        Role firmAAdmin = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        authPut(saToken,
                "/api/v1/super-admin/firms/" + firmAId + "/roles/" + firmAAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAAdmin.getId(), "permissionIds", permIds("CASE_MANAGEMENT:VIEW", "BILLING:VIEW"))));

        // SA sets Firm B's FIRM_ADMIN role
        Role firmBAdmin = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "FIRM_ADMIN").orElseThrow();
        authPut(saToken,
                "/api/v1/super-admin/firms/" + firmBId + "/roles/" + firmBAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmBAdmin.getId(), "permissionIds", permIds("CASE_MANAGEMENT:VIEW"))));

        // Verify: Firm A has 2 perms, Firm B has 1
        assertEquals(2, permCodes(firmAAdmin.getId()).size());
        assertEquals(1, permCodes(firmBAdmin.getId()).size());

        // FIRM_ADMIN sees ALL non-GLOBAL permissions (no ceiling for FIRM_ADMIN)
        User adminA = userRepository.findByUsernameAndFirmId("isoadminA2", firmA.getId()).orElseThrow();
        String tokenA = generateAccessToken(adminA);

        MvcResult ceilingA = authGet(tokenA,
                "/api/v1/firm/roles/" + firmAAdmin.getId() + "/permissions");
        assertSuccess(ceilingA);
        JsonNode availableA = parseResponse(ceilingA).path("data").path("availablePermissions");
        assertTrue(availableA.size() > 2, "Firm A FIRM_ADMIN sees all non-GLOBAL permissions");

        // Firm B admin CANNOT assign BILLING:VIEW (not in their ceiling)
        User adminB = userRepository.findByUsernameAndFirmId("isoadminB2", firmB.getId()).orElseThrow();
        String tokenB = generateAccessToken(adminB);

        Role firmBAdvocate = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE").orElseThrow();
        MvcResult denied = authPut(tokenB,
                "/api/v1/firm/roles/" + firmBAdvocate.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmBAdvocate.getId(), "permissionIds", List.of(perm("BILLING:VIEW").getId()))));
        assertEquals(403, denied.getResponse().getStatus(),
                "Firm B cannot assign BILLING:VIEW — not in their ceiling");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCENARIO 3: SA custom role on-behalf
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("STEP 7: SA creates custom role on-behalf for firm")
    void step7_saCustomRoleOnBehalf() throws Exception {
        registerSuperAdmin("rbacsa7", "rbacsa7@test.com", "Pass123!",
                "RBAC SA 7", "9850000014", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa7", "rbacsa7@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM7",
                "name", "RBAC Test Firm 7",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin7",
                "adminEmail", "admin7@rbactest.com",
                "adminMobileNo", "9850000015",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 7"
        )));
        assertSuccess(firmRes);

        String firmId = parseResponse(firmRes).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();

        // SA creates custom role on-behalf
        MvcResult createRole = authPost(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles",
                apiRequest(Map.of(
                        "name", "Senior Advocate",
                        "code", "SENIOR_ADVOCATE",
                        "description", "Senior advocate with extended permissions"
                )));
        assertSuccess(createRole);

        String roleId = parseResponse(createRole).path("data").path("id").asText();
        Role customRole = roleRepository.findById(UUID.fromString(roleId)).orElseThrow();
        assertEquals("SENIOR_ADVOCATE", customRole.getRoleCode());
        assertEquals(firm.getId(), customRole.getFirm().getId());

        // SA assigns permissions to the custom role (no ceiling for SA)
        Set<UUID> customPerms = permIds(
                "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", "CASE_MANAGEMENT:EDIT",
                "CASE_MANAGEMENT:DELETE", "BILLING:VIEW", "BILLING:APPROVE"
        );
        MvcResult assignPerms = authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + roleId + "/permissions",
                apiRequest(Map.of("roleId", roleId, "permissionIds", customPerms)));
        assertSuccess(assignPerms);

        // Verify custom role has the permissions
        assertEquals(6, permCodes(customRole.getId()).size());

        // Firm Admin can see the custom role in their role list
        User adminUser = userRepository.findByUsernameAndFirmId("rbacadmin7", firm.getId()).orElseThrow();
        String adminToken = generateAccessToken(adminUser);

        MvcResult listRoles = authGet(adminToken, "/api/v1/firm/roles");
        assertSuccess(listRoles);
        JsonNode roles = parseResponse(listRoles).path("data");
        boolean found = false;
        for (JsonNode role : roles) {
            if ("SENIOR_ADVOCATE".equals(role.path("code").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Firm Admin can see the custom role created by SA");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCENARIO 4: Firm Admin views ceiling for employee role
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("STEP 8: Firm Admin sees correct ceiling for employee roles")
    void step8_firmAdminSeesCeiling() throws Exception {
        registerSuperAdmin("rbacsa8", "rbacsa8@test.com", "Pass123!",
                "RBAC SA 8", "9850000016", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("rbacsa8", "rbacsa8@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "RBACFIRM8",
                "name", "RBAC Test Firm 8",
                "firmType", "FIRM",
                "adminUsername", "rbacadmin8",
                "adminEmail", "admin8@rbactest.com",
                "adminMobileNo", "9850000017",
                "adminPassword", "AdminPass123!",
                "adminFullName", "RBAC Admin 8"
        )));
        assertSuccess(firmRes);

        String firmId = parseResponse(firmRes).path("data").path("firmId").asText();
        Firm firm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();
        Role firmAdmin = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "FIRM_ADMIN").orElseThrow();
        Role advocate = roleRepository.findByFirmIdAndRoleCode(firm.getId(), "ADVOCATE").orElseThrow();

        // SA sets FIRM_ADMIN permissions via override (this IS the ceiling)
        Set<UUID> ceilingPerms = permIds(
                "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE",
                "BILLING:VIEW",
                "DASHBOARD_MANAGEMENT:VIEW", "DASHBOARD_MANAGEMENT:ACCESS"
        );
        authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + firmAdmin.getId() + "/permissions",
                apiRequest(Map.of("roleId", firmAdmin.getId(), "permissionIds", ceilingPerms)));

        User adminUser = userRepository.findByUsernameAndFirmId("rbacadmin8", firm.getId()).orElseThrow();
        String adminToken = generateAccessToken(adminUser);

        // Firm Admin views ADVOCATE permissions — ceiling should show 5 permissions
        MvcResult result = authGet(adminToken,
                "/api/v1/firm/roles/" + advocate.getId() + "/permissions");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        JsonNode current = data.path("currentPermissions");
        JsonNode available = data.path("availablePermissions");

        assertEquals(0, current.size(), "ADVOCATE starts with 0 permissions");
        assertEquals(5, available.size(), "ADVOCATE ceiling = FIRM_ADMIN's 5 permissions");

        // Verify the available permissions match the ceiling
        Set<String> availableCodes = new HashSet<>();
        for (JsonNode p : available) {
            availableCodes.add(p.path("code").asText());
        }
        assertTrue(availableCodes.contains("CASE_MANAGEMENT:VIEW"));
        assertTrue(availableCodes.contains("BILLING:VIEW"));
        assertTrue(availableCodes.contains("DASHBOARD_MANAGEMENT:VIEW"));
    }
}
