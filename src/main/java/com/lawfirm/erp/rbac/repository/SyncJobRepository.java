package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.SyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    /** Latest job for a template — the PUT response returns this id for polling. */
    Optional<SyncJob> findTopByTemplateIdOrderByCreatedAtDesc(UUID templateId);
}
