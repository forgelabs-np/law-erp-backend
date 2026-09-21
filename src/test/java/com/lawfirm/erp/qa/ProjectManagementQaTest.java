package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.projectmanagement.entity.Credential;
import com.lawfirm.erp.modules.projectmanagement.repository.CredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Project Management exercised end-to-end, including the one module that IS
 * bound to a client (clientUserId) and its portal. Covers credentials
 * (encryption + reveal gating), renewals and per-firm isolation.
 *
 * NOTE: unlike the rest of the product, these endpoints take a bare DTO body
 * (no {@code {"data": ...}} envelope) and ProjectController.updateStatus takes
 * the status as a query parameter. That inconsistency is recorded in the QA report.
 */
@Transactional
class ProjectManagementQaTest extends QaBaseTest {

    @Autowired
    private CredentialRepository credentialRepository;

    private record Setup(Firm firm, String adminToken) {}

    private Setup firmWithProjects(String firmCode, String adminUsername) throws Exception {
        String sa = token(superAdmin("qa_sa_" + firmCode.toLowerCase()));
        createFirm(sa, firmCode, adminUsername);
        Firm firm = firmByCode(firmCode);
        String adminToken = grantEverythingAndToken(sa, firm, adminUsername);
        return new Setup(firm, adminToken);
    }

