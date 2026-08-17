package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCaseRepository extends JpaRepository<ClientCase, Long> {

    /** The courts we actually have active client cases in — the dynamic scrape list. */
    @Query("SELECT DISTINCT cc.courtId FROM ClientCase cc " +
           "WHERE cc.caseStatus = com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus.ACTIVE " +
           "AND cc.active = true")
    List<Integer> findDistinctActiveCourtIds();

    Optional<ClientCase> findByCaseNoInternal(String caseNoInternal);

    List<ClientCase> findByCourtIdAndCaseStatus(Integer courtId,
            com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus caseStatus);
}
