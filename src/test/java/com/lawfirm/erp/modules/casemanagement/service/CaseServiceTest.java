package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.casemanagement.dto.request.*;
import com.lawfirm.erp.modules.casemanagement.dto.response.CaseResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.TimelineEventResponse;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.CaseParty;
import com.lawfirm.erp.modules.casemanagement.entity.CaseTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.entity.Hearing;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.CasePartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseTimelineRepository;
import com.lawfirm.erp.modules.casemanagement.repository.HearingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final String FIRM_CODE = "APX";
    private static final String CASE_NUMBER = "APX-CIV-2026-00001";
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID PARTY_ID = UUID.randomUUID();

    @Mock private CaseRepository caseRepository;
    @Mock private CasePartyRepository casePartyRepository;
    @Mock private CaseTimelineRepository caseTimelineRepository;
    @Mock private HearingRepository hearingRepository;
    @Mock private CaseNumberGenerator caseNumberGenerator;
    @Mock private AuditService auditService;

    private CaseService caseService;

    @BeforeEach
    void setUp() {
        caseService = new CaseService(caseRepository, casePartyRepository,
                caseTimelineRepository, hearingRepository, caseNumberGenerator, auditService);
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private Case createCivilCase() {
        Case c = new Case();
        c.setId(CASE_ID);
        c.setFirmId(FIRM_ID);
        c.setCaseNumber(CASE_NUMBER);
        c.setCaseType(CaseType.CIVIL);
        c.setTitle("Test Civil Case");
        c.setCaseStage(CaseStage.FILED);
        c.setStatus(CaseStatus.ACTIVE);
        c.setFilingDate(LocalDate.now());
        return c;
    }

    private void setFirmContext() {
        FirmContextHolder.set(FIRM_ID, FIRM_CODE);
    }

    @Nested
    class CreateCase {
        @Test
        void shouldCreateCivilCaseWithParties() {
            setFirmContext();
            when(caseNumberGenerator.generate(FIRM_CODE, CaseType.CIVIL)).thenReturn(CASE_NUMBER);
            when(caseRepository.save(any())).thenAnswer(i -> {
                Case c = i.getArgument(0);
                c.setId(CASE_ID);
                return c;
            });
            when(casePartyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            CreateCaseRequest request = new CreateCaseRequest();
            request.setCaseType(CaseType.CIVIL);
            request.setTitle("Test Civil Case");
            CreateCaseRequest.PartyEntry plaintiff = new CreateCaseRequest.PartyEntry();
            plaintiff.setFullName("John Doe");
            request.setPlaintiffs(List.of(plaintiff));

            CaseResponse response = caseService.createCase(request);

            assertNotNull(response);
            assertEquals(CASE_NUMBER, response.getCaseNumber());
            assertEquals(CaseStage.FILED, response.getCaseStage());
            verify(caseTimelineRepository).save(any());
        }

        @Test
        void shouldThrowWhenNoFirmContext() {
            assertThrows(ForbiddenException.class,
                    () -> caseService.createCase(new CreateCaseRequest()));
        }
    }

    @Nested
    class GetCase {
        @Test
        void shouldReturnCaseWhenFound() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(casePartyRepository.findByCaseIdAndFirmId(CASE_ID, FIRM_ID))
                    .thenReturn(List.of());

            CaseResponse response = caseService.getCase(CASE_NUMBER);

            assertNotNull(response);
            assertEquals(CASE_NUMBER, response.getCaseNumber());
        }

        @Test
        void shouldThrowWhenNotFound() {
            setFirmContext();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> caseService.getCase(CASE_NUMBER));
        }
    }

    @Nested
    class ListCases {
        @Test
        void shouldReturnPagedResults() {
            setFirmContext();
            Case c = createCivilCase();
            Page<Case> page = new PageImpl<>(List.of(c));
            when(caseRepository.findByFirmId(eq(FIRM_ID), any(PageRequest.class)))
                    .thenReturn(page);

            Page<CaseResponse> result = caseService.listCases(
                    null, null, null, null, null, null, null, null, 0, 20);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        void shouldApplyFiltersWhenProvided() {
            setFirmContext();
            Case c = createCivilCase();
            Page<Case> page = new PageImpl<>(List.of(c));
            when(caseRepository.findByFilters(any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any())).thenReturn(page);

            Page<CaseResponse> result = caseService.listCases(
                    CaseType.CIVIL, CaseStage.FILED, CaseStatus.ACTIVE,
                    null, null, null, null, null, 0, 20);

            assertEquals(1, result.getTotalElements());
            verify(caseRepository).findByFilters(any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any());
        }
    }

    @Nested
    class UpdateCase {
        @Test
        void shouldUpdateAllowedFields() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(caseRepository.save(any())).thenReturn(c);
            when(casePartyRepository.findByCaseIdAndFirmId(CASE_ID, FIRM_ID))
                    .thenReturn(List.of());

            UpdateCaseRequest request = new UpdateCaseRequest();
            request.setTitle("Updated Title");

            CaseResponse response = caseService.updateCase(CASE_NUMBER, request);

            assertEquals("Updated Title", response.getTitle());
        }
    }

    @Nested
    class DeleteCase {
        @Test
        void shouldDeleteWhenNoActiveHearings() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(hearingRepository.findByCaseIdOrderByDateDesc(CASE_ID))
                    .thenReturn(List.of());
            when(caseTimelineRepository.findByCaseIdOrderByCreatedAtDesc(CASE_ID))
                    .thenReturn(List.of());
            doNothing().when(casePartyRepository).deleteByCaseIdAndFirmId(CASE_ID, FIRM_ID);

            caseService.deleteCase(CASE_NUMBER);

            verify(caseRepository).delete(c);
        }

        @Test
        void shouldThrowWhenActiveHearingsExist() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            Hearing h = new Hearing();
            h.setStatus(HearingStatus.SCHEDULED);
            when(hearingRepository.findByCaseIdOrderByDateDesc(CASE_ID))
                    .thenReturn(List.of(h));

            assertThrows(BusinessRuleException.class,
                    () -> caseService.deleteCase(CASE_NUMBER));
        }
    }

    @Nested
    class UpdateStage {
        @Test
        void shouldTransitionToValidStage() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(caseRepository.save(any())).thenReturn(c);
            when(casePartyRepository.findByCaseIdAndFirmId(CASE_ID, FIRM_ID))
                    .thenReturn(List.of());

            UpdateCaseStageRequest request = new UpdateCaseStageRequest();
            request.setStage(CaseStage.UNDER_SUMMONS);

            CaseResponse response = caseService.updateStage(CASE_NUMBER, request);

            assertEquals(CaseStage.UNDER_SUMMONS, response.getCaseStage());
        }

        @Test
        void shouldThrowWhenInvalidTransition() {
            setFirmContext();
            Case c = createCivilCase();
            c.setCaseStage(CaseStage.FILED);
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            UpdateCaseStageRequest request = new UpdateCaseStageRequest();
            request.setStage(CaseStage.TRIAL);

            assertThrows(BusinessRuleException.class,
                    () -> caseService.updateStage(CASE_NUMBER, request));
        }

        @Test
        void shouldThrowWhenCaseClosed() {
            setFirmContext();
            Case c = createCivilCase();
            c.setStatus(CaseStatus.CLOSED);
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            UpdateCaseStageRequest request = new UpdateCaseStageRequest();
            request.setStage(CaseStage.FILED);

            assertThrows(BusinessRuleException.class,
                    () -> caseService.updateStage(CASE_NUMBER, request));
        }

        @Test
        void shouldCloseCaseOnFinalStage() {
            setFirmContext();
            Case c = createCivilCase();
            c.setCaseStage(CaseStage.ARGUMENT);
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(caseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(casePartyRepository.findByCaseIdAndFirmId(CASE_ID, FIRM_ID))
                    .thenReturn(List.of());

            UpdateCaseStageRequest request = new UpdateCaseStageRequest();
            request.setStage(CaseStage.CLOSED);

            CaseResponse response = caseService.updateStage(CASE_NUMBER, request);

            assertEquals(CaseStatus.CLOSED, response.getStatus());
        }
    }

    @Nested
    class GetTimeline {
        @Test
        void shouldReturnTimelineEvents() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            CaseTimelineEvent event = new CaseTimelineEvent();
            event.setEventType(TimelineEventType.CASE_CREATED);
            event.setTitle("Case created");
            when(caseTimelineRepository.findByCaseIdAndFirmIdOrderByCreatedAtDesc(CASE_ID, FIRM_ID))
                    .thenReturn(List.of(event));

            List<TimelineEventResponse> timeline = caseService.getTimeline(CASE_NUMBER);

            assertEquals(1, timeline.size());
            assertEquals(TimelineEventType.CASE_CREATED, timeline.get(0).getEventType());
        }
    }

    @Nested
    class AddParty {
        @Test
        void shouldAddPartySuccessfully() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));
            when(casePartyRepository.save(any())).thenAnswer(i -> {
                CaseParty cp = i.getArgument(0);
                cp.setId(PARTY_ID);
                return cp;
            });

            AddPartyRequest request = new AddPartyRequest();
            request.setPartyType(PartyType.PLAINTIFF);
            request.setFullName("John Doe");

            PartyResponse response = caseService.addParty(CASE_NUMBER, request);

            assertNotNull(response);
            assertEquals("John Doe", response.getFullName());
            assertEquals(PartyType.PLAINTIFF, response.getPartyType());
        }
    }

    @Nested
    class LinkParty {
        @Test
        void shouldLinkPartyToClient() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            CaseParty cp = new CaseParty();
            cp.setId(PARTY_ID);
            cp.setFirmId(FIRM_ID);
            cp.setCaseId(CASE_ID);
            cp.setFullName("John Doe");
            when(casePartyRepository.findById(PARTY_ID)).thenReturn(Optional.of(cp));
            when(casePartyRepository.save(any())).thenReturn(cp);

            LinkPartyRequest request = new LinkPartyRequest();
            UUID clientId = UUID.randomUUID();
            request.setClientId(clientId);
            request.setOurClient(true);

            PartyResponse response = caseService.linkParty(CASE_NUMBER, PARTY_ID, request);

            assertNotNull(response);
        }

        @Test
        void shouldThrowWhenPartyNotInSameFirm() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            CaseParty cp = new CaseParty();
            cp.setFirmId(UUID.randomUUID()); // different firm
            cp.setCaseId(CASE_ID);
            when(casePartyRepository.findById(PARTY_ID)).thenReturn(Optional.of(cp));

            LinkPartyRequest request = new LinkPartyRequest();
            request.setClientId(UUID.randomUUID());

            assertThrows(ForbiddenException.class,
                    () -> caseService.linkParty(CASE_NUMBER, PARTY_ID, request));
        }
    }

    @Nested
    class RemoveParty {
        @Test
        void shouldRemovePartySuccessfully() {
            setFirmContext();
            Case c = createCivilCase();
            when(caseRepository.findByCaseNumberAndFirmId(CASE_NUMBER, FIRM_ID))
                    .thenReturn(Optional.of(c));

            CaseParty cp = new CaseParty();
            cp.setFirmId(FIRM_ID);
            cp.setCaseId(CASE_ID);
            when(casePartyRepository.findById(PARTY_ID)).thenReturn(Optional.of(cp));

            caseService.removeParty(CASE_NUMBER, PARTY_ID);

            verify(casePartyRepository).delete(cp);
        }
    }
}
