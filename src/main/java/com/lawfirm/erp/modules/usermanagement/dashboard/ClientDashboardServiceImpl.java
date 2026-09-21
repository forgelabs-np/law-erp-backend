package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import com.lawfirm.erp.modules.invoice.repository.InvoiceRepository;
import com.lawfirm.erp.modules.usermanagement.dto.response.ClientDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDashboardServiceImpl implements ClientDashboardService {

    private final MatterRepository matterRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final CourtCaseRepository courtCaseRepository;
    private final CourtEventRepository courtEventRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    public ClientDashboardResponse getDashboard(DashboardScope scope) {
        UUID firmId = getRequiredFirmId(scope);
        UUID userId = scope.getUserId();

        // Client sees only matters where they are linked as a client via MatterParty
        List<UUID> clientMatterIds = matterPartyRepository.findByFirmIdAndClientId(firmId, userId)
                .stream().map(MatterParty::getMatterId).collect(java.util.stream.Collectors.toList());
        List<Matter> matters = clientMatterIds.isEmpty() ? List.of()
                : matterRepository.findAllById(clientMatterIds).stream()
                .filter(m -> m.getFirmId().equals(firmId))
                .collect(java.util.stream.Collectors.toList());

        List<Matter> activeMatters = matters.stream()
                .filter(m -> m.getStatus() == MatterStatus.ACTIVE)
                .collect(java.util.stream.Collectors.toList());
        List<Matter> closedMatters = matters.stream()
                .filter(m -> m.getStatus() == MatterStatus.CLOSED)
                .collect(java.util.stream.Collectors.toList());

        // Get leaf court cases for client's matters
        List<UUID> leafIds = matters.stream().map(Matter::getCurrentCourtCaseId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toList());
        Map<UUID, Matter> matterMap = matters.stream().collect(Collectors.toMap(Matter::getId, m -> m));
        Map<UUID, CourtCase> ccMap = leafIds.isEmpty() ? Map.of()
                : courtCaseRepository.findAllById(leafIds).stream()
                .collect(Collectors.toMap(CourtCase::getId, c -> c));

        // Get events for client's court cases
        Map<UUID, List<CourtEvent>> eventsByCC = leafIds.isEmpty() ? Map.of()
                : courtEventRepository.findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(leafIds, firmId)
                .stream().collect(Collectors.groupingBy(CourtEvent::getCourtCaseId));

        LocalDate today = LocalDate.now();

        // Client's matters list
        List<ClientDashboardResponse.MyMatter> myMatters = buildMyMatters(matters, ccMap, eventsByCC, today);

        // Next hearing across all matters
        ClientDashboardResponse.MyNextHearing nextHearing = findNextHearing(eventsByCC, ccMap, matterMap, today);

        // Upcoming events (next 30 days)
        List<ClientDashboardResponse.MyUpcomingEvent> upcomingEvents = buildUpcomingEvents(
                eventsByCC, ccMap, matterMap, today, today.plusDays(30));

        // Client's invoices — Invoice has no clientId field, so we show all firm invoices
        // TODO: filter by client once Invoice gets a clientId FK
        List<Invoice> clientInvoices = invoiceRepository.findByFirmId(firmId, PageRequest.of(0, 100)).getContent();
        ClientDashboardResponse.MyInvoiceStats invoiceStats = buildInvoiceStats(clientInvoices);

        // Outstanding invoices
        List<ClientDashboardResponse.MyOutstandingInvoice> outstanding = buildOutstandingInvoices(clientInvoices, today);

        return ClientDashboardResponse.builder()
                .myMatterStats(ClientDashboardResponse.MyMatterStats.builder()
                        .totalMatters(matters.size())
                        .activeMatters(activeMatters.size())
                        .closedMatters(closedMatters.size())
                        .build())
                .myMatters(myMatters)
                .myNextHearing(nextHearing)
                .myUpcomingEvents(upcomingEvents)
                .myInvoiceStats(invoiceStats)
                .myOutstandingInvoices(outstanding)
                .myRecentUpdates(List.of())  // TODO: add audit log filtering
                .build();
    }

    private List<ClientDashboardResponse.MyMatter> buildMyMatters(
            List<Matter> matters, Map<UUID, CourtCase> ccMap,
            Map<UUID, List<CourtEvent>> eventsByCC, LocalDate today) {

        return matters.stream().map(m -> {
            CourtCase cc = m.getCurrentCourtCaseId() != null ? ccMap.get(m.getCurrentCourtCaseId()) : null;

            // Find last event date
            LocalDate lastUpdate = null;
            if (m.getCurrentCourtCaseId() != null) {
                List<CourtEvent> events = eventsByCC.getOrDefault(m.getCurrentCourtCaseId(), List.of());
                lastUpdate = events.stream()
                        .map(CourtEvent::getScheduledDate)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
            }

            // Find next hearing
            LocalDate nextHearingDate = null;
            if (m.getCurrentCourtCaseId() != null) {
                List<CourtEvent> events = eventsByCC.getOrDefault(m.getCurrentCourtCaseId(), List.of());
                nextHearingDate = events.stream()
                        .filter(e -> e.getStatus() == CourtEventStatus.SCHEDULED)
                        .map(CourtEvent::getScheduledDate)
                        .filter(d -> d != null && !d.isBefore(today))
                        .min(Comparator.naturalOrder())
                        .orElse(null);
            }

            return ClientDashboardResponse.MyMatter.builder()
                    .matterNumber(m.getMatterNumber())
                    .title(m.getTitle())
                    .status(m.getStatus().name())
                    .courtName(cc != null ? cc.getCourtName() : null)
                    .ourCourtCaseRef(cc != null ? cc.getOurCourtCaseRef() : null)
                    .lastUpdate(lastUpdate)
                    .nextHearingDate(nextHearingDate)
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }

    private ClientDashboardResponse.MyNextHearing findNextHearing(
            Map<UUID, List<CourtEvent>> eventsByCC, Map<UUID, CourtCase> ccMap,
            Map<UUID, Matter> matterMap, LocalDate today) {

        LocalDate nearestDate = null;
        CourtEvent nearestEvent = null;
        CourtCase nearestCC = null;

        for (var entry : eventsByCC.entrySet()) {
            CourtCase cc = ccMap.get(entry.getKey());
            for (CourtEvent e : entry.getValue()) {
                if (e.getStatus() == CourtEventStatus.SCHEDULED
                        && e.getScheduledDate() != null
                        && !e.getScheduledDate().isBefore(today)) {
                    if (nearestDate == null || e.getScheduledDate().isBefore(nearestDate)) {
                        nearestDate = e.getScheduledDate();
                        nearestEvent = e;
                        nearestCC = cc;
                    }
                }
            }
        }

        if (nearestEvent == null || nearestCC == null) return null;

        Matter m = matterMap.get(nearestCC.getMatterId());
        int daysUntil = (int) ChronoUnit.DAYS.between(today, nearestDate);
        return ClientDashboardResponse.MyNextHearing.builder()
                .matterTitle(m != null ? m.getTitle() : null)
                .ourCourtCaseRef(nearestCC.getOurCourtCaseRef())
                .hearingDate(nearestDate)
                .courtName(nearestCC.getCourtName())
                .daysUntil(daysUntil)
                .build();
    }

    private List<ClientDashboardResponse.MyUpcomingEvent> buildUpcomingEvents(
            Map<UUID, List<CourtEvent>> eventsByCC, Map<UUID, CourtCase> ccMap,
            Map<UUID, Matter> matterMap, LocalDate from, LocalDate to) {

        List<ClientDashboardResponse.MyUpcomingEvent> events = new ArrayList<>();

        for (var entry : eventsByCC.entrySet()) {
            CourtCase cc = ccMap.get(entry.getKey());
            Matter m = cc != null ? matterMap.get(cc.getMatterId()) : null;

            for (CourtEvent e : entry.getValue()) {
                if (e.getStatus() == CourtEventStatus.SCHEDULED
                        && e.getScheduledDate() != null
                        && !e.getScheduledDate().isBefore(from)
                        && !e.getScheduledDate().isAfter(to)) {
                    events.add(ClientDashboardResponse.MyUpcomingEvent.builder()
                            .matterTitle(m != null ? m.getTitle() : null)
                            .ourCourtCaseRef(cc != null ? cc.getOurCourtCaseRef() : null)
                            .eventDate(e.getScheduledDate())
                            .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                            .courtRoom(e.getCourtRoom())
                            .build());
                }
            }
        }

        return events.stream()
                .sorted(Comparator.comparing(ClientDashboardResponse.MyUpcomingEvent::getEventDate))
                .collect(java.util.stream.Collectors.toList());
    }

    private ClientDashboardResponse.MyInvoiceStats buildInvoiceStats(List<Invoice> invoices) {
        long total = invoices.size();
        List<Invoice> outstanding = invoices.stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.SENT || inv.getStatus() == InvoiceStatus.OVERDUE)
                .collect(java.util.stream.Collectors.toList());
        BigDecimal totalOutstanding = outstanding.stream()
                .map(Invoice::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ClientDashboardResponse.MyInvoiceStats.builder()
                .totalInvoices(total)
                .outstanding(outstanding.size())
                .outstandingAmount(totalOutstanding)
                .build();
    }

    private List<ClientDashboardResponse.MyOutstandingInvoice> buildOutstandingInvoices(
            List<Invoice> invoices, LocalDate today) {

        return invoices.stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.SENT || inv.getStatus() == InvoiceStatus.OVERDUE)
                .map(inv -> {
                    int daysUntil = inv.getDueDate() != null
                            ? (int) ChronoUnit.DAYS.between(today, inv.getDueDate()) : 0;
                    return ClientDashboardResponse.MyOutstandingInvoice.builder()
                            .invoiceNumber(inv.getInvoiceNumber())
                            .amount(inv.getTotal())
                            .dueDate(inv.getDueDate())
                            .daysUntil(daysUntil)
                            .build();
                })
                .sorted(Comparator.comparing(ClientDashboardResponse.MyOutstandingInvoice::getDueDate))
                .collect(java.util.stream.Collectors.toList());
    }

    private UUID getRequiredFirmId(DashboardScope scope) {
        if (scope.getFirmId() == null) throw new ForbiddenException("Firm context required");
        return scope.getFirmId();
    }
}
