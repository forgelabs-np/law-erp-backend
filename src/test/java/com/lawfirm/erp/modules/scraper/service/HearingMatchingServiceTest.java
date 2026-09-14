package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus;
import com.lawfirm.erp.modules.scraper.repository.ClientCaseRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HearingMatchingServiceTest {

    @Mock private ClientCaseRepository clientCaseRepository;
    @Mock private DailyHearingRepository dailyRepository;
    @Mock private WeeklyHearingRepository weeklyRepository;
    @Mock private HearingMatchRepository matchRepository;
    @Mock private NotificationDispatcher dispatcher;

    private HearingMatchingService service;

    private final Long caseId = 1001L;

    @BeforeEach
    void setUp() {
        service = new HearingMatchingService(clientCaseRepository, dailyRepository,
                weeklyRepository, matchRepository, dispatcher);
        when(dispatcher.channel()).thenReturn("test");
    }

    private ClientCase activeCase() {
        ClientCase cc = new ClientCase();
        cc.setId(caseId);
        cc.setCourtId(39);
        cc.setCaseNoInternal("39-081-32030");
        cc.setCaseStatus(ClientCaseStatus.ACTIVE);
        cc.setActive(true);
        return cc;
    }

    private DailyHearing hearing() {
        DailyHearing h = new DailyHearing();
        h.setCourtId(39);
        h.setCaseNoInternal("39-081-32030");
        h.setHearingDateBs("2083-05-02");
        h.setHearingDateAd(LocalDate.of(2026, 8, 18));
        h.setBench("1");
        h.setSerialNo("क");
        h.setJudgeName("इजलाश 1");
        h.setOrderType("स्थगित");
        h.setCaseNoBs("081-C4-3827");
        h.setSubject("लेनदेन");
        h.setPlaintiff("राम");
        h.setDefendant("श्याम");
        return h;
    }

    @Test
    @DisplayName("Creates a match for an active client case and dispatches a notification")
    void matchesAndNotifies() {
        List<HearingMatch> savedMatches = new ArrayList<>();
        when(clientCaseRepository.findAll()).thenReturn(List.of(activeCase()));
        when(dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                any(), any(), any())).thenReturn(List.of(hearing()));
        when(weeklyRepository.findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                any(), any(), any())).thenReturn(List.of());
        when(matchRepository.existsByClientCaseIdAndCourtIdAndHearingDateBs(any(), any(), any()))
                .thenReturn(false);
        when(matchRepository.save(any(HearingMatch.class))).thenAnswer(inv -> {
            HearingMatch m = inv.getArgument(0);
            savedMatches.add(m);
            return m;
        });
        when(matchRepository.findByNotifiedFalse()).thenAnswer(inv -> new ArrayList<>(savedMatches));

        int created = service.matchAndNotify();

        assertEquals(1, created);
        HearingMatch saved = savedMatches.stream()
                .filter(m -> m.getClientCaseId().equals(caseId))
                .findFirst().orElseThrow();
        assertEquals(39, saved.getCourtId());
        assertEquals("2083-05-02", saved.getHearingDateBs());
        assertEquals("इजलाश 1", saved.getJudgeName());
        // Matches carry the full row — no join back needed to display them.
        assertEquals("1", saved.getBench());
        assertEquals("क", saved.getSerialNo());
        assertEquals("081-C4-3827", saved.getCaseNoBs());
        assertEquals("लेनदेन", saved.getSubject());
        assertEquals("राम", saved.getPlaintiff());
        assertEquals("श्याम", saved.getDefendant());
        verify(dispatcher, atLeastOnce()).dispatch(any(), any(), any());
        assertTrue(saved.isNotified(), "match marked notified after successful dispatch");
    }

    @Test
    @DisplayName("Skips already-matched rows — no duplicate matches")
    void dedupes() {
        when(clientCaseRepository.findAll()).thenReturn(List.of(activeCase()));
        when(dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                any(), any(), any())).thenReturn(List.of(hearing()));
        when(weeklyRepository.findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                any(), any(), any())).thenReturn(List.of());
        when(matchRepository.existsByClientCaseIdAndCourtIdAndHearingDateBs(any(), any(), any()))
                .thenReturn(true);

        int created = service.matchAndNotify();

        assertEquals(0, created);
        verify(matchRepository, never()).save(any(HearingMatch.class));
        verify(dispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("Backfill fills legacy matches that predate the detail columns")
    void backfillLegacyMatches() {
        HearingMatch legacy = new HearingMatch();
        legacy.setId(900L);
        legacy.setClientCaseId(caseId);
        legacy.setCourtId(39);
        legacy.setCaseNoInternal("39-081-32030");
        legacy.setHearingDateBs("2083-05-02");
        legacy.setSubject(null);
        when(matchRepository.findBySubjectIsNull()).thenReturn(List.of(legacy));
        when(dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateBs(any(), any(), any()))
                .thenReturn(java.util.Optional.of(hearing()));
        when(matchRepository.save(any(HearingMatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.backfillLegacyMatches();

        assertEquals("लेनदेन", legacy.getSubject());
        assertEquals("राम", legacy.getPlaintiff());
        assertEquals("081-C4-3827", legacy.getCaseNoBs());
        verify(matchRepository, atLeastOnce()).save(legacy);
    }

    @Test
    @DisplayName("A failing dispatcher never blocks matching — the match stays un-notified for retry")
    void dispatcherFailureDoesNotBlock() {
        List<HearingMatch> savedMatches = new ArrayList<>();
        when(clientCaseRepository.findAll()).thenReturn(List.of(activeCase()));
        when(dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                any(), any(), any())).thenReturn(List.of(hearing()));
        when(weeklyRepository.findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
                any(), any(), any())).thenReturn(List.of());
        when(matchRepository.existsByClientCaseIdAndCourtIdAndHearingDateBs(any(), any(), any()))
                .thenReturn(false);
        when(matchRepository.save(any(HearingMatch.class))).thenAnswer(inv -> {
            HearingMatch m = inv.getArgument(0);
            savedMatches.add(m);
            return m;
        });
        when(matchRepository.findByNotifiedFalse()).thenAnswer(inv -> new ArrayList<>(savedMatches));
        // Simulate a broken channel.
        doThrow(new RuntimeException("smtp down")).when(dispatcher).dispatch(any(), any(), any());

        int created = service.matchAndNotify();

        assertEquals(1, created, "matching must succeed despite notification failure");
        assertFalse(savedMatches.get(0).isNotified(), "un-notified on dispatch failure so it is retried");
    }
}
