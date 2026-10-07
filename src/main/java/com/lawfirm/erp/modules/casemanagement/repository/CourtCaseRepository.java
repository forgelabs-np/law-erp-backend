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

    List<CourtCase> findByStatusAndAppealDeadlineBefore(CourtCaseStatus status, LocalDate date);

    List<CourtCase> findByFirmIdAndStatusAndAppealDeadlineBetweenAndAppealLapsedFalse(
            UUID firmId, CourtCaseStatus status, LocalDate from, LocalDate to);

    long countByMatterIdAndFirmIdAndStatusNotIn(UUID matterId, UUID firmId, List<CourtCaseStatus> statuses);

    boolean existsByParentCourtCaseId(UUID parentCourtCaseId);

    @Query("SELECT DISTINCT cc.parentCourtCaseId FROM CourtCase cc WHERE cc.parentCourtCaseId IN :parentIds")
    Set<UUID> findParentIdsWithChildren(@Param("parentIds") Collection<UUID> parentIds);

    @Query("SELECT cc.id FROM CourtCase cc WHERE cc.matterId IN :matterIds")
    List<UUID> findIdsByMatterIdIn(@Param("matterIds") Collection<UUID> matterIds);

    @Query("SELECT cc.courtName, cc.courtLevel, COUNT(cc) as caseCount " +
           "FROM CourtCase cc " +
           "WHERE cc.firmId = :firmId AND cc.status = 'ACTIVE' " +
           "GROUP BY cc.courtName, cc.courtLevel " +
           "ORDER BY cc.courtLevel, cc.courtName")
    List<Object[]> findDistinctActiveCourtsByFirmId(@Param("firmId") UUID firmId);

    @Query("SELECT cc FROM CourtCase cc " +
           "WHERE cc.firmId = :firmId AND cc.courtName = :courtName AND cc.status = 'ACTIVE'")
    List<CourtCase> findByFirmIdAndCourtNameAndStatusActive(
            @Param("firmId") UUID firmId, @Param("courtName") String courtName);

    List<CourtCase> findByFirmIdAndAdvocateId(@Param("firmId") UUID firmId, @Param("advocateId") UUID advocateId);
}
