package com.lawfirm.erp.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.storage.FirmStorageUsage;
import com.lawfirm.erp.common.storage.FirmStorageUsageRepository;
import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.casemanagement.entity.CaseAssignment;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseAssignmentRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.repository.DocumentRepository;
import com.lawfirm.erp.modules.document.support.DocumentTestFiles;
import com.lawfirm.erp.modules.document.support.FakeStorageService;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.ProjectMember;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectMemberRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * End-to-end document store behaviour over real HTTP with real security, backed by an
 * in-memory object store instead of MinIO.
 *
 * <pre>
 * Firm A ─ adminA        (all permissions)          sees every document in the firm
 *        ├ advocate      (assigned to the matter)   sees only the assigned case's documents
 *        ├ advocate2     (no assignment)            sees nothing
 *        └ clientA       (own case, shared only)    sees only what was shared with them
 * Firm B ─ adminB        (all permissions)          sees nothing of Firm A's
 * </pre>
 *
 * <p>{@code @Transactional} keeps one persistence context open for the whole test, which is
 * what lets the shared harness mint tokens for entities whose firm/role associations are lazy
 * proxies. Everything rolls back at the end.
 */
@Transactional
class DocumentQaTest extends QaBaseTest {

    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Replaces MinIO for this test class only. */
    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        StorageService fakeStorageService() {
            return new FakeStorageService();
        }
    }

    @Autowired private FakeStorageService storageService;
    @Autowired private MatterRepository matterRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private CaseAssignmentRepository caseAssignmentRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private FirmStorageUsageRepository storageUsageRepository;

    private String saToken;
    private Firm firmA;
    private String adminAToken;
    private String adminAUsername;
    private Matter matter;
    private Project project;
    private User clientA;

    @BeforeEach
    void setUpFirmA() throws Exception {
        User sa = superAdmin("docsa" + shortId());
        saToken = token(sa);

        String code = "D" + shortId().toUpperCase();
        adminAUsername = "docadmin" + shortId();
        createFirm(saToken, code, adminAUsername);
        firmA = firmByCode(code);
        adminAToken = grantEverythingAndToken(saToken, firmA, adminAUsername);

        // The working permissions a firm advocate would be granted for documents.
        grantRolePermissions(saToken, firmA, firmRole(firmA, "ADVOCATE"), permIds(
                "DOCUMENT_MANAGEMENT:ACCESS", "DOCUMENT_MANAGEMENT:VIEW", "DOCUMENT_MANAGEMENT:UPLOAD",
                "DOCUMENT_MANAGEMENT:EDIT", "DOCUMENT_MANAGEMENT:SHARE"));

        // A client may read its own shared documents.
        clientA = clientUser(firmA, "docclient" + shortId());
        grantRolePermissions(saToken, firmA, firmRole(firmA, "CLIENT"),
                permIds("DOCUMENT_MANAGEMENT:ACCESS", "DOCUMENT_MANAGEMENT:VIEW"));

        matter = matterRepository.save(newMatter("MT-DOC-0001", null));
        project = projectRepository.save(newProject("DOC-PRJ-0001"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Upload end to end
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a case document uploads and comes back ACTIVE in one call")
    void caseDocumentRoundTrip() throws Exception {
        byte[] pdf = DocumentTestFiles.pdf("petition body");

        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", pdf);

        assertEquals("ACTIVE", document.path("status").asText(),
                "a single upload must finish the document — there is no confirm step");
        assertEquals("PRIVATE", document.path("visibility").asText(), "uploads start firm-only");
        assertEquals(matter.getMatterNumber(), document.path("matterNumber").asText());
        assertEquals(pdf.length, document.path("sizeBytes").asLong());
        assertFalse(document.path("uuid").asText().isBlank(),
                "the document needs a uuid for the audit trail");

        String storageKey = documentRepository.findById(document.path("id").asLong())
                .orElseThrow().getStorageKey();
        assertTrue(storageKey.startsWith("firms/" + firmA.getId() + "/cases/"
                + matter.getMatterNumber() + "/"), storageKey);
        assertTrue(storageKey.endsWith("/petition.pdf"), storageKey);

        MvcResult download = authGet(adminAToken,
                "/api/v1/firm/documents/" + document.path("id").asLong() + "/download-url");
        assertAllowed(download, "download a document");
        assertTrue(json(download).path("data").path("downloadUrl").asText().contains("fake-storage"));

        assertEquals(1L, matterTimelineCount(matter.getId()),
                "the upload should appear on the case timeline");
    }

    @Test
    @DisplayName("a project document is filed under its project code")
    void projectDocumentRoundTrip() throws Exception {
        JsonNode document = uploadDocument(adminAToken, null, project.getProjectCode(),
                "agreement.docx", DOCX, DocumentTestFiles.zip("word/document.xml"));

        String storageKey = documentRepository.findById(document.path("id").asLong())
                .orElseThrow().getStorageKey();
        assertTrue(storageKey.startsWith("firms/" + firmA.getId() + "/projects/"
                + project.getProjectCode() + "/"), storageKey);
        assertTrue(storageKey.endsWith("/agreement.docx"), storageKey);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Firm admin sees everything
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a firm admin sees every document in the firm, case and project alike")
    void firmAdminSeesEveryDocument() throws Exception {
        uploadDocument(adminAToken, matter.getMatterNumber(), null, "petition.pdf",
                "application/pdf", DocumentTestFiles.pdf("case doc"));
        uploadDocument(adminAToken, null, project.getProjectCode(), "agreement.docx",
                DOCX, DocumentTestFiles.zip("word/document.xml"));

        MvcResult library = authGet(adminAToken, "/api/v1/firm/documents");
        assertAllowed(library, "browse the library");
        assertEquals(2, json(library).path("data").path("totalElements").asInt());

        MvcResult caseOnly = authGet(adminAToken,
                "/api/v1/firm/matters/" + matter.getMatterNumber() + "/documents");
        assertAllowed(caseOnly, "list case documents");
        assertEquals(1, json(caseOnly).path("data").path("totalElements").asInt());

        MvcResult projectOnly = authGet(adminAToken,
                "/api/v1/firm/projects/" + project.getProjectCode() + "/documents");
        assertAllowed(projectOnly, "list project documents");
        assertEquals(1, json(projectOnly).path("data").path("totalElements").asInt());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Assigned staff see only their work
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("staff see only documents on cases they are assigned to")
    void assignedStaffSeeOnlyTheirCases() throws Exception {
        uploadDocument(adminAToken, matter.getMatterNumber(), null, "petition.pdf",
                "application/pdf", DocumentTestFiles.pdf("case doc"));
        uploadDocument(adminAToken, null, project.getProjectCode(), "agreement.docx",
                DOCX, DocumentTestFiles.zip("word/document.xml"));

        User advocate = advocateAssignedTo(matter);
        String advocateToken = freshToken(advocate);

        MvcResult library = authGet(advocateToken, "/api/v1/firm/documents");
        assertAllowed(library, "assigned staff library");
        assertEquals(1, json(library).path("data").path("totalElements").asInt(),
                "only the assigned case's document should be listed");
        assertEquals(matter.getMatterNumber(),
                json(library).path("data").path("content").get(0).path("matterNumber").asText());

        assertAllowed(authGet(advocateToken,
                "/api/v1/firm/matters/" + matter.getMatterNumber() + "/documents"),
                "assigned case panel");

        // The project they are not a member of: unreadable contents and no upload.
        MvcResult projectDocs = authGet(advocateToken,
                "/api/v1/firm/projects/" + project.getProjectCode() + "/documents");
        assertAllowed(projectDocs, "project panel is readable");
        assertEquals(0, json(projectDocs).path("data").path("totalElements").asInt(),
                "an unassigned project shows no documents");

        assertDenied(uploadMultipart(advocateToken, null, project.getProjectCode(), null,
                "sneaky.pdf", "application/pdf", DocumentTestFiles.pdf("x")),
                "upload to an unassigned project");
    }

    @Test
    @DisplayName("staff with no assignment see an empty library, not the firm's")
    void unassignedStaffSeeNothing() throws Exception {
        uploadDocument(adminAToken, matter.getMatterNumber(), null, "petition.pdf",
                "application/pdf", DocumentTestFiles.pdf("case doc"));

        User loose = firmUser(firmA, "ADVOCATE", "advloose" + shortId());
        String looseToken = freshToken(loose);

        MvcResult library = authGet(looseToken, "/api/v1/firm/documents");
        assertAllowed(library, "unassigned staff library");
        assertEquals(0, json(library).path("data").path("totalElements").asInt(),
                "an employee with no assignments gets an empty page, not the firm's documents");
    }

    @Test
    @DisplayName("a project member sees the project's documents and may add to them")
    void projectMemberSeesProjectDocuments() throws Exception {
        uploadDocument(adminAToken, null, project.getProjectCode(), "agreement.docx",
                DOCX, DocumentTestFiles.zip("word/document.xml"));

        User advocate = firmUser(firmA, "ADVOCATE", "advmember" + shortId());
        projectMemberRepository.save(ProjectMember.builder()
                .projectId(project.getId())
                .userId(advocate.getId())
                .roleInProject(ProjectMemberRole.MEMBER)
                .build());
        String advocateToken = freshToken(advocate);

        MvcResult library = authGet(advocateToken, "/api/v1/firm/documents");
        assertAllowed(library, "project member library");
        assertEquals(1, json(library).path("data").path("totalElements").asInt());

        assertAllowed(uploadMultipart(advocateToken, null, project.getProjectCode(), null,
                "draft.pdf", "application/pdf", DocumentTestFiles.pdf("x")),
                "upload into a project the user belongs to");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Clients
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a client sees only documents shared with them, on their own case")
    void clientSeesOnlySharedDocumentsOnTheirCase() throws Exception {
        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", DocumentTestFiles.pdf("case doc"));
        long documentId = document.path("id").asLong();

        matter = linkMatterToClient(matter, clientA);
        String clientToken = freshToken(clientA);

        MvcResult beforeShare = authGet(clientToken, "/api/v1/client/documents");
        assertAllowed(beforeShare, "client library before sharing");
        assertEquals(0, json(beforeShare).path("data").path("totalElements").asInt(),
                "an unshared document must not reach the client");

        assertDenied(authGet(clientToken, "/api/v1/client/documents/" + documentId + "/download-url"),
                "download of an unshared document");

        assertAllowed(authPatch(adminAToken, "/api/v1/firm/documents/" + documentId + "/visibility",
                apiRequest(Map.of("visibility", "SHARED"))), "share a document");

        MvcResult afterShare = authGet(clientToken, "/api/v1/client/documents");
        assertAllowed(afterShare, "client library after sharing");
        assertEquals(1, json(afterShare).path("data").path("totalElements").asInt());

        assertAllowed(authGet(clientToken, "/api/v1/client/documents/" + documentId + "/download-url"),
                "client download of a shared document");
    }

    @Test
    @DisplayName("a client can never upload, share or archive")
    void clientIsReadOnly() throws Exception {
        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", DocumentTestFiles.pdf("case doc"));
        long documentId = document.path("id").asLong();
        matter = linkMatterToClient(matter, clientA);
        String clientToken = freshToken(clientA);

        assertDenied(uploadMultipart(clientToken, matter.getMatterNumber(), null, null,
                "mine.pdf", "application/pdf", DocumentTestFiles.pdf("x")), "client upload");

        assertDenied(authPatch(clientToken, "/api/v1/firm/documents/" + documentId + "/visibility",
                apiRequest(Map.of("visibility", "SHARED"))), "client visibility change");

        assertDenied(authDelete(clientToken, "/api/v1/firm/documents/" + documentId),
                "client archive");
    }

    @Test
    @DisplayName("a client cannot reach another client's case, even when the document is shared")
    void clientCannotReadAnotherClientsCase() throws Exception {
        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", DocumentTestFiles.pdf("case doc"));
        long documentId = document.path("id").asLong();
        assertAllowed(authPatch(adminAToken, "/api/v1/firm/documents/" + documentId + "/visibility",
                apiRequest(Map.of("visibility", "SHARED"))), "share a document");

        matter = linkMatterToClient(matter, clientA);

        // A different client, on their own separate case.
        User otherClient = clientUser(firmA, "docclientb" + shortId());
        grantRolePermissions(saToken, firmA, firmRole(firmA, "CLIENT"),
                permIds("DOCUMENT_MANAGEMENT:ACCESS", "DOCUMENT_MANAGEMENT:VIEW"));
        matterRepository.save(newMatter("MT-DOC-0002", otherClient));
        String otherToken = freshToken(otherClient);

        assertDenied(authGet(otherToken, "/api/v1/client/documents/" + documentId + "/download-url"),
                "another client's download");

        assertDenied(authGet(otherToken,
                "/api/v1/client/documents?matterNumber=" + matter.getMatterNumber()),
                "another client's case listing");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Cross-firm isolation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("another firm's admin can neither see nor fetch a document by id")
    void otherFirmCannotReachTheDocument() throws Exception {
        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", DocumentTestFiles.pdf("case doc"));
        long documentId = document.path("id").asLong();

        String codeB = "X" + shortId().toUpperCase();
        String adminBUsername = "docadminb" + shortId();
        createFirm(saToken, codeB, adminBUsername);
        Firm firmB = firmByCode(codeB);
        String adminBToken = grantEverythingAndToken(saToken, firmB, adminBUsername);

        MvcResult library = authGet(adminBToken, "/api/v1/firm/documents");
        assertAllowed(library, "firm B library");
        assertEquals(0, json(library).path("data").path("totalElements").asInt(),
                "firm B must not see firm A's documents");

        // Not found rather than forbidden: as far as firm B is concerned the row does not exist.
        assertEquals(404, status(authGet(adminBToken,
                "/api/v1/firm/documents/" + documentId + "/download-url")));
        assertEquals(404, status(authDelete(adminBToken, "/api/v1/firm/documents/" + documentId)));
        assertEquals(404, status(authGet(adminBToken,
                "/api/v1/firm/matters/" + matter.getMatterNumber() + "/documents")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Upload integrity
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("an executable renamed to .pdf is rejected and nothing is stored")
    void disguisedExecutableIsRejected() throws Exception {
        byte[] exe = DocumentTestFiles.windowsExecutable();
        int storedBefore = storageService.objects.size();

        assertRejected(uploadMultipart(adminAToken, matter.getMatterNumber(), null, null,
                "petition.pdf", "application/pdf", exe), "uploading a disguised executable");

        assertEquals(storedBefore, storageService.objects.size(),
                "the rejected object must be removed from storage");
    }

    @Test
    @DisplayName("a file bigger than the policy is refused and nothing is stored")
    void oversizedFileIsRefused() throws Exception {
        // Lower the policy to 1 KB, then try to store 2 KB.
        assertAllowed(authPut(saToken, "/api/v1/super-admin/config",
                Map.of("STORAGE_MAX_FILE_SIZE_BYTES", "1024")), "lower the storage policy");

        byte[] tooBig = DocumentTestFiles.pdf("x".repeat(2000));
        int storedBefore = storageService.objects.size();
        assertRejected(uploadMultipart(adminAToken, matter.getMatterNumber(), null, null,
                "huge.pdf", "application/pdf", tooBig), "upload above the size policy");
        assertEquals(storedBefore, storageService.objects.size(),
                "a refused upload must not store anything");
    }

    @Test
    @DisplayName("an unsupported file type is refused and nothing is stored")
    void unlistedFileTypeIsRefused() throws Exception {
        int storedBefore = storageService.objects.size();
        assertRejected(uploadMultipart(adminAToken, matter.getMatterNumber(), null, null,
                "script.sh", "application/x-sh", "#!/bin/sh".getBytes()),
                "unsupported file type");
        assertEquals(storedBefore, storageService.objects.size(),
                "a refused upload must not store anything");
    }

    @Test
    @DisplayName("a filename without an allowed extension is refused")
    void extensionlessFilenameIsRefused() throws Exception {
        assertRejected(uploadMultipart(adminAToken, matter.getMatterNumber(), null, null,
                "noextension", "application/pdf", DocumentTestFiles.pdf("x")),
                "filename without an allowed extension");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. Per-firm storage allocation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the platform allocates space per firm and uploads stop once it is full")
    void perFirmAllocationIsEnforced() throws Exception {
        // 1 KB for this firm.
        assertAllowed(authPut(saToken,
                "/api/v1/super-admin/firms/" + firmA.getId() + "/storage-quota",
                apiRequest(Map.of("quotaBytes", 1024))), "allocate storage to a firm");

        MvcResult usageBefore = authGet(adminAToken, "/api/v1/firm/documents/storage-usage");
        assertAllowed(usageBefore, "read own storage usage");
        assertEquals(1024, json(usageBefore).path("data").path("quotaBytes").asLong());
        assertFalse(json(usageBefore).path("data").path("unlimited").asBoolean());

        uploadDocument(adminAToken, matter.getMatterNumber(), null, "small.pdf",
                "application/pdf", DocumentTestFiles.pdf("small"));

        long used = usageBytesInDb();
        assertTrue(used > 0, "usage should count the stored bytes");
        assertTrue(used <= 1024, "usage must stay within the allocation");

        // A file that does not fit is refused.
        assertRejected(uploadMultipart(adminAToken, matter.getMatterNumber(), null, null,
                "big.pdf", "application/pdf", DocumentTestFiles.pdf("x".repeat(2000))),
                "upload beyond the allocation");
        assertFalse(storageService.objects.isEmpty(), "the earlier upload should still be there");
    }

    @Test
    @DisplayName("only a platform admin can change a firm's allocation")
    void onlySuperAdminCanAllocate() throws Exception {
        assertDenied(authPut(adminAToken,
                "/api/v1/super-admin/firms/" + firmA.getId() + "/storage-quota",
                apiRequest(Map.of("quotaBytes", 10L * 1024 * 1024 * 1024))),
                "firm admin raising its own quota");
    }

    @Test
    @DisplayName("archiving frees the space and hides the document but keeps the file")
    void archivingReleasesSpaceAndKeepsTheObject() throws Exception {
        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", DocumentTestFiles.pdf("case doc"));
        long documentId = document.path("id").asLong();
        String storageKey = documentRepository.findById(documentId).orElseThrow().getStorageKey();
        assertTrue(usageBytesInDb() > 0);

        assertAllowed(authDelete(adminAToken, "/api/v1/firm/documents/" + documentId),
                "archive a document");

        assertEquals(0L, usageBytesInDb(), "archiving should give the space back");
        assertEquals(DocumentStatus.ARCHIVED,
                documentRepository.findById(documentId).orElseThrow().getStatus());
        assertTrue(storageService.has(storageKey),
                "the stored file is kept for legal retention, not destroyed");

        assertRejected(authGet(adminAToken,
                "/api/v1/firm/documents/" + documentId + "/download-url"),
                "download of an archived document");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. Permission gating
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a view-only role can read but not upload")
    void viewOnlyRoleCannotUpload() throws Exception {
        User paralegal = firmUser(firmA, "PARALEGAL", "docpara" + shortId());
        // ACCESS + VIEW only, which is the READ_ONLY shape of the seeded matrix.
        assignRolePermissions(adminAToken, paralegal.getRole(),
                permIds("DOCUMENT_MANAGEMENT:ACCESS", "DOCUMENT_MANAGEMENT:VIEW"));
        String paralegalToken = freshToken(paralegal);

        assertAllowed(authGet(paralegalToken, "/api/v1/firm/documents"), "read without UPLOAD");

        assertDenied(uploadMultipart(paralegalToken, matter.getMatterNumber(), null, null,
                "sneaky.pdf", "application/pdf", DocumentTestFiles.pdf("x")),
                "upload without DOCUMENT_MANAGEMENT:UPLOAD");
    }

    @Test
    @DisplayName("a role with no document permission cannot list at all")
    void noDocumentPermissionCannotList() throws Exception {
        User stranger = firmUser(firmA, "PARALEGAL", "docnone" + shortId());
        assignRolePermissions(adminAToken, stranger.getRole(),
                permIds("CASE_MANAGEMENT:ACCESS", "CASE_MANAGEMENT:VIEW"));
        String strangerToken = freshToken(stranger);

        assertDenied(authGet(strangerToken, "/api/v1/firm/documents"),
                "list without any document permission");
        assertDenied(authGet(strangerToken, "/api/v1/firm/documents/storage-usage"),
                "storage usage without any document permission");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. Audit trail
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload, download, share and archive are audited against the document uuid")
    void documentActionsAreAudited() throws Exception {
        JsonNode document = uploadDocument(adminAToken, matter.getMatterNumber(), null,
                "petition.pdf", "application/pdf", DocumentTestFiles.pdf("case doc"));
        long documentId = document.path("id").asLong();
        UUID documentUuid = UUID.fromString(document.path("uuid").asText());

        assertAllowed(authGet(adminAToken, "/api/v1/firm/documents/" + documentId + "/download-url"),
                "download a document");
        assertAllowed(authPatch(adminAToken, "/api/v1/firm/documents/" + documentId + "/visibility",
                apiRequest(Map.of("visibility", "SHARED"))), "share a document");
        assertAllowed(authDelete(adminAToken, "/api/v1/firm/documents/" + documentId),
                "archive a document");

        for (AuditAction action : List.of(AuditAction.DOCUMENT_UPLOADED, AuditAction.DOCUMENT_DOWNLOADED,
                AuditAction.DOCUMENT_SHARED, AuditAction.DOCUMENT_DELETED)) {
            List<AuditLog> entries = auditLogRepository.findAll().stream()
                    .filter(log -> log.getAction() == action && documentUuid.equals(log.getEntityId()))
                    .toList();

            assertFalse(entries.isEmpty(),
                    "no audit entry for " + action + " linked to the document uuid");
            AuditLog entry = entries.get(0);
            assertEquals(firmA.getId(), entry.getFirmId(), action + " should carry the firm");
            assertNotNull(entry.getSummary(), action + " should carry a summary");
            assertFalse(entry.getSummary().contains("http"),
                    "a presigned URL must never reach the audit log");
            assertFalse(entry.getSummary().isBlank(), action + " summary should not be blank");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** One multipart POST to the API: the bytes go through the server, so it finishes at once. */
    private JsonNode uploadDocument(String token, String matterNumber, String projectCode,
                                    String filename, String contentType, byte[] content) throws Exception {
        MvcResult result = uploadMultipart(token, matterNumber, projectCode, null,
                filename, contentType, content);
        assertAllowed(result, "upload " + filename);
        return json(result).path("data");
    }

    private MvcResult uploadMultipart(String token, String matterNumber, String projectCode,
                                      String courtCaseRef, String filename, String contentType,
                                      byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        MockMultipartHttpServletRequestBuilder request =
                multipart("/api/v1/firm/documents").file(file);
        if (matterNumber != null) {
            request.param("matterNumber", matterNumber);
        }
        if (projectCode != null) {
            request.param("projectCode", projectCode);
        }
        if (courtCaseRef != null) {
            request.param("courtCaseRef", courtCaseRef);
        }
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
    }

    private User advocateAssignedTo(Matter target) {
        User advocate = firmUser(firmA, "ADVOCATE", "adv" + shortId());
        CaseAssignment assignment = new CaseAssignment();
        assignment.setFirmId(firmA.getId());
        assignment.setMatterId(target.getId());
        assignment.setUserId(advocate.getId());
        assignment.setAssignmentRole(AssignmentRole.PRIMARY_ADVOCATE);
        caseAssignmentRepository.save(assignment);
        return advocate;
    }

    private Matter newMatter(String matterNumber, User owner) {
        Matter created = new Matter();
        created.setFirmId(firmA.getId());
        created.setMatterNumber(matterNumber);
        created.setMatterType(MatterType.CIVIL);
        created.setTitle("Document test matter " + matterNumber);
        created.setStatus(MatterStatus.ACTIVE);
        created.setOriginatingCourtLevel(CourtLevel.DISTRICT);
        if (owner != null) {
            created.setClientUserId(owner.getId());
            created.setClientName(owner.getFullName());
        }
        return created;
    }

    private Matter linkMatterToClient(Matter target, User client) {
        Matter current = matterRepository.findById(target.getId()).orElseThrow();
        current.setClientUserId(client.getId());
        current.setClientName(client.getFullName());
        return matterRepository.save(current);
    }

    private Project newProject(String projectCode) {
        return Project.builder()
                .firmId(firmA.getId())
                .projectCode(projectCode)
                .name("Document test project")
                .clientName("Test Client")
                .status(ProjectStatus.ACTIVE)
                .ownerId(firmAdmin(firmA, adminAUsername).getId())
                .build();
    }

    private long usageBytesInDb() {
        return storageUsageRepository.findByFirmId(firmA.getId())
                .map(FirmStorageUsage::getUsedBytes)
                .orElse(0L);
    }

    private long matterTimelineCount(UUID matterId) {
        return entityManager
                .createQuery("SELECT COUNT(e) FROM MatterTimelineEvent e WHERE e.matterId = :matterId",
                        Long.class)
                .setParameter("matterId", matterId)
                .getSingleResult();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
