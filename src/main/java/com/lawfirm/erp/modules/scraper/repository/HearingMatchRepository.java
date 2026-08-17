package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.HearingMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface HearingMatchRepository extends JpaRepository<HearingMatch, UUID> {

    boolean existsByClientCaseIdAndCourtIdAndHearingDateBs(
            UUID clientCaseId, Integer courtId, String hearingDateBs);

    List<HearingMatch> findByClientCaseIdOrderByHearingDateAdDesc(UUID clientCaseId);

    List<HearingMatch> findByNotifiedFalse();

    List<HearingMatch> findByHearingDateAdBetween(LocalDate from, LocalDate to);

    List<HearingMatch> findBySubjectIsNull();
}
