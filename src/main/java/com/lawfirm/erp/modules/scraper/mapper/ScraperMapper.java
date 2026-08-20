package com.lawfirm.erp.modules.scraper.mapper;

import com.lawfirm.erp.modules.scraper.dto.HearingStatusResponse;
import com.lawfirm.erp.modules.scraper.entity.Court;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ScraperMapper {

    public HearingStatusResponse.Hearing toHearing(LocalDate today, LocalDate ad, String bs,
                                                     String judge, String subject, String orderType,
                                                     HearingSource source) {
        return HearingStatusResponse.Hearing.builder()
                .hearingDateAd(ad).hearingDateBs(bs).judgeName(judge)
                .subject(subject).orderType(orderType).source(source)
                .build();
    }

    public List<HearingStatusResponse.Hearing> sortUpcoming(List<HearingStatusResponse.Hearing> upcoming) {
        upcoming.sort(Comparator.comparing(HearingStatusResponse.Hearing::getHearingDateAd,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return upcoming;
    }

    public List<HearingStatusResponse.Hearing> sortHistory(List<HearingStatusResponse.Hearing> history) {
        history.sort(Comparator.comparing(HearingStatusResponse.Hearing::getHearingDateAd,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return history;
    }

    public String resolveCourtName(Court court) {
        return court != null ? court.getCourtName() : null;
    }
}
