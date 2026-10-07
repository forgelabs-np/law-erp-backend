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

    @Query("SELECT DISTINCT cc.courtId FROM ClientCase cc " +
           "WHERE cc.caseStatus = com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus.ACTIVE " +
           "AND cc.active = true")
    List<Integer> findDistinctActiveCourtIds();

    Optional<ClientCase> findByCaseNoInternal(String caseNoInternal);

    Optional<ClientCase> findByCaseNoInternalAndFirmId(String caseNoInternal, UUID firmId);

    List<ClientCase> findByClientIdAndFirmIdAndActive(UUID clientId, UUID firmId, boolean active);

    List<ClientCase> findByCourtIdAndCaseStatus(Integer courtId,
            com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus caseStatus);

    Optional<ClientCase> findByCourtIdAndCaseNoBs(Integer courtId, String caseNoBs);

    List<ClientCase> findByClientIdAndActive(UUID clientId, boolean active);

    List<ClientCase> findByCourtIdAndActive(Integer courtId, boolean active);
}
