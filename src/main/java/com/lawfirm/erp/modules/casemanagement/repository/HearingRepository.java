package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.Hearing;
import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
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
public interface HearingRepository extends JpaRepository<Hearing, UUID> {

    List<Hearing> findByCaseIdOrderByDateDesc(UUID caseId);

    Page<Hearing> findByFirmIdAndDateBetweenOrderByDateAscTimeAsc(
            UUID firmId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Hearing> findByFirmIdAndAdvocateIdAndDateBetweenOrderByDateAscTimeAsc(
            UUID firmId, UUID advocateId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT h FROM Hearing h WHERE h.firmId = :firmId AND h.date = :date " +
           "ORDER BY h.time ASC")
    List<Hearing> findTodayHearings(@Param("firmId") UUID firmId, @Param("date") LocalDate date);

    @Query("SELECT h FROM Hearing h WHERE h.advocateId = :advocateId AND h.date = :date " +
           "ORDER BY h.time ASC")
    List<Hearing> findTodayHearingsByAdvocate(@Param("advocateId") UUID advocateId,
                                               @Param("date") LocalDate date);

    @Query("SELECT h FROM Hearing h WHERE h.firmId = :firmId " +
           "AND h.date BETWEEN :from AND :to " +
           "AND h.status = 'SCHEDULED' " +
           "ORDER BY h.date ASC, h.time ASC")
    List<Hearing> findUpcomingHearings(@Param("firmId") UUID firmId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Query("SELECT h FROM Hearing h WHERE h.advocateId = :advocateId " +
           "AND h.date BETWEEN :from AND :to " +
           "AND h.status = 'SCHEDULED' " +
           "ORDER BY h.date ASC, h.time ASC")
    List<Hearing> findUpcomingHearingsByAdvocate(@Param("advocateId") UUID advocateId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @Query("SELECT h FROM Hearing h WHERE h.advocateId = :advocateId " +
           "AND h.date = :date " +
           "AND h.status = 'SCHEDULED' " +
           "AND (:excludeId IS NULL OR h.id <> :excludeId) " +
           "AND ((h.time <= :endTime AND (h.endTime IS NULL OR h.endTime >= :startTime))" +
           "  OR (h.time >= :startTime AND h.time < :endTime))")
    List<Hearing> findConflicts(@Param("advocateId") UUID advocateId,
                                @Param("date") LocalDate date,
                                @Param("startTime") LocalTime startTime,
                                @Param("endTime") LocalTime endTime,
                                @Param("excludeId") UUID excludeId);

    Optional<Hearing> findByIdAndFirmId(UUID id, UUID firmId);
}
