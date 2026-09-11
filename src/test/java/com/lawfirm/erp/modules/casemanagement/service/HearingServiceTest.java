package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.casemanagement.dto.request.CreateHearingRequest;
import com.lawfirm.erp.modules.casemanagement.dto.request.UpdateHearingRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.HearingResponse;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.Hearing;
import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
import com.lawfirm.erp.modules.casemanagement.enums.HearingType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseTimelineRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.repository.HearingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HearingServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID HEARING_ID = UUID.randomUUID();
    private static final String CASE_NUMBER = "APX-CIV-2026-00001";

    @Mock private HearingRepository hearingRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private CaseTimelineRepository caseTimelineRepository;
    @Mock private AuditService auditService;

    private HearingService hearingService;

    @BeforeEach
    void setUp() {
        hearingService = new HearingService(hearingRepository, caseRepository, caseTimelineRepository, auditService);
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private void setFirmContext() {
        FirmContextHolder.set(FIRM_ID, null);
    }

    private Case createCase() {
        Case c = new Case();
        c.setId(CASE_ID);
        c.setFirmId(FIRM_ID);
        c.setCaseNumber(CASE_NUMBER);
        return c;
    }

    @Nested
    class ScheduleHearing {
        @Test
        void shouldScheduleSuccessfully() {
            setFirmContext();
            Case c = createCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(hearingRepository.save(any())).thenAnswer(i -> {
                Hearing h = i.getArgument(0);
                h.setId(HEARING_ID);
                return h;
            });

            CreateHearingRequest request = new CreateHearingRequest();
            request.setTitle("First Hearing");
            request.setDate(LocalDate.now().plusDays(7));
            request.setHearingType(HearingType.FIRST_HEARING);

            HearingResponse response = hearingService.scheduleHearing(CASE_NUMBER, request);

            assertNotNull(response);
            assertEquals("First Hearing", response.getTitle());
            assertEquals(HearingStatus.SCHEDULED, response.getStatus());
            verify(caseTimelineRepository).save(any());
        }

        @Test
        void shouldDetectConflict() {
            setFirmContext();
            Case c = createCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            UUID advocateId = UUID.randomUUID();
            when(hearingRepository.findConflicts(eq(advocateId), any(), any(), any(), isNull()))
                    .thenReturn(List.of(new Hearing()));

            CreateHearingRequest request = new CreateHearingRequest();
            request.setTitle("First Hearing");
            request.setDate(LocalDate.now().plusDays(7));
            request.setTime(LocalTime.of(10, 0));
            request.setEndTime(LocalTime.of(11, 0));
            request.setHearingType(HearingType.FIRST_HEARING);
            request.setAdvocateId(advocateId);

            assertThrows(BusinessRuleException.class,
                    () -> hearingService.scheduleHearing(CASE_NUMBER, request));
        }

        @Test
        void shouldThrowWhenCaseNotFound() {
            setFirmContext();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> hearingService.scheduleHearing(CASE_NUMBER, new CreateHearingRequest()));
        }
    }

    @Nested
    class GetCaseHearings {
        @Test
        void shouldReturnAllHearingsForCase() {
            setFirmContext();
            Case c = createCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            Hearing h = new Hearing();
            h.setTitle("First Hearing");
            h.setStatus(HearingStatus.SCHEDULED);
            when(hearingRepository.findByCaseIdOrderByDateDesc(CASE_ID))
                    .thenReturn(List.of(h));

            List<HearingResponse> hearings = hearingService.getCaseHearings(CASE_NUMBER);

            assertEquals(1, hearings.size());
            assertEquals("First Hearing", hearings.get(0).getTitle());
        }
    }

    @Nested
    class GetHearing {
        @Test
        void shouldReturnHearingWhenFound() {
            setFirmContext();
            Hearing h = new Hearing();
            h.setId(HEARING_ID);
            h.setFirmId(FIRM_ID);
            h.setTitle("Test Hearing");
            h.setStatus(HearingStatus.SCHEDULED);
            when(hearingRepository.findByIdAndFirmId(HEARING_ID, FIRM_ID))
                    .thenReturn(Optional.of(h));

            HearingResponse response = hearingService.getHearing(HEARING_ID);

            assertNotNull(response);
            assertEquals("Test Hearing", response.getTitle());
        }

        @Test
        void shouldThrowWhenNotFound() {
            setFirmContext();
            when(hearingRepository.findByIdAndFirmId(HEARING_ID, FIRM_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> hearingService.getHearing(HEARING_ID));
        }
    }

    @Nested
    class UpdateHearing {
        @Test
        void shouldUpdateFields() {
            setFirmContext();
            Hearing h = new Hearing();
            h.setId(HEARING_ID);
            h.setFirmId(FIRM_ID);
            h.setCaseId(CASE_ID);
            h.setTitle("Old Title");
            h.setStatus(HearingStatus.SCHEDULED);
            when(hearingRepository.findByIdAndFirmId(HEARING_ID, FIRM_ID))
                    .thenReturn(Optional.of(h));
            when(hearingRepository.save(any())).thenReturn(h);

            UpdateHearingRequest request = new UpdateHearingRequest();
            request.setTitle("Updated Title");

            HearingResponse response = hearingService.updateHearing(HEARING_ID, request);

            assertEquals("Updated Title", response.getTitle());
        }

        @Test
        void shouldRecordTimelineOnStatusChange() {
            setFirmContext();
            Hearing h = new Hearing();
            h.setId(HEARING_ID);
            h.setFirmId(FIRM_ID);
            h.setCaseId(CASE_ID);
            h.setTitle("Test Hearing");
            h.setStatus(HearingStatus.SCHEDULED);
            when(hearingRepository.findByIdAndFirmId(HEARING_ID, FIRM_ID))
                    .thenReturn(Optional.of(h));

            Case c = createCase();
            when(caseRepository.findByIdAndFirmId(CASE_ID, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(hearingRepository.save(any())).thenReturn(h);

            UpdateHearingRequest request = new UpdateHearingRequest();
            request.setStatus(HearingStatus.HELD);
            request.setOutcome("Case adjourned");

            hearingService.updateHearing(HEARING_ID, request);

            verify(caseTimelineRepository, times(1)).save(any());
        }
    }

    @Nested
    class CancelHearing {
        @Test
        void shouldCancelHearing() {
            setFirmContext();
            Hearing h = new Hearing();
            h.setId(HEARING_ID);
            h.setFirmId(FIRM_ID);
            h.setStatus(HearingStatus.SCHEDULED);
            when(hearingRepository.findByIdAndFirmId(HEARING_ID, FIRM_ID))
                    .thenReturn(Optional.of(h));
            when(hearingRepository.save(any())).thenReturn(h);

            hearingService.cancelHearing(HEARING_ID);

            assertEquals(HearingStatus.CANCELED, h.getStatus());
            verify(hearingRepository).save(h);
        }
    }
}
