package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.storage.StorageProperties;
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
import com.lawfirm.erp.modules.document.dto.request.ConfirmUploadRequest;
import com.lawfirm.erp.modules.document.dto.request.InitiateUploadRequest;
import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.dto.response.DownloadUrlResponse;
import com.lawfirm.erp.modules.document.dto.response.UploadTicketResponse;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @BeforeEach
    void setUp() {
        storageService = new FakeStorageService();

        StorageProperties properties = new StorageProperties();
        properties.setMaxFileSizeBytes(50L * 1024 * 1024);
        properties.setUploadExpirySeconds(1800);
        properties.setDownloadExpirySeconds(900);

        DocumentScopeGuard scopeGuard = new DocumentScopeGuard(
                readScopeGuard, matterScopeGuard, caseAssignmentRepository,
                projectMemberRepository, projectRepository);

        service = new DocumentServiceImpl(
                documentRepository, matterRepository, projectRepository, courtCaseRepository,
                matterTimelineRepository, storageService, quotaService, properties,
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
    // initiateUpload — authorization
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("requesting an upload ticket")
    class InitiateUpload {

        @Test
        @DisplayName("a client account can never upload")
        void clientCannotUpload() {
            givenClient();
            when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(matter()));

            assertThrows(ForbiddenException.class,
                    () -> service.initiateUpload(caseUploadRequest("petition.pdf", "application/pdf", 1000)));
        }

        @Test
        @DisplayName("staff not assigned to the case are refused")
        void unassignedStaffCannotUploadToACase() {
            givenAssignmentScopedStaff();
            when(caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                    .thenReturn(false);

            assertThrows(ForbiddenException.class,
                    () -> service.initiateUpload(caseUploadRequest("petition.pdf", "application/pdf", 1000)));
        }

        @Test
        @DisplayName("staff not on the project are refused")
        void unassignedStaffCannotUploadToAProject() {
            givenAssignmentScopedStaff();
            when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).thenReturn(false);

            assertThrows(ForbiddenException.class,
                    () -> service.initiateUpload(projectUploadRequest("spec.docx", DOCX, 1000)));
        }

        @Test
        @DisplayName("assigned staff may upload to their case")
        void assignedStaffCanUploadToTheirCase() {
            givenAssignmentScopedStaff();
            when(caseAssignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                    .thenReturn(true);

            UploadTicketResponse ticket = service.initiateUpload(
                    caseUploadRequest("petition.pdf", "application/pdf", 1024));

            assertNotNull(ticket);
            assertEquals("petition.pdf", ticket.fileName());
        }

        @Test
        @DisplayName("a firm admin is never narrowed by assignment")
        void firmAdminIsNotAssignmentScoped() {
            givenPlainFirmAdmin();

            assertNotNull(service.initiateUpload(caseUploadRequest("petition.pdf", "application/pdf", 1024)));
            verify(caseAssignmentRepository, never())
                    .existsByMatterIdAndUserIdAndFirmId(any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // initiateUpload — validation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("upload validation")
    class UploadValidation {

        @Test
        @DisplayName("exactly one owner must be supplied")
        void requiresExactlyOneOwner() {
            InitiateUploadRequest both = caseUploadRequest("a.pdf", "application/pdf", 10);
            both.setProjectCode(PROJECT_CODE);
            assertThrows(BusinessRuleException.class, () -> service.initiateUpload(both));

            InitiateUploadRequest neither = caseUploadRequest("a.pdf", "application/pdf", 10);
            neither.setMatterNumber(null);
            assertThrows(BusinessRuleException.class, () -> service.initiateUpload(neither));
        }

        @Test
        void rejectsAnUnsupportedContentType() {
            givenPlainFirmAdmin();

            assertThrows(BusinessRuleException.class, () -> service.initiateUpload(
                    caseUploadRequest("payload.bin", "application/x-msdownload", 10)));
        }

        @Test
        @DisplayName("an executable filename is refused before anything is created")
        void rejectsAnExecutableFilename() {
            givenPlainFirmAdmin();

            assertThrows(BusinessRuleException.class, () -> service.initiateUpload(
                    caseUploadRequest("invoice.exe", "application/pdf", 10)));
        }

        @Test
        void rejectsAFileLargerThanTheLimit() {
            givenPlainFirmAdmin();

            assertThrows(BusinessRuleException.class, () -> service.initiateUpload(
                    caseUploadRequest("big.pdf", "application/pdf", 51L * 1024 * 1024)));
        }

        @Test
        @DisplayName("an over-quota upload is refused before the client sends any bytes")
        void refusesWhenTheFirmHasNoSpaceLeft() {
            givenPlainFirmAdmin();
            when(quotaService.canFit(FIRM_ID, 10L)).thenReturn(false);

            assertThrows(BusinessRuleException.class, () -> service.initiateUpload(
                    caseUploadRequest("a.pdf", "application/pdf", 10)));
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

            InitiateUploadRequest request = caseUploadRequest("a.pdf", "application/pdf", 10);
            request.setCourtCaseRef("MT-1-D1");

            assertThrows(ResourceNotFoundException.class, () -> service.initiateUpload(request));
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

            UploadTicketResponse ticket = service.initiateUpload(
                    caseUploadRequest("petition.pdf", "application/pdf", 2048));
            String key = ticket.fields().get("key");

            assertTrue(key.startsWith("firms/" + FIRM_ID + "/cases/" + MATTER_NUMBER + "/"), key);
            assertTrue(key.endsWith("/petition.pdf"), key);
        }

        @Test
        @DisplayName("a project document lands inside the project folder, named by project code")
        void projectDocumentLandsInItsProjectFolder() {
            givenPlainFirmAdmin();

            UploadTicketResponse ticket = service.initiateUpload(
                    projectUploadRequest("agreement.docx", DOCX, 2048));
            String key = ticket.fields().get("key");

            assertTrue(key.startsWith("firms/" + FIRM_ID + "/projects/" + PROJECT_CODE + "/"), key);
            assertTrue(key.endsWith("/agreement.docx"), key);
        }

        @Test
        @DisplayName("the ticket pins the exact key and content type")
        void ticketPinsKeyAndContentType() {
            givenPlainFirmAdmin();

            UploadTicketResponse ticket = service.initiateUpload(
                    caseUploadRequest("petition.pdf", "application/pdf", 2048));

            // The policy conditions are echoed back so the client sends exactly what was signed.
            String[] parts = ticket.fields().get("key").split("/");
            assertEquals("firms", parts[0]);
            assertEquals(FIRM_ID.toString(), parts[1]);
            assertEquals("cases", parts[2]);
            assertEquals(MATTER_NUMBER, parts[3]);
            assertDoesNotThrow(() -> UUID.fromString(parts[4]), "the folder should be a generated id");
            assertEquals("petition.pdf", parts[5]);
            assertEquals("application/pdf", ticket.fields().get("Content-Type"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // confirmUpload
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confirming an upload")
    class ConfirmUpload {

        @Test
        @DisplayName("an object that never arrived cannot be activated")
        void rejectsWhenTheObjectIsMissing() {
            givenPlainFirmAdmin();
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(pendingCaseDocument("k1")));

            assertThrows(BusinessRuleException.class, () -> service.confirmUpload(1L, null));
        }

        @Test
        @DisplayName("a file whose real size differs from the declared size is discarded")
        void discardsWhenTheSizeDoesNotMatch() {
            givenPlainFirmAdmin();
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(pendingCaseDocument("k1")));
            storageService.put("k1", DocumentTestFiles.pdf("bigger than declared"));

            assertThrows(BusinessRuleException.class, () -> service.confirmUpload(1L, null));
            assertEquals(1, storageService.deletedKeys.size());
            assertTrue(!storageService.has("k1"));
        }

        @Test
        @DisplayName("an executable wearing a PDF content type is discarded")
        void discardsWhenTheSignatureDoesNotMatch() {
            givenPlainFirmAdmin();
            byte[] exe = DocumentTestFiles.windowsExecutable();
            Document document = pendingCaseDocument("k1");
            document.setSizeBytes(exe.length);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            storageService.put("k1", exe);

            assertThrows(BusinessRuleException.class, () -> service.confirmUpload(1L, null));
            verify(quotaService, never()).reserve(any(), any(Long.class));
            assertFalse(storageService.has("k1"), "the rejected object must be removed");
            assertTrue(storageService.deletedKeys.contains("k1"));
        }

        @Test
        @DisplayName("when the quota turns out to be full, the bytes are handed back")
        void discardsWhenQuotaReservationFails() {
            givenPlainFirmAdmin();
            byte[] pdf = DocumentTestFiles.pdf("content");
            Document document = pendingCaseDocument("k1");
            document.setSizeBytes(pdf.length);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            storageService.put("k1", pdf);
            doThrow(new BusinessRuleException("Storage allocation exceeded"))
                    .when(quotaService).reserve(FIRM_ID, pdf.length);

            assertThrows(BusinessRuleException.class, () -> service.confirmUpload(1L, null));
            assertTrue(!storageService.has("k1"), "the rejected object must not be left behind");
        }

        @Test
        @DisplayName("a clean upload activates, reserves storage and lands on the case timeline")
        void activatesAndRecordsTheTimelineEvent() {
            givenPlainFirmAdmin();
            byte[] pdf = DocumentTestFiles.pdf("content");
            Document document = pendingCaseDocument("k1");
            document.setSizeBytes(pdf.length);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));
            storageService.put("k1", pdf);

            DocumentResponse response = service.confirmUpload(1L, null);

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
            byte[] docx = DocumentTestFiles.zip("word/document.xml");
            Document document = pendingProjectDocument("k2");
            document.setSizeBytes(docx.length);
            when(documentRepository.findByIdAndFirmId(2L, FIRM_ID)).thenReturn(Optional.of(document));
            storageService.put("k2", docx);

            DocumentResponse response = service.confirmUpload(2L, null);

            assertEquals(DocumentStatus.ACTIVE, response.getStatus());
            verify(matterTimelineRepository, never()).save(any());
        }

        @Test
        void rejectsAnExpiredUploadWindow() {
            givenPlainFirmAdmin();
            Document document = pendingCaseDocument("k1");
            document.setUploadExpiresAt(LocalDateTime.now().minusMinutes(1));
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            assertThrows(BusinessRuleException.class, () -> service.confirmUpload(1L, null));
        }

        @Test
        void rejectsADocumentThatIsAlreadyActive() {
            givenPlainFirmAdmin();
            Document document = pendingCaseDocument("k1");
            document.setStatus(DocumentStatus.ACTIVE);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(document));

            assertThrows(BusinessRuleException.class, () -> service.confirmUpload(1L, null));
        }

        @Test
        @DisplayName("another firm's document is indistinguishable from a missing one")
        void rejectsADocumentFromAnotherFirm() {
            givenPlainFirmAdmin();
            when(documentRepository.findByIdAndFirmId(99L, FIRM_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.confirmUpload(99L, null));
        }

        @Test
        @DisplayName("a client can never confirm an upload")
        void rejectsClients() {
            when(readScopeGuard.isClientScope()).thenReturn(true);
            when(documentRepository.findByIdAndFirmId(1L, FIRM_ID)).thenReturn(Optional.of(pendingCaseDocument("k1")));

            assertThrows(ForbiddenException.class, () -> service.confirmUpload(1L, null));
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
            storageService.put(document.getStorageKey(), new byte[4096]);

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
    // Fixtures
    // ═══════════════════════════════════════════════════════════════════════

    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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

    private InitiateUploadRequest caseUploadRequest(String filename, String contentType, long sizeBytes) {
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setMatterNumber(MATTER_NUMBER);
        request.setFilename(filename);
        request.setContentType(contentType);
        request.setSizeBytes(sizeBytes);
        return request;
    }

    private InitiateUploadRequest projectUploadRequest(String filename, String contentType, long sizeBytes) {
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setProjectCode(PROJECT_CODE);
        request.setFilename(filename);
        request.setContentType(contentType);
        request.setSizeBytes(sizeBytes);
        return request;
    }

    private Document pendingCaseDocument(String storageKey) {
        return Document.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .firmId(FIRM_ID)
                .matterId(MATTER_ID)
                .originalFilename("petition.pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .sizeBytes(100)
                .storageKey(storageKey)
                .status(DocumentStatus.PENDING_UPLOAD)
                .visibility(DocumentVisibility.PRIVATE)
                .uploadExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    private Document pendingProjectDocument(String storageKey) {
        return Document.builder()
                .id(2L)
                .uuid(UUID.randomUUID())
                .firmId(FIRM_ID)
                .projectId(PROJECT_ID)
                .originalFilename("agreement.docx")
                .contentType(DOCX)
                .extension("docx")
                .sizeBytes(100)
                .storageKey(storageKey)
                .status(DocumentStatus.PENDING_UPLOAD)
                .visibility(DocumentVisibility.PRIVATE)
                .uploadExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    private Document activeDocument(DocumentVisibility visibility) {
        Document document = pendingCaseDocument("k1");
        document.setStatus(DocumentStatus.ACTIVE);
        document.setVisibility(visibility);
        document.setUploadedByUserId(USER_ID);
        return document;
    }

    @Test
    @DisplayName("a document belonging to another firm is never returned")
    void crossFirmDocumentsAreNotVisible() {
        // The lookup is scoped by the caller's own firm, so another firm's row simply is not found.
        when(documentRepository.findByIdAndFirmId(5L, FIRM_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.archive(5L));
    }
}
