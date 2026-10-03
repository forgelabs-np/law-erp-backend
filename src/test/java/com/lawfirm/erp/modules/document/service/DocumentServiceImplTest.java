package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.exception.StorageOperationException;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.common.storage.StorageQuotaService;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseAssignmentRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterTimelineRepository;
import com.lawfirm.erp.modules.casemanagement.service.MatterScopeGuard;
import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.dto.response.DownloadUrlResponse;
import com.lawfirm.erp.modules.document.entity.Document;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import com.lawfirm.erp.modules.document.repository.DocumentRepository;
import com.lawfirm.erp.modules.document.support.DocumentTestFiles;
import com.lawfirm.erp.modules.document.support.FakeStorageService;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectMemberRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Document flows with every collaborator mocked.
 *
 * <p>{@link DocumentScopeGuard} is built for real, so the authorization rules under test are
 * the production ones rather than a re-implementation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceImplTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private CourtCaseRepository courtCaseRepository;
    @Mock private MatterTimelineRepository matterTimelineRepository;
    @Mock private StorageQuotaService quotaService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private ReadScopeGuard readScopeGuard;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private MatterScopeGuard matterScopeGuard;
    @Mock private CaseAssignmentRepository caseAssignmentRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private FakeStorageService storageService;
    private DocumentServiceImpl service;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID MATTER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COURT_CASE_ID = UUID.randomUUID();
    private static final String MATTER_NUMBER = "MT-2026-00041";
    private static final String PROJECT_CODE = "ABC-PRJ-2026-00007";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @BeforeEach
    void setUp() {
        storageService = new FakeStorageService();

        // Storage policy comes from the DB (STORAGE config group); these stub the resolved values.
        when(systemConfigService.storageMaxFileSizeBytes()).thenReturn(50L * 1024 * 1024);
        when(systemConfigService.storageDownloadExpirySeconds()).thenReturn(900);
        when(systemConfigService.documentMaxFilenameLength()).thenReturn(120);

        DocumentScopeGuard scopeGuard = new DocumentScopeGuard(
                readScopeGuard, matterScopeGuard, caseAssignmentRepository,
                projectMemberRepository, projectRepository);

        service = new DocumentServiceImpl(
                documentRepository, matterRepository, projectRepository, courtCaseRepository,
                matterTimelineRepository, storageService, quotaService, systemConfigService,
                scopeGuard, new DocumentMapper(), readScopeGuard, matterScopeGuard,
                currentUserResolver);

        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
        when(readScopeGuard.currentUserId()).thenReturn(USER_ID);
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(quotaService.canFit(any(), any(Long.class))).thenReturn(true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // upload — authorization
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("uploading — authorization")
    class UploadAuthorization {

        @Test
        @DisplayName("a client account can never upload")
        void clientCannotUpload() {
            givenClient();
            when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(matter()));

            assertThrows(ForbiddenException.class,
                    () -> uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x")));
        }

        @Test
        @DisplayName("staff not assigned to the case are refused")
        void unassignedStaffCannotUploadToACase() {
            givenAssignmentScopedStaff();
            when(caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                    .thenReturn(false);

            assertThrows(ForbiddenException.class,
                    () -> uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x")));
        }

        @Test
        @DisplayName("staff not on the project are refused")
        void unassignedStaffCannotUploadToAProject() {
            givenAssignmentScopedStaff();
            when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).thenReturn(false);

            assertThrows(ForbiddenException.class,
                    () -> uploadProject("spec.docx", DOCX, DocumentTestFiles.zip("word/document.xml")));
        }

        @Test
        @DisplayName("assigned staff may upload to their case")
        void assignedStaffCanUploadToTheirCase() {
            givenAssignmentScopedStaff();
            when(caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                    .thenReturn(true);

            DocumentResponse response = uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x"));

            assertNotNull(response);
            assertEquals("petition.pdf", response.getFileName());
        }

        @Test
        @DisplayName("a firm admin is never narrowed by assignment")
        void firmAdminIsNotAssignmentScoped() {
            givenPlainFirmAdmin();

            assertNotNull(uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x")));
            verify(caseAssignmentRepository, never())
                    .existsByMatterIdAndUserIdAndFirmId(any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // upload — validation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("upload validation")
    class UploadValidation {

        @Test
        @DisplayName("exactly one owner must be supplied")
        void requiresExactlyOneOwner() {
            givenPlainFirmAdmin();
            byte[] pdf = DocumentTestFiles.pdf("x");

            assertThrows(BusinessRuleException.class, () -> service.upload(
                    MATTER_NUMBER, PROJECT_CODE, null, "a.pdf", "application/pdf", pdf.length,
                    new ByteArrayInputStream(pdf)));
            assertThrows(BusinessRuleException.class, () -> service.upload(
                    null, null, null, "a.pdf", "application/pdf", pdf.length,
                    new ByteArrayInputStream(pdf)));
        }

        @Test
        void rejectsAnUnsupportedContentType() {
            givenPlainFirmAdmin();
            byte[] bytes = "hello".getBytes();

            assertThrows(BusinessRuleException.class, () -> service.upload(
                    MATTER_NUMBER, null, null, "payload.bin", "application/x-msdownload",
                    bytes.length, new ByteArrayInputStream(bytes)));
        }

        @Test
        @DisplayName("an executable filename is refused before anything is stored")
        void rejectsAnExecutableFilename() {
            givenPlainFirmAdmin();

            assertThrows(BusinessRuleException.class,
                    () -> uploadCase("invoice.exe", "application/pdf", DocumentTestFiles.pdf("x")));
            verify(documentRepository, never()).save(any(Document.class));
        }

        @Test
        void rejectsAFileLargerThanTheLimit() {
            givenPlainFirmAdmin();
            byte[] small = DocumentTestFiles.pdf("x");

            assertThrows(BusinessRuleException.class, () -> service.upload(
                    MATTER_NUMBER, null, null, "big.pdf", "application/pdf", 51L * 1024 * 1024,
                    new ByteArrayInputStream(small)));
        }

        @Test
        @DisplayName("an over-quota upload is refused before anything is stored")
        void refusesWhenTheFirmHasNoSpaceLeft() {
            givenPlainFirmAdmin();
            when(quotaService.canFit(FIRM_ID, 10L)).thenReturn(false);
            byte[] ten = "0123456789".getBytes();

            assertThrows(BusinessRuleException.class, () -> service.upload(
                    MATTER_NUMBER, null, null, "a.pdf", "application/pdf", 10, new ByteArrayInputStream(ten)));
            verify(documentRepository, never()).save(any(Document.class));
        }

        @Test
        @DisplayName("a court case that belongs to another matter is rejected")
        void rejectsACourtCaseFromAnotherMatter() {
            givenPlainFirmAdmin();
            CourtCase foreign = new CourtCase();
            foreign.setId(COURT_CASE_ID);
            foreign.setMatterId(UUID.randomUUID());
            when(courtCaseRepository.findByOurCourtCaseRefAndFirmId("MT-1-D1", FIRM_ID))
                    .thenReturn(Optional.of(foreign));
            byte[] pdf = DocumentTestFiles.pdf("x");

            assertThrows(ResourceNotFoundException.class, () -> service.upload(
                    MATTER_NUMBER, null, "MT-1-D1", "a.pdf", "application/pdf", pdf.length,
                    new ByteArrayInputStream(pdf)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Storage key layout
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("where the object is stored")
    class StorageKeys {

        @Test
        @DisplayName("a case document lands inside the case folder, named by case number")
        void caseDocumentLandsInItsCaseFolder() {
            givenPlainFirmAdmin();
            uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x"));

            String key = onlyStoredKey();
            assertTrue(key.startsWith("firms/" + FIRM_ID + "/cases/" + MATTER_NUMBER + "/"), key);
            assertTrue(key.endsWith("/petition.pdf"), key);
        }

        @Test
        @DisplayName("a project document lands inside the project folder, named by project code")
        void projectDocumentLandsInItsProjectFolder() {
            givenPlainFirmAdmin();
            uploadProject("agreement.docx", DOCX, DocumentTestFiles.zip("word/document.xml"));

            String key = onlyStoredKey();
            assertTrue(key.startsWith("firms/" + FIRM_ID + "/projects/" + PROJECT_CODE + "/"), key);
            assertTrue(key.endsWith("/agreement.docx"), key);
        }

        @Test
        @DisplayName("the key has firm/cases/<matterNumber>/<generatedId>/<filename> shape")
        void keyHasTheExpectedShape() {
            givenPlainFirmAdmin();
            uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x"));

            String[] parts = onlyStoredKey().split("/");
            assertEquals("firms", parts[0]);
            assertEquals(FIRM_ID.toString(), parts[1]);
            assertEquals("cases", parts[2]);
            assertEquals(MATTER_NUMBER, parts[3]);
            UUID.fromString(parts[4]);
            assertEquals("petition.pdf", parts[5]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // upload — storage behaviour
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("storing the file")
    class UploadStorage {

        @Test
        @DisplayName("an executable wearing a PDF content type is discarded")
        void discardsWhenTheSignatureDoesNotMatch() {
            givenPlainFirmAdmin();
            byte[] exe = DocumentTestFiles.windowsExecutable();

            assertThrows(BusinessRuleException.class,
                    () -> uploadCase("petition.pdf", "application/pdf", exe));
            verify(quotaService, never()).reserve(any(), any(Long.class));
            assertTrue(storageService.objects.isEmpty(), "the rejected object must be removed");
            assertFalse(storageService.deletedKeys.isEmpty());
        }

        @Test
        @DisplayName("when the quota turns out to be full, the bytes are handed back")
        void discardsWhenQuotaReservationFails() {
            givenPlainFirmAdmin();
            byte[] pdf = DocumentTestFiles.pdf("content");
            doThrow(new BusinessRuleException("Storage allocation exceeded"))
                    .when(quotaService).reserve(FIRM_ID, pdf.length);

            assertThrows(BusinessRuleException.class,
                    () -> uploadCase("petition.pdf", "application/pdf", pdf));
            assertTrue(storageService.objects.isEmpty(), "the rejected object must not be left behind");
        }

        @Test
        @DisplayName("a storage failure leaves no document row behind")
        void storageFailureLeavesNoRow() {
            givenPlainFirmAdmin();
            storageService.failingWrites = true;

            assertThrows(StorageOperationException.class,
                    () -> uploadCase("petition.pdf", "application/pdf", DocumentTestFiles.pdf("x")));
            verify(documentRepository, never()).save(any(Document.class));
        }

        @Test
        @DisplayName("a clean upload activates, reserves storage and lands on the case timeline")
        void activatesAndRecordsTheTimelineEvent() {
            givenPlainFirmAdmin();
            byte[] pdf = DocumentTestFiles.pdf("content");

            DocumentResponse response = uploadCase("petition.pdf", "application/pdf", pdf);

            assertEquals(DocumentStatus.ACTIVE, response.getStatus());
            assertEquals(USER_ID, response.getUploadedByUserId());
            verify(quotaService).reserve(FIRM_ID, pdf.length);

            ArgumentCaptor<MatterTimelineEvent> event = ArgumentCaptor.forClass(MatterTimelineEvent.class);
            verify(matterTimelineRepository).save(event.capture());
            assertEquals(TimelineEventType.DOCUMENT_UPLOADED, event.getValue().getEventType());
            assertEquals("Document uploaded: petition.pdf", event.getValue().getTitle());
            assertEquals(MATTER_ID, event.getValue().getMatterId());
        }

        @Test
        @DisplayName("projects have no timeline, so a project document records no event")
        void doesNotRecordATimelineEventForProjects() {
            givenPlainFirmAdmin();

            DocumentResponse response = uploadProject(
                    "agreement.docx", DOCX, DocumentTestFiles.zip("word/document.xml"));

            assertEquals(DocumentStatus.ACTIVE, response.getStatus());
            verify(matterTimelineRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // download
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("downloading")
    class Download {

        @Test
        @DisplayName("a client cannot download a document that was never shared")
        void clientCannotDownloadAPrivateDocument() {
            givenClient();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            assertThrows(ForbiddenException.class, () -> service.downloadUrl(1L));
        }

        @Test
        @DisplayName("a client can download a shared document on their own case")
        void clientCanDownloadASharedDocumentOnTheirCase() {
            givenClient();
            Document document = activeDocument(DocumentVisibility.SHARED);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            DownloadUrlResponse response = service.downloadUrl(1L);

            assertNotNull(response.downloadUrl());
            assertEquals(document.getUuid(), response.documentUuid());
        }

        @Test
        @DisplayName("a shared document on someone else's case is still refused")
        void clientCannotDownloadAnotherClientsSharedDocument() {
            givenClient();
            Document document = activeDocument(DocumentVisibility.SHARED);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            doThrow(new ForbiddenException("You do not have access to this document"))
                    .when(matterScopeGuard).requireVisible(eq(MATTER_ID), eq(FIRM_ID), anyString());

            assertThrows(ForbiddenException.class, () -> service.downloadUrl(1L));
        }

        @Test
        @DisplayName("staff not assigned to the case cannot download from it")
        void unassignedStaffCannotDownload() {
            givenAssignmentScopedStaff();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            document.setMatterId(MATTER_ID);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            when(caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                    .thenReturn(false);

            assertThrows(ForbiddenException.class, () -> service.downloadUrl(1L));
        }

        @Test
        void archivedDocumentsAreNotDownloadable() {
            givenPlainFirmAdmin();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            document.setStatus(DocumentStatus.ARCHIVED);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            assertThrows(BusinessRuleException.class, () -> service.downloadUrl(1L));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // archive
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("archiving")
    class Archive {

        @Test
        @DisplayName("archiving frees the space but keeps the file for legal retention")
        void releasesStorageAndKeepsTheObject() {
            givenPlainFirmAdmin();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            document.setSizeBytes(4096L);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            storageService.seed(document.getStorageKey(), new byte[4096]);

            DocumentResponse response = service.archive(1L);

            assertEquals(DocumentStatus.ARCHIVED, response.getStatus());
            assertNotNull(response.getArchivedAt());
            verify(quotaService).release(FIRM_ID, 4096L);
            assertTrue(storageService.has(document.getStorageKey()),
                    "the stored file must survive archiving");
            assertTrue(storageService.deletedKeys.isEmpty(),
                    "archiving must never delete the stored object");
        }

        @Test
        @DisplayName("archiving twice does not release the space twice")
        void archivingIsIdempotent() {
            givenPlainFirmAdmin();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            document.setStatus(DocumentStatus.ARCHIVED);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            service.archive(1L);

            verify(quotaService, never()).release(any(), any(Long.class));
        }

        @Test
        void unassignedStaffCannotArchive() {
            givenAssignmentScopedStaff();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            when(caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                    .thenReturn(false);

            assertThrows(ForbiddenException.class, () -> service.archive(1L));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sharing
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("changing visibility")
    class Visibility {

        @Test
        @DisplayName("uploads start private and can be promoted to shared")
        void promotesToShared() {
            givenPlainFirmAdmin();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            DocumentResponse response = service.updateVisibility(1L, DocumentVisibility.SHARED);

            assertEquals(DocumentVisibility.SHARED, response.getVisibility());
        }

        @Test
        void cannotShareAnArchivedDocument() {
            givenPlainFirmAdmin();
            Document document = activeDocument(DocumentVisibility.PRIVATE);
            document.setStatus(DocumentStatus.ARCHIVED);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            assertThrows(BusinessRuleException.class,
                    () -> service.updateVisibility(1L, DocumentVisibility.SHARED));
        }

        @Test
        void clientsCannotChangeVisibility() {
            when(readScopeGuard.isClientScope()).thenReturn(true);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID))
                    .thenReturn(Optional.of(activeDocument(DocumentVisibility.SHARED)));

            assertThrows(ForbiddenException.class,
                    () -> service.updateVisibility(1L, DocumentVisibility.PRIVATE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // listing — the embedded download link
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("listing documents")
    class ListDocuments {

        @Test
        @DisplayName("an ACTIVE document carries a presigned documentUrl")
        void activeDocumentCarriesADownloadUrl() {
            givenPlainFirmAdmin();
            Document active = activeDocument(DocumentVisibility.PRIVATE);
            givenListReturns(active);

            PagedResponse<DocumentResponse> page = service.listLibrary(null, null, null, 0, 20);

            assertEquals(1, page.getContent().size());
            String url = page.getContent().get(0).getDocumentUrl();
            assertNotNull(url, "an active document must expose a download link in the list");
            assertTrue(url.contains(active.getStorageKey()), url);
        }

        @Test
        @DisplayName("an ARCHIVED document exposes no link — archiving is not downloadable")
        void archivedDocumentHasNoDownloadUrl() {
            givenPlainFirmAdmin();
            Document archived = activeDocument(DocumentVisibility.PRIVATE);
            archived.setStatus(DocumentStatus.ARCHIVED);
            givenListReturns(archived);

            assertNull(service.listLibrary(null, null, null, 0, 20).getContent().get(0).getDocumentUrl());
        }

        /** A firm admin's list runs findForFirm; matter numbers are resolved in one batch. */
        private void givenListReturns(Document document) {
            when(documentRepository.findForFirm(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(java.util.List.of(document)));
            when(matterRepository.findAllById(any())).thenReturn(java.util.List.of(matter()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Super Admin is kept out — documents are firm records
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("a Super Admin cannot touch firm documents")
    class SuperAdminAccess {

        @Test
        @DisplayName("the document library is refused")
        void cannotListTheLibrary() {
            when(currentUserResolver.isSuperAdmin()).thenReturn(true);

            assertThrows(ForbiddenException.class,
                    () -> service.listLibrary(null, null, null, 0, 20));
            verify(documentRepository, never())
                    .findForFirm(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void cannotUpload() {
            when(currentUserResolver.isSuperAdmin()).thenReturn(true);

            assertThrows(ForbiddenException.class,
                    () -> uploadCase("a.pdf", "application/pdf", DocumentTestFiles.pdf("x")));
        }

        @Test
        void cannotDownload() {
            when(currentUserResolver.isSuperAdmin()).thenReturn(true);

            assertThrows(ForbiddenException.class, () -> service.downloadUrl(1L));
        }
    }

    @Test
    @DisplayName("a document belonging to another firm is never returned")
    void crossFirmDocumentsAreNotVisible() {
        // The lookup is scoped by the caller's own firm, so another firm's row simply is not found.
        when(documentRepository.findByIdAndFirmId(5L, FIRM_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.archive(5L));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Fixtures
    // ═══════════════════════════════════════════════════════════════════════

    private DocumentResponse uploadCase(String filename, String contentType, byte[] content) {
        return service.upload(MATTER_NUMBER, null, null, filename, contentType, content.length,
                new ByteArrayInputStream(content));
    }

    private DocumentResponse uploadProject(String filename, String contentType, byte[] content) {
        return service.upload(null, PROJECT_CODE, null, filename, contentType, content.length,
                new ByteArrayInputStream(content));
    }

    private String onlyStoredKey() {
        assertEquals(1, storageService.objects.size(), "expected exactly one stored object");
        return storageService.objects.keySet().iterator().next();
    }

    private void givenPlainFirmAdmin() {
        when(readScopeGuard.isClientScope()).thenReturn(false);
        when(readScopeGuard.isAssignmentScope()).thenReturn(false);
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter()));
        when(projectRepository.findByProjectCodeAndFirmId(PROJECT_CODE, FIRM_ID))
                .thenReturn(Optional.of(project()));
    }

    private void givenAssignmentScopedStaff() {
        when(readScopeGuard.isClientScope()).thenReturn(false);
        when(readScopeGuard.isAssignmentScope()).thenReturn(true);
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter()));
        when(projectRepository.findByProjectCodeAndFirmId(PROJECT_CODE, FIRM_ID))
                .thenReturn(Optional.of(project()));
    }

    private void givenClient() {
        when(readScopeGuard.isClientScope()).thenReturn(true);
    }

    private Matter matter() {
        Matter matter = new Matter();
        matter.setId(MATTER_ID);
        matter.setFirmId(FIRM_ID);
        matter.setMatterNumber(MATTER_NUMBER);
        matter.setMatterType(MatterType.CIVIL);
        matter.setTitle("Test matter");
        matter.setOriginatingCourtLevel(CourtLevel.DISTRICT);
        return matter;
    }

    private Project project() {
        Project project = Project.builder()
                .firmId(FIRM_ID)
                .projectCode(PROJECT_CODE)
                .name("Test project")
                .clientName("Client")
                .status(ProjectStatus.ACTIVE)
                .ownerId(USER_ID)
                .build();
        project.setId(PROJECT_ID);
        return project;
    }

    private Document activeDocument(DocumentVisibility visibility) {
        return Document.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .firmId(FIRM_ID)
                .matterId(MATTER_ID)
                .originalFilename("petition.pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .sizeBytes(100)
                .storageKey("k1")
                .status(DocumentStatus.ACTIVE)
                .visibility(visibility)
                .uploadedByUserId(USER_ID)
                .build();
    }
}
