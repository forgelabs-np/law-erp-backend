package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.response.TemplateSyncPreviewResponse.FirmImpact;
import com.lawfirm.erp.rbac.entity.SyncJob;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Async execution of a template-permission sync (spec §6).
 *
 * Separate bean from TemplatePermissionService so @Async is proxy-honored
 * (same reasoning as AsyncAuditWriter). Runs on the existing "taskExecutor"
 * pool — reused, not a new executor (spec §3 infra check).
 *
 * Failure isolation: one firm's failure is recorded and does not stop others;
 * the job ends COMPLETED_WITH_FAILURES when any firm failed, so SA can retry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateSyncRunner {

    private final SyncJobRepository syncJobRepository;
    private final FirmSyncExecutor firmSyncExecutor;
    private final RoleRepository roleRepository;

    @Async("taskExecutor")
    public void runSync(UUID syncJobId,
                        UUID templateId,
                        List<FirmImpact> firmImpacts,
                        Map<UUID, List<UUID>> employeeTemplateStripIds,
                        UUID requestedBy) {
        SyncJob job = syncJobRepository.findById(syncJobId).orElse(null);
        if (job == null) {
            log.error("Sync job {} vanished before execution — aborting", syncJobId);
            return;
        }

        job.setStatus(SyncJob.SyncStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setFirmsTotal(firmImpacts.size());
        syncJobRepository.save(job);

        int completed = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        try {
            // Employee-template strips first (platform-wide, spec §4b) — a firm
            // onboarded mid-sync must clone from an already-clean template.
            if (!employeeTemplateStripIds.isEmpty()) {
                firmSyncExecutor.executeTemplateStrips(employeeTemplateStripIds, syncJobId, requestedBy);
            }

            for (FirmImpact impact : firmImpacts) {
                try {
                    firmSyncExecutor.executeFirmSync(impact, syncJobId, templateId, requestedBy);
                    completed++;
                } catch (Exception e) {
                    failed++;
                    log.error("Sync job {}: firm {} failed — {}",
                            syncJobId, impact.getFirmId(), e.getMessage(), e);
                    errors.append("firm ").append(impact.getFirmId())
                            .append(": ").append(e.getMessage()).append("; ");
                }
            }
        } catch (Exception fatal) {
            // Template-strip phase itself failed — nothing reliable was applied.
            log.error("Sync job {} failed fatally during template strips: {}",
                    syncJobId, fatal.getMessage(), fatal);
            job.setStatus(SyncJob.SyncStatus.FAILED);
            job.setErrorSummary(truncate("template-strip phase failed: " + fatal.getMessage()));
            job.setCompletedAt(LocalDateTime.now());
            syncJobRepository.save(job);
            return;
        }

        job.setFirmsCompleted(completed);
        job.setFirmsFailed(failed);
        job.setStatus(failed > 0
                ? SyncJob.SyncStatus.COMPLETED_WITH_FAILURES
                : SyncJob.SyncStatus.COMPLETED);
        job.setErrorSummary(errors.isEmpty() ? null : truncate(errors.toString()));
        job.setCompletedAt(LocalDateTime.now());
        syncJobRepository.save(job);

        String templateCode = roleRepository.findById(templateId)
                .map(r -> r.getRoleCode()).orElse("?");
        log.info("Sync job {} for template '{}': {} firms synced, {} failed",
                syncJobId, templateCode, completed, failed);
    }

    private static String truncate(String s) {
        return s.length() <= 4000 ? s : s.substring(0, 3997) + "...";
    }
}
