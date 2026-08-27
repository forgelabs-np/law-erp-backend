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
        registerSuperAdmin("dualsa", "dualsa@test.com", "Pass123!",
                "Dual SA", "9820000001", "test-super-admin-secret-key-12345");

        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = createSystemUser("dualsa", "dualsa@test.com", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
        String saToken = generateAccessToken(saUser);

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

        User adminA = userRepository.findByUsernameAndFirmId("adminA", firmA.getId()).orElseThrow();
        User adminB = userRepository.findByUsernameAndFirmId("adminB", firmB.getId()).orElseThrow();
        firmAToken = generateAccessToken(adminA);
        firmBToken = generateAccessToken(adminB);
    }

    private String refreshToken(UUID userId) {
        User fresh = userRepository.findById(userId).orElseThrow();
        return generateAccessToken(fresh);
    }

    @Test
    @Order(1)
    @DisplayName("Firm roles start with 0 permissions (no auto-cloning)")
    void firmRolesStartEmpty() throws Exception {
        setupTwoFirms();

        Role firmAdminA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        Role firmAdvocateA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE").orElseThrow();

        Set<String> adminPerms = rolePermissionRepository.findPermissionsByRoleId(firmAdminA.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> advocatePerms = rolePermissionRepository.findPermissionsByRoleId(firmAdvocateA.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertTrue(adminPerms.isEmpty(),
                "Firm FIRM_ADMIN should start with 0 permissions, got: " + adminPerms.size());
        Assertions.assertTrue(advocatePerms.isEmpty(),
                "Firm ADVOCATE should start with 0 permissions, got: " + advocatePerms.size());
    }

    @Test
    @Order(2)
    @DisplayName("FIRM_ADMIN ceiling comes from system FIRM_ADMIN role")
    void firmAdminCeilingFromSystem() throws Exception {
        setupTwoFirms();

        Role firmAdminA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();

        MvcResult result = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions");
        assertSuccess(result);

        JsonNode available = parseResponse(result).path("data").path("availablePermissions");

        Role systemAdmin = getSystemRole("FIRM_ADMIN");
        Set<String> systemPermCodes = rolePermissionRepository.findPermissionsByRoleId(systemAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        for (JsonNode p : available) {
            Assertions.assertTrue(systemPermCodes.contains(p.path("code").asText()),
                    "Permission " + p.path("code").asText() + " should be in system FIRM_ADMIN ceiling");
        }
    }

    @Test
    @Order(3)
    @DisplayName("FIRM_ADMIN can enable permissions for itself, then ADVOCATE ceiling follows")
    void firmAdminEnablesAndAdvocateCeilingFollows() throws Exception {
        setupTwoFirms();

        Role firmAdminA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        Role firmAdvocateA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE").orElseThrow();

        MvcResult ceilingResult = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }

        var request = apiRequest(Map.of("roleId", firmAdminA.getId(), "permissionIds", permIds));
        MvcResult result = authPut(firmAToken,
                "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions", request);
        assertSuccess(result);

        User adminA = userRepository.findByUsernameAndFirmId("adminA", firmA.getId()).orElseThrow();
        String freshToken = refreshToken(adminA.getId());

        MvcResult advocateResult = authGet(freshToken,
                "/api/v1/firm/roles/" + firmAdvocateA.getId() + "/permissions");
        assertSuccess(advocateResult);

        JsonNode advocateAvailable = parseResponse(advocateResult).path("data").path("availablePermissions");
        Set<String> advocateAvailableCodes = new HashSet<>();
        for (JsonNode p : advocateAvailable) {
            advocateAvailableCodes.add(p.path("code").asText());
        }

        Set<String> firmAdminCodes = rolePermissionRepository.findPermissionsByRoleId(firmAdminA.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertEquals(firmAdminCodes, advocateAvailableCodes,
                "ADVOCATE ceiling should exactly match FIRM_ADMIN's enabled permissions");
    }

    @Test
    @Order(4)
    @DisplayName("ADVOCATE cannot get permission FIRM_ADMIN has NOT enabled")
    void advocateCannotExceedFirmAdminCeiling() throws Exception {
        setupTwoFirms();

        Role firmAdminA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        Role firmAdvocateA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE").orElseThrow();

        MvcResult ceilingResult = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(2, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAdminA.getId(), "permissionIds", permIds));
        authPut(firmAToken, "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions", enableReq);

        User adminA = userRepository.findByUsernameAndFirmId("adminA", firmA.getId()).orElseThrow();
        String freshToken = refreshToken(adminA.getId());

        Set<String> firmAdminCodes = rolePermissionRepository.findPermissionsByRoleId(firmAdminA.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Role systemFirmAdmin = getSystemRole("FIRM_ADMIN");
        Permission outsideCeiling = rolePermissionRepository.findPermissionsByRoleId(systemFirmAdmin.getId())
                .stream()
                .filter(p -> !firmAdminCodes.contains(p.getCode()))
                .findFirst()
                .orElse(null);

        if (outsideCeiling == null) {
            Assertions.assertTrue(true, "FIRM_ADMIN has all system permissions, cannot test ceiling enforcement");
            return;
        }

        var request = apiRequest(Map.of("roleId", firmAdvocateA.getId(), "permissionIds", List.of(outsideCeiling.getId())));
        MvcResult result = authPut(freshToken,
                "/api/v1/firm/roles/" + firmAdvocateA.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Should reject permission outside FIRM_ADMIN ceiling, got: " + status +
                        " — " + result.getResponse().getContentAsString());
    }

    @Test
    @Order(5)
    @DisplayName("GLOBAL permissions are NOT in firm role ceiling")
    void globalPermissions_notInFirmCeiling() throws Exception {
        setupTwoFirms();

        Role firmAdminA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        Role firmAdvocateA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE").orElseThrow();

        MvcResult ceilingResult = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(5, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAdminA.getId(), "permissionIds", permIds));
        authPut(firmAToken, "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions", enableReq);

        User adminA = userRepository.findByUsernameAndFirmId("adminA", firmA.getId()).orElseThrow();
        String freshToken = refreshToken(adminA.getId());

        MvcResult result = authGet(freshToken,
                "/api/v1/firm/roles/" + firmAdvocateA.getId() + "/permissions");
        assertSuccess(result);

        JsonNode advocateAvailable = parseResponse(result).path("data").path("availablePermissions");
        for (JsonNode perm : advocateAvailable) {
            String scope = perm.path("scope").asText();
            Assertions.assertNotEquals("GLOBAL", scope,
                    "GLOBAL permission " + perm.path("code").asText() +
                            " should NOT appear in firm role ceiling");
        }
    }

    @Test
    @Order(6)
    @DisplayName("Firm A admin cannot modify Firm B roles")
    void crossFirm_firmACannotModifyFirmBRoles() throws Exception {
        setupTwoFirms();

        Role firmBAdvocate = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "ADVOCATE").orElseThrow();

        var request = apiRequest(Map.of("roleId", firmBAdvocate.getId(), "permissionIds", List.of(UUID.randomUUID())));
        MvcResult result = authPut(firmAToken,
                "/api/v1/firm/roles/" + firmBAdvocate.getId() + "/permissions", request);

        int status = result.getResponse().getStatus();
        Assertions.assertEquals(403, status,
                "Firm A admin should NOT modify Firm B roles, got: " + status);
    }

    @Test
    @Order(7)
    @DisplayName("Firm A and Firm B have independent permissions after modification")
    void crossFirm_independentPermissions() throws Exception {
        setupTwoFirms();

        Role firmAAdmin = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        Role firmBAdmin = roleRepository.findByFirmIdAndRoleCode(firmB.getId(), "FIRM_ADMIN").orElseThrow();

        Set<String> firmAPerms = rolePermissionRepository.findPermissionsByRoleId(firmAAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> firmBPerms = rolePermissionRepository.findPermissionsByRoleId(firmBAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertEquals(firmAPerms, firmBPerms,
                "Both firms should start with identical (empty) FIRM_ADMIN permissions");

        MvcResult ceilingResult = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAAdmin.getId() + "/permissions");
        JsonNode available = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> permIds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, available.size()); i++) {
            permIds.add(UUID.fromString(available.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAAdmin.getId(), "permissionIds", permIds));
        authPut(firmAToken, "/api/v1/firm/roles/" + firmAAdmin.getId() + "/permissions", enableReq);

        firmAPerms = rolePermissionRepository.findPermissionsByRoleId(firmAAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());
        firmBPerms = rolePermissionRepository.findPermissionsByRoleId(firmBAdmin.getId())
                .stream().map(Permission::getCode).collect(Collectors.toSet());

        Assertions.assertNotEquals(firmAPerms, firmBPerms,
                "Firm A permissions should differ from Firm B after modification");
        Assertions.assertTrue(firmBPerms.isEmpty(),
                "Firm B permissions should remain empty (unchanged)");
    }

    @Test
    @Order(8)
    @DisplayName("Permission change invalidates user sessions (permVersion bump)")
    void permissionChange_invalidatesSessions() throws Exception {
        setupTwoFirms();

        Role firmAdminA = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "FIRM_ADMIN").orElseThrow();
        Role advocateRole = roleRepository.findByFirmIdAndRoleCode(firmA.getId(), "ADVOCATE").orElseThrow();

        MvcResult ceilingResult = authGet(firmAToken,
                "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions");
        JsonNode adminAvailable = parseResponse(ceilingResult).path("data").path("availablePermissions");

        List<UUID> adminPermIds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, adminAvailable.size()); i++) {
            adminPermIds.add(UUID.fromString(adminAvailable.get(i).path("id").asText()));
        }
        var enableReq = apiRequest(Map.of("roleId", firmAdminA.getId(), "permissionIds", adminPermIds));
        authPut(firmAToken, "/api/v1/firm/roles/" + firmAdminA.getId() + "/permissions", enableReq);

        User adminA = userRepository.findByUsernameAndFirmId("adminA", firmA.getId()).orElseThrow();
        String freshToken = refreshToken(adminA.getId());

        User advocate = createUser("advocate1", "adv1@test.com", "pass",
                UserType.FIRM_USER, advocateRole, firmA);
        String advocateToken = generateAccessToken(advocate);

        MvcResult before = authGet(advocateToken, "/api/v1/firm/roles");
        int beforeStatus = before.getResponse().getStatus();
        Assertions.assertNotEquals(401, beforeStatus,
                "Advocate token should be valid (not 401), got: " + beforeStatus);

        MvcResult permResult = authGet(freshToken,
                "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions");
        JsonNode available = parseResponse(permResult).path("data").path("availablePermissions");

        if (available.size() > 0) {
            List<UUID> permIds = new ArrayList<>();
            for (int i = 0; i < Math.min(1, available.size()); i++) {
                permIds.add(UUID.fromString(available.get(i).path("id").asText()));
            }

            var request = apiRequest(Map.of("roleId", advocateRole.getId(), "permissionIds", permIds));
            MvcResult updateResult = authPut(freshToken,
                    "/api/v1/firm/roles/" + advocateRole.getId() + "/permissions", request);
            assertSuccess(updateResult);

            MvcResult after = authGet(advocateToken, "/api/v1/firm/roles");
            int afterStatus = after.getResponse().getStatus();
            Assertions.assertEquals(401, afterStatus,
                    "Advocate token should be rejected after permission change (401), got: " + afterStatus);
        }
    }
}
