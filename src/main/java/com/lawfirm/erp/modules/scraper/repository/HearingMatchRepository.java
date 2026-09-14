package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HearingMatchRepository extends JpaRepository<HearingMatch, Long> {

    boolean existsByClientCaseIdAndCourtIdAndHearingDateBs(
            Long clientCaseId, Integer courtId, String hearingDateBs);

    List<HearingMatch> findByClientCaseIdOrderByHearingDateAdDesc(Long clientCaseId);

    List<HearingMatch> findByNotifiedFalse();

    List<HearingMatch> findByHearingDateAdBetween(LocalDate from, LocalDate to);

    List<HearingMatch> findBySubjectIsNull();
}
