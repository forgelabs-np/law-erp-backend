package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.DailyHearing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyHearingRepository extends JpaRepository<DailyHearing, Long> {

    @Query("SELECT MAX(d.scrapedDate) FROM DailyHearing d")
    Optional<LocalDate> findMaxScrapedDate();

    Optional<DailyHearing> findByCourtIdAndCaseNoInternalAndHearingDateBs(
            Integer courtId, String caseNoInternal, String hearingDateBs);

    List<DailyHearing> findByCaseNoInternalOrderByHearingDateAdDesc(String caseNoInternal);

    List<DailyHearing> findByCaseNoInternalAndHearingDateBs(String caseNoInternal, String hearingDateBs);

    List<DailyHearing> findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
            Integer courtId, String caseNoInternal, LocalDate from);

    List<DailyHearing> findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(
            LocalDate from, LocalDate to);

    long countByCourtIdAndHearingDateBs(Integer courtId, String hearingDateBs);

    void deleteByCourtIdAndHearingDateBs(Integer courtId, String hearingDateBs);
}
