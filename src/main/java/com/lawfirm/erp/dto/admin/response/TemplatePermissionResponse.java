package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response for template permission reads, edits, and previews.
 * Delta fields are populated on preview/edit; null on plain reads.
 */
@Data
@Builder
public class TemplatePermissionResponse {

    private UUID templateId;
    private String templateCode;
    private String templateName;
    private LocalDateTime lastSaEditAt;

    private List<PermissionResponse> currentPermissions;

    // ── Delta vs previous template state (preview/edit only) ──────────────
    private List<String> addedPermissionCodes;
    private List<String> removedPermissionCodes;

    /** Chain-validation offenders — non-null only when the edit was rejected. */
    private List<String> chainValidationViolations;

    /**
     * Set on PUT: the async sync job propagating this edit to firm clones.
     * Poll GET /api/v1/admin/roles/sync-jobs/{id} until terminal status.
     */
    private UUID syncJobId;
}
