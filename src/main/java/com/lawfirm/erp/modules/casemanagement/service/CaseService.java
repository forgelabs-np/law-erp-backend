package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.*;
import com.lawfirm.erp.modules.casemanagement.dto.response.*;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.CaseParty;
import com.lawfirm.erp.modules.casemanagement.entity.CaseTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.CasePartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseTimelineRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseRepository caseRepository;
    private final CasePartyRepository casePartyRepository;
    private final CaseTimelineRepository caseTimelineRepository;
    private final HearingRepository hearingRepository;
    private final CaseNumberGenerator caseNumberGenerator;
    private final AuditService auditService;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request) {
        UUID firmId = FirmContextHolder.getFirmId();
        String firmCode = FirmContextHolder.getFirmCode();
        if (firmId == null || firmCode == null)
            throw new ForbiddenException("Firm context required");

        // Generate case number
        String caseNumber = caseNumberGenerator.generate(firmCode, request.getCaseType());

        Case c = new Case();
        c.setFirmId(firmId);
        c.setCaseNumber(caseNumber);
        c.setCaseType(request.getCaseType());
        c.setTitle(request.getTitle());
        c.setCaseStage(CaseStage.initial(request.getCaseType()));
        c.setStatus(CaseStatus.ACTIVE);

        // Common fields
        c.setCourtName(request.getCourtName());
        c.setCourtCaseNumber(request.getCourtCaseNumber());
        c.setFilingDate(request.getFilingDate() != null ? request.getFilingDate() : LocalDate.now());
        c.setFilingNumber(request.getFilingNumber());
        c.setAssignedTo(request.getAssignedTo());
        c.setDescription(request.getDescription());

        // Civil-specific
        c.setMediationDate(request.getMediationDate());
        c.setMediationOutcome(request.getMediationOutcome());
        c.setWrittenStatementDeadline(request.getWrittenStatementDeadline());

        // Criminal-specific
        c.setFirNumber(request.getFirNumber());
        c.setFirDate(request.getFirDate());
        c.setPoliceStation(request.getPoliceStation());
        c.setInvestigationAuthority(request.getInvestigationAuthority());
        c.setArrestDate(request.getArrestDate());
        c.setChargeSheetDate(request.getChargeSheetDate());
        c.setBailStatus(request.getBailStatus());

        Case saved = caseRepository.save(c);

        // Add parties
        if (request.getPlaintiffs() != null) {
            for (CreateCaseRequest.PartyEntry p : request.getPlaintiffs()) {
                CaseParty cp = buildParty(saved, p);
                if (cp.getPartyType() == null) cp.setPartyType(PartyType.PLAINTIFF);
                if (cp.getRepresentation() == null) cp.setRepresentation(PartyRepresentation.REPRESENTED);
                casePartyRepository.save(cp);
            }
        }
        if (request.getDefendants() != null) {
            for (CreateCaseRequest.PartyEntry p : request.getDefendants()) {
                CaseParty cp = buildParty(saved, p);
                if (cp.getPartyType() == null) cp.setPartyType(PartyType.DEFENDANT);
                if (cp.getRepresentation() == null) cp.setRepresentation(PartyRepresentation.OPPOSING);
                casePartyRepository.save(cp);
            }
        }

        // Record timeline event
        recordTimeline(saved, TimelineEventType.CASE_CREATED, "Case created", null);

        // Audit log
        auditService.log(AuditAction.CASE_CREATED, AuditEntity.CASE, saved.getId(),
                "Case created: " + caseNumber + " (" + request.getCaseType() + ") - " + request.getTitle());

        return toResponse(saved);
    }

    public Page<CaseResponse> listCases(CaseType caseType, CaseStage caseStage, CaseStatus status,
                                         UUID assignedTo, String courtName, LocalDate dateFrom, LocalDate dateTo,
                                         String search, int page, int size) {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Case> cases;

        if (anyFilterApplied(caseType, caseStage, status, assignedTo, courtName, dateFrom, dateTo, search)) {
            cases = caseRepository.findByFilters(firmId, caseType, caseStage, status, assignedTo,
                    courtName, dateFrom, dateTo, search, pageable);
        } else {
            cases = caseRepository.findByFirmId(firmId, pageable);
        }

        return cases.map(this::toResponse);
    }

    public CaseResponse getCase(String caseNumber) {
        Case c = getValidatedCase(caseNumber, getRequiredFirmId());
        return toResponse(c);
    }

    @Transactional
    public CaseResponse updateCase(String caseNumber, UpdateCaseRequest request) {
        Case c = getValidatedCase(caseNumber, getRequiredFirmId());

        if (request.getTitle() != null) c.setTitle(request.getTitle());
        if (request.getCourtName() != null) c.setCourtName(request.getCourtName());
        if (request.getCourtCaseNumber() != null) c.setCourtCaseNumber(request.getCourtCaseNumber());
        if (request.getFilingDate() != null) c.setFilingDate(request.getFilingDate());
        if (request.getFilingNumber() != null) c.setFilingNumber(request.getFilingNumber());
        if (request.getAssignedTo() != null) c.setAssignedTo(request.getAssignedTo());
        if (request.getDescription() != null) c.setDescription(request.getDescription());

        if (request.getMediationDate() != null) c.setMediationDate(request.getMediationDate());
        if (request.getMediationOutcome() != null) c.setMediationOutcome(request.getMediationOutcome());
        if (request.getWrittenStatementDeadline() != null) c.setWrittenStatementDeadline(request.getWrittenStatementDeadline());

        if (request.getFirNumber() != null) c.setFirNumber(request.getFirNumber());
        if (request.getFirDate() != null) c.setFirDate(request.getFirDate());
        if (request.getPoliceStation() != null) c.setPoliceStation(request.getPoliceStation());
        if (request.getInvestigationAuthority() != null) c.setInvestigationAuthority(request.getInvestigationAuthority());
        if (request.getArrestDate() != null) c.setArrestDate(request.getArrestDate());
        if (request.getChargeSheetDate() != null) c.setChargeSheetDate(request.getChargeSheetDate());
        if (request.getBailStatus() != null) c.setBailStatus(request.getBailStatus());

        Case saved = caseRepository.save(c);
        recordTimeline(saved, TimelineEventType.CASE_NOTE_ADDED, "Case details updated", null);

        // Audit log
        auditService.log(AuditAction.CASE_UPDATED, AuditEntity.CASE, saved.getId(),
                "Case updated: " + caseNumber);

        return toResponse(saved);
    }

    @Transactional
    public void deleteCase(String caseNumber) {
        Case c = getValidatedCase(caseNumber, getRequiredFirmId());
        UUID caseId = c.getId();

        boolean hasHearings = hearingRepository.findByCaseIdOrderByDateDesc(caseId)
                .stream().anyMatch(h -> h.getStatus() != HearingStatus.CANCELED);
        if (hasHearings) {
            throw new BusinessRuleException("Cannot delete case with active hearings. Cancel hearings first.");
        }

        casePartyRepository.deleteByCaseIdAndFirmId(caseId, c.getFirmId());
        caseTimelineRepository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .forEach(caseTimelineRepository::delete);
        caseRepository.delete(c);

        // Audit log
        auditService.log(AuditAction.CASE_DELETED, AuditEntity.CASE, caseId,
                "Case deleted: " + caseNumber);
    }

    @Transactional
    public CaseResponse updateStage(String caseNumber, UpdateCaseStageRequest request) {
        Case c = getValidatedCase(caseNumber, getRequiredFirmId());
        CaseStage newStage = request.getStage();

        if (c.getStatus() == CaseStatus.CLOSED) {
            throw new BusinessRuleException("Cannot change stage of a closed case");
        }

        if (!newStage.isValidFor(c.getCaseType())) {
            throw new BusinessRuleException(
                    "Stage '" + newStage + "' is not valid for case type '" + c.getCaseType() + "'");
        }

        if (!c.getCaseStage().allowedTransitions().contains(newStage)) {
            throw new BusinessRuleException(
                    "Cannot transition from '" + c.getCaseStage() + "' to '" + newStage + "'");
        }

        CaseStage oldStage = c.getCaseStage();
        c.setCaseStage(newStage);
        if (newStage == CaseStage.CLOSED) {
            c.setStatus(CaseStatus.CLOSED);
        }

        Case saved = caseRepository.save(c);
        recordTimeline(saved, TimelineEventType.STAGE_CHANGE,
                "Stage changed", "From '" + oldStage + "' to '" + newStage + "'");

        // Audit log
        auditService.log(AuditAction.CASE_STATUS_CHANGED, AuditEntity.CASE, saved.getId(),
                "Stage changed: " + caseNumber + " from " + oldStage + " to " + newStage);

        return toResponse(saved);
    }

    public List<TimelineEventResponse> getTimeline(String caseNumber) {
        Case c = getValidatedCase(caseNumber, getRequiredFirmId());
        return caseTimelineRepository.findByCaseIdAndFirmIdOrderByCreatedAtDesc(c.getId(), c.getFirmId())
                .stream()
                .map(e -> TimelineEventResponse.builder()
                        .id(e.getId())
                        .eventType(e.getEventType())
                        .title(e.getTitle())
                        .description(e.getDescription())
                        .createdAt(e.getCreatedAt())
                        .createdBy(e.getCreatedBy())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public PartyResponse addParty(String caseNumber, AddPartyRequest request) {
        Case c = getValidatedCase(caseNumber, getRequiredFirmId());

        CaseParty cp = new CaseParty();
        cp.setFirmId(c.getFirmId());
        cp.setCaseId(c.getId());
        cp.setPartyType(request.getPartyType());
        cp.setRepresentation(request.getRepresentation() != null ? request.getRepresentation() : PartyRepresentation.REPRESENTED);
        cp.setFullName(request.getFullName());
        cp.setMobileNo(request.getMobileNo());
        cp.setEmail(request.getEmail());
        cp.setAddress(request.getAddress());
        cp.setClientId(request.getClientId());
        cp.setOurClient(request.isOurClient());
        cp.setAdvocateId(request.getAdvocateId());
        cp.setNotes(request.getNotes());

        CaseParty saved = casePartyRepository.save(cp);
        recordTimeline(c, TimelineEventType.PARTY_ADDED, "Party added: " + request.getFullName(), null);

        // Audit log for party addition on case
        auditService.log(AuditAction.CASE_UPDATED, AuditEntity.CASE, c.getId(),
                "Party added to case " + caseNumber + ": " + request.getFullName() + " (" + request.getPartyType() + ")");

        return toPartyResponse(saved);
    }

    @Transactional
    public PartyResponse linkParty(String caseNumber, UUID partyId, LinkPartyRequest request) {
        UUID firmId = getRequiredFirmId();
        Case c = getValidatedCase(caseNumber, firmId);

        CaseParty cp = casePartyRepository.findById(partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + partyId));

        if (!cp.getFirmId().equals(firmId) || !cp.getCaseId().equals(c.getId())) {
            throw new ForbiddenException("Party does not belong to this case or firm");
        }

        cp.setClientId(request.getClientId());
        cp.setOurClient(request.isOurClient());
        CaseParty saved = casePartyRepository.save(cp);
        return toPartyResponse(saved);
    }

    @Transactional
    public void removeParty(String caseNumber, UUID partyId) {
        UUID firmId = getRequiredFirmId();
        Case c = getValidatedCase(caseNumber, firmId);

        CaseParty cp = casePartyRepository.findById(partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found with id: " + partyId));

        if (!cp.getFirmId().equals(firmId) || !cp.getCaseId().equals(c.getId())) {
            throw new ForbiddenException("Party does not belong to this case or firm");
        }

        casePartyRepository.delete(cp);
    }

    // --- Internal helpers ---

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private Case getValidatedCase(String caseNumber, UUID firmId) {
        return caseRepository.findByCaseNumberAndFirmId(caseNumber, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseNumber));
    }

    private CaseParty buildParty(Case c, CreateCaseRequest.PartyEntry p) {
        CaseParty cp = new CaseParty();
        cp.setFirmId(c.getFirmId());
        cp.setCaseId(c.getId());
        cp.setFullName(p.getFullName());
        cp.setMobileNo(p.getMobileNo());
        cp.setEmail(p.getEmail());
        cp.setAddress(p.getAddress());
        cp.setClientId(p.getClientId());
        cp.setOurClient(p.isOurClient());
        cp.setAdvocateId(p.getAdvocateId());
        cp.setNotes(p.getNotes());
        return cp;
    }

    private void recordTimeline(Case c, TimelineEventType type, String title, String description) {
        CaseTimelineEvent event = new CaseTimelineEvent();
        event.setFirmId(c.getFirmId());
        event.setCaseId(c.getId());
        event.setEventType(type);
        event.setTitle(title);
        event.setDescription(description);
        caseTimelineRepository.save(event);
    }

    private CaseResponse toResponse(Case c) {
        UUID firmId = c.getFirmId();
        List<PartyResponse> parties = casePartyRepository.findByCaseIdAndFirmId(c.getId(), firmId)
                .stream()
                .map(this::toPartyResponse)
                .collect(Collectors.toList());

        int hearingCount = (int) hearingRepository.findByCaseIdOrderByDateDesc(c.getId())
                .stream().filter(h -> h.getStatus() != HearingStatus.CANCELED).count();

        return CaseResponse.builder()
                .id(c.getId())
                .firmId(c.getFirmId())
                .caseNumber(c.getCaseNumber())
                .caseType(c.getCaseType())
                .title(c.getTitle())
                .caseStage(c.getCaseStage())
                .status(c.getStatus())
                .courtName(c.getCourtName())
                .courtCaseNumber(c.getCourtCaseNumber())
                .judgeName(c.getJudgeName())
                .filingDate(c.getFilingDate())
                .filingNumber(c.getFilingNumber())
                .assignedTo(c.getAssignedTo())
                .description(c.getDescription())
                .parties(parties)
                .hearingCount(hearingCount)
                .mediationDate(c.getMediationDate())
                .mediationOutcome(c.getMediationOutcome())
                .writtenStatementDeadline(c.getWrittenStatementDeadline())
                .firNumber(c.getFirNumber())
                .firDate(c.getFirDate())
                .policeStation(c.getPoliceStation())
                .investigationAuthority(c.getInvestigationAuthority())
                .arrestDate(c.getArrestDate())
                .chargeSheetDate(c.getChargeSheetDate())
                .bailStatus(c.getBailStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private PartyResponse toPartyResponse(CaseParty cp) {
        return PartyResponse.builder()
                .id(cp.getId())
                .partyType(cp.getPartyType())
                .representation(cp.getRepresentation())
                .fullName(cp.getFullName())
                .mobileNo(cp.getMobileNo())
                .email(cp.getEmail())
                .address(cp.getAddress())
                .clientId(cp.getClientId())
                .isOurClient(cp.isOurClient())
                .advocateId(cp.getAdvocateId())
                .notes(cp.getNotes())
                .build();
    }

    private boolean anyFilterApplied(CaseType caseType, CaseStage caseStage, CaseStatus status,
                                      UUID assignedTo, String courtName, LocalDate dateFrom, LocalDate dateTo,
                                      String search) {
        return caseType != null || caseStage != null || status != null || assignedTo != null
                || courtName != null || dateFrom != null || dateTo != null || search != null;
    }
}
