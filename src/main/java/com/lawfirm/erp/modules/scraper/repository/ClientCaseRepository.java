package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.ClientCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Find client case by court's official number (Nepali BS format).
     * Used for matching court case numbers from case management module.
     */
    Optional<ClientCase> findByCourtIdAndCaseNoBs(Integer courtId, String caseNoBs);

    /**
     * Find all client cases for a specific client.
     * Used for lawyer dashboard to see all tracked cases.
     */
    List<ClientCase> findByClientIdAndActive(UUID clientId, boolean active);

    /**
     * Find client cases by court ID and active status.
     * Used for bulk operations and scraper optimization.
     */
    List<ClientCase> findByCourtIdAndActive(Integer courtId, boolean active);
}
