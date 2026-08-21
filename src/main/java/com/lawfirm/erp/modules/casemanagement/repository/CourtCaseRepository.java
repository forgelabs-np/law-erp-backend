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
}
