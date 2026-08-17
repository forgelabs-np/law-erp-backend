package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.WeeklyHearing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyHearingRepository extends JpaRepository<WeeklyHearing, UUID> {

    Optional<WeeklyHearing> findByCourtIdAndCaseNoInternalAndHearingDateBs(
            Integer courtId, String caseNoInternal, String hearingDateBs);

    List<WeeklyHearing> findByCourtIdAndCaseNoInternalAndHearingDateAdGreaterThanEqualOrderByHearingDateAdAsc(
            Integer courtId, String caseNoInternal, LocalDate from);

    List<WeeklyHearing> findByCaseNoInternalOrderByHearingDateAdDesc(String caseNoInternal);

    List<WeeklyHearing> findByHearingDateAdBetweenOrderByCourtIdAscHearingDateAdAsc(
            LocalDate from, LocalDate to);
}
