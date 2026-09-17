package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public interface CourtCaseRepository extends JpaRepository<CourtCase, UUID> {

    List<CourtCase> findByMatterIdAndFirmIdOrderByCreatedAtAsc(UUID matterId, UUID firmId);

    Optional<CourtCase> findByOurCourtCaseRefAndFirmId(String ourCourtCaseRef, UUID firmId);

    Optional<CourtCase> findByIdAndFirmId(UUID id, UUID firmId);

    long countByMatterIdAndCourtLevel(UUID matterId, CourtLevel courtLevel);

    List<CourtCase> findByParentCourtCaseId(UUID parentCourtCaseId);

    /** All decided cases whose appeal window has lapsed — used by the cross-firm deadline watcher. */
    List<CourtCase> findByStatusAndAppealDeadlineBefore(CourtCaseStatus status, LocalDate date);

    /** Firm-scoped watch list: decided, not lapsed, deadline closing within the window. */
    List<CourtCase> findByFirmIdAndStatusAndAppealDeadlineBetweenAndAppealLapsedFalse(
            UUID firmId, CourtCaseStatus status, LocalDate from, LocalDate to);

    long countByMatterIdAndFirmIdAndStatusNotIn(UUID matterId, UUID firmId, List<CourtCaseStatus> statuses);

    boolean existsByParentCourtCaseId(UUID parentCourtCaseId);

    /** Returns the set of parentCourtCaseIds that have at least one child. */
    @Query("SELECT DISTINCT cc.parentCourtCaseId FROM CourtCase cc WHERE cc.parentCourtCaseId IN :parentIds")
    Set<UUID> findParentIdsWithChildren(@Param("parentIds") Collection<UUID> parentIds);

    /** Court case IDs for given matter IDs — for employee calendar filtering. */
    @Query("SELECT cc.id FROM CourtCase cc WHERE cc.matterId IN :matterIds")
    List<UUID> findIdsByMatterIdIn(@Param("matterIds") Collection<UUID> matterIds);

    /**
     * Get distinct courts where the firm has active cases.
     * Returns court names grouped by court level with case counts.
     * Used for scraper integration to know which courts to scrape.
     */
    @Query("SELECT cc.courtName, cc.courtLevel, COUNT(cc) as caseCount " +
           "FROM CourtCase cc " +
           "WHERE cc.firmId = :firmId AND cc.status = 'ACTIVE' " +
           "GROUP BY cc.courtName, cc.courtLevel " +
           "ORDER BY cc.courtLevel, cc.courtName")
    List<Object[]> findDistinctActiveCourtsByFirmId(@Param("firmId") UUID firmId);

    /**
     * Get all active court cases for a specific court name.
     * Used to link case management cases to scraper client cases.
     */
    @Query("SELECT cc FROM CourtCase cc " +
           "WHERE cc.firmId = :firmId AND cc.courtName = :courtName AND cc.status = 'ACTIVE'")
    List<CourtCase> findByFirmIdAndCourtNameAndStatusActive(
            @Param("firmId") UUID firmId, @Param("courtName") String courtName);

    /**
     * Get all court cases for a specific advocate.
     * Used for lawyer dashboard and case assignment views.
     */
    List<CourtCase> findByFirmIdAndAdvocateId(@Param("firmId") UUID firmId, @Param("advocateId") UUID advocateId);
}
