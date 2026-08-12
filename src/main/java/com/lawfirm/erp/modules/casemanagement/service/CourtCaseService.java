package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.RecordJudgmentRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateCourtCaseStageRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtCaseResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.CourtCaseRoleResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.UpcomingAppealResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCaseRole;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.entity.MatterTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRoleRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtCaseService {

    private final CourtCaseRepository courtCaseRepository;
    private final MatterRepository matterRepository;
    private final CourtEventRepository courtEventRepository;
    private final CourtCaseRoleRepository courtCaseRoleRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final MatterTimelineRepository matterTimelineRepository;
    private final AppealDeadlineEngine appealDeadlineEngine;
    private final AuditService auditService;

    public CourtCaseResponse getCourtCase(String ourCourtCaseRef) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);
        Matter matter = matterRepository.findById(cc.getMatterId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found"));
        return toResponse(cc, matter);
    }

    @Transactional
    public CourtCaseResponse updateCourtCase(String ourCourtCaseRef, UpdateCourtCaseRequest request) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);
        Matter matter = matterRepository.findById(cc.getMatterId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found"));

        if (request.getCourtCaseNumber() != null) cc.setCourtCaseNumber(request.getCourtCaseNumber());
        if (request.getCourtName() != null) cc.setCourtName(request.getCourtName());
        if (request.getAdvocateId() != null) cc.setAdvocateId(request.getAdvocateId());
        if (request.getJudgeName() != null) cc.setJudgeName(request.getJudgeName());

        if (request.getFirNumber() != null) cc.setFirNumber(request.getFirNumber());
        if (request.getFirDate() != null) cc.setFirDate(request.getFirDate());
        if (request.getPoliceStation() != null) cc.setPoliceStation(request.getPoliceStation());
        if (request.getInvestigationAuthority() != null) cc.setInvestigationAuthority(request.getInvestigationAuthority());
        if (request.getArrestDate() != null) cc.setArrestDate(request.getArrestDate());
        if (request.getChargeSheetDate() != null) cc.setChargeSheetDate(request.getChargeSheetDate());
        if (request.getBailStatus() != null) cc.setBailStatus(request.getBailStatus());

        if (request.getMediationDate() != null) cc.setMediationDate(request.getMediationDate());
        if (request.getMediationOutcome() != null) cc.setMediationOutcome(request.getMediationOutcome());
        if (request.getWrittenStatementDeadline() != null) cc.setWrittenStatementDeadline(request.getWrittenStatementDeadline());

        cc = courtCaseRepository.save(cc);
        auditService.log(AuditAction.COURT_CASE_UPDATED, AuditEntity.COURT_CASE, cc.getId(),
                "Court case updated: " + ourCourtCaseRef);

        return toResponse(cc, matter);
    }

    @Transactional
    public CourtCaseResponse updateStage(String ourCourtCaseRef, UpdateCourtCaseStageRequest request) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);
        Matter matter = matterRepository.findById(cc.getMatterId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found"));

        CourtCaseStage newStage = request.getStage();
        CourtCaseStage oldStage = cc.getStage();

        if (CourtCaseStage.isTerminal(oldStage)) {
            throw new BusinessRuleException("Cannot change stage of a closed court case");
        }
        if (!newStage.isValidFor(cc.getCourtLevel(), matter.getMatterType(), cc.getRelationType())) {
            throw new BusinessRuleException("Stage '" + newStage + "' is not valid for "
                    + cc.getCourtLevel() + "/" + matter.getMatterType() + "/" + cc.getRelationType());
        }
        if (!oldStage.allowedTransitions().contains(newStage)) {
            throw new BusinessRuleException("Cannot transition from '" + oldStage + "' to '" + newStage + "'");
        }

        cc.setStage(newStage);
        switch (newStage) {
            case JUDGMENT_AWAITED -> cc.setStatus(CourtCaseStatus.JUDGMENT_AWAITED);
            case JUDGMENT_DELIVERED -> cc.setStatus(CourtCaseStatus.DECIDED);
            case CLOSED -> cc.setStatus(CourtCaseStatus.CLOSED);
            case EXECUTION -> {
                cc.setStatus(CourtCaseStatus.ACTIVE);
                cc.setAppealLapsed(false); // firm started enforcement — judgment no longer "lapsed"
            }
            default -> { }
        }
        cc = courtCaseRepository.save(cc);

        // Mediation lifecycle events
        if (oldStage == CourtCaseStage.MEDIATION && newStage != CourtCaseStage.MEDIATION) {
            recordTimeline(matter, cc.getId(),
                    newStage == CourtCaseStage.CLOSED
                            ? TimelineEventType.MEDIATION_SUCCEEDED
                            : TimelineEventType.MEDIATION_FAILED,
                    newStage == CourtCaseStage.CLOSED
                            ? "Mediation succeeded — settlement registered"
                            : "Mediation failed — proceeding to hearing",
                    cc.getOurCourtCaseRef());
        }

        recordTimeline(matter, cc.getId(), TimelineEventType.STAGE_CHANGE,
                "Stage changed", "From '" + oldStage + "' to '" + newStage + "'");
        auditService.log(AuditAction.COURT_CASE_STAGE_CHANGED, AuditEntity.COURT_CASE, cc.getId(),
                "Stage changed: " + ourCourtCaseRef + " from " + oldStage + " to " + newStage);

        return toResponse(cc, matter);
    }

    @Transactional
    public CourtCaseResponse recordJudgment(String ourCourtCaseRef, RecordJudgmentRequest request) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);
        Matter matter = matterRepository.findById(cc.getMatterId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found"));
        recordJudgmentInternal(cc, matter, request.getJudgmentDate(),
                request.getJudgmentSummary(), request.getDecisionInFavorOfPartyId());
        return toResponse(cc, matter);
    }

    /**
     * THE one place a CourtCase becomes DECIDED with a judgment on record.
     * Used by the judgment endpoint AND by marking a CourtEvent held with
     * outcomeType=JUDGMENT_DELIVERED — the two paths can never get out of sync:
     * a DECIDED case always carries judgment fields and a computed appeal deadline.
     *
     * partyIsState is recorded once at court-case creation; the deadline engine
     * always trusts the stored value, never a per-call flag.
     */
    void recordJudgmentInternal(CourtCase cc, Matter matter, LocalDate judgmentDate,
                                String judgmentSummary, UUID decisionInFavorOfPartyId) {
        if (CourtCaseStage.isTerminal(cc.getStage())) {
            throw new BusinessRuleException("Cannot record judgment on a closed court case");
        }
        if (cc.getStage() != CourtCaseStage.JUDGMENT_AWAITED
                && cc.getStage() != CourtCaseStage.JUDGMENT_DELIVERED) {
            throw new BusinessRuleException(
                    "Judgment can only be recorded from JUDGMENT_AWAITED (current stage: " + cc.getStage() + ")");
        }

        cc.setJudgmentDate(judgmentDate);
        cc.setJudgmentSummary(judgmentSummary);
        cc.setDecisionInFavorOfPartyId(decisionInFavorOfPartyId);
        cc.setStage(CourtCaseStage.JUDGMENT_DELIVERED);
        cc.setStatus(CourtCaseStatus.DECIDED);
        cc.setAppealLapsed(false);
        cc.setAppealDeadline(appealDeadlineEngine.compute(
                cc.getCourtLevel(), matter.getMatterType(), cc.isPartyIsState(), judgmentDate));
        cc.setAppealRequiresLeave(cc.getCourtLevel() == CourtLevel.HIGH);
        courtCaseRepository.save(cc);

        recordTimeline(matter, cc.getId(), TimelineEventType.JUDGMENT_RECORDED,
                "Judgment recorded: " + cc.getOurCourtCaseRef(), judgmentSummary);
        auditService.log(AuditAction.JUDGMENT_RECORDED, AuditEntity.COURT_CASE, cc.getId(),
                "Judgment recorded for " + cc.getOurCourtCaseRef()
                        + (cc.getAppealDeadline() != null
                        ? " — appeal deadline " + cc.getAppealDeadline() : "")
                        + (Boolean.TRUE.equals(cc.getAppealRequiresLeave()) ? " (leave to appeal required)" : ""));
    }

    /**
     * Legal next moves from the current stage, filtered by court level / matter type /
     * relation — so the frontend never duplicates the state machine.
     */
    public List<CourtCaseStage> getAllowedStages(String ourCourtCaseRef) {
        UUID firmId = getRequiredFirmId();
        CourtCase cc = findCourtCase(ourCourtCaseRef, firmId);
        Matter matter = matterRepository.findById(cc.getMatterId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found"));
        return cc.getStage().allowedTransitions().stream()
                .filter(s -> s.isValidFor(cc.getCourtLevel(), matter.getMatterType(), cc.getRelationType()))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Decided cases whose statutory appeal window closes within N days and no appeal
     * has been filed yet — the proactive watch list.
     */
    public List<UpcomingAppealResponse> listUpcomingAppealDeadlines(int withinDays) {
        UUID firmId = getRequiredFirmId();
        LocalDate today = LocalDate.now();
        List<CourtCase> due = courtCaseRepository
                .findByFirmIdAndStatusAndAppealDeadlineBetweenAndAppealLapsedFalse(
                        firmId, CourtCaseStatus.DECIDED, today, today.plusDays(withinDays))
                .stream()
                .filter(cc -> cc.getStage() == CourtCaseStage.JUDGMENT_DELIVERED)
                .filter(cc -> !courtCaseRepository.existsByParentCourtCaseId(cc.getId()))
                .collect(Collectors.toList());
        if (due.isEmpty()) return List.of();

        Set<UUID> matterIds = due.stream().map(CourtCase::getMatterId).collect(Collectors.toSet());
        Map<UUID, Matter> matters = matterRepository.findAllById(matterIds).stream()
                .collect(Collectors.toMap(Matter::getId, Function.identity()));

        return due.stream().map(cc -> {
            Matter m = matters.get(cc.getMatterId());
            return UpcomingAppealResponse.builder()
                    .id(cc.getId())
                    .ourCourtCaseRef(cc.getOurCourtCaseRef())
                    .courtLevel(cc.getCourtLevel())
                    .courtName(cc.getCourtName())
                    .judgmentDate(cc.getJudgmentDate())
                    .appealDeadline(cc.getAppealDeadline())
                    .partyIsState(cc.isPartyIsState())
                    .appealRequiresLeave(Boolean.TRUE.equals(cc.getAppealRequiresLeave()))
                    .matterNumber(m != null ? m.getMatterNumber() : null)
                    .matterTitle(m != null ? m.getTitle() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private CourtCase findCourtCase(String ourCourtCaseRef, UUID firmId) {
        return courtCaseRepository.findByOurCourtCaseRefAndFirmId(ourCourtCaseRef, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Court case not found: " + ourCourtCaseRef));
    }

    private void recordTimeline(Matter matter, UUID courtCaseId, TimelineEventType type,
                                String title, String description) {
        MatterTimelineEvent event = new MatterTimelineEvent();
        event.setFirmId(matter.getFirmId());
        event.setMatterId(matter.getId());
        event.setCourtCaseId(courtCaseId);
        event.setEventType(type);
        event.setTitle(title);
        event.setDescription(description);
        matterTimelineRepository.save(event);
    }

    private CourtCaseResponse toResponse(CourtCase cc, Matter matter) {
        List<CourtCaseRole> roles = courtCaseRoleRepository
                .findByCourtCaseIdAndFirmId(cc.getId(), cc.getFirmId());
        Map<UUID, MatterParty> partiesById = roles.isEmpty() ? Map.of()
                : matterPartyRepository.findAllById(
                        roles.stream().map(CourtCaseRole::getMatterPartyId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(MatterParty::getId, Function.identity()));

        List<CourtCaseRoleResponse> roleResponses = roles.stream()
                .map(r -> {
                    MatterParty p = partiesById.get(r.getMatterPartyId());
                    return CourtCaseRoleResponse.builder()
                            .id(r.getId())
                            .matterPartyId(r.getMatterPartyId())
                            .fullName(p != null ? p.getFullName() : null)
                            .courtCaseId(r.getCourtCaseId())
                            .roleType(r.getRoleType())
                            .representation(r.getRepresentation())
                            .advocateId(r.getAdvocateId())
                            .build();
                })
                .collect(Collectors.toList());

        int eventCount = courtEventRepository.maxSequenceNo(cc.getId());

        return CourtCaseResponse.builder()
                .id(cc.getId())
                .matterId(matter.getId())
                .matterNumber(matter.getMatterNumber())
                .matterTitle(matter.getTitle())
                .parentCourtCaseId(cc.getParentCourtCaseId())
                .relationType(cc.getRelationType())
                .courtLevel(cc.getCourtLevel())
                .courtName(cc.getCourtName())
                .courtCaseNumber(cc.getCourtCaseNumber())
                .ourCourtCaseRef(cc.getOurCourtCaseRef())
                .filingDate(cc.getFilingDate())
                .stage(cc.getStage())
                .status(cc.getStatus())
                .advocateId(cc.getAdvocateId())
                .judgeName(cc.getJudgeName())
                .judgmentDate(cc.getJudgmentDate())
                .judgmentSummary(cc.getJudgmentSummary())
                .decisionInFavorOfPartyId(cc.getDecisionInFavorOfPartyId())
                .appealDeadline(cc.getAppealDeadline())
                .partyIsState(cc.isPartyIsState())
                .appealRequiresLeave(Boolean.TRUE.equals(cc.getAppealRequiresLeave()))
                .appealLapsed(cc.isAppealLapsed())
                .firNumber(cc.getFirNumber())
                .firDate(cc.getFirDate())
                .policeStation(cc.getPoliceStation())
                .investigationAuthority(cc.getInvestigationAuthority())
                .arrestDate(cc.getArrestDate())
                .chargeSheetDate(cc.getChargeSheetDate())
                .bailStatus(cc.getBailStatus())
                .mediationDate(cc.getMediationDate())
                .mediationOutcome(cc.getMediationOutcome())
                .writtenStatementDeadline(cc.getWrittenStatementDeadline())
                .roles(roleResponses)
                .eventCount(eventCount)
                .createdAt(cc.getCreatedAt())
                .updatedAt(cc.getUpdatedAt())
                .build();
    }
}
