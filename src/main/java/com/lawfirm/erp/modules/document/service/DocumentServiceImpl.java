package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.common.storage.StorageQuotaService;
import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.common.storage.StorageUsageView;
import com.lawfirm.erp.common.storage.StoredObject;
import com.lawfirm.erp.modules.audit.annotation.Audit;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
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
import com.lawfirm.erp.modules.document.util.DocumentStoragePath;
import com.lawfirm.erp.modules.document.util.DocumentTypePolicy;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Document lifecycle: upload → read/share → archive.
 *
 * <p>The bytes are written by this service on the way in — one multipart request stores the file
 * and activates the document together, so there is no ticket to issue and nothing for the client
 * to confirm. The default mode of failure is "nothing happened", never a half-stored document.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private static final int MAGIC_BYTE_PROBE = 4096;

    /** Matches the etag column width. A longer value is dropped rather than failing the insert. */
    private static final int ETAG_MAX_LENGTH = 64;

    private final DocumentRepository documentRepository;
    private final MatterRepository matterRepository;
    private final ProjectRepository projectRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final MatterTimelineRepository matterTimelineRepository;
    private final StorageService storageService;
    private final StorageQuotaService quotaService;
    private final SystemConfigService systemConfigService;
    private final DocumentScopeGuard documentScopeGuard;
    private final DocumentMapper documentMapper;
    private final ReadScopeGuard readScopeGuard;
    private final MatterScopeGuard matterScopeGuard;
    private final CurrentUserResolver currentUserResolver;

    @Override
    // audit_logs.entity_id is a UUID, so the audit trail references the document's uuid — its
    // numeric primary key could not be stored there.
    @Audit(action = AuditAction.DOCUMENT_UPLOADED, entity = AuditEntity.DOCUMENT,
            entityId = "#result.uuid", summary = "'Document uploaded: ' + #result.fileName",
            skipIfNullResult = true)
    @Transactional
    public DocumentResponse upload(String matterNumber, String projectCode, String courtCaseRef,
                                   String originalFilename, String contentType, long sizeBytes,
                                   InputStream content) {
        UUID firmId = requireFirmId();

        DocumentTypePolicy.validateFilename(originalFilename);
        String normalizedType = DocumentTypePolicy.normalizeContentType(contentType);
        DocumentTypePolicy.requireAllowedContentType(normalizedType);

        if (sizeBytes <= 0) {
            throw new BusinessRuleException("The file is empty");
        }
        if (sizeBytes > maxFileSizeBytes()) {
            throw new BusinessRuleException("A file may be at most "
                    + StorageQuotaService.humanReadable(maxFileSizeBytes()));
        }

        boolean hasMatter = StringUtils.hasText(matterNumber);
        boolean hasProject = StringUtils.hasText(projectCode);
        if (hasMatter == hasProject) {
            throw new BusinessRuleException(
                    "Provide either a case (matterNumber) or a project (projectCode) — exactly one");
        }

        if (!quotaService.canFit(firmId, sizeBytes)) {
            throw new BusinessRuleException("This file does not fit in your firm's remaining storage allocation");
        }

        // The row needs its key before it is inserted, so the per-document segment is a
        // generated id rather than the database id of the row being created.
        String objectId = UUID.randomUUID().toString();

        Document document = Document.builder()
                .firmId(firmId)
                .originalFilename(originalFilename)
                .contentType(normalizedType)
                .extension(DocumentTypePolicy.extensionFor(normalizedType))
                .sizeBytes(sizeBytes)
                .visibility(DocumentVisibility.PRIVATE)
                .status(DocumentStatus.ACTIVE)
                .build();

        if (hasMatter) {
            Matter matter = matterRepository
                    .findByMatterNumberAndFirmId(matterNumber, firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Matter not found: " + matterNumber));
            documentScopeGuard.requireUploadAllowed(matter);

            document.setMatterId(matter.getId());
            document.setCourtCaseId(resolveCourtCaseId(courtCaseRef, matter, firmId));
            document.setStorageKey(DocumentStoragePath.forCase(
                    firmId, matter.getMatterNumber(), objectId, originalFilename, maxFilenameLength()));
        } else {
            Project project = projectRepository
                    .findByProjectCodeAndFirmId(projectCode, firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectCode));
            documentScopeGuard.requireUploadAllowed(project);

            document.setProjectId(project.getId());
            document.setStorageKey(DocumentStoragePath.forProject(
                    firmId, project.getProjectCode(), objectId, originalFilename, maxFilenameLength()));
        }

        // From here on the object exists in storage: any failure below must remove it again,
        // or the bucket keeps bytes nothing points at.
        StoredObject stored = storageService.put(
                document.getStorageKey(), content, sizeBytes, normalizedType);

        if (!stored.exists() || stored.size() != sizeBytes) {
            discard(document.getStorageKey());
            throw new BusinessRuleException("The file could not be stored completely. Please try again.");
        }

        // The declared content type is attacker-controlled; the file's own leading bytes are not.
        byte[] head = storageService.readHead(document.getStorageKey(), MAGIC_BYTE_PROBE);
        if (!DocumentTypePolicy.matchesContent(normalizedType, head)) {
            discard(document.getStorageKey());
            throw new BusinessRuleException("The file content does not match its declared type ('"
                    + normalizedType + "')");
        }

        try {
            quotaService.reserve(firmId, sizeBytes);
        } catch (RuntimeException e) {
            discard(document.getStorageKey());
            throw e;
        }

        document.setUploadedByUserId(currentUserResolver.getCurrentUserId());
        document.setEtag(normalizeEtag(stored.etag()));
        Document saved = documentRepository.save(document);

        recordTimeline(saved);
        return toResponse(saved);
    }

    // ========================================================================
    // Read
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> listLibrary(DocumentStatus status, DocumentVisibility visibility,
                                                      String search, int page, int size) {
        UUID firmId = requireFirmId();
        return list(firmId, null, null, status, visibility, search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> listForMatter(String matterNumber, DocumentStatus status,
                                                        DocumentVisibility visibility, String search,
                                                        int page, int size) {
        UUID firmId = requireFirmId();
        Matter matter = matterRepository.findByMatterNumberAndFirmId(matterNumber, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found: " + matterNumber));
        // A client may only open a case that is theirs.
        matterScopeGuard.requireVisible(matter);
        return list(firmId, matter.getId(), null, status, visibility, search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> listForProject(String projectCode, DocumentStatus status,
                                                         DocumentVisibility visibility, String search,
                                                         int page, int size) {
        UUID firmId = requireFirmId();
        Project project = projectRepository.findByProjectCodeAndFirmId(projectCode, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectCode));
        documentScopeGuard.requireProjectReadable(project);
        return list(firmId, null, project.getId(), status, visibility, search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> listForClient(String matterNumber, String projectCode,
                                                        String search, int page, int size) {
        UUID firmId = requireFirmId();

        UUID matterId = null;
        UUID projectId = null;

        if (StringUtils.hasText(matterNumber)) {
            Matter matter = matterRepository.findByMatterNumberAndFirmId(matterNumber, firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Matter not found: " + matterNumber));
            matterScopeGuard.requireVisible(matter);
            matterId = matter.getId();
        }
        if (StringUtils.hasText(projectCode)) {
            Project project = projectRepository.findByProjectCodeAndFirmId(projectCode, firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectCode));
            documentScopeGuard.requireProjectReadable(project);
            projectId = project.getId();
        }
        // The client query ignores the requested visibility and status entirely.
        return list(firmId, matterId, projectId, null, null, search, page, size);
    }

    @Override
    @Audit(action = AuditAction.DOCUMENT_DOWNLOADED, entity = AuditEntity.DOCUMENT,
            entityId = "#result.documentUuid", summary = "'Document downloaded: ' + #result.fileName",
            skipIfNullResult = true)
    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadUrl(Long documentId) {
        Document document = loadForFirm(documentId, requireFirmId());
        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new BusinessRuleException("This document is not available for download");
        }
        documentScopeGuard.requireVisible(document);

        Duration ttl = downloadTtl();
        // The generated URL is a bearer credential: it is returned and never logged. The audit
        // summary above deliberately carries only the filename.
        String url = storageService.presignDownload(
                document.getStorageKey(), document.getOriginalFilename(), ttl);
        return new DownloadUrlResponse(document.getUuid(), url,
                document.getOriginalFilename(), Instant.now().plus(ttl));
    }

    // ========================================================================
    // Mutate
    // ========================================================================

    @Override
    @Audit(action = AuditAction.DOCUMENT_SHARED, entity = AuditEntity.DOCUMENT,
            entityId = "#result.uuid",
            summary = "'Document visibility changed to ' + #visibility + ': ' + #result.fileName",
            skipIfNullResult = true)
    @Transactional
    public DocumentResponse updateVisibility(Long documentId, DocumentVisibility visibility) {
        Document document = loadForFirm(documentId, requireFirmId());
        documentScopeGuard.requireStaffAction(document);

        if (document.getStatus() == DocumentStatus.ARCHIVED) {
            throw new BusinessRuleException("An archived document cannot be shared");
        }
        document.setVisibility(visibility);
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Audit(action = AuditAction.DOCUMENT_DELETED, entity = AuditEntity.DOCUMENT,
            entityId = "#result.uuid", summary = "'Document archived: ' + #result.fileName",
            skipIfNullResult = true)
    @Transactional
    public DocumentResponse archive(Long documentId) {
        UUID firmId = requireFirmId();
        Document document = loadForFirm(documentId, firmId);
        documentScopeGuard.requireStaffAction(document);

        if (document.getStatus() == DocumentStatus.ARCHIVED) {
            return toResponse(document);
        }
        boolean wasActive = document.getStatus() == DocumentStatus.ACTIVE;

        document.setStatus(DocumentStatus.ARCHIVED);
        document.setArchivedAt(LocalDateTime.now());
        document.setArchivedByUserId(currentUserResolver.getCurrentUserId());
        Document saved = documentRepository.save(document);

        if (wasActive) {
            quotaService.release(firmId, document.getSizeBytes());
        }
        // The stored object is deliberately NOT deleted: legal documents are never destroyed
        // here, only hidden. Purging is a separate, explicitly authorised operation.
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StorageUsageView storageUsage() {
        return quotaService.usage(requireFirmId());
    }

    // ========================================================================
    // Internals
    // ========================================================================

    private PagedResponse<DocumentResponse> list(UUID firmId, UUID matterId, UUID projectId,
                                                 DocumentStatus status, DocumentVisibility visibility,
                                                 String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String filter = StringUtils.hasText(search) ? search.trim() : null;

        Page<Document> documents;
        if (readScopeGuard.isClientScope()) {
            documents = documentRepository.findVisibleToClient(firmId, readScopeGuard.currentUserId(),
                    DocumentVisibility.SHARED, DocumentStatus.ACTIVE, matterId, projectId, filter, pageable);
        } else if (readScopeGuard.isAssignmentScope()) {
            documents = documentRepository.findAssignedTo(firmId, readScopeGuard.currentUserId(),
                    matterId, projectId, status, visibility, filter, pageable);
        } else {
            documents = documentRepository.findForFirm(firmId, matterId, projectId, status, visibility,
                    filter, pageable);
        }
        return PagedResponse.of(documents, toResponses(documents.getContent()));
    }

    /** Owner references are resolved in two batched lookups rather than one per row. */
    private List<DocumentResponse> toResponses(List<Document> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Set<UUID> matterIds = documents.stream().map(Document::getMatterId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> projectIds = documents.stream().map(Document::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> matterNumbers = matterIds.isEmpty() ? Map.of()
                : matterRepository.findAllById(matterIds).stream()
                        .collect(Collectors.toMap(Matter::getId, Matter::getMatterNumber));
        Map<UUID, String> projectCodes = projectIds.isEmpty() ? Map.of()
                : projectRepository.findAllById(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, Project::getProjectCode));

        return documents.stream()
                .map(document -> withDocumentUrl(document, documentMapper.toResponse(document,
                        lookup(matterNumbers, document.getMatterId()),
                        lookup(projectCodes, document.getProjectId()))))
                .toList();
    }

    private DocumentResponse toResponse(Document document) {
        String matterNumber = document.getMatterId() == null ? null
                : matterRepository.findById(document.getMatterId())
                        .map(Matter::getMatterNumber).orElse(null);
        String projectCode = document.getProjectId() == null ? null
                : projectRepository.findById(document.getProjectId())
                        .map(Project::getProjectCode).orElse(null);
        return withDocumentUrl(document,
                documentMapper.toResponse(document, matterNumber, projectCode));
    }

    /**
     * Attaches a presigned link so a caller can download straight from a list response instead of
     * making one {@code download-url} round trip per row. Only an {@code ACTIVE} document has a
     * retrievable object: an {@code ARCHIVED} one is deliberately not downloadable and gets
     * {@code null}.
     *
     * <p>Presigning here does not write a {@code DOCUMENT_DOWNLOADED} audit row, so a download
     * taken straight from the list is not individually audited. Use the {@code download-url}
     * endpoint when the audit trail must carry a row per download.
     */
    private DocumentResponse withDocumentUrl(Document document, DocumentResponse response) {
        if (document.getStatus() == DocumentStatus.ACTIVE
                && StringUtils.hasText(document.getStorageKey())) {
            response.setDocumentUrl(storageService.presignDownload(
                    document.getStorageKey(), document.getOriginalFilename(), downloadTtl()));
        }
        return response;
    }

    /** Never a 403: a document in another firm must not be distinguishable from one that does not exist. */
    private Document loadForFirm(Long documentId, UUID firmId) {
        return documentRepository.findByIdAndFirmId(documentId, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private UUID resolveCourtCaseId(String courtCaseRef, Matter matter, UUID firmId) {
        if (!StringUtils.hasText(courtCaseRef)) {
            return null;
        }
        return courtCaseRepository.findByOurCourtCaseRefAndFirmId(courtCaseRef, firmId)
                .filter(courtCase -> matter.getId().equals(courtCase.getMatterId()))
                .map(courtCase -> courtCase.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Court case not found on this matter: " + courtCaseRef));
    }

    /** Only case documents reach the matter timeline; projects have no timeline. */
    private void recordTimeline(Document document) {
        if (document.getMatterId() == null) {
            return;
        }
        MatterTimelineEvent event = new MatterTimelineEvent();
        event.setFirmId(document.getFirmId());
        event.setMatterId(document.getMatterId());
        event.setCourtCaseId(document.getCourtCaseId());
        event.setEventType(TimelineEventType.DOCUMENT_UPLOADED);
        event.setTitle("Document uploaded: " + document.getOriginalFilename());
        event.setDescription(document.getExtension().toUpperCase() + " · "
                + StorageQuotaService.humanReadable(document.getSizeBytes()));
        matterTimelineRepository.save(event);
    }

    /** Best-effort cleanup — a failure here must not mask the original error. */
    private void discard(String storageKey) {
        try {
            storageService.delete(storageKey);
        } catch (Exception e) {
            log.warn("Could not remove the rejected object '{}': {}", storageKey, e.getMessage());
        }
    }

    /**
     * S3 returns an unquoted hex etag; some clients send it quoted. Anything that is still not
     * a plausible etag afterwards is discarded rather than truncated, since a partial etag would
     * be misleading in the audit trail.
     */
    private static String normalizeEtag(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty() || value.length() > ETAG_MAX_LENGTH) {
            return null;
        }
        return value;
    }

    private static String lookup(Map<UUID, String> map, UUID key) {
        return key == null ? null : map.get(key);
    }

    /**
     * The caller's firm, or a refusal.
     *
     * <p>Every document operation funnels through here, so it is also where a platform
     * (Super Admin) session is turned away: documents are firm records and the platform has no
     * business reading a firm's files. {@code PermissionEvaluator} exempts Super Admin from
     * permission checks, so this cannot be left to the permission layer.
     */
    private UUID requireFirmId() {
        if (currentUserResolver.isSuperAdmin()) {
            throw new ForbiddenException(
                    "Documents belong to a firm; platform administrators cannot access them");
        }
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This request is not associated with a firm");
        }
        return firmId;
    }

    /** Storage policy is DB-configurable (STORAGE group); see {@link SystemConfigService}. */
    private long maxFileSizeBytes() {
        return systemConfigService.storageMaxFileSizeBytes();
    }

    private Duration downloadTtl() {
        return Duration.ofSeconds(systemConfigService.storageDownloadExpirySeconds());
    }

    private int maxFilenameLength() {
        return systemConfigService.documentMaxFilenameLength();
    }
}
