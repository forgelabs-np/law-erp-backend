package com.lawfirm.erp.modules.scraper.service;

import com.lawfirm.erp.modules.scraper.dto.HearingRecord;
import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HearingIngestionServiceTest {

    @Mock private DailyHearingRepository dailyRepository;
    @Mock private WeeklyHearingRepository weeklyRepository;

    private HearingIngestionService service;

    @BeforeEach
    void setUp() {
        service = new HearingIngestionService(dailyRepository, weeklyRepository);
    }

    private HearingRecord record() {
        return HearingRecord.builder()
                .courtId(39)
                .hearingDateBs("2083-05-01")
                .hearingDateAd(LocalDate.of(2026, 8, 17))
                .caseNoBs("081-C4-3827")
                .caseNoInternal("39-081-32030")
                .judgeName("इजलाश 1")
                .subject("लेनदेन")
                .plaintiff("राम")
                .defendant("श्याम")
                .orderType("स्थगित")
                .source(HearingSource.DAILY)
                .build();
    }

    @Test
    @DisplayName("New record is inserted")
    void inserts() {
        when(dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateBs(any(), any(), any()))
                .thenReturn(Optional.empty());

        int n = service.upsertDaily(List.of(record()));

        assertEquals(1, n);
        ArgumentCaptor<DailyHearing> captor = ArgumentCaptor.forClass(DailyHearing.class);
        verify(dailyRepository, times(1)).save(captor.capture());
        DailyHearing saved = captor.getValue();
        assertEquals(39, saved.getCourtId());
        assertEquals("2083-05-01", saved.getHearingDateBs());
        assertEquals("39-081-32030", saved.getCaseNoInternal());
        assertEquals("इजलाश 1", saved.getJudgeName());
        assertEquals("स्थगित", saved.getOrderType());
    }

    @Test
    @DisplayName("Re-upserting the same key updates in place — no duplicate row")
    void updatesInPlace() {
        DailyHearing existing = new DailyHearing();
        existing.setId(UUID.randomUUID());
        existing.setCourtId(39);
        existing.setCaseNoInternal("39-081-32030");
        existing.setHearingDateBs("2083-05-01");
        existing.setSubject("old");
        when(dailyRepository.findByCourtIdAndCaseNoInternalAndHearingDateBs(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        service.upsertDaily(List.of(record()));

        ArgumentCaptor<DailyHearing> captor = ArgumentCaptor.forClass(DailyHearing.class);
        verify(dailyRepository, times(1)).save(captor.capture());
        DailyHearing saved = captor.getValue();
        assertEquals(existing.getId(), saved.getId(), "must update the existing row, not insert");
        assertEquals("लेनदेन", saved.getSubject());
    }

    @Test
    @DisplayName("Skips records that cannot be keyed (no internal number or date)")
    void skipsUnkeyable() {
        HearingRecord noInternal = record();
        noInternal.setCaseNoInternal(null);
        HearingRecord noDate = record();
        noDate.setHearingDateBs(null);

        int n = service.upsertDaily(List.of(noInternal, noDate));

        assertEquals(0, n);
        verify(dailyRepository, never()).save(any());
    }
}
