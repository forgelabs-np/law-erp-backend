package com.lawfirm.erp.modules.projectmanagement.repository;

import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RenewalInstanceRepository extends JpaRepository<RenewalInstance, Long> {

    List<RenewalInstance> findByRenewalIdAndActive(Long renewalId, boolean active);

    long countByRenewalIdAndStatus(Long renewalId, RenewalInstanceStatus status);

    /** Daily overdue scan: mark PENDING instances past their due date. */
    @Modifying
    @Query("UPDATE RenewalInstance ri SET ri.status = com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus.OVERDUE " +
           "WHERE ri.status = com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus.PENDING " +
           "AND ri.dueDate < :today AND ri.active = true")
    int markOverdueInstances(@Param("today") LocalDate today);

    /** Count overdue instances across all projects for a firm. */
    @Query("SELECT COUNT(ri) FROM RenewalInstance ri " +
           "JOIN Renewal r ON r.id = ri.renewalId " +
           "WHERE r.projectId = :projectId " +
           "AND ri.status = com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus.OVERDUE " +
           "AND ri.active = true")
    long countOverdueByProjectId(@Param("projectId") java.util.UUID projectId);

    /** Upcoming instances across all projects for a firm. */
    @Query("SELECT COUNT(ri) FROM RenewalInstance ri " +
           "JOIN Renewal r ON r.id = ri.renewalId " +
           "WHERE r.projectId = :projectId " +
           "AND ri.status = com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus.PENDING " +
           "AND ri.dueDate BETWEEN :from AND :to " +
           "AND ri.active = true")
    long countUpcomingByProjectId(@Param("projectId") java.util.UUID projectId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
