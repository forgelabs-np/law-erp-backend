package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Case Management exercised per firm: matter lifecycle, court cases, hearings,
 * assignments, dashboards, and — the point of a multi-tenant product — that
 * Firm A can never reach Firm B's case data. Also records the client-binding
 * situation (matters carry no client link, only parties do).
 */
@Transactional
class CaseManagementQaTest extends QaBaseTest {

    private record FirmSetup(Firm firm, String adminToken) {}

    private FirmSetup firmWithCaseAccess(String firmCode, String adminUsername) throws Exception {
        String sa = token(superAdmin("qa_sa_" + firmCode.toLowerCase()));
        createFirm(sa, firmCode, adminUsername);
        Firm firm = firmByCode(firmCode);
        String adminToken = grantEverythingAndToken(sa, firm, adminUsername);
        return new FirmSetup(firm, adminToken);
    }

    private Map<String, Object> matterBody(String title, UUID partnerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("matterType", "CIVIL");
        body.put("title", title);
        body.put("originatingCourtLevel", "DISTRICT");
        body.put("courtName", "Kathmandu District Court");
        if (partnerId != null) body.put("assignedPartnerId", partnerId);
        return body;
    }

    private String createMatter(String token, String title, UUID partnerId) throws Exception {
        MvcResult result = authPost(token, "/api/v1/firm/matters", apiRequest(matterBody(title, partnerId)));
        assertAllowed(result, "create matter " + title);
        String matterNumber = json(result).path("data").path("matterNumber").asText();
        assertFalse(matterNumber.isBlank(), "Matter creation must return a matterNumber");
        return matterNumber;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle + data correctness
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CASE-01: full matter lifecycle persists correct, linked data")
    void matterLifecycle() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASE1", "qa_case1_admin");
        String token = s.adminToken();

        // Matter with a party that is marked as "our client"
        User client = clientUser(s.firm(), "qa_case1_client");
        Map<String, Object> party = new LinkedHashMap<>();
        party.put("fullName", "Ram Bahadur");
        party.put("mobileNo", "9801111111");
        party.put("isOurClient", true);
        party.put("clientId", client.getId());
        party.put("roleType", "PLAINTIFF");

        Map<String, Object> matter = matterBody("Land dispute", null);
        matter.put("parties", List.of(party));
        matter.put("description", "Boundary dispute in Lalitpur");

        MvcResult created = authPost(token, "/api/v1/firm/matters", apiRequest(matter));
        assertAllowed(created, "create matter with a client party");
        JsonNode data = json(created).path("data");
        String matterNumber = data.path("matterNumber").asText();
        String matterId = data.path("id").asText();

        assertEquals("ACTIVE", data.path("status").asText(), "New matter should be ACTIVE");
        assertTrue(matterNumber.contains(s.firm().getLawFirmCode()),
                "Matter number should be firm-scoped, got: " + matterNumber);
        assertEquals(1, data.path("parties").size(), "The client party should be persisted");
        assertEquals(client.getId().toString(), data.path("parties").get(0).path("clientId").asText(),
                "Party should link back to the client user");

        // Creating a matter already files its first court case (stage = initialFor(level, type))
        JsonNode afterCreate = json(authGet(token, "/api/v1/firm/matters/" + matterNumber)).path("data");
        assertEquals(1, afterCreate.path("courtCases").size(),
                "createMatter should auto-file the originating court case");
        assertEquals("FILED", afterCreate.path("courtCases").get(0).path("stage").asText(),
                "A civil district matter starts at FILED");
        assertEquals(afterCreate.path("courtCases").get(0).path("id").asText(),
                afterCreate.path("currentCourtCaseId").asText(),
                "The originating court case must be the active one");

        // Add a second court case to the chain
        Map<String, Object> courtCase = new LinkedHashMap<>();
        courtCase.put("relationType", "ORIGINAL");
        courtCase.put("courtLevel", "DISTRICT");
        courtCase.put("courtName", "Lalitpur District Court");
        courtCase.put("courtCaseNumber", "079-CR-0123");
        courtCase.put("filingDate", LocalDate.now().toString());

        MvcResult withCase = authPost(token, "/api/v1/firm/matters/" + matterNumber + "/court-cases",
                apiRequest(courtCase));
        assertAllowed(withCase, "add court case");
        JsonNode courtCases = json(withCase).path("data").path("courtCases");
        assertEquals(2, courtCases.size(), "The matter should now own a chain of two court cases");
        String ref = courtCases.get(courtCases.size() - 1).path("ourCourtCaseRef").asText();
        assertFalse(ref.isBlank(), "Court case must get a firm-scoped reference");

        // Schedule a hearing and read it back
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "TARIK");
        event.put("scheduledDate", LocalDate.now().plusDays(14).toString());
        event.put("judgeName", "Hon. Justice Sharma");
        event.put("courtRoom", "Court Room 3");
        assertAllowed(authPost(token, "/api/v1/firm/court-cases/" + ref + "/events", apiRequest(event)),
                "schedule hearing");

