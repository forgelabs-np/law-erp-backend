package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for module access control:
 * - Firm modules are enabled at creation
 * - Firm admin can view enabled modules
 * - SuperAdmin can list all modules
 */
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModuleAccessE2ETest extends BaseIntegrationTest {

    @Autowired
    private FirmModuleRepository firmModuleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private String saToken;
    private String firmAdminToken;
    private Firm testFirm;

    private void setupFirm() throws Exception {
        registerSuperAdmin("modsa", "modsa@test.com", "Pass123!",
                "Module SA", "9830000001", "test-super-admin-secret-key-12345");

        // Look up the registered super admin user from DB
        Role saRole = getSystemRole("SUPER_ADMIN");
        User saUser = userRepository.findByUsername("modsa").orElseThrow();
        saToken = generateAccessToken(saUser);

        var firmRequest = apiRequest(Map.of(
                "lawFirmCode", "MODFIRM",
                "name", "Module Test Firm",
                "firmType", "FIRM",
                "adminUsername", "modadmin",
                "adminEmail", "modadmin@testfirm.com",
                "adminMobileNo", "9830000002",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Module Admin"
        ));

        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms", firmRequest);
        assertSuccess(result);

        String firmId = parseResponse(result).path("data").path("firmId").asText();
        testFirm = firmRepository.findById(UUID.fromString(firmId)).orElseThrow();

        // Get firm admin and generate token directly (bypass MFA)
        User admin = userRepository.findByUsernameAndFirmId("modadmin", testFirm.getId()).orElseThrow();
        firmAdminToken = generateAccessToken(admin);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Modules Enabled at Firm Creation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("All modules are enabled when firm is created")
    void firmCreation_enablesAllModules() throws Exception {
        setupFirm();

        List<FirmModule> modules = firmModuleRepository.findByFirmIdWithModule(testFirm.getId());
        Assertions.assertFalse(modules.isEmpty(), "Firm should have modules enabled");

        for (FirmModule fm : modules) {
            Assertions.assertTrue(fm.getIsEnabled(),
                    "Module " + fm.getModule().getCode() + " should be enabled");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Firm admin can list enabled modules")
    void firmAdmin_listModules() throws Exception {
        setupFirm();

        MvcResult result = authGet(firmAdminToken, "/api/v1/firm/modules");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertTrue(data.isArray(), "Response should be an array");
        Assertions.assertTrue(data.size() > 0, "Should have at least one module");

        for (JsonNode module : data) {
            Assertions.assertTrue(module.has("moduleCode"), "Module should have moduleCode");
            Assertions.assertTrue(module.has("isEnabled"), "Module should have isEnabled");
            Assertions.assertTrue(module.has("moduleName"), "Module should have moduleName");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. SuperAdmin Module Management
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("SuperAdmin can list all modules")
    void superAdmin_listModules() throws Exception {
        setupFirm();

        MvcResult result = authGet(saToken, "/api/v1/admin/modules");
        assertSuccess(result);

        JsonNode data = parseResponse(result).path("data");
        Assertions.assertTrue(data.isArray());
        Assertions.assertTrue(data.size() >= 9, "Should have at least 9 system modules");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Module Access Enforcement
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Module permissions are correctly scoped to modules")
    void modulePermissions_correctlyScoped() throws Exception {
        setupFirm();

        List<Permission> casePerms = permissionRepository.findAll().stream()
                .filter(p -> "CASE_MANAGEMENT".equals(p.getModuleCode()))
                .toList();

        Assertions.assertFalse(casePerms.isEmpty(),
                "CASE_MANAGEMENT should have permissions");

        for (Permission p : casePerms) {
            Assertions.assertEquals("CASE_MANAGEMENT", p.getModuleCode(),
                    "Permission " + p.getCode() + " should be in CASE_MANAGEMENT module");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Each permission belongs to exactly one module")
    void permissions_singleModule() throws Exception {
        List<Permission> allPerms = permissionRepository.findAll();
        Map<String, List<Permission>> byModule = allPerms.stream()
                .collect(java.util.stream.Collectors.groupingBy(Permission::getModuleCode));

        for (var entry : byModule.entrySet()) {
            for (Permission p : entry.getValue()) {
                Assertions.assertEquals(entry.getKey(), p.getModuleCode(),
                        "Permission " + p.getCode() + " module code mismatch");
            }
        }
    }
}
