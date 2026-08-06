package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.CaseTimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseTimelineRepository extends JpaRepository<CaseTimelineEvent, UUID> {

    List<CaseTimelineEvent> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<CaseTimelineEvent> findByCaseIdAndFirmIdOrderByCreatedAtDesc(UUID caseId, UUID firmId);
}