    /** Raw DTO body — project endpoints are not wrapped in ApiRequest. */
    private JsonNode createProject(String token, String name, UUID clientUserId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("clientName", clientUserId == null ? name + " Client" : "Placeholder Client");
        body.put("description", "QA project");
        body.put("startDate", LocalDate.now().toString());
        body.put("targetEndDate", LocalDate.now().plusMonths(6).toString());
        if (clientUserId != null) body.put("clientUserId", clientUserId);

        MvcResult result = authPost(token, "/api/v1/projects", body);
        assertAllowed(result, "create project " + name);
        return json(result).path("data");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Client binding + portal
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PRJ-01: a project bound to a client appears in that client's portal — and only theirs")
    void projectBoundToClientPortal() throws Exception {
        Setup s = firmWithProjects("QAPRJ1", "qa_prj1_admin");
        User clientA = clientUser(s.firm(), "qa_prj1_clienta");
        User clientB = clientUser(s.firm(), "qa_prj1_clientb");

        JsonNode boundA = createProject(s.adminToken(), "Trademark Portfolio A", clientA.getId());
        assertEquals(clientA.getId().toString(), boundA.path("clientUserId").asText(),
                "Project must store the client link");
        // NOTE: ProjectServiceImpl only fills clientName from the client when the caller
        // omits it, but CreateProjectRequest.clientName is @NotBlank — so the caller's
        // string always wins and the auto-fill branch is unreachable through the API.
        assertEquals("Placeholder Client", boundA.path("clientName").asText(),
                "clientName is whatever the caller sent; the denormalize fallback never runs via the API");

        // Unbound project — the "new client" flow
        JsonNode unbound = createProject(s.adminToken(), "Internal Compliance", null);
        assertTrue(unbound.path("clientUserId").isNull() || unbound.path("clientUserId").isMissingNode(),
                "Unbound project should have no client link");

        JsonNode firmList = json(authGet(s.adminToken(), "/api/v1/projects?page=0&size=20")).path("data").path("content");
        assertEquals(2, firmList.size(), "Firm side should see both projects");

        // Client A sees only its own project
        JsonNode portalA = json(authGet(token(clientA), "/api/v1/client/projects")).path("data");
        assertEquals(1, portalA.size(), "Client A must see exactly one project");
        assertEquals("Trademark Portfolio A", portalA.get(0).path("name").asText());

        // Client B sees nothing
        assertEquals(0, json(authGet(token(clientB), "/api/v1/client/projects")).path("data").size(),
                "Client B must not see Client A's project");

        String codeA = portalA.get(0).path("projectCode").asText();
        assertAllowed(authGet(token(clientA), "/api/v1/client/projects/" + codeA), "client reads own project");
        assertAllowed(authGet(token(clientA), "/api/v1/client/projects/" + codeA + "/renewals"),
                "client reads own project renewals");

        MvcResult steal = authGet(token(clientB), "/api/v1/client/projects/" + codeA);
        assertNotEquals(200, status(steal),
                "FINDING: Client B read Client A's project by code — " + raw(steal));

        // The unbound project must not leak into anyone's portal
        for (User client : new User[]{clientA, clientB}) {
            JsonNode portal = json(authGet(token(client), "/api/v1/client/projects")).path("data");
            for (JsonNode project : portal) {
                assertNotEquals("Internal Compliance", project.path("name").asText(),
                        "An unbound project must never appear in a client portal");
            }
        }
    }

    @Test
    @DisplayName("PRJ-02: linking a project to another firm's client is rejected")
    void crossFirmClientLinkRejected() throws Exception {
        Setup a = firmWithProjects("QAPRJ2A", "qa_prj2a_admin");
        Setup b = firmWithProjects("QAPRJ2B", "qa_prj2b_admin");
        User foreignClient = clientUser(b.firm(), "qa_prj2b_client");

        MvcResult result = authPost(a.adminToken(), "/api/v1/projects", Map.of(
                "name", "Cross-firm project",
                "clientName", "Foreign",
                "clientUserId", foreignClient.getId()));
        assertNotEquals(200, status(result),
                "Firm A must not bind a project to Firm B's client — got " + status(result) + " " + raw(result));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Project lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PRJ-03: project update, status change, members and dashboard work")
    void projectLifecycle() throws Exception {
        Setup s = firmWithProjects("QAPRJ3", "qa_prj3_admin");
        String token = s.adminToken();
        JsonNode project = createProject(token, "License Renewals", null);
        String code = project.path("projectCode").asText();
        UUID ownerId = UUID.fromString(project.path("ownerId").asText());
        User employee = firmUser(s.firm(), "ADVOCATE", "qa_prj3_emp");

        assertAllowed(authPut(token, "/api/v1/projects/" + code,
                Map.of("name", "License Renewals 2026", "clientName", "Renamed Client")), "update project");
        assertEquals("License Renewals 2026",
                json(authGet(token, "/api/v1/projects/" + code)).path("data").path("name").asText());

        // Status arrives as a query parameter on this endpoint
        assertAllowed(authPatch(token, "/api/v1/projects/" + code + "/status?status=ON_HOLD", null), "change status");
        assertEquals("ON_HOLD", json(authGet(token, "/api/v1/projects/" + code)).path("data").path("status").asText());

        assertAllowed(authPost(token, "/api/v1/projects/" + code + "/members",
                Map.of("userId", employee.getId(), "role", "MEMBER")), "add member");
        assertTrue(json(authGet(token, "/api/v1/projects/" + code + "/members")).path("data").size() >= 2,
                "Member list should include the owner and the added member");
        assertAllowed(authDelete(token, "/api/v1/projects/" + code + "/members/" + employee.getId()), "remove member");

        assertNotNull(ownerId, "Project must record an owner");
        assertAllowed(authGet(token, "/api/v1/projects/dashboard"), "project dashboard");
        assertAllowed(authGet(token, "/api/v1/projects?status=ON_HOLD&page=0&size=10"), "filter projects by status");
    }

    @Test
    @DisplayName("PRJ-04: Firm B cannot read, edit or list Firm A's projects")
    void crossFirmProjectIsolation() throws Exception {
        Setup a = firmWithProjects("QAPRJ4A", "qa_prj4a_admin");
        Setup b = firmWithProjects("QAPRJ4B", "qa_prj4b_admin");

        String codeA = createProject(a.adminToken(), "Firm A Project", null).path("projectCode").asText();
        createProject(b.adminToken(), "Firm B Project", null);

        assertNotEquals(200, status(authGet(b.adminToken(), "/api/v1/projects/" + codeA)),
                "Firm B read Firm A's project");
        assertNotEquals(200, status(authPut(b.adminToken(), "/api/v1/projects/" + codeA,
                Map.of("name", "Hijacked", "clientName", "Hijacker"))), "Firm B edited Firm A's project");
        assertNotEquals(200, status(authPost(b.adminToken(), "/api/v1/projects/" + codeA + "/credentials",
                Map.of("siteName", "Injected", "usernameOrEmail", "x", "password", "y"))),
                "Firm B added a credential to Firm A's project");

        JsonNode listB = json(authGet(b.adminToken(), "/api/v1/projects?page=0&size=50")).path("data").path("content");
        assertEquals(1, listB.size(), "Firm B must only see its own project");
        assertEquals("Firm B Project", listB.get(0).path("name").asText());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Credentials
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PRJ-05: credentials are stored encrypted and reveal is separately gated")
    void credentialsEncryptedAndGated() throws Exception {
        Setup s = firmWithProjects("QAPRJ5", "qa_prj5_admin");
        Firm firm = s.firm();
        String token = s.adminToken();
        String code = createProject(token, "Credential Project", null).path("projectCode").asText();

        String plaintext = "SuperSecret#2026";
        MvcResult added = authPost(token, "/api/v1/projects/" + code + "/credentials", Map.of(
                "siteName", "IRD Portal",
                "siteType", "TAX",
                "siteUrl", "https://ird.gov.np",
                "usernameOrEmail", "firm-login",
                "password", plaintext,
                "notes", "Quarterly filing login"));
        assertAllowed(added, "add credential");
        long credentialId = json(added).path("data").path("id").asLong();

        Credential stored = credentialRepository.findById(credentialId).orElseThrow();
        assertNotEquals(plaintext, stored.getEncryptedPassword(),
                "Credential password must not be stored in clear text");
        assertFalse(stored.getEncryptedPassword().contains(plaintext),
                "Cipher text must not embed the plain password");

        assertAllowed(authGet(token, "/api/v1/projects/" + code + "/credentials"), "list credentials");
        assertAllowed(authGet(token, "/api/v1/projects/" + code + "/credentials/" + credentialId), "read credential");

        // A user holding only PROJECT_MANAGEMENT:VIEW cannot touch credentials
        assignRolePermissions(token, firmRole(firm, "ADVOCATE"),
                permIds("PROJECT_MANAGEMENT:ACCESS", "PROJECT_MANAGEMENT:VIEW"));
        User employee = firmUser(firm, "ADVOCATE", "qa_prj5_emp");
        String empToken = token(employee);

        assertDenied(authGet(empToken, "/api/v1/projects/" + code + "/credentials"),
                "credential list without CREDENTIAL_VIEW");
        assertDenied(authPost(empToken, "/api/v1/projects/" + code + "/credentials/" + credentialId + "/reveal", null),
                "reveal without CREDENTIAL_REVEAL");

        MvcResult revealed = authPost(token, "/api/v1/projects/" + code + "/credentials/" + credentialId + "/reveal", null);
        assertAllowed(revealed, "admin reveal");
        assertTrue(raw(revealed).contains(plaintext), "Reveal must return the original password");

        assertAllowed(authPut(token, "/api/v1/projects/" + code + "/credentials/" + credentialId,
                Map.of("siteName", "IRD Portal v2", "usernameOrEmail", "firm-login-2", "password", "NewSecret#2026")),
                "update credential");
        assertAllowed(authDelete(token, "/api/v1/projects/" + code + "/credentials/" + credentialId), "delete credential");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Renewals
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PRJ-06: renewal types and renewal instances run through their lifecycle")
    void renewals() throws Exception {
        Setup s = firmWithProjects("QAPRJ6", "qa_prj6_admin");
        String token = s.adminToken();
        String code = createProject(token, "Renewal Project", null).path("projectCode").asText();

        assertAllowed(authGet(token, "/api/v1/projects/renewal-types"), "list renewal types");

        MvcResult type = authPost(token, "/api/v1/projects/renewal-types",
                Map.of("name", "QA Annual Filing", "description", "QA generated type"));
        assertAllowed(type, "create renewal type");
        long typeId = json(type).path("data").path("id").asLong();

        Map<String, Object> renewal = new LinkedHashMap<>();
        renewal.put("renewalTypeId", typeId);
        renewal.put("title", "Annual ROC filing");
        renewal.put("recurrence", "YEARLY");
        renewal.put("startDate", LocalDate.now().toString());
        renewal.put("endDate", LocalDate.now().plusYears(2).toString());

        MvcResult created = authPost(token, "/api/v1/projects/" + code + "/renewals", renewal);
        assertAllowed(created, "create renewal");
        long renewalId = json(created).path("data").path("id").asLong();

        JsonNode fetched = json(authGet(token, "/api/v1/projects/" + code + "/renewals/" + renewalId)).path("data");
        assertEquals("Annual ROC filing", fetched.path("title").asText());
        assertTrue(json(authGet(token, "/api/v1/projects/" + code + "/renewals")).path("data").size() >= 1,
                "Renewal should be listed under the project");

        JsonNode instances = fetched.path("instances");
        assertTrue(instances.size() >= 1, "A recurring renewal should generate instances");
        long instanceId = instances.get(0).path("id").asLong();

        assertAllowed(authPatch(token, "/api/v1/projects/" + code + "/renewals/" + renewalId
                + "/instances/" + instanceId, Map.of("status", "COMPLETED", "notes", "Filed on time")),
                "complete renewal instance");

        assertEquals("COMPLETED",
                json(authGet(token, "/api/v1/projects/" + code + "/renewals/" + renewalId))
                        .path("data").path("instances").get(0).path("status").asText(),
                "Instance status change must persist");

        assertAllowed(authPut(token, "/api/v1/projects/" + code + "/renewals/" + renewalId,
                Map.of("renewalTypeId", typeId, "title", "Annual ROC filing (revised)",
                        "recurrence", "YEARLY", "startDate", LocalDate.now().toString())), "update renewal");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Access control
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PRJ-07: employees with no project permission are blocked from every project endpoint")
    void projectAccessControl() throws Exception {
        Setup s = firmWithProjects("QAPRJ7", "qa_prj7_admin");
        Firm firm = s.firm();
        String code = createProject(s.adminToken(), "Guarded Project", null).path("projectCode").asText();

        User employee = firmUser(firm, "ADVOCATE", "qa_prj7_emp");
        String empToken = token(employee);

        assertDenied(authGet(empToken, "/api/v1/projects?page=0&size=5"), "list projects without permission");
        assertDenied(authPost(empToken, "/api/v1/projects", Map.of("name", "Nope", "clientName", "Nope")),
                "create project without permission");
        assertDenied(authGet(empToken, "/api/v1/projects/" + code), "read project without permission");
        assertDenied(authPut(empToken, "/api/v1/projects/" + code,
                Map.of("name", "Nope", "clientName", "Nope")), "edit project without permission");
        assertDenied(authGet(empToken, "/api/v1/projects/dashboard"), "project dashboard without permission");
        assertDenied(authGet(empToken, "/api/v1/projects/renewal-types"), "renewal types without permission");
    }

    @Test
    @DisplayName("PRJ-08: the client portal cannot reach firm-side project management APIs")
    void clientPortalCannotReachFirmApis() throws Exception {
        Setup s = firmWithProjects("QAPRJ8", "qa_prj8_admin");
        User client = clientUser(s.firm(), "qa_prj8_client");
        String clientToken = token(client);
        String code = createProject(s.adminToken(), "Portal Guard Project", client.getId())
                .path("projectCode").asText();

        assertAllowed(authGet(clientToken, "/api/v1/client/projects"), "portal list");
        assertDenied(authGet(clientToken, "/api/v1/projects?page=0&size=5"), "portal hitting firm project list");
        assertDenied(authGet(clientToken, "/api/v1/projects/" + code), "portal hitting firm project detail");
        assertDenied(authGet(clientToken, "/api/v1/projects/dashboard"), "portal hitting firm dashboard");
        assertDenied(authGet(clientToken, "/api/v1/projects/" + code + "/credentials"), "portal hitting credentials");
    }
}
