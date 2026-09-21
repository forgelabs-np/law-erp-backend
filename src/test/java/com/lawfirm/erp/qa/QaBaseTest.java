package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.integration.BaseIntegrationTest;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared harness for the manual-QA style functional suite.
 *
 * Wraps {@link BaseIntegrationTest} with the flows a QA engineer repeats:
 * create a firm, grant a role permissions, mint a token that reflects the
 * grant, and read HTTP status/body without ceremony.
 */
public abstract class QaBaseTest extends BaseIntegrationTest {

    protected static final String SA_SECRET = "test-super-admin-secret-key-12345";
    protected static final String ADMIN_PWD = "AdminPass123!";
    protected static final String EMP_PWD = "Employee123!";
    protected static final String CLIENT_PWD = "Client123!";

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    protected EntityManager entityManager;

    // ═══════════════════════════════════════════════════════════════════════
    // Permissions
    // ═══════════════════════════════════════════════════════════════════════

    protected Permission perm(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Seed permission missing: " + code));
    }

    protected UUID permId(String code) {
        return perm(code).getId();
    }

    protected Set<UUID> permIds(String... codes) {
        return Arrays.stream(codes).map(this::permId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    protected Set<String> permCodes(UUID roleId) {
        return rolePermissionRepository.findPermissionsByRoleId(roleId).stream()
                .map(Permission::getCode).collect(Collectors.toSet());
    }

    /** Every permission seeded in the system — the "give this role everything" case. */
    protected Set<UUID> everyPermissionId() {
        return permissionRepository.findAll().stream()
                .map(Permission::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Super admin
    // ═══════════════════════════════════════════════════════════════════════

    protected User superAdmin(String username) {
        Role saRole = getSystemRole("SUPER_ADMIN");
        return createSystemUser(username, username + "@qa.test", "Pass123!",
                UserType.SUPER_ADMIN, saRole);
    }

    /**
     * Mint a token for a user, re-reading the row first so permVersion matches
     * what the filter compares against. Tokens minted from a stale entity are
     * rejected with 401 "permissions have changed".
     */
    protected String token(User user) {
        User fresh = userRepository.findById(user.getId()).orElse(user);
        return generateAccessToken(fresh);
    }

    /**
     * Token for a user whose {@code permissionVersion} may have been bumped by a
     * bulk JPQL update (role changes, permission grants). Those updates bypass
     * the persistence context, so a plain re-read can return the stale version
     * and the filter then rejects the token as "permissions have changed".
     */
    protected String freshToken(User user) {
        entityManager.flush();
        entityManager.clear();
        User fresh = userRepository.findById(user.getId())
                .orElseThrow(() -> new AssertionError("User vanished: " + user.getId()));
        return generateAccessToken(fresh);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Firms
    // ═══════════════════════════════════════════════════════════════════════

    /** POST /super-admin/firms — returns the response data node. */
    protected JsonNode createFirm(String saToken, String firmCode, String adminUsername) throws Exception {
        return createFirm(saToken, firmCode, adminUsername, new HashMap<>());
    }

    protected JsonNode createFirm(String saToken, String firmCode, String adminUsername,
                                  Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lawFirmCode", firmCode);
        body.put("name", firmCode + " Law Firm");
        body.put("firmType", "FIRM");
        body.put("email", adminUsername + "@" + firmCode.toLowerCase() + ".test");
        body.put("phone", "9800000000");
        body.put("adminUsername", adminUsername);
        body.put("adminEmail", adminUsername + "@" + firmCode.toLowerCase() + ".test");
        body.put("adminMobileNo", "98" + String.format("%08d", Math.abs(adminUsername.hashCode()) % 100000000));
        body.put("adminPassword", ADMIN_PWD);
        body.put("adminFullName", adminUsername + " Full");
        body.putAll(extra);

        MvcResult result = authPost(saToken, "/api/v1/super-admin/firms", apiRequest(body));
        assertEquals(200, result.getResponse().getStatus(),
                "Firm creation should return 200: " + result.getResponse().getContentAsString());
        JsonNode json = parseResponse(result);
        assertTrue(json.path("success").asBoolean(), "Firm creation failed: " + json.path("message").asText());
        return json.path("data");
    }

    protected Firm firmByCode(String firmCode) {
        return firmRepository.findByLawFirmCode(firmCode)
                .orElseThrow(() -> new AssertionError("Firm not found: " + firmCode));
    }

    protected Role firmRole(Firm firm, String roleCode) {
        return roleRepository.findByFirmIdAndRoleCode(firm.getId(), roleCode)
                .orElseThrow(() -> new AssertionError("Firm role not found: " + roleCode));
    }

    protected User firmAdmin(Firm firm, String adminUsername) {
        return userRepository.findByUsernameAndFirmId(adminUsername, firm.getId())
                .orElseThrow(() -> new AssertionError("Firm admin not found: " + adminUsername));
    }

    /** Employee created through the API — the username is generated, so read it back. */
    protected EmployeeFixture createEmployee(Firm firm, String adminToken, String requestedUsername) throws Exception {
        MvcResult created = authPost(adminToken, "/api/v1/firm/employees", apiRequest(Map.of(
                "username", requestedUsername,
                "email", requestedUsername + "@" + firm.getLawFirmCode().toLowerCase() + ".test",
                "mobileNo", "98" + String.format("%08d", Math.abs(requestedUsername.hashCode()) % 100000000),
                "password", EMP_PWD,
                "fullName", requestedUsername + " Employee",
                "roleId", firmRole(firm, "ADVOCATE").getId(),
                "designation", "Advocate")));
        assertAllowed(created, "create employee " + requestedUsername);
        JsonNode data = json(created).path("data");
        return new EmployeeFixture(
                UUID.fromString(data.path("id").asText()),
                data.path("username").asText(),
                EMP_PWD);
    }

    /** An employee created through the API: id, the generated username, its password. */
    protected record EmployeeFixture(UUID id, String username, String password) {}

    /** Firm user on a cloned (non-system) firm role. */
    protected User firmUser(Firm firm, String roleCode, String username) {
        Role role = firmRole(firm, roleCode);
        String email = username + "@" + firm.getLawFirmCode().toLowerCase() + ".test";
        return createUser(username, email, EMP_PWD, UserType.FIRM_USER, role, firm);
    }

    /** A client account with portal access enabled — the only kind that may sign in. */
    protected User clientUser(Firm firm, String username) {
        Role role = firmRole(firm, "CLIENT");
        String email = username + "@" + firm.getLawFirmCode().toLowerCase() + ".test";
        User client = createUser(username, email, CLIENT_PWD, UserType.CLIENT, role, firm);
        client.setPortalAccessEnabled(true);
        return userRepository.save(client);
    }

    protected User clientUserWithoutPortal(Firm firm, String username) {
        Role role = firmRole(firm, "CLIENT");
        String email = username + "@" + firm.getLawFirmCode().toLowerCase() + ".test";
        User client = createUser(username, email, CLIENT_PWD, UserType.CLIENT, role, firm);
        client.setPortalAccessEnabled(false);
        return userRepository.save(client);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Permission grants
    // ═══════════════════════════════════════════════════════════════════════

    /** SA assigns an explicit permission set to a firm role (the ceiling). */
    protected MvcResult grantRolePermissions(String saToken, Firm firm, Role role, Collection<UUID> permissionIds)
            throws Exception {
        MvcResult result = authPut(saToken,
                "/api/v1/super-admin/firms/" + firm.getId() + "/roles/" + role.getId() + "/permissions",
                apiRequest(Map.of("roleId", role.getId(), "permissionIds", permissionIds)));
        permissionEvaluator.clearAllCache();
        return result;
    }

    /** Firm admin assigns an explicit permission set to one of their firm roles. */
    protected MvcResult assignRolePermissions(String firmAdminToken, Role role, Collection<UUID> permissionIds)
            throws Exception {
        MvcResult result = authPut(firmAdminToken,
                "/api/v1/firm/roles/" + role.getId() + "/permissions",
                apiRequest(Map.of("roleId", role.getId(), "permissionIds", permissionIds)));
        permissionEvaluator.clearAllCache();
        return result;
    }

    /**
     * Full "super admin hands over everything" setup:
     * SA grants every seeded permission to the firm's FIRM_ADMIN role, and the
     * admin's token is re-minted afterwards (the grant bumps permVersion).
     */
    protected String grantEverythingAndToken(String saToken, Firm firm, String adminUsername) throws Exception {
        MvcResult grant = grantRolePermissions(saToken, firm, firmRole(firm, "FIRM_ADMIN"), everyPermissionId());
        assertEquals(200, grant.getResponse().getStatus(),
                "Granting all permissions should succeed: " + grant.getResponse().getContentAsString());
        return token(firmAdmin(firm, adminUsername));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result helpers
    // ═══════════════════════════════════════════════════════════════════════

    protected int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return parseResponse(result);
    }

    protected String raw(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected boolean ok(MvcResult result) throws Exception {
        return status(result) == 200 && json(result).path("success").asBoolean();
    }

    /** Assert the call was rejected by authorization (401/403) — never 200, never 5xx. */
    protected void assertDenied(MvcResult result, String what) throws Exception {
        int code = status(result);
        assertTrue(code == 403 || code == 401,
                what + " should be denied, got " + code + " — " + raw(result));
    }

    /**
     * Assert the call did not succeed, without insisting on 403 — business-rule
     * rejections legitimately come back as 400 (e.g. "cannot delete default role").
     */
    protected void assertRejected(MvcResult result, String what) throws Exception {
        int code = status(result);
        assertTrue(code >= 400 && code < 500,
                what + " should be rejected with a 4xx, got " + code + " — " + raw(result));
    }

    /** Assert the call succeeded. */
    protected void assertAllowed(MvcResult result, String what) throws Exception {
        assertEquals(200, status(result), what + " should be allowed, got " + status(result) + " — " + raw(result));
        assertTrue(json(result).path("success").asBoolean(), what + " returned success=false: " + raw(result));
    }
}
