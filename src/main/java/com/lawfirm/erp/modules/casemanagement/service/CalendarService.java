package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.CalendarEventResponse;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.Hearing;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final HearingRepository hearingRepository;
    private final CaseRepository caseRepository;

    public List<CalendarEventResponse> getCalendar(UUID firmId, LocalDate from, LocalDate to, UUID advocateId) {
        List<Hearing> hearings;
        if (advocateId != null) {
            hearings = hearingRepository.findByFirmIdAndAdvocateIdAndDateBetweenOrderByDateAscTimeAsc(
                    firmId, advocateId, from, to, org.springframework.data.domain.Pageable.unpaged())
                    .getContent();
        } else {
            hearings = hearingRepository.findByFirmIdAndDateBetweenOrderByDateAscTimeAsc(
                    firmId, from, to, org.springframework.data.domain.Pageable.unpaged())
                    .getContent();
        }
        return enrichWithCaseDetails(hearings);
    }

    public List<CalendarEventResponse> getTodayHearings(UUID firmId, UUID advocateId) {
        LocalDate today = LocalDate.now();
        List<Hearing> hearings;
        if (advocateId != null) {
            hearings = hearingRepository.findTodayHearingsByAdvocate(advocateId, today);
        } else {
            hearings = hearingRepository.findTodayHearings(firmId, today);
        }
        return enrichWithCaseDetails(hearings);
    }

    public List<CalendarEventResponse> getUpcomingHearings(UUID firmId, int days, UUID advocateId) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(days);
        List<Hearing> hearings;
        if (advocateId != null) {
            hearings = hearingRepository.findUpcomingHearingsByAdvocate(advocateId, from, to);
        } else {
            hearings = hearingRepository.findUpcomingHearings(firmId, from, to);
        }
        return enrichWithCaseDetails(hearings);
    }

    private List<CalendarEventResponse> enrichWithCaseDetails(List<Hearing> hearings) {
        if (hearings.isEmpty()) return List.of();

        Map<UUID, Case> caseMap = caseRepository.findAllById(
                hearings.stream().map(Hearing::getCaseId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Case::getId, Function.identity()));

        return hearings.stream().map(h -> {
            Case c = caseMap.get(h.getCaseId());
            return CalendarEventResponse.builder()
                    .id(h.getId())
                    .caseId(h.getCaseId())
                    .caseNumber(c != null ? c.getCaseNumber() : null)
                    .caseTitle(c != null ? c.getTitle() : null)
                    .title(h.getTitle())
                    .date(h.getDate())
                    .time(h.getTime())
                    .endTime(h.getEndTime())
                    .courtRoom(h.getCourtRoom())
                    .hearingType(h.getHearingType())
                    .status(h.getStatus())
                    .advocateId(h.getAdvocateId())
                    .build();
        }).collect(Collectors.toList());
    }
}