        JsonNode events = json(authGet(token, "/api/v1/firm/court-cases/" + ref + "/events")).path("data");
        assertTrue(events.size() >= 1, "Scheduled hearing should be listed");

        // Timeline + read-back
        JsonNode timeline = json(authGet(token, "/api/v1/firm/matters/" + matterNumber + "/timeline")).path("data");
        assertTrue(timeline.size() >= 1, "Timeline should record matter activity");

        JsonNode fetched = json(authGet(token, "/api/v1/firm/matters/" + matterNumber)).path("data");
        assertEquals(matterId, fetched.path("id").asText(), "Read-back must return the same matter");
        assertEquals(2, fetched.path("courtCases").size(), "Matter should own auto-filed + added court cases");

        // Stale matters + search + dashboards stay consistent
        assertAllowed(authGet(token, "/api/v1/firm/matters/stale?days=90"), "stale matters");
        assertAllowed(authGet(token, "/api/v1/firm/matters?search=Land"), "search matters");
        assertAllowed(authGet(token, "/api/v1/firm/dashboard"), "case dashboard");
        assertAllowed(authGet(token, "/api/v1/firm/calendar?from=" + LocalDate.now().minusDays(1)
                + "&to=" + LocalDate.now().plusDays(30)), "calendar window");
        assertAllowed(authGet(token, "/api/v1/firm/calendar/today"), "calendar today");
        assertAllowed(authGet(token, "/api/v1/firm/calendar/upcoming?days=30"), "calendar upcoming");
        assertAllowed(authGet(token, "/api/v1/firm/court-cases/" + ref + "/allowed-stages"), "allowed stages");
        assertAllowed(authGet(token, "/api/v1/firm/court-cases/appeal-deadlines?days=30"), "appeal deadlines");
    }

    @Test
    @DisplayName("CASE-02: stage transition and judgment recording update the court case")
    void stageAndJudgment() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASE2", "qa_case2_admin");
        String token = s.adminToken();
        String matterNumber = createMatter(token, "Cheque bounce", null);

        // The matter's originating court case is created for us
        JsonNode matter = json(authGet(token, "/api/v1/firm/matters/" + matterNumber)).path("data");
        String ref = matter.path("courtCases").get(0).path("ourCourtCaseRef").asText();
        assertEquals("FILED", matter.path("courtCases").get(0).path("stage").asText(),
                "A civil district matter starts at FILED");

        // Illegal jumps are blocked (EXECUTION is not reachable from FILED)
        assertRejected(authPut(token, "/api/v1/firm/court-cases/" + ref + "/stage",
                apiRequest(Map.of("stage", "EXECUTION"))), "illegal stage jump from FILED");

        // Walk the legal workflow to the judgment stage, only using stages the API offers
        Set<String> terminal = Set.of("CLOSED", "APPEALED", "FURTHER_APPEALED", "REMANDED",
                "EXECUTION", "SENTENCING");
        String current = "FILED";
        for (int hop = 0; hop < 10 && !"JUDGMENT_AWAITED".equals(current); hop++) {
            JsonNode allowed = json(authGet(token, "/api/v1/firm/court-cases/" + ref + "/allowed-stages")).path("data");
            String next = null;
            for (JsonNode candidate : allowed) {
                String stage = candidate.isTextual() ? candidate.asText() : candidate.path("stage").asText();
                if (!terminal.contains(stage)) { next = stage; break; }
            }
            assertNotNull(next, "No non-terminal stage available from " + current + " — payload: " + allowed);

            assertAllowed(authPut(token, "/api/v1/firm/court-cases/" + ref + "/stage",
                    apiRequest(Map.of("stage", next))), "advance " + current + " -> " + next);
            assertEquals(next,
                    json(authGet(token, "/api/v1/firm/court-cases/" + ref)).path("data").path("stage").asText(),
                    "Stage change must persist");
            current = next;
        }
        assertEquals("JUDGMENT_AWAITED", current, "The legal workflow should be able to reach the judgment stage");

        MvcResult judgment = authPut(token, "/api/v1/firm/court-cases/" + ref + "/judgment", apiRequest(Map.of(
                "judgmentDate", LocalDate.now().toString(),
                "judgmentSummary", "Decree in favour of the plaintiff")));
        assertAllowed(judgment, "record judgment");
        JsonNode cc = json(authGet(token, "/api/v1/firm/court-cases/" + ref)).path("data");
        assertEquals(LocalDate.now().toString(), cc.path("judgmentDate").asText(), "Judgment date must persist");
        assertEquals("JUDGMENT_DELIVERED", cc.path("stage").asText(),
                "Recording a judgment must move the case to JUDGMENT_DELIVERED");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Assignments
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CASE-03: employees can be assigned to and revoked from a matter")
    void matterAssignments() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASE3", "qa_case3_admin");
        String token = s.adminToken();

        User advocate = firmUser(s.firm(), "ADVOCATE", "qa_case3_adv");
        User paralegal = firmUser(s.firm(), "PARALEGAL", "qa_case3_par");
        String matterNumber = createMatter(token, "Assignment matter", advocate.getId());

        assertAllowed(authPost(token, "/api/v1/firm/matters/" + matterNumber + "/assignments",
                apiRequest(Map.of("userId", advocate.getId(), "assignmentRole", "PRIMARY_ADVOCATE"))),
                "assign primary advocate");
        assertAllowed(authPost(token, "/api/v1/firm/matters/" + matterNumber + "/assignments",
                apiRequest(Map.of("userId", paralegal.getId(), "assignmentRole", "PARALEGAL"))),
                "assign paralegal");

        JsonNode assignments = json(authGet(token, "/api/v1/firm/matters/" + matterNumber + "/assignments")).path("data");
        assertEquals(2, assignments.size(), "Both assignments should be listed");

        assertAllowed(authDelete(token, "/api/v1/firm/matters/" + matterNumber + "/assignments/" + paralegal.getId()),
                "revoke paralegal");
        assertEquals(1, json(authGet(token, "/api/v1/firm/matters/" + matterNumber + "/assignments")).path("data").size(),
                "Revoked assignment must disappear");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cross-firm isolation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CASE-04: Firm B cannot read, edit or list Firm A's matters")
    void crossFirmMatterIsolation() throws Exception {
        FirmSetup a = firmWithCaseAccess("QACASEA", "qa_casea_admin");
        FirmSetup b = firmWithCaseAccess("QACASEB", "qa_caseb_admin");

        String matterA = createMatter(a.adminToken(), "Firm A confidential matter", null);
        String matterB = createMatter(b.adminToken(), "Firm B matter", null);

        MvcResult read = authGet(b.adminToken(), "/api/v1/firm/matters/" + matterA);
        assertNotEquals(200, status(read),
                "Firm B read Firm A's matter (IDOR): " + raw(read));

        MvcResult edit = authPut(b.adminToken(), "/api/v1/firm/matters/" + matterA,
                apiRequest(Map.of("title", "Tampered by Firm B")));
        assertNotEquals(200, status(edit), "Firm B edited Firm A's matter: " + raw(edit));

        JsonNode listB = json(authGet(b.adminToken(), "/api/v1/firm/matters?page=0&size=50")).path("data").path("content");
        for (JsonNode matter : listB) {
            assertEquals(matterB, matter.path("matterNumber").asText(),
                    "Firm B's matter list must not contain Firm A rows");
        }

        // Firm A's matter is untouched
        assertEquals("Firm A confidential matter",
                json(authGet(a.adminToken(), "/api/v1/firm/matters/" + matterA)).path("data").path("title").asText());
    }

    @Test
    @DisplayName("CASE-05: Firm B cannot reach Firm A's court case or hearing endpoints")
    void crossFirmCourtCaseIsolation() throws Exception {
        FirmSetup a = firmWithCaseAccess("QACASEC", "qa_casec_admin");
        FirmSetup b = firmWithCaseAccess("QACASED", "qa_cased_admin");

        String matterA = createMatter(a.adminToken(), "Firm A case", null);
        MvcResult withCase = authPost(a.adminToken(), "/api/v1/firm/matters/" + matterA + "/court-cases",
                apiRequest(Map.of("relationType", "ORIGINAL", "courtLevel", "DISTRICT",
                        "courtName", "Kathmandu District Court")));
        assertAllowed(withCase, "firm A adds court case");
        String ref = json(withCase).path("data").path("courtCases").get(0).path("ourCourtCaseRef").asText();

        MvcResult readFromB = authGet(b.adminToken(), "/api/v1/firm/court-cases/" + ref);
        assertNotEquals(200, status(readFromB), "Firm B read Firm A's court case: " + raw(readFromB));

        MvcResult eventsFromB = authGet(b.adminToken(), "/api/v1/firm/court-cases/" + ref + "/events");
        assertNotEquals(200, status(eventsFromB), "Firm B read Firm A's hearings: " + raw(eventsFromB));

        MvcResult stageFromB = authPut(b.adminToken(), "/api/v1/firm/court-cases/" + ref + "/stage",
                apiRequest(Map.of("stage", "CLOSED")));
        assertNotEquals(200, status(stageFromB), "Firm B advanced Firm A's case stage: " + raw(stageFromB));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Access control per employee
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CASE-06: an employee without case permissions cannot touch any case endpoint")
    void employeeWithoutCasePermission() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASE6", "qa_case6_admin");
        Firm firm = s.firm();

        // ADVOCATE role left ungranted → no permissions at all
        User employee = firmUser(firm, "ADVOCATE", "qa_case6_emp");
        String token = token(employee);

        assertDenied(authGet(token, "/api/v1/firm/matters?page=0&size=5"), "list matters without permission");
        assertDenied(authPost(token, "/api/v1/firm/matters", apiRequest(matterBody("Nope", null))),
                "create matter without permission");
        assertDenied(authGet(token, "/api/v1/firm/calendar"), "calendar without permission");
        assertDenied(authGet(token, "/api/v1/firm/dashboard"), "dashboard without permission");
    }

    @Test
    @DisplayName("CASE-07: a departed employee's token stops working once deactivated")
    void deactivatedEmployeeLosesAccess() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASE7", "qa_case7_admin");
        Firm firm = s.firm();

        assignRolePermissions(s.adminToken(), firmRole(firm, "PARALEGAL"),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));
        User employee = firmUser(firm, "PARALEGAL", "qa_case7_par");
        UUID employeeId = employee.getId();
        String token = token(employee);
        assertAllowed(authGet(token, "/api/v1/firm/matters?page=0&size=5"), "paralegal reads matters");

        assertAllowed(authPatch(s.adminToken(), "/api/v1/firm/employees/" + employeeId + "/toggle", null),
                "deactivate employee");

        MvcResult after = authGet(token, "/api/v1/firm/matters?page=0&size=5");
        assertDenied(after, "deactivated employee reading matters");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Client binding
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CASE-08: a matter is bound to a client, and the party fallback still resolves owners")
    void matterClientBinding() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASE8", "qa_case8_admin");
        String token = s.adminToken();
        User client = clientUser(s.firm(), "qa_case8_client");

        // First-class link on create
        Map<String, Object> body = matterBody("Matter with a client link", null);
        body.put("clientUserId", client.getId());
        MvcResult created = authPost(token, "/api/v1/firm/matters", apiRequest(body));
        assertAllowed(created, "create matter with client link");
        JsonNode data = json(created).path("data");
        assertEquals(client.getId().toString(), data.path("clientUserId").asText(),
                "Matter must expose the linked client");
        assertEquals(client.getFullName(), data.path("clientName").asText(),
                "The client name is denormalized for list rendering");

        // Re-assign through update
        User otherClient = clientUser(s.firm(), "qa_case8_client2");
        String matterNumber = data.path("matterNumber").asText();
        assertAllowed(authPut(token, "/api/v1/firm/matters/" + matterNumber,
                apiRequest(Map.of("clientUserId", otherClient.getId()))), "re-assign the matter's client");
        assertEquals(otherClient.getId().toString(),
                json(authGet(token, "/api/v1/firm/matters/" + matterNumber)).path("data").path("clientUserId").asText(),
                "Re-assignment must persist");

        // Party-level fallback keeps pre-existing matters working. The documented key is
        // `isOurClient`, but Lombok/Jackson derived `ourClient` for this DTO, so the API
        // accepts both spellings — a mismatch here silently drops the flag (the hearing
        // reminder job and the client portal both depend on it).
        String legacy = createMatter(token, "Legacy matter, party-only client", null);
        assertAllowed(authPost(token, "/api/v1/firm/matters/" + legacy + "/parties", apiRequest(Map.of(
                "fullName", "Our client",
                "mobileNo", "9802222222",
                "isOurClient", true,
                "clientId", client.getId(),
                "roleType", "PLAINTIFF"))), "attach client as a party");
        assertTrue(json(authGet(token, "/api/v1/firm/matters/" + legacy)).path("data").path("parties")
                        .get(0).path("isOurClient").asBoolean(),
                "The documented isOurClient key must set the flag");

        JsonNode parties = json(authGet(token, "/api/v1/firm/matters/" + legacy)).path("data").path("parties");
        assertEquals(client.getId().toString(), parties.get(0).path("clientId").asText(),
                "Party-level clientId remains the backward-compatible link");
        assertTrue(parties.get(0).path("ourClient").asBoolean(),
                "The legacy ourClient alias must keep working (and still be returned)");

        MvcResult match = authPost(token, "/api/v1/firm/matters/parties/match",
                apiRequest(Map.of("fullName", "Our client", "mobileNo", "9802222222")));
        assertAllowed(match, "party match by name/mobile");
        assertTrue(json(match).path("data").size() >= 1, "Party matching should find the linked client");

        // Party-linked matters are still visible to that client's portal
        MvcResult portalList = authGet(token(client), "/api/v1/client/matters");
        assertAllowed(portalList, "client portal lists own cases");
        assertTrue(json(portalList).path("data").size() >= 1,
                "The party-linked matter should appear in the client's portal");
    }

    @Test
    @DisplayName("CASE-09: staff can filter matters by client; a foreign client link is rejected")
    void matterClientFilterAndValidation() throws Exception {
        FirmSetup a = firmWithCaseAccess("QACASEF", "qa_casef_admin");
        FirmSetup b = firmWithCaseAccess("QACASEG", "qa_caseg_admin");
        User clientA = clientUser(a.firm(), "qa_casef_client");
        User foreignClient = clientUser(b.firm(), "qa_caseg_client");

        Map<String, Object> forA = matterBody("Client A matter", null);
        forA.put("clientUserId", clientA.getId());
        assertAllowed(authPost(a.adminToken(), "/api/v1/firm/matters", apiRequest(forA)), "create client A matter");
        createMatter(a.adminToken(), "Unlinked matter", null);

        MvcResult filtered = authGet(a.adminToken(),
                "/api/v1/firm/matters?clientUserId=" + clientA.getId() + "&page=0&size=20");
        assertAllowed(filtered, "filter matters by client");
        JsonNode content = json(filtered).path("data").path("content");
        assertEquals(1, content.size(), "Only the matching client's matter should be returned");
        assertEquals("Client A matter", content.get(0).path("title").asText());

        Map<String, Object> foreign = matterBody("Cross-firm client matter", null);
        foreign.put("clientUserId", foreignClient.getId());
        assertRejected(authPost(a.adminToken(), "/api/v1/firm/matters", apiRequest(foreign)),
                "binding a matter to another firm's client");
    }

    @Test
    @DisplayName("CASE-10: the client portal shows only the client's own cases")
    void clientPortalMyCases() throws Exception {
        FirmSetup s = firmWithCaseAccess("QACASEH", "qa_caseh_admin");
        String token = s.adminToken();
        User clientOne = clientUser(s.firm(), "qa_caseh_client1");
        User clientTwo = clientUser(s.firm(), "qa_caseh_client2");

        Map<String, Object> one = matterBody("Client one dispute", null);
        one.put("clientUserId", clientOne.getId());
        MvcResult created = authPost(token, "/api/v1/firm/matters", apiRequest(one));
        assertAllowed(created, "create client one matter");
        String oneNumber = json(created).path("data").path("matterNumber").asText();

        Map<String, Object> two = matterBody("Client two dispute", null);
        two.put("clientUserId", clientTwo.getId());
        String twoNumber = createMatterWithClient(token, two);
        createMatter(token, "Unlinked firm matter", null);

        JsonNode portal = json(authGet(token(clientOne), "/api/v1/client/matters")).path("data");
        assertEquals(1, portal.size(), "Client one must see exactly one case");
        assertEquals("Client one dispute", portal.get(0).path("title").asText());
        assertFalse(portal.get(0).path("currentCourtCaseRef").asText().isBlank(),
                "The portal should expose the current proceeding reference");

        assertEquals(1, json(authGet(token(clientTwo), "/api/v1/client/matters")).path("data").size(),
                "Client two must see exactly one case");

        assertAllowed(authGet(token(clientOne), "/api/v1/client/matters/" + oneNumber), "read own case");
        assertNotEquals(200, status(authGet(token(clientOne), "/api/v1/client/matters/" + twoNumber)),
                "Client one must not read client two's case by number");
        assertNotEquals(200, status(authGet(token(clientOne), "/api/v1/firm/matters/" + twoNumber)),
                "Client one must not reach client two's case through the firm API either");

        // A client account with portal access switched off cannot use the portal at all
        User disabled = clientUserWithoutPortal(s.firm(), "qa_caseh_client3");
        assertDenied(authGet(token(disabled), "/api/v1/client/matters"),
                "client with portal access disabled");
    }

    private String createMatterWithClient(String token, Map<String, Object> body) throws Exception {
        MvcResult created = authPost(token, "/api/v1/firm/matters", apiRequest(body));
        assertAllowed(created, "create matter");
        return json(created).path("data").path("matterNumber").asText();
    }

    @Test
    @DisplayName("CASE-11: unauthenticated callers get 401 on every case endpoint")
    void unauthenticatedCaseAccess() throws Exception {
        assertUnauthorized(authGet("", "/api/v1/firm/matters?page=0&size=5"));
        assertUnauthorized(authGet("not-a-real-token", "/api/v1/firm/matters?page=0&size=5"));
    }
}
