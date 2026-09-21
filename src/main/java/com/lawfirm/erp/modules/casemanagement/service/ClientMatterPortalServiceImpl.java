package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.casemanagement.dto.response.ClientMatterResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "My cases" for the client portal.
 *
 * <p>Ownership comes from two sources so pre-existing data keeps working:
 * {@code matters.clientUserId} (the first-class link added with this feature) and a
 * {@code MatterParty} marked {@code isOurClient} with a {@code clientId}. Anything else in
 * the firm is invisible here, regardless of the client's role permissions.
 */
@Service
@RequiredArgsConstructor
public class ClientMatterPortalServiceImpl implements ClientMatterPortalService {

    private final MatterRepository matterRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final CourtEventRepository courtEventRepository;
    private final UserRepository userRepository;
    private final ReadScopeGuard readScopeGuard;

    @Override
    public List<ClientMatterResponse> listMyMatters() {
        UUID clientId = requirePortalAccess();
        UUID firmId = requireFirmId();

        List<Matter> matters = ownedMatters(clientId, firmId);
        if (matters.isEmpty()) {
            return List.of();
        }

        Map<UUID, CourtCase> currentCases = currentCases(matters, firmId);
        Map<UUID, LocalDateTime> nextHearings = nextHearings(currentCases.values(), firmId);

        return matters.stream()
                .map(m -> toResponse(m, currentCases.get(m.getCurrentCourtCaseId()), nextHearings))
                .collect(Collectors.toList());
    }

    @Override
    public ClientMatterResponse getMyMatter(String matterNumber) {
        UUID clientId = requirePortalAccess();
        UUID firmId = requireFirmId();

        Matter matter = ownedMatters(clientId, firmId).stream()
                .filter(m -> m.getMatterNumber().equals(matterNumber))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found: " + matterNumber));

        Map<UUID, CourtCase> currentCases = currentCases(List.of(matter), firmId);
        Map<UUID, LocalDateTime> nextHearings = nextHearings(currentCases.values(), firmId);
        return toResponse(matter, currentCases.get(matter.getCurrentCourtCaseId()), nextHearings);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Only an enabled client-portal account may use this surface. */
    private UUID requirePortalAccess() {
        UUID clientId = readScopeGuard.currentUserId();
        User user = clientId == null ? null : userRepository.findById(clientId).orElse(null);
        if (user == null || user.getUserType() != UserType.CLIENT
                || !Boolean.TRUE.equals(user.getPortalAccessEnabled())) {
            throw new ForbiddenException(
                    "Client portal access is disabled for your account. Please contact your firm.");
        }
        return clientId;
    }

    private UUID requireFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) {
            throw new ForbiddenException("Firm context required");
        }
        return firmId;
    }

    private List<Matter> ownedMatters(UUID clientId, UUID firmId) {
        Map<UUID, Matter> owned = new LinkedHashMap<>();
        matterRepository.findByClientUserIdAndFirmIdOrderByCreatedAtDesc(clientId, firmId)
                .forEach(m -> owned.put(m.getId(), m));
        // Backward compatibility: matters where the client was only recorded as a party
        for (MatterParty party : matterPartyRepository.findByClientIdAndFirmIdAndOurClientTrue(clientId, firmId)) {
            if (!owned.containsKey(party.getMatterId())) {
                matterRepository.findByIdAndFirmId(party.getMatterId(), firmId)
                        .ifPresent(m -> owned.put(m.getId(), m));
            }
        }
        return owned.values().stream()
                .filter(Matter::isActive)
                .sorted(Comparator.comparing(Matter::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private Map<UUID, CourtCase> currentCases(List<Matter> matters, UUID firmId) {
        Set<UUID> ids = matters.stream()
                .map(Matter::getCurrentCourtCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return courtCaseRepository.findAllById(ids).stream()
                .filter(cc -> cc.getFirmId().equals(firmId))
                .collect(Collectors.toMap(CourtCase::getId, Function.identity()));
    }

    /** Earliest still-scheduled hearing at or after today, per court case. */
    private Map<UUID, LocalDateTime> nextHearings(Collection<CourtCase> courtCases, UUID firmId) {
        List<UUID> caseIds = courtCases.stream().map(CourtCase::getId).collect(Collectors.toList());
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        Map<UUID, LocalDateTime> next = new HashMap<>();
        for (CourtEvent event : courtEventRepository
                .findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(caseIds, firmId)) {
            if (event.getStatus() != CourtEventStatus.SCHEDULED || event.getScheduledDate() == null
                    || event.getScheduledDate().isBefore(today)) {
                continue;
            }
            LocalTime time = event.getScheduledTime() != null ? event.getScheduledTime() : LocalTime.MIDNIGHT;
            LocalDateTime when = event.getScheduledDate().atTime(time);
            next.merge(event.getCourtCaseId(), when, (a, b) -> a.isBefore(b) ? a : b);
        }
        return next;
    }

    private ClientMatterResponse toResponse(Matter matter, CourtCase currentCase,
                                            Map<UUID, LocalDateTime> nextHearings) {
        return ClientMatterResponse.builder()
                .id(matter.getId())
                .matterNumber(matter.getMatterNumber())
                .title(matter.getTitle())
                .matterType(matter.getMatterType())
                .status(matter.getStatus())
                .originatingCourtLevel(matter.getOriginatingCourtLevel())
                .currentCourtCaseRef(currentCase != null ? currentCase.getOurCourtCaseRef() : null)
                .courtName(currentCase != null ? currentCase.getCourtName() : null)
                .stage(currentCase != null ? currentCase.getStage() : null)
                .nextHearingAt(currentCase != null ? nextHearings.get(currentCase.getId()) : null)
                .createdAt(matter.getCreatedAt())
                .updatedAt(matter.getUpdatedAt())
                .build();
    }
}
