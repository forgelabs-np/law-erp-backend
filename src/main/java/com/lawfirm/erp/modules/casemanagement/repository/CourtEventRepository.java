package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.CourtEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourtEventRepository extends JpaRepository<CourtEvent, UUID> {

    List<CourtEvent> findByCourtCaseIdAndFirmIdOrderBySequenceNoAsc(UUID courtCaseId, UUID firmId);

    List<CourtEvent> findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(List<UUID> courtCaseIds, UUID firmId);

    Optional<CourtEvent> findByIdAndFirmId(UUID id, UUID firmId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM CourtEvent e WHERE e.courtCaseId = :courtCaseId")
    int maxSequenceNo(@Param("courtCaseId") UUID courtCaseId);

    @Query("SELECT e.courtCaseId, COUNT(e) FROM CourtEvent e WHERE e.courtCaseId IN :courtCaseIds GROUP BY e.courtCaseId")
    List<Object[]> countByCourtCaseIds(@Param("courtCaseIds") List<UUID> courtCaseIds);

    /** Latest held Peshi per court case — for stale-matter detection (daysSinceLastPeshi). */
    @Query("SELECT e.courtCaseId, MAX(e.scheduledDate) FROM CourtEvent e " +
           "WHERE e.courtCaseId IN :courtCaseIds " +
           "AND e.eventType = com.lawfirm.erp.modules.casemanagement.enums.CourtEventType.PESHI " +
           "AND e.status <> com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus.CANCELED " +
           "GROUP BY e.courtCaseId")
    List<Object[]> findLatestPeshiByCourtCaseIds(@Param("courtCaseIds") List<UUID> courtCaseIds);

    Page<CourtEvent> findByFirmIdAndScheduledDateBetweenOrderByScheduledDateAscScheduledTimeAsc(
            UUID firmId, LocalDate from, LocalDate to, Pageable pageable);

    Page<CourtEvent> findByFirmIdAndAttendingAdvocateIdAndScheduledDateBetweenOrderByScheduledDateAscScheduledTimeAsc(
            UUID firmId, UUID advocateId, LocalDate from, LocalDate to, Pageable pageable);

    List<CourtEvent> findByFirmIdAndScheduledDate(UUID firmId, LocalDate date);

    List<CourtEvent> findByScheduledDate(LocalDate date);

    /** Tomorrow's real hearings — the T-1 reminder job. */
    List<CourtEvent> findByEventTypeAndStatusAndScheduledDate(
            com.lawfirm.erp.modules.casemanagement.enums.CourtEventType eventType,
            com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus status,
            LocalDate date);

    List<CourtEvent> findByFirmIdAndAttendingAdvocateIdAndScheduledDate(UUID firmId, UUID advocateId, LocalDate date);

    List<CourtEvent> findByFirmIdAndScheduledDateBetween(UUID firmId, LocalDate from, LocalDate to);

    List<CourtEvent> findByFirmIdAndAttendingAdvocateIdAndScheduledDateBetween(UUID firmId, UUID advocateId,
                                                                               LocalDate from, LocalDate to);

    /**
     * Overlap check: same advocate, same date, time ranges intersect, event not canceled.
     * excludeId is null on create, the event's own id on update.
     */
    @Query("SELECT e FROM CourtEvent e WHERE e.firmId = :firmId " +
           "AND e.attendingAdvocateId = :advocateId " +
           "AND e.scheduledDate = :date " +
           "AND e.status <> com.lawfirm.erp.modules.casemanagement.enums.CourtEventStatus.CANCELED " +
           "AND (:excludeId IS NULL OR e.id <> :excludeId) " +
           "AND e.scheduledTime IS NOT NULL " +
           "AND ((e.scheduledTime <= :startTime AND (e.endTime IS NULL OR e.endTime > :startTime)) " +
           "     OR (e.scheduledTime < COALESCE(:endTime, e.scheduledTime)))")
    List<CourtEvent> findConflicts(@Param("firmId") UUID firmId,
                                   @Param("advocateId") UUID advocateId,
                                   @Param("date") LocalDate date,
                                   @Param("startTime") LocalTime startTime,
                                   @Param("endTime") LocalTime endTime,
                                   @Param("excludeId") UUID excludeId);
}
