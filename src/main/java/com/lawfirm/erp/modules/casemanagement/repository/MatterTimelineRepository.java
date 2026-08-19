package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.MatterTimelineEvent;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatterTimelineRepository extends JpaRepository<MatterTimelineEvent, UUID> {

    List<MatterTimelineEvent> findByMatterIdAndFirmIdOrderByCreatedAtDesc(UUID matterId, UUID firmId);

    /**
     * Firm-wide activity feed across all matters — the "overall timeline".
     * Optional filters: matter type, matter status, and createdAt window.
     */
    @Query("SELECT e FROM MatterTimelineEvent e JOIN Matter m ON m.id = e.matterId " +
           "WHERE e.firmId = :firmId " +
           "AND (:matterType IS NULL OR m.matterType = :matterType) " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:from IS NULL OR e.createdAt >= :from) " +
           "AND (:to IS NULL OR e.createdAt <= :to)")
    Page<MatterTimelineEvent> findFirmEvents(@Param("firmId") UUID firmId,
                                             @Param("matterType") MatterType matterType,
                                             @Param("status") MatterStatus status,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             Pageable pageable);
}
