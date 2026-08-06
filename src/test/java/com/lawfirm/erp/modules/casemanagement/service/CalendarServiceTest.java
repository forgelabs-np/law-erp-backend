package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.CalendarEventResponse;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.Hearing;
import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
import com.lawfirm.erp.modules.casemanagement.enums.HearingType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID ADVOCATE_ID = UUID.randomUUID();
    private static final UUID HEARING_ID = UUID.randomUUID();

    @Mock private HearingRepository hearingRepository;
    @Mock private CaseRepository caseRepository;

    private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService(hearingRepository, caseRepository);
    }

    private Hearing createHearing(LocalDate date, LocalTime time) {
        Hearing h = new Hearing();
        h.setId(HEARING_ID);
        h.setFirmId(FIRM_ID);
        h.setCaseId(CASE_ID);
        h.setTitle("Test Hearing");
        h.setDate(date);
        h.setTime(time);
        h.setHearingType(HearingType.FIRST_HEARING);
        h.setStatus(HearingStatus.SCHEDULED);
        return h;
    }

    private Case createCase() {
        Case c = new Case();
        c.setId(CASE_ID);
        c.setCaseNumber("APX-CIV-2026-00001");
        c.setTitle("Test Case");
        return c;
    }

    @Test
    void testGetCalendarEnrichesWithCaseDetails() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(7);
        Hearing h = createHearing(from.plusDays(1), LocalTime.of(10, 0));
        Page<Hearing> page = new PageImpl<>(List.of(h));

        when(hearingRepository.findByFirmIdAndDateBetweenOrderByDateAscTimeAsc(
                FIRM_ID, from, to, Pageable.unpaged()))
                .thenReturn(page);
        when(caseRepository.findAllById(Set.of(CASE_ID)))
                .thenReturn(List.of(createCase()));

        List<CalendarEventResponse> result = calendarService.getCalendar(FIRM_ID, from, to, null);

        assertEquals(1, result.size());
        assertEquals("APX-CIV-2026-00001", result.get(0).getCaseNumber());
        assertEquals("Test Case", result.get(0).getCaseTitle());
    }

    @Test
    void testGetCalendarFiltersByAdvocate() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(7);
        Hearing h = createHearing(from.plusDays(1), LocalTime.of(10, 0));
        Page<Hearing> page = new PageImpl<>(List.of(h));

        when(hearingRepository.findByFirmIdAndAdvocateIdAndDateBetweenOrderByDateAscTimeAsc(
                FIRM_ID, ADVOCATE_ID, from, to, Pageable.unpaged()))
                .thenReturn(page);
        when(caseRepository.findAllById(Set.of(CASE_ID)))
                .thenReturn(List.of(createCase()));

        List<CalendarEventResponse> result = calendarService.getCalendar(FIRM_ID, from, to, ADVOCATE_ID);

        assertEquals(1, result.size());
    }

    @Test
    void testGetTodayHearings() {
        LocalDate today = LocalDate.now();
        Hearing h = createHearing(today, LocalTime.of(10, 0));

        when(hearingRepository.findTodayHearings(FIRM_ID, today))
                .thenReturn(List.of(h));
        when(caseRepository.findAllById(Set.of(CASE_ID)))
                .thenReturn(List.of(createCase()));

        List<CalendarEventResponse> result = calendarService.getTodayHearings(FIRM_ID, null);

        assertEquals(1, result.size());
    }

    @Test
    void testGetUpcomingHearings() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(7);
        Hearing h = createHearing(from.plusDays(1), LocalTime.of(10, 0));

        when(hearingRepository.findUpcomingHearings(FIRM_ID, from, to))
                .thenReturn(List.of(h));
        when(caseRepository.findAllById(Set.of(CASE_ID)))
                .thenReturn(List.of(createCase()));

        List<CalendarEventResponse> result = calendarService.getUpcomingHearings(FIRM_ID, 7, null);

        assertEquals(1, result.size());
    }

    @Test
    void testGetUpcomingHearingsByAdvocate() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(7);
        Hearing h = createHearing(from.plusDays(1), LocalTime.of(10, 0));

        when(hearingRepository.findUpcomingHearingsByAdvocate(ADVOCATE_ID, from, to))
                .thenReturn(List.of(h));
        when(caseRepository.findAllById(Set.of(CASE_ID)))
                .thenReturn(List.of(createCase()));

        List<CalendarEventResponse> result = calendarService.getUpcomingHearings(FIRM_ID, 7, ADVOCATE_ID);

        assertEquals(1, result.size());
    }

    @Test
    void testGetTodayHearingsByAdvocate() {
        LocalDate today = LocalDate.now();
        Hearing h = createHearing(today, LocalTime.of(10, 0));

        when(hearingRepository.findTodayHearingsByAdvocate(ADVOCATE_ID, today))
                .thenReturn(List.of(h));
        when(caseRepository.findAllById(Set.of(CASE_ID)))
                .thenReturn(List.of(createCase()));

        List<CalendarEventResponse> result = calendarService.getTodayHearings(FIRM_ID, ADVOCATE_ID);

        assertEquals(1, result.size());
    }
}
