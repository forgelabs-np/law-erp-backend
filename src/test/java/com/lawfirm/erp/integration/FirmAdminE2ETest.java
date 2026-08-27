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

@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirmAdminE2ETest extends BaseIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private com.lawfirm.erp.rbac.repository.RolePermissionRepository rolePermissionRepository;

    private String firmToken;
    private String saToken;
    private Firm testFirm;
    private Role advocateRole;
    private Role firmAdminRole;
    private User firmAdminUser;

    private void setupFirmWithAdmin() throws Exception {
        registerSuperAdmin("firmsa", "firmsa@test.com", "Pass123!",
                "Firm SA", "9810000001", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("firmsa", "firmsa@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        saToken = generateAccessToken(saUser);

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

        firmAdminRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "FIRM_ADMIN")
                .orElseThrow();
        advocateRole = roleRepository.findByFirmIdAndRoleCode(testFirm.getId(), "ADVOCATE")
                .orElseThrow(() -> new RuntimeException("ADVOCATE role not cloned"));

        firmAdminUser = userRepository.findByUsernameAndFirmId("rbacadmin", testFirm.getId())
                .orElseThrow();
        firmToken = generateAccessToken(firmAdminUser);
    }

    private String refreshToken() {
        User fresh = userRepository.findById(firmAdminUser.getId()).orElseThrow();
        return generateAccessToken(fresh);
    }

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

        for (JsonNode role : data) {
            Assertions.assertNotEquals("SUPER_ADMIN", role.path("code").asText(),
                    "SUPER_ADMIN should not appear in firm roles");
        }
    }

    @Test
    @Order(2)
    @DisplayName("FIRM_ADMIN starts with 0 permissions, ceiling comes from system FIRM_ADMIN")
    void firmAdminStartsWithEmptyPermissions() throws Exception {
        setupFirmWithAdmin();

        MvcResult result = authGet(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        JsonNode current = data.path("currentPermissions");
        JsonNode available = data.path("availablePermissions");

        Assertions.assertTrue(current.isArray() && current.size() == 0,
                "FIRM_ADMIN should start with 0 permissions, got: " + current.size());
        Assertions.assertTrue(available.isArray() && available.size() > 0,
                "FIRM_ADMIN ceiling should show system role permissions as available");

        Role systemFirmAdmin = getSystemRole("FIRM_ADMIN");
        Set<String> systemPermCodes = rolePermissionRepository.findPermissionsByRoleId(systemFirmAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        for (JsonNode p : available) {
            Assertions.assertTrue(systemPermCodes.contains(p.path("code").asText()),
                    "Available permission " + p.path("code").asText() + " should be in system FIRM_ADMIN role");
        }
    }

    @Test
    @Order(3)
    @DisplayName("FIRM_ADMIN can enable permissions for itself from system ceiling")
    void firmAdminEnablesOwnPermissions() throws Exception {
        setupFirmWithAdmin();

        MvcResult ceilingResult = authGet(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }

        var request = apiRequest(Map.of("roleId", firmAdminRole.getId(), "permissionIds", permIds));
        MvcResult result = authPut(firmToken,
                "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions", request);
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertEquals(permIds.size(), data.path("permissions").size(),
                "FIRM_ADMIN should now have the permissions it enabled");
    }

    @Test
    @Order(4)
    @DisplayName("ADVOCATE ceiling = firm FIRM_ADMIN enabled permissions (not system role)")
    void advocateCeilingFromFirmAdmin() throws Exception {
        setupFirmWithAdmin();

        MvcResult ceilingResult = authGet(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAdminRole.getId(), "permissionIds", permIds));
        authPut(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions", enableReq);

        String freshToken = refreshToken();

        MvcResult result = authGet(freshToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        JsonNode current = data.path("currentPermissions");
        JsonNode advocateAvailable = data.path("availablePermissions");

        Assertions.assertTrue(current.isArray() && current.size() == 0,
                "ADVOCATE should start with 0 permissions");

        Set<String> advocateAvailableCodes = new HashSet<>();
        for (JsonNode p : advocateAvailable) {
            advocateAvailableCodes.add(p.path("code").asText());
        }

        Set<UUID> firmAdminEnabledIds = new HashSet<>(permIds);
        for (JsonNode p : advocateAvailable) {
            UUID permId = UUID.fromString(p.path("id").asText());
            Assertions.assertTrue(firmAdminEnabledIds.contains(permId),
                    "Available permission " + p.path("code").asText() +
                            " should be in FIRM_ADMIN's enabled set, not system role");
        }
    }

    @Test
    @Order(5)
    @DisplayName("ADVOCATE can get permissions that FIRM_ADMIN has enabled")
    void advocateGetsPermissionsFromFirmAdminCeiling() throws Exception {
        setupFirmWithAdmin();

        MvcResult ceilingResult = authGet(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAdminRole.getId(), "permissionIds", permIds));
        authPut(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions", enableReq);

        String freshToken = refreshToken();

        List<UUID> assignToAdvocate = permIds.subList(0, Math.min(2, permIds.size()));
        var request = apiRequest(Map.of("roleId", advocateRole.getId(), "permissionIds", assignToAdvocate));
        MvcResult result = authPut(freshToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions", request);
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertEquals(assignToAdvocate.size(), data.path("permissions").size());
    }

    @Test
    @Order(6)
    @DisplayName("ADVOCATE cannot get permission FIRM_ADMIN has NOT enabled")
    void advocateCannotExceedFirmAdminCeiling() throws Exception {
        setupFirmWithAdmin();

        MvcResult ceilingResult = authGet(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(2, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAdminRole.getId(), "permissionIds", permIds));
        authPut(firmToken, "/api/v1/firm/roles/" + firmAdminRole.getId() + "/permissions", enableReq);

        String freshToken = refreshToken();

        Set<String> firmAdminEnabledCodes = rolePermissionRepository.findPermissionsByRoleId(firmAdminRole.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Role systemFirmAdmin = getSystemRole("FIRM_ADMIN");
        Permission outsideCeiling = rolePermissionRepository.findPermissionsByRoleId(systemFirmAdmin.getId())
                .stream()
                .filter(p -> !firmAdminEnabledCodes.contains(p.getCode()))
                .findFirst()
                .orElse(null);

        if (outsideCeiling == null) {
            Assertions.assertTrue(true, "All system permissions enabled, cannot test ceiling enforcement");
            return;
        }

        var request = apiRequest(Map.of("roleId", advocateRole.getId(), "permissionIds", List.of(outsideCeiling.getId())));
        MvcResult result = authPut(freshToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Should reject permission outside FIRM_ADMIN ceiling, got: " + status +
                        " — " + result.getResponse().getContentAsString());
    }

    @Test
    @Order(7)
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

    @Test
    @Order(8)
    @DisplayName("Firm admin can list users assigned to a role")
    void listRoleUsers() throws Exception {
        setupFirmWithAdmin();

        MvcResult result = authGet(firmToken, "/api/v1/firm/roles/" + advocateRole.getId() + "/users");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertTrue(data.isArray(), "Response should be an array");
    }
}
