package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.rbac.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Role-by-role sweep of a single firm: every cloned role is exercised against
 * the endpoints it should and should not reach, then a custom role is rolled
 * out to a real user. Also covers "can role X read/edit role Y?" and the
 * client-portal role.
 */
@Transactional
class FirmRoleMatrixQaTest extends QaBaseTest {

    private record Fixture(Firm firm, String adminToken) {}

    /** SA creates the firm and hands the FIRM_ADMIN role every permission. */
    private Fixture setup(String firmCode, String adminUsername) throws Exception {
        String sa = token(superAdmin("qa_sa_" + firmCode.toLowerCase()));
        createFirm(sa, firmCode, adminUsername);
        Firm firm = firmByCode(firmCode);
        String adminToken = grantEverythingAndToken(sa, firm, adminUsername);
        return new Fixture(firm, adminToken);
    }

    private Map<String, Object> matterBody(String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("matterType", "CIVIL");
        body.put("title", title);
        body.put("originatingCourtLevel", "DISTRICT");
        body.put("courtName", "Kathmandu District Court");
        return body;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Firm admin — the full toolkit
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-01: fully-privileged firm admin can run every firm-management surface")
    void firmAdminFullToolkit() throws Exception {
        Fixture f = setup("QAROLE1", "qa_role1_admin");
        String admin = f.adminToken();
        Firm firm = f.firm();

        // Roles
        assertAllowed(authGet(admin, "/api/v1/firm/roles"), "list roles");
        assertAllowed(authGet(admin, "/api/v1/firm/roles/" + firmRole(firm, "ADVOCATE").getId() + "/permissions"),
                "read role permissions");
        assertAllowed(authGet(admin, "/api/v1/firm/roles/" + firmRole(firm, "PARALEGAL").getId() + "/users"),
                "read role users");

        // Custom role lifecycle
        MvcResult created = authPost(admin, "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Junior Associate",
                "code", "JUNIOR_ASSOCIATE",
                "description", "Junior lawyer",
                "isActive", true,
                "permissionIds", permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"))));
        assertAllowed(created, "create custom role");
        UUID customRoleId = UUID.fromString(json(created).path("data").path("id").asText());

        assertAllowed(authPut(admin, "/api/v1/firm/roles/" + customRoleId + "/permissions", apiRequest(Map.of(
                "roleId", customRoleId,
                "permissionIds", permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE")))),
                "update custom role permissions");
        assertEquals(3, permCodes(customRoleId).size(), "Permissions should be replaced, not appended");

        assertAllowed(authPatch(admin, "/api/v1/firm/roles/" + customRoleId + "/toggle", null), "toggle custom role");
        assertAllowed(authDelete(admin, "/api/v1/firm/roles/" + customRoleId), "delete custom role");

        // Employees
        MvcResult employee = authPost(admin, "/api/v1/firm/employees", apiRequest(Map.of(
                "username", "qa_emp_new",
                "email", "qa_emp_new@" + firm.getLawFirmCode().toLowerCase() + ".test",
                "mobileNo", "9812345678",
                "password", EMP_PWD,
                "fullName", "QA New Employee",
                "roleId", firmRole(firm, "ADVOCATE").getId(),
                "designation", "Advocate")));
        assertAllowed(employee, "create employee");
        String employeeId = json(employee).path("data").path("id").asText();
        assertFalse(employeeId.isBlank(), "Employee creation must return an id");

        assertAllowed(authGet(admin, "/api/v1/firm/employees?page=0&size=10"), "list employees");
        assertAllowed(authGet(admin, "/api/v1/firm/employees/" + employeeId), "read employee");
        assertAllowed(authPatch(admin, "/api/v1/firm/employees/" + employeeId + "/role",
                apiRequest(Map.of("roleId", firmRole(firm, "PARALEGAL").getId()))), "change employee role");
        assertAllowed(authPatch(admin, "/api/v1/firm/employees/" + employeeId + "/toggle", null), "toggle employee");

        // Clients
        MvcResult client = authPost(admin, "/api/v1/firm/clients", apiRequest(Map.of(
                "username", "qa_client_new",
                "email", "qa_client_new@" + firm.getLawFirmCode().toLowerCase() + ".test",
                "mobileNo", "9812345679",
                "password", CLIENT_PWD,
                "fullName", "QA New Client",
                "portalAccessEnabled", true)));
        assertAllowed(client, "create client");
        String clientId = json(client).path("data").path("id").asText();

        assertAllowed(authGet(admin, "/api/v1/firm/clients?page=0&size=10"), "list clients");
        assertAllowed(authGet(admin, "/api/v1/firm/clients/" + clientId), "read client");
        assertAllowed(authPatch(admin, "/api/v1/firm/clients/" + clientId + "/portal-access",
                apiRequest(Boolean.FALSE)), "revoke portal access");

        // User management
        assertAllowed(authGet(admin, "/api/v1/modules/users?page=0&size=10"), "list users");
        assertAllowed(authGet(admin, "/api/v1/modules/users/search?q=qa_emp_new"), "search users");
        assertAllowed(authGet(admin, "/api/v1/modules/users/" + employeeId + "/profile"), "user profile");
        assertAllowed(authGet(admin, "/api/v1/modules/users/" + employeeId + "/permissions"), "user permissions");
        assertAllowed(authGet(admin, "/api/v1/modules/users/" + employeeId + "/activity"), "user activity");
        assertAllowed(authPost(admin, "/api/v1/modules/users/" + employeeId + "/reset-password",
                apiRequest(Map.of("newPassword", "Fresh2026!"))), "reset employee password");
        assertAllowed(authPost(admin, "/api/v1/modules/users/" + employeeId + "/reset-mfa",
                apiRequest(Map.of("userId", employeeId, "reason", "QA"))), "reset employee MFA");

        // Audit + dashboards
        assertAllowed(authGet(admin, "/api/v1/firm/audit?page=0&size=10"), "firm audit log");
        assertAllowed(authGet(admin, "/api/v1/firm/dashboard"), "case dashboard");
        assertAllowed(authGet(admin, "/api/v1/projects/dashboard"), "project dashboard");

        // The global dashboard aggregates with a native `date(...)` query.
        // H2 (MODE=PostgreSQL) has no date() function, so it 400s here while
        // working on Postgres — assert only that it is not a crash.
        MvcResult globalDashboard = authGet(admin, "/api/v1/modules/dashboard");
        assertTrue(status(globalDashboard) < 500,
                "Global dashboard must not 5xx, got " + status(globalDashboard) + " — " + raw(globalDashboard));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADVOCATE — operational role, no admin surface
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-02: an advocate can do case work but cannot touch admin endpoints")
    void advocateScope() throws Exception {
        Fixture f = setup("QAROLE2", "qa_role2_admin");
        Firm firm = f.firm();

        assignRolePermissions(f.adminToken(), firmRole(firm, "ADVOCATE"), permIds(
                "CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE",
                "CASE_MANAGEMENT:EDIT", "CALENDAR:ACCESS", "CALENDAR:VIEW", "DASHBOARD_MANAGEMENT:VIEW"));

        User advocate = firmUser(firm, "ADVOCATE", "qa_adv2");
        String advToken = token(advocate);

        // Allowed: case work
        assertAllowed(authGet(advToken, "/api/v1/firm/matters?page=0&size=5"), "advocate lists matters");
        MvcResult created = authPost(advToken, "/api/v1/firm/matters", apiRequest(matterBody("Advocate matter")));
        assertAllowed(created, "advocate creates a matter");

        // Denied: every administrative surface
        assertDenied(authGet(advToken, "/api/v1/firm/roles"), "advocate listing roles");
        assertDenied(authPost(advToken, "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Rogue", "code", "ROGUE", "isActive", true))), "advocate creating a role");
        assertDenied(authGet(advToken, "/api/v1/firm/employees?page=0&size=5"), "advocate listing employees");
        assertDenied(authGet(advToken, "/api/v1/firm/clients?page=0&size=5"), "advocate listing clients");
        assertDenied(authGet(advToken, "/api/v1/modules/users?page=0&size=5"), "advocate listing all users");
        assertDenied(authGet(advToken, "/api/v1/firm/config"), "advocate reading firm config");
        assertDenied(authGet(advToken, "/api/v1/firm/email-config"), "advocate reading email config");
        assertDenied(authGet(advToken, "/api/v1/firm/audit?page=0&size=5"), "advocate reading audit log");
        assertDenied(authGet(advToken, "/api/v1/admin/roles"), "advocate reading platform roles");
        // A well-formed body, so the 403 comes from the role check and not from validation
        assertDenied(authPost(advToken, "/api/v1/notifications/broadcast", apiRequest(Map.of(
                "title", "Rogue", "body", "Rogue", "audience", "ALL"))),
                "advocate broadcasting notifications");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PARALEGAL — read-only
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-03: a read-only paralegal can view matters but cannot create, edit or delete")
    void paralegalReadOnly() throws Exception {
        Fixture f = setup("QAROLE3", "qa_role3_admin");
        Firm firm = f.firm();

        // Seed one matter as the admin so the paralegal has something to read
        authPost(f.adminToken(), "/api/v1/firm/matters", apiRequest(matterBody("Seeded matter")));

        assignRolePermissions(f.adminToken(), firmRole(firm, "PARALEGAL"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));

        User paralegal = firmUser(firm, "PARALEGAL", "qa_par3");
        String parToken = token(paralegal);

        assertAllowed(authGet(parToken, "/api/v1/firm/matters?page=0&size=5"), "paralegal reads matters");
        assertDenied(authPost(parToken, "/api/v1/firm/matters", apiRequest(matterBody("Paralegal matter"))),
                "paralegal creating a matter");
        assertDenied(authPut(parToken, "/api/v1/firm/matters/ANY-NUMBER",
                apiRequest(Map.of("title", "Edited by paralegal"))), "paralegal editing a matter");
        assertDenied(authGet(parToken, "/api/v1/firm/roles"), "paralegal listing roles");
        assertDenied(authGet(parToken, "/api/v1/firm/employees?page=0&size=5"), "paralegal listing employees");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Privilege escalation attempts
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-04: an advocate cannot edit another role's permissions (no privilege escalation)")
    void roleCannotEditAnotherRole() throws Exception {
        Fixture f = setup("QAROLE4", "qa_role4_admin");
        Firm firm = f.firm();

        assignRolePermissions(f.adminToken(), firmRole(firm, "ADVOCATE"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE",
                        "ROLE_MANAGEMENT:ACCESS", "ROLE_MANAGEMENT:VIEW", "ROLE_MANAGEMENT:EDIT"));

        User advocate = firmUser(firm, "ADVOCATE", "qa_adv4");
        String advToken = token(advocate);

        // Even holding ROLE_MANAGEMENT:EDIT, the controller is FIRM_ADMIN-only
        MvcResult escalate = authPut(advToken,
                "/api/v1/firm/roles/" + firmRole(firm, "ADVOCATE").getId() + "/permissions",
                apiRequest(Map.of("roleId", firmRole(firm, "ADVOCATE").getId(),
                        "permissionIds", everyPermissionId())));
        assertDenied(escalate, "advocate granting itself every permission");

        UUID roleId = firmRole(firm, "ADVOCATE").getId();
        assertFalse(permCodes(roleId).contains("USER_MANAGEMENT:DELETE"),
                "Advocate must not have escalated its own role");
        assertEquals(6, permCodes(roleId).size(), "Advocate role permissions must be unchanged");
    }

    @Test
    @DisplayName("ROLE-05: firm admin cannot grant a permission above its own ceiling")
    void firmAdminCannotExceedCeiling() throws Exception {
        String sa = token(superAdmin("qa_sa_ceiling"));
        createFirm(sa, "QAROLE5", "qa_role5_admin");
        Firm firm = firmByCode("QAROLE5");

        // Ceiling deliberately excludes EMPLOYEE and USER_MANAGEMENT
        grantRolePermissions(sa, firm, firmRole(firm, "FIRM_ADMIN"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW", "ROLE_MANAGEMENT:ACCESS", "ROLE_MANAGEMENT:VIEW"));
        String admin = token(firmAdmin(firm, "qa_role5_admin"));

        MvcResult result = assignRolePermissions(admin, firmRole(firm, "PARALEGAL"),
                permIds("EMPLOYEE:ACCESS", "EMPLOYEE:VIEW", "EMPLOYEE:EDIT"));
        assertDenied(result, "firm admin granting a permission it does not hold");
        assertTrue(permCodes(firmRole(firm, "PARALEGAL").getId()).isEmpty(),
                "Paralegal role must stay empty after a rejected escalation attempt");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Custom role
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-06: a custom role grants exactly its permissions, nothing more")
    void customRoleScoping() throws Exception {
        Fixture f = setup("QAROLE6", "qa_role6_admin");
        Firm firm = f.firm();

        MvcResult created = authPost(f.adminToken(), "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Case Reader",
                "code", "CASE_READER",
                "description", "Read-only case access",
                "isActive", true)));
        assertAllowed(created, "create CASE_READER role");
        UUID readerRoleId = UUID.fromString(json(created).path("data").path("id").asText());

        // Permissions are applied in a second call — create-role drops permissionIds
        assertAllowed(assignRolePermissions(f.adminToken(), roleRepository.findById(readerRoleId).orElseThrow(),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW")), "grant reader permissions");

        String adminToken = f.adminToken();
        authPost(adminToken, "/api/v1/firm/matters", apiRequest(matterBody("Matter for reader")));

        User reader = createUser("qa_reader6", "qa_reader6@" + firm.getLawFirmCode().toLowerCase() + ".test",
                EMP_PWD, com.lawfirm.erp.common.enums.UserType.FIRM_USER,
                roleRepository.findById(readerRoleId).orElseThrow(), firm);
        String readerToken = token(reader);

        assertAllowed(authGet(readerToken, "/api/v1/firm/matters?page=0&size=5"), "custom role reads matters");
        assertDenied(authPost(readerToken, "/api/v1/firm/matters", apiRequest(matterBody("Should fail"))),
                "custom role creating a matter");
        assertDenied(authGet(readerToken, "/api/v1/firm/clients?page=0&size=5"), "custom role listing clients");
        assertDenied(authGet(readerToken, "/api/v1/modules/users?page=0&size=5"), "custom role listing users");
        assertDenied(authGet(readerToken, "/api/v1/firm/dashboard"), "custom role reading the case dashboard");

        JsonNode perms = json(authGet(adminToken, "/api/v1/firm/roles/" + readerRoleId + "/permissions")).path("data");
        assertTrue(perms.toString().contains("CASE_MANAGEMENT:VIEW"), "Role permission read-back should list VIEW");
    }

    @Test
    @DisplayName("ROLE-06b: firm admin creates a role with permissions in one call; ceiling still applies")
    void firmAdminCreatesRoleWithPermissions() throws Exception {
        String sa = token(superAdmin("qa_sa_role6b"));
        createFirm(sa, "QAROLE6B", "qa_role6b_admin");
        Firm firm = firmByCode("QAROLE6B");

        // Ceiling deliberately excludes EMPLOYEE so the over-reach below is a real escalation
        grantRolePermissions(sa, firm, firmRole(firm, "FIRM_ADMIN"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW",
                        "ROLE_MANAGEMENT:ACCESS", "ROLE_MANAGEMENT:VIEW"));
        String admin = token(firmAdmin(firm, "qa_role6b_admin"));

        MvcResult created = authPost(admin, "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Case Reader",
                "code", "CASE_READER_6B",
                "description", "Read-only case access",
                "isActive", true,
                "permissionIds", permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"))));
        assertAllowed(created, "create a role with permissions");
        UUID roleId = UUID.fromString(json(created).path("data").path("id").asText());
        assertEquals(java.util.Set.of("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"),
                permCodes(roleId),
                "create must apply the permissionIds in the same call");

        // The ceiling is enforced on create exactly as it is on update
        MvcResult escalated = authPost(admin, "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Wide Role",
                "code", "WIDE_ROLE_6B",
                "isActive", true,
                "permissionIds", permIds("EMPLOYEE:ACCESS", "EMPLOYEE:VIEW", "EMPLOYEE:EDIT"))));
        assertDenied(escalated, "create a role above the firm admin's ceiling");
    }

    @Test
    @DisplayName("ROLE-06c: a firm cannot mint a platform-level SUPER_ADMIN role")
    void firmCannotCreateSuperAdminRole() throws Exception {
        Fixture f = setup("QAROLE6C", "qa_role6c_admin");

        // JwtUtil grants ROLE_<roleCode> straight from the code, so a firm-scoped SUPER_ADMIN
        // role would let an employee reach /api/v1/super-admin/** (verified: it did before this guard).
        assertRejected(authPost(f.adminToken(), "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Sneaky", "code", "SUPER_ADMIN", "isActive", true))),
                "a firm admin minting a SUPER_ADMIN-coded role");

        assertTrue(roleRepository.findByFirmIdAndRoleCode(f.firm().getId(), "SUPER_ADMIN").isEmpty(),
                "No SUPER_ADMIN-coded role may exist inside a firm");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Role change propagation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-07: downgrading an employee's role takes effect and invalidates the old session")
    void roleChangePropagates() throws Exception {
        Fixture f = setup("QAROLE7", "qa_role7_admin");
        Firm firm = f.firm();

        assignRolePermissions(f.adminToken(), firmRole(firm, "ADVOCATE"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE"));
        assignRolePermissions(f.adminToken(), firmRole(firm, "PARALEGAL"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));

        EmployeeFixture employee = createEmployee(firm, f.adminToken(), "qa_shift7");
        String employeeId = employee.id().toString();

        String before = freshToken(userRepository.findById(employee.id()).orElseThrow());
        assertAllowed(authPost(before, "/api/v1/firm/matters", apiRequest(matterBody("Before shift"))),
                "advocate can create before the downgrade");

        assertAllowed(authPatch(f.adminToken(), "/api/v1/firm/employees/" + employeeId + "/role",
                apiRequest(Map.of("roleId", firmRole(firm, "PARALEGAL").getId()))), "downgrade to paralegal");

        // Old token: either rejected as stale (401) or now denied (403)
        MvcResult withOldToken = authPost(before, "/api/v1/firm/matters", apiRequest(matterBody("After shift")));
        assertDenied(withOldToken, "stale token creating a matter after downgrade");

        String after = freshToken(userRepository.findById(employee.id()).orElseThrow());
        assertAllowed(authGet(after, "/api/v1/firm/matters?page=0&size=5"), "paralegal can still read");
        assertDenied(authPost(after, "/api/v1/firm/matters", apiRequest(matterBody("After shift 2"))),
                "paralegal cannot create after downgrade");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Default role protection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-08: cloned default roles cannot be deleted but a custom role can")
    void defaultRoleDeletionBlocked() throws Exception {
        Fixture f = setup("QAROLE8", "qa_role8_admin");
        Firm firm = f.firm();

        MvcResult deleteAdvocate = authDelete(f.adminToken(), "/api/v1/firm/roles/" + firmRole(firm, "ADVOCATE").getId());
        assertRejected(deleteAdvocate, "deleting the cloned ADVOCATE role");
        assertTrue(raw(deleteAdvocate).contains("default role"),
                "The rejection should explain that default roles are protected: " + raw(deleteAdvocate));

        MvcResult custom = authPost(f.adminToken(), "/api/v1/firm/roles", apiRequest(Map.of(
                "name", "Temp Role", "code", "TEMP_ROLE", "isActive", true)));
        assertAllowed(custom, "create throwaway role");
        UUID tempId = UUID.fromString(json(custom).path("data").path("id").asText());
        assertAllowed(authDelete(f.adminToken(), "/api/v1/firm/roles/" + tempId), "delete custom role");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CLIENT role — own-records scoping
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-09: a CLIENT account only ever sees its own matters (OWN scope enforced)")
    void clientRoleSeesOnlyOwnMatters() throws Exception {
        Fixture f = setup("QAROLE9", "qa_role9_admin");
        Firm firm = f.firm();

        User client = clientUser(firm, "qa_client9");

        // One matter for this client, two for other clients
        Map<String, Object> own = matterBody("Client's own matter");
        own.put("clientUserId", client.getId());
        assertAllowed(authPost(f.adminToken(), "/api/v1/firm/matters", apiRequest(own)), "seed own matter");
        assertAllowed(authPost(f.adminToken(), "/api/v1/firm/matters",
                apiRequest(matterBody("Matter of another client"))), "seed matter 1");
        assertAllowed(authPost(f.adminToken(), "/api/v1/firm/matters",
                apiRequest(matterBody("Second unrelated matter"))), "seed matter 2");

        grantRolePermissions(token(superAdmin("qa_sa_role9")), firm, firmRole(firm, "CLIENT"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));

        String clientToken = token(client);
        MvcResult result = authGet(clientToken, "/api/v1/firm/matters?page=0&size=20");
        assertAllowed(result, "client reads its own matters");

        JsonNode content = json(result).path("data").path("content");
        assertEquals(1, content.size(),
                "A client must see exactly one matter — its own (got " + content.size() + ")");
        assertEquals("Client's own matter", content.get(0).path("title").asText());
        assertEquals(client.getId().toString(), content.get(0).path("clientUserId").asText());

        // Asking for somebody else's client explicitly must not widen the scope
        MvcResult otherClientFilter = authGet(clientToken, "/api/v1/firm/matters?clientUserId="
                + UUID.randomUUID() + "&page=0&size=20");
        if (status(otherClientFilter) == 200) {
            assertEquals(0, json(otherClientFilter).path("data").path("content").size(),
                    "A client must not be able to filter to another client's matters");
        } else {
            assertDenied(otherClientFilter, "client asking for another client's matters");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Unauthenticated / malformed access
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ROLE-10: user profile and activity both require USER_MANAGEMENT:VIEW")
    void userActivityRequiresPermission() throws Exception {
        Fixture f = setup("QAROLE10", "qa_role10_admin");
        Firm firm = f.firm();

        // Advocate with case permissions only — no USER_MANAGEMENT grant at all
        assignRolePermissions(f.adminToken(), firmRole(firm, "ADVOCATE"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));
        User advocate = firmUser(firm, "ADVOCATE", "qa_adv10");
        User admin = firmAdmin(firm, "qa_role10_admin");
        String advToken = token(advocate);

        assertDenied(authGet(advToken, "/api/v1/modules/users/" + admin.getId() + "/profile"),
                "advocate reading another user's profile");
        assertDenied(authGet(advToken, "/api/v1/modules/users/" + admin.getId() + "/activity"),
                "advocate reading another user's activity");

        // With the permission granted in the role, the same call succeeds
        grantRolePermissions(token(superAdmin("qa_sa_role10")), firm, firmRole(firm, "ADVOCATE"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW",
                        "USER_MANAGEMENT:ACCESS", "USER_MANAGEMENT:VIEW"));
        String refreshed = freshToken(userRepository.findById(advocate.getId()).orElseThrow());
        assertAllowed(authGet(refreshed, "/api/v1/modules/users/" + admin.getId() + "/activity"),
                "activity with USER_MANAGEMENT:VIEW");
    }
}
