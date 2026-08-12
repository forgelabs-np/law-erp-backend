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
import com.lawfirm.erp.modules.casemanagement.entity.*;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.*;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatterService {

    private final MatterRepository matterRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final CourtEventRepository courtEventRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final CourtCaseRoleRepository courtCaseRoleRepository;
    private final MatterTimelineRepository matterTimelineRepository;
    private final MatterNumberGenerator matterNumberGenerator;
    private final CourtCaseRefGenerator courtCaseRefGenerator;
    private final AuditService auditService;

    @Transactional
    public MatterResponse createMatter(CreateMatterRequest request) {
        UUID firmId = getRequiredFirmId();
        String firmCode = FirmContextHolder.getFirmCode();
        if (firmCode == null) throw new ForbiddenException("Firm context required");

        String matterNumber = matterNumberGenerator.generate(firmCode);

        Matter matter = new Matter();
        matter.setFirmId(firmId);
        matter.setMatterNumber(matterNumber);
        matter.setMatterType(request.getMatterType());
        matter.setTitle(request.getTitle());
        matter.setStatus(MatterStatus.ACTIVE);
        matter.setOriginatingCourtLevel(request.getOriginatingCourtLevel());
        matter.setAssignedPartnerId(request.getAssignedPartnerId());
        matter.setDescription(request.getDescription());
        matter = matterRepository.save(matter);

        // ORIGINAL court case at the originating level
        CourtCase cc = new CourtCase();
        cc.setFirmId(firmId);
        cc.setMatterId(matter.getId());
        cc.setParentCourtCaseId(null);
        cc.setRelationType(RelationType.ORIGINAL);
        cc.setCourtLevel(request.getOriginatingCourtLevel());
        cc.setCourtName(request.getCourtName());
        cc.setCourtCaseNumber(request.getCourtCaseNumber());
        cc.setOurCourtCaseRef(courtCaseRefGenerator.generate(
                matterNumber, matter.getId(), request.getOriginatingCourtLevel()));
        cc.setFilingDate(request.getFilingDate() != null ? request.getFilingDate() : LocalDate.now());
        cc.setStage(CourtCaseStage.initialFor(
                request.getOriginatingCourtLevel(), request.getMatterType(), RelationType.ORIGINAL));
        cc.setStatus(CourtCaseStatus.ACTIVE);
        cc.setAdvocateId(request.getAdvocateId());
        cc.setActive(true);
        cc = courtCaseRepository.save(cc);

        matter.setCurrentCourtCaseId(cc.getId());
        matter = matterRepository.save(matter);

        // Parties → MatterParty identity + CourtCaseRole on the original case
        for (PartyEntryRequest entry : request.getParties()) {
            MatterParty party = buildParty(matter, entry);
            party = matterPartyRepository.save(party);
            createRole(cc, party, entry.getRoleType(), entry.getRepresentation(), entry.getAdvocateId());
        }

        recordTimeline(matter, null, TimelineEventType.MATTER_CREATED,
                "Matter created: " + matterNumber, null);
        recordTimeline(matter, cc.getId(), TimelineEventType.COURT_CASE_ADDED,
                "Filed at " + cc.getCourtName(), cc.getOurCourtCaseRef());

        auditService.log(AuditAction.MATTER_CREATED, AuditEntity.MATTER, matter.getId(),
                "Matter created: " + matterNumber + " (" + request.getMatterType() + ") - " + request.getTitle());
        auditService.log(AuditAction.COURT_CASE_CREATED, AuditEntity.COURT_CASE, cc.getId(),
                "Court case filed: " + cc.getOurCourtCaseRef() + " at " + cc.getCourtName());

        return toMatterResponse(matter, true);
    }

    public Page<MatterResponse> listMatters(MatterType matterType, MatterStatus status,
                                            String search, int page, int size) {
        UUID firmId = getRequiredFirmId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Matter> matters;
        if (matterType != null || status != null || search != null) {
            matters = matterRepository.findByFilters(firmId, matterType, status, search, pageable);
        } else {
            matters = matterRepository.findByFirmId(firmId, pageable);
        }
        return matters.map(m -> toMatterResponse(m, false));
    }

    public MatterResponse getMatter(String matterNumber) {
        Matter matter = findMatter(matterNumber);
        return toMatterResponse(matter, true);
    }

    public List<TimelineEventResponse> getTimeline(String matterNumber) {
        Matter matter = findMatter(matterNumber);
        List<MatterTimelineEvent> events =
                matterTimelineRepository.findByMatterIdAndFirmIdOrderByCreatedAtDesc(matter.getId(), matter.getFirmId());

        // Batch-resolve court case refs (single query, no N+1)
        Set<UUID> ccIds = events.stream()
                .map(MatterTimelineEvent::getCourtCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> refs = ccIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(ccIds).stream()
                        .collect(Collectors.toMap(CourtCase::getId, CourtCase::getOurCourtCaseRef));

        return events.stream()
                .map(e -> TimelineEventResponse.builder()
                        .id(e.getId())
                        .matterId(matter.getId())
                        .matterNumber(matter.getMatterNumber())
                        .courtCaseId(e.getCourtCaseId())
                        .ourCourtCaseRef(e.getCourtCaseId() != null ? refs.get(e.getCourtCaseId()) : null)
                        .eventType(e.getEventType())
                        .title(e.getTitle())
                        .description(e.getDescription())
                        .createdAt(e.getCreatedAt())
                        .createdBy(e.getCreatedBy())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Firm-wide activity feed across all matters, newest first. Filters: matter type,
     * matter status, createdAt window. Batch-resolves matter + court-case refs.
     */
    public Page<TimelineEventResponse> getFirmTimeline(MatterType matterType, MatterStatus status,
                                                       LocalDate from, LocalDate to,
                                                       int page, int size) {
        UUID firmId = getRequiredFirmId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<MatterTimelineEvent> events =
                matterTimelineRepository.findFirmEvents(firmId, matterType, status, fromDt, toDt, pageable);

        Set<UUID> matterIds = events.getContent().stream()
                .map(MatterTimelineEvent::getMatterId).collect(Collectors.toSet());
        Map<UUID, Matter> matters = matterIds.isEmpty() ? Map.of()
                : matterRepository.findAllById(matterIds).stream()
                        .collect(Collectors.toMap(Matter::getId, Function.identity()));

        Set<UUID> ccIds = events.getContent().stream()
                .map(MatterTimelineEvent::getCourtCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> refs = ccIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(ccIds).stream()
                        .collect(Collectors.toMap(CourtCase::getId, CourtCase::getOurCourtCaseRef));

        return events.map(e -> {
            Matter m = matters.get(e.getMatterId());
            return TimelineEventResponse.builder()
                    .id(e.getId())
                    .matterId(e.getMatterId())
                    .matterNumber(m != null ? m.getMatterNumber() : null)
                    .matterTitle(m != null ? m.getTitle() : null)
                    .courtCaseId(e.getCourtCaseId())
                    .ourCourtCaseRef(e.getCourtCaseId() != null ? refs.get(e.getCourtCaseId()) : null)
                    .eventType(e.getEventType())
                    .title(e.getTitle())
                    .description(e.getDescription())
                    .createdAt(e.getCreatedAt())
                    .createdBy(e.getCreatedBy())
                    .build();
        });
    }

    /** Long-pending matters: current leaf hasn't had a real Peshi in N days. */
    public Page<StaleMatterResponse> getStaleMatters(int days, int page, int size) {
        UUID firmId = getRequiredFirmId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Matter> matters = matterRepository.findByFirmId(firmId, pageable);

        List<UUID> leafIds = matters.getContent().stream()
                .map(Matter::getCurrentCourtCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<UUID, LocalDate> latestPeshi = leafIds.isEmpty() ? Map.of()
                : courtEventRepository.findLatestPeshiByCourtCaseIds(leafIds).stream()
                        .collect(Collectors.toMap(
                                row -> (UUID) row[0],
                                row -> (LocalDate) row[1]));

        Map<UUID, String> refs = leafIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(leafIds).stream()
                        .collect(Collectors.toMap(CourtCase::getId, CourtCase::getOurCourtCaseRef));

        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<StaleMatterResponse> stale = matters.getContent().stream()
                .map(m -> {
                    UUID leafId = m.getCurrentCourtCaseId();
                    LocalDate last = leafId != null ? latestPeshi.get(leafId) : null;
                    long since = last == null ? Long.MAX_VALUE
                            : java.time.temporal.ChronoUnit.DAYS.between(last, LocalDate.now());
                    if (last != null && last.isAfter(cutoff)) return null; // healthy — not stale

                    return StaleMatterResponse.builder()
                            .id(m.getId())
                            .matterNumber(m.getMatterNumber())
                            .title(m.getTitle())
                            .matterType(m.getMatterType())
                            .status(m.getStatus())
                            .currentCourtCaseRef(leafId != null ? refs.get(leafId) : null)
                            .daysSinceLastPeshi(last == null ? null : (int) since)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(stale, pageable, stale.size());
    }

    @Transactional
    public MatterResponse updateMatter(String matterNumber, UpdateMatterRequest request) {
        Matter matter = findMatter(matterNumber);

        if (request.getTitle() != null) matter.setTitle(request.getTitle());
        if (request.getDescription() != null) matter.setDescription(request.getDescription());
        if (request.getAssignedPartnerId() != null) matter.setAssignedPartnerId(request.getAssignedPartnerId());
        if (request.getStatus() != null) matter.setStatus(request.getStatus());

        matter = matterRepository.save(matter);
        recordTimeline(matter, null, TimelineEventType.MATTER_NOTE_ADDED, "Matter details updated", null);
        auditService.log(AuditAction.MATTER_UPDATED, AuditEntity.MATTER, matter.getId(),
                "Matter updated: " + matterNumber);

        return toMatterResponse(matter, true);
    }

    @Transactional
    public MatterResponse addCourtCase(String matterNumber, AddCourtCaseRequest request) {
        UUID firmId = getRequiredFirmId();
        Matter matter = findMatter(matterNumber);
        RelationType relation = request.getRelationType();

        if (relation == RelationType.WRIT && request.getCourtLevel() == CourtLevel.DISTRICT) {
            throw new BusinessRuleException("Writ petitions cannot be filed at District Court level");
        }

        CourtCase parent = null;
        if (relation != RelationType.WRIT) {
            UUID parentId = request.getParentCourtCaseId() != null
                    ? request.getParentCourtCaseId() : matter.getCurrentCourtCaseId();
            if (parentId == null) {
                throw new BusinessRuleException("No parent court case to appeal from");
            }
            parent = courtCaseRepository.findByIdAndFirmId(parentId, firmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent court case not found: " + parentId));
            // REVIEW is the one relation exempt from the closed-parent guard: a review
            // petition (Supreme Court) is filed AFTER the judgment has gone final — the
            // parent is CLOSED by design, and a closed parent is exactly where review lives.
            boolean parentClosed = parent.getStatus() == CourtCaseStatus.CLOSED
                    || parent.getStage() == CourtCaseStage.CLOSED;
            if (parentClosed && relation != RelationType.REVIEW) {
                throw new BusinessRuleException("Cannot attach a new proceeding to a closed court case");
            }
        }

        CourtCase cc = new CourtCase();
        cc.setFirmId(firmId);
        cc.setMatterId(matter.getId());
        cc.setParentCourtCaseId(parent != null ? parent.getId() : null);
        cc.setRelationType(relation);
        cc.setCourtLevel(request.getCourtLevel());
        cc.setCourtName(request.getCourtName());
        cc.setCourtCaseNumber(request.getCourtCaseNumber());
        cc.setOurCourtCaseRef(courtCaseRefGenerator.generate(
                matter.getMatterNumber(), matter.getId(), request.getCourtLevel()));
        cc.setFilingDate(request.getFilingDate() != null ? request.getFilingDate() : LocalDate.now());
        cc.setStage(CourtCaseStage.initialFor(request.getCourtLevel(), matter.getMatterType(), relation));
        cc.setStatus(CourtCaseStatus.ACTIVE);
        cc.setAdvocateId(request.getAdvocateId());
        cc.setJudgeName(request.getJudgeName());
        cc.setPartyIsState(request.isPartyIsState());
        cc.setActive(true);
        cc = courtCaseRepository.save(cc);

        // Roles for the new instance — created fresh (roles flip on appeal)
        UUID matterId = matter.getId();
        for (PartyRoleRequest roleReq : request.getRoles()) {
            MatterParty party;
            if (roleReq.getMatterPartyId() != null) {
                party = matterPartyRepository.findByIdAndFirmId(roleReq.getMatterPartyId(), firmId)
                        .filter(p -> p.getMatterId().equals(matterId))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Matter party not found: " + roleReq.getMatterPartyId()));
            } else {
                PartyEntryRequest entry = new PartyEntryRequest();
                entry.setFullName(roleReq.getFullName());
                entry.setMobileNo(roleReq.getMobileNo());
                entry.setEmail(roleReq.getEmail());
                entry.setClientId(roleReq.getClientId());
                party = matterPartyRepository.save(buildParty(matter, entry));
            }
            createRole(cc, party, roleReq.getRoleType(), roleReq.getRepresentation(), roleReq.getAdvocateId());
        }

        // Parent is superseded by the child; matter leaf moves to the new instance.
        // REVIEW does NOT touch the parent: it stays however it already is (CLOSED/final).
        if (parent != null && relation != RelationType.REVIEW) {
            if (relation == RelationType.REMAND) {
                parent.setStage(CourtCaseStage.REMANDED);
                parent.setStatus(CourtCaseStatus.REMANDED);
            } else {
                parent.setStage(CourtCaseStage.APPEALED);
                parent.setStatus(CourtCaseStatus.APPEALED);
            }
            parent.setActive(true);
            courtCaseRepository.save(parent);
        }
        matter.setCurrentCourtCaseId(cc.getId());
        matter.setStatus(MatterStatus.ACTIVE);
        matter = matterRepository.save(matter);

        boolean isAppeal = relation == RelationType.APPEAL
                || relation == RelationType.CROSS_APPEAL
                || relation == RelationType.REVISION
                || relation == RelationType.REVIEW;
        recordTimeline(matter, cc.getId(),
                isAppeal ? TimelineEventType.APPEAL_FILED : TimelineEventType.COURT_CASE_ADDED,
                (isAppeal ? "Appeal filed" : "New proceeding") + " at " + cc.getCourtName(),
                cc.getOurCourtCaseRef());
        auditService.log(isAppeal ? AuditAction.APPEAL_FILED : AuditAction.COURT_CASE_CREATED,
                AuditEntity.COURT_CASE, cc.getId(),
                (isAppeal ? "Appeal" : "Court case") + " created: " + cc.getOurCourtCaseRef()
                        + " (" + relation + ")");

        return toMatterResponse(matter, true);
    }

    @Transactional
    public MatterResponse addParty(String matterNumber, PartyEntryRequest request) {
        Matter matter = findMatter(matterNumber);
        MatterParty party = matterPartyRepository.save(buildParty(matter, request));

        // Also give the party a role on the current leaf — practical for later additions
        if (matter.getCurrentCourtCaseId() != null) {
            createRoleForParty(party, matter.getCurrentCourtCaseId(),
                    request.getRoleType(), request.getRepresentation(), request.getAdvocateId());
        }

        recordTimeline(matter, matter.getCurrentCourtCaseId(), TimelineEventType.PARTY_ADDED,
                "Party added: " + request.getFullName(), null);
        auditService.log(AuditAction.MATTER_UPDATED, AuditEntity.MATTER, matter.getId(),
                "Party added to matter " + matterNumber + ": " + request.getFullName());

        return toMatterResponse(matter, true);
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private Matter findMatter(String matterNumber) {
        return matterRepository.findByMatterNumberAndFirmId(matterNumber, getRequiredFirmId())
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found: " + matterNumber));
    }

    private MatterParty buildParty(Matter matter, PartyEntryRequest entry) {
        MatterParty p = new MatterParty();
        p.setFirmId(matter.getFirmId());
        p.setMatterId(matter.getId());
        p.setFullName(entry.getFullName());
        p.setMobileNo(entry.getMobileNo());
        p.setEmail(entry.getEmail());
        p.setAddress(entry.getAddress());
        p.setClientId(entry.getClientId());
        p.setOurClient(entry.isOurClient());
        p.setNotes(entry.getNotes());
        p.setActive(true);
        return p;
    }

    private void createRole(CourtCase cc, MatterParty party, PartyType roleType,
                            PartyRepresentation representation, UUID advocateId) {
        CourtCaseRole role = new CourtCaseRole();
        role.setFirmId(cc.getFirmId());
        role.setMatterPartyId(party.getId());
        role.setCourtCaseId(cc.getId());
        role.setRoleType(roleType);
        role.setRepresentation(representation != null ? representation : PartyRepresentation.REPRESENTED);
        role.setAdvocateId(advocateId);
        role.setActive(true);
        courtCaseRoleRepository.save(role);
    }

    private void createRoleForParty(MatterParty party, UUID courtCaseId, PartyType roleType,
                                    PartyRepresentation representation, UUID advocateId) {
        CourtCaseRole role = new CourtCaseRole();
        role.setFirmId(party.getFirmId());
        role.setMatterPartyId(party.getId());
        role.setCourtCaseId(courtCaseId);
        role.setRoleType(roleType);
        role.setRepresentation(representation != null ? representation : PartyRepresentation.REPRESENTED);
        role.setAdvocateId(advocateId);
        role.setActive(true);
        courtCaseRoleRepository.save(role);
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

    private MatterResponse toMatterResponse(Matter matter, boolean full) {
        List<CourtCaseResponse> courtCases = new ArrayList<>();
        List<MatterPartyResponse> parties = new ArrayList<>();

        if (full) {
            UUID firmId = matter.getFirmId();
            List<CourtCase> ccs = courtCaseRepository.findByMatterIdAndFirmIdOrderByCreatedAtAsc(
                    matter.getId(), firmId);

            // Batch: event counts + party names (no N+1)
            Map<UUID, Integer> eventCounts = ccs.isEmpty() ? Map.of()
                    : courtEventRepository.countByCourtCaseIds(
                            ccs.stream().map(CourtCase::getId).collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).intValue()));

            Map<UUID, MatterParty> partiesById = matterPartyRepository
                    .findByMatterIdAndFirmId(matter.getId(), firmId).stream()
                    .collect(Collectors.toMap(MatterParty::getId, Function.identity()));

            Map<UUID, List<CourtCaseRole>> rolesByCase = ccs.isEmpty() ? Map.of()
                    : courtCaseRoleRepository.findByCourtCaseIdInAndFirmId(
                            ccs.stream().map(CourtCase::getId).collect(Collectors.toList()), firmId)
                    .stream().collect(Collectors.groupingBy(CourtCaseRole::getCourtCaseId));

            courtCases = ccs.stream()
                    .map(cc -> toCourtCaseResponse(cc, matter,
                            rolesByCase.getOrDefault(cc.getId(), List.of()),
                            eventCounts.getOrDefault(cc.getId(), 0), partiesById))
                    .collect(Collectors.toList());

            parties = partiesById.values().stream()
                    .map(p -> toPartyResponse(p, matter))
                    .collect(Collectors.toList());
        }

        return MatterResponse.builder()
                .id(matter.getId())
                .matterNumber(matter.getMatterNumber())
                .matterType(matter.getMatterType())
                .title(matter.getTitle())
                .status(matter.getStatus())
                .currentCourtCaseId(matter.getCurrentCourtCaseId())
                .assignedPartnerId(matter.getAssignedPartnerId())
                .originatingCourtLevel(matter.getOriginatingCourtLevel())
                .description(matter.getDescription())
                .courtCases(courtCases)
                .parties(parties)
                .createdAt(matter.getCreatedAt())
                .updatedAt(matter.getUpdatedAt())
                .build();
    }

    private CourtCaseResponse toCourtCaseResponse(CourtCase cc, Matter matter,
                                                  List<CourtCaseRole> roles,
                                                  int eventCount,
                                                  Map<UUID, MatterParty> partiesById) {
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

    private MatterPartyResponse toPartyResponse(MatterParty p, Matter matter) {
        return MatterPartyResponse.builder()
                .id(p.getId())
                .matterId(matter.getId())
                .matterNumber(matter.getMatterNumber())
                .fullName(p.getFullName())
                .mobileNo(p.getMobileNo())
                .email(p.getEmail())
                .address(p.getAddress())
                .clientId(p.getClientId())
                .isOurClient(p.isOurClient())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
