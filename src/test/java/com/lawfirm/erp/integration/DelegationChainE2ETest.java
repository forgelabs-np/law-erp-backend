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
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end walkthrough of the Super Admin → Firm Admin → Employee delegation
 * chain (docs/rbac-delegation-chain-design.md). Mirrors the operator guide:
 * every numbered step here is a numbered API call in the guide.
 *
 * NOT @Transactional: the template sync runs on the async taskExecutor AFTER
 * the request transaction commits — a wrapping test transaction would roll
 * everything back and the afterCommit hook would never fire.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DelegationChainE2ETest extends BaseIntegrationTest {

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    /**
     * Token generation touches user.getRole() (lazy). This class is NOT
     * @Transactional (the async sync must really run), so init the graph
     * inside a programmatic transaction first.
     */
    private String tokenFor(UUID userId) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return tx.execute(status -> {
            User u = userRepository.findById(userId).orElseThrow();
            u.getRole().getRoleCode(); // force-init lazy proxy inside the session
            return generateAccessToken(u);
        });
    }

    private static String saToken;
    private static String firmAdminToken;
    private static String paralegalToken;

    private static UUID firmId;
    private static UUID firmAdminRoleId;
    private static UUID paralegalRoleId;
    private static UUID firmAdminUserId;
    private static UUID paralegalUserId;

    // ═══════════════════════════════════════════════════════════════════════
    // Step 0 — bootstrap: SA, firm, admin, employee (mirrors onboarding)
    // ═══════════════════════════════════════════════ SA registers + creates the firm
    @Test
    @Order(0)
    @DisplayName("STEP 0: SA creates firm; firm admin + paralegal get tokens")
    void step0_bootstrap() throws Exception {
        registerSuperAdmin("chainsa", "chainsa@test.com", "Pass123!",
                "Chain SA", "9821000001", "test-super-admin-secret-key-12345");
        User sa = userRepository.findByUsername("chainsa").orElseThrow();
        saToken = tokenFor(sa.getId()); // SA user's firm proxy needs a session too

        // SA onboards the firm via the real endpoint
        MvcResult firmRes = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(Map.of(
                "lawFirmCode", "CHAIN1",
                "name", "Chain Test Firm",
                "firmType", "FIRM",
                "adminUsername", "chainadmin",
                "adminEmail", "chainadmin@test.com",
                "adminMobileNo", "9821000002",
                "adminPassword", "AdminPass123!",
                "adminFullName", "Chain Admin"
        )));
        assertSuccess(firmRes);
        firmId = UUID.fromString(parseResponse(firmRes).path("data").path("firmId").asText());

        Firm firm = firmRepository.findById(firmId).orElseThrow();
        User admin = userRepository.findByUsernameAndFirmId("chainadmin", firmId).orElseThrow();
        firmAdminUserId = admin.getId();
        firmAdminToken = tokenFor(firmAdminUserId);

        // Employee (paralegal) — direct DB creation mirrors employee onboarding
        Role paralegalClone = roleRepository.findByFirmIdAndRoleCode(firmId, "PARALEGAL").orElseThrow();
        paralegalRoleId = paralegalClone.getId();
        User paralegal = createUser("chainpara", "chainpara@test.com", "ParaPass123!",
                UserType.FIRM_USER, paralegalClone, firm);
        paralegalUserId = paralegal.getId();
        paralegalToken = tokenFor(paralegalUserId);

        firmAdminRoleId = roleRepository.findByFirmIdAndRoleCode(firmId, "FIRM_ADMIN").orElseThrow().getId();

        // The delegation baseline: clones start with ZERO permissions
        assertEquals(0, permsOf(firmAdminRoleId).size(), "FIRM_ADMIN clone starts empty");
        assertEquals(0, permsOf(paralegalRoleId).size(), "PARALEGAL clone starts empty");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STEP 1 — SA populates the firm admin (surgical override, existing API)
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    @Order(1)
    @DisplayName("STEP 1: SA override gives FIRM_ADMIN clone its permission set")
    void step1_saOverridesFirmAdmin() throws Exception {
        Set<UUID> ids = permIds("CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE",
                "CASE_MANAGEMENT:EDIT", "BILLING:VIEW", "USER_MANAGEMENT:VIEW",
                "USER_MANAGEMENT:EDIT", "ROLE_MANAGEMENT:VIEW", "ROLE_MANAGEMENT:EDIT",
                "DASHBOARD_MANAGEMENT:VIEW", "DASHBOARD_MANAGEMENT:ACCESS");

        MvcResult result = authPut(saToken,
                "/api/v1/super-admin/firms/" + firmId + "/roles/" + firmAdminRoleId + "/permissions",
                apiRequest(Map.of("roleId", firmAdminRoleId, "permissionIds", ids)));
        assertSuccess(result);
        assertEquals(10, permsOf(firmAdminRoleId).size());

        // STEP 2 — SA visibility: the firm's roles as SA now sees them
        MvcResult listRes = authGet(saToken, "/api/v1/super-admin/firms/" + firmId + "/roles");
        assertSuccess(listRes);
        assertTrue(parseResponse(listRes).path("data").size() >= 2,
                "SA sees the firm's roles");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STEP 3 — Firm Admin distributes within ceiling to employees
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    @Order(3)
    @DisplayName("STEP 3: Firm Admin gives PARALEGAL a subset within their ceiling")
    void step3_firmAdminDistributes() throws Exception {
        // Step 1's override bumped the admin's permissionVersion — the old token
        // is stale (the real system forces re-login). Frontend does the same.
        firmAdminToken = tokenFor(firmAdminUserId);

        Set<UUID> ids = permIds("CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE",
                "DASHBOARD_MANAGEMENT:VIEW", "DASHBOARD_MANAGEMENT:ACCESS");

        MvcResult result = authPut(firmAdminToken,
                "/api/v1/firm/roles/" + paralegalRoleId + "/permissions",
                apiRequest(Map.of("roleId", paralegalRoleId, "permissionIds", ids)));
        assertSuccess(result);
        assertEquals(4, permsOf(paralegalRoleId).size());

        // Employee sees their effective permissions + ceiling in the UI payload
        MvcResult permRes = authGet(firmAdminToken,
                "/api/v1/firm/roles/" + paralegalRoleId + "/permissions");
        assertSuccess(permRes);
        assertTrue(parseResponse(permRes).path("data").path("availablePermissions").size() >= 4,
                "ceiling (firm admin's set) drives the checkbox UI");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STEP 4 — John/Ron case: variation handled by a custom role, not per-user
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    @Order(4)
    @DisplayName("STEP 4: Custom role 'SENIOR_PARALEGAL' created; anchor validated")
    void step4_customRole() throws Exception {
        // Without a parent template anchor → rejected (Phase 0/5 rule)
        MvcResult noAnchor = authPost(firmAdminToken, "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Senior Paralegal",
                "code", "SENIOR_PARALEGAL",
                "parentRoleId", UUID.randomUUID().toString()  // nonexistent
        )));
        assertEquals(404, noAnchor.getResponse().getStatus(),
                "nonexistent parent template is rejected");

        // With the PARALEGAL system template as base → created
        UUID paralegalTemplateId = getSystemRole("PARALEGAL").getId();
        MvcResult created = authPost(firmAdminToken, "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Senior Paralegal",
                "code", "SENIOR_PARALEGAL",
                "parentRoleId", paralegalTemplateId.toString()
        )));
        assertSuccess(created);

        // Reassignment happens via existing bulk-role-change (not re-tested here)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STEP 5 — SA reshapes the platform: template edit + async diff sync
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    @Order(5)
    @DisplayName("STEP 5: SA narrows FIRM_ADMIN template; async sync cascades to firm")
    void step5_saEditsTemplate_syncCascades() throws Exception {
        // Current FIRM_ADMIN template set, minus USER_MANAGEMENT:VIEW.
        // (BILLING:VIEW would be correctly REJECTED by chain validation — the
        // ADVOCATE template holds it via READ_ONLY. USER_MANAGEMENT is FULL for
        // FIRM_ADMIN and NO_ACCESS for every employee template: legal narrowing.)
        Role faTemplate = getSystemRole("FIRM_ADMIN");
        Set<UUID> newIds = rolePermissionRepository.findPermissionsByRoleId(faTemplate.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());

        UUID userView = permIds("USER_MANAGEMENT:VIEW").iterator().next();
        Assumptions.assumeTrue(newIds.contains(userView),
                "Template has USER_MANAGEMENT:VIEW to remove (seed matrix dependent)");

        newIds.remove(userView);

        // Preview first — zero writes, shows the delta + per-firm impact
        MvcResult preview = mockMvc.perform(post(
                        "/api/v1/admin/roles/templates/" + faTemplate.getId()
                        + "/permissions/preview")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                apiRequest(Map.of("permissionIds", newIds)))))
                .andExpect(status().isOk())
                .andReturn();
        assertSuccess(preview);
        assertTrue(parseResponse(preview).path("data").path("removedPermissionCodes").toString()
                        .contains("USER_MANAGEMENT:VIEW"),
                "preview names USER_MANAGEMENT:VIEW as removed");

        // Commit — returns jobId for polling
        MvcResult commit = mockMvc.perform(put(
                        "/api/v1/admin/roles/templates/" + faTemplate.getId() + "/permissions")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                apiRequest(Map.of("permissionIds", newIds)))))
                .andExpect(status().isOk())
                .andReturn();
        assertSuccess(commit);
        String jobId = parseResponse(commit).path("data").path("syncJobId").asText();
        assertFalse(jobId == null || jobId.isEmpty(), "PUT returns syncJobId");

        // ═════════════════════════════════════════════════════════════════
        // Poll the job until terminal (real async — up to ~15s)
        // ═════════════════════════════════════════════════════════════════
        JsonNode jobData = pollJobUntilTerminal(jobId);
        String status = jobData.path("status").asText();
        assertTrue(status.equals("COMPLETED") || status.equals("COMPLETED_WITH_FAILURES"),
                "job reached terminal status, got: " + status);
        assertEquals(0, jobData.path("firmsFailed").asInt(), "no firm failures");

        // ── Cascade assertions (the invariant, verified through the API) ──
        // Firm admin clone lost USER_MANAGEMENT:VIEW
        Set<String> adminAfter = permCodes(firmAdminRoleId);
        assertFalse(adminAfter.contains("USER_MANAGEMENT:VIEW"),
                "cascade: firm admin clone narrowed");

        // Employee clone stripped of anything the admin lost (they never had it — stable)
        Set<String> paraAfter = permCodes(paralegalRoleId);
        assertFalse(paraAfter.contains("USER_MANAGEMENT:VIEW"),
                "cascade: employee holds nothing the admin lost");

        // Templates stripped too (fresh-onboarding gap closed)
        Set<String> templateAfter = permCodes(getSystemRole("FIRM_ADMIN").getId());
        assertFalse(templateAfter.contains("USER_MANAGEMENT:VIEW"));
        // Employee templates may still hold it if their own template delta never
        // included it — strip applies only to over-ceiling perms (spec §4b)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STEP 6 — Chain validation: employee template cannot exceed FIRM_ADMIN
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    @Order(6)
    @DisplayName("STEP 6: Chain validation rejects employee template over-ceiling edit")
    void step6_chainValidationRejects() throws Exception {
        Role faTemplate = getSystemRole("FIRM_ADMIN");

        // Give FIRM_ADMIN template a permission that PARALEGAL template will then request
        UUID calView = permIds("CALENDAR:VIEW").iterator().next();
        Set<UUID> faIds = rolePermissionRepository.findPermissionsByRoleId(faTemplate.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());
        faIds.add(calView);
        authPut(saToken, "/api/v1/admin/roles/templates/" + faTemplate.getId() + "/permissions",
                apiRequest(Map.of("permissionIds", faIds)));
        // (commit accepted; may enqueue a job — irrelevant for this step)

        // PARALEGAL template requests CALENDAR:VIEW — legal now (admin template has it)
        Role paraTemplate = getSystemRole("PARALEGAL");
        Set<UUID> paraIds = rolePermissionRepository.findPermissionsByRoleId(paraTemplate.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());
        paraIds.add(calView);
        MvcResult okEdit = mockMvc.perform(put(
                        "/api/v1/admin/roles/templates/" + paraTemplate.getId() + "/permissions")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                apiRequest(Map.of("permissionIds", paraIds)))))
                .andReturn();
        assertSuccess(okEdit);

        // Now SA narrows FIRM_ADMIN template below PARALEGAL → must be rejected
        Set<UUID> narrowed = rolePermissionRepository.findPermissionsByRoleId(faTemplate.getId())
                .stream().map(Permission::getId).collect(Collectors.toSet());
        narrowed.remove(calView);
        MvcResult rejected = mockMvc.perform(put(
                        "/api/v1/admin/roles/templates/" + faTemplate.getId() + "/permissions")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                apiRequest(Map.of("permissionIds", narrowed)))))
                .andReturn();
        assertEquals(400, rejected.getResponse().getStatus(),
                "narrowing FIRM_ADMIN below an employee template violates the chain");
        assertTrue(parseResponse(rejected).path("message").asText()
                        .contains("CALENDAR:VIEW"),
                "error names the offending permission code");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private Set<UUID> permIds(String... codes) {
        Set<UUID> ids = new HashSet<>();
        for (String code : codes) {
            Permission p = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new RuntimeException("Seed permission missing: " + code));
            ids.add(p.getId());
        }
        return ids;
    }

    private Set<String> permsOf(UUID roleId) {
        return rolePermissionRepository.findPermissionsByRoleId(roleId)
                .stream().map(Permission::getCode).collect(Collectors.toSet());
    }

    private Set<String> permCodes(UUID roleId) {
        return permsOf(roleId);
    }

    private JsonNode pollJobUntilTerminal(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = authGet(saToken, "/api/v1/admin/roles/sync-jobs/" + jobId);
            if (res.getResponse().getStatus() == 200) {
                JsonNode data = parseResponse(res).path("data");
                String status = data.path("status").asText();
                if ("COMPLETED".equals(status) || "COMPLETED_WITH_FAILURES".equals(status)
                        || "FAILED".equals(status)) {
                    return data;
                }
            }
            Thread.sleep(250);
        }
        fail("Sync job did not reach terminal status within 20s: " + jobId);
        return null;
    }
}
