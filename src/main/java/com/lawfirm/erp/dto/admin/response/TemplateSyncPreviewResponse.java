package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Dry-run result for a template permission change. Zero writes — same
 * computation the sync job performs, minus persistence. Surfaces cascade
 * side-effects to SA before commit (spec §7: nothing about a cascade is silent).
 */
@Data
@Builder
public class TemplateSyncPreviewResponse {

    private UUID templateId;
    private String templateCode;

    private List<String> addedPermissionCodes;
    private List<String> removedPermissionCodes;

    private List<String> chainValidationViolations;

    /** True when the edit is legal and can be committed. */
    private boolean wouldViolateChain;

    // ── Fan-out impact (per affected firm role/template) ──────────────────
    private List<FirmImpact> firmImpacts;

    /** Totals across all firms for quick display. */
    private int firmsAffected;
    private int clonesSynced;
    private int clonesSkippedByCeiling;
    private int cascadeStrippedFromClones;
    private int cascadeStrippedFromTemplates;

    /**
     * FIRM_ADMIN-narrowing only: employee system templates stripped of
     * now-over-ceiling permissions, keyed by template code. Applies
     * platform-wide (not per firm) — closes the fresh-onboarding gap.
     */
    private java.util.Map<String, List<String>> employeeTemplateStrips;

    /** Same strip, keyed by template ID — machine-usable for the sync job. */
    private java.util.Map<UUID, java.util.List<UUID>> employeeTemplateStripIds;

    @Data
    @Builder
    public static class FirmImpact {
        private UUID firmId;
        private String firmCode;

        /** Role code of the clone(s) touched in this firm. */
        private List<CloneImpact> cloneImpacts;

        /** Narrowing cascade targets in this firm: employee role → perms to strip. */
        private List<CascadeStrip> cascadeTargets;
    }

    @Data
    @Builder
    public static class CascadeStrip {
        private UUID roleId;
        private String roleCode;
        private List<UUID> permissionIds;
        private List<String> permissionCodes;
    }

    @Data
    @Builder
    public static class CloneImpact {
        private UUID roleId;
        private String roleCode;

        /** Permissions that would be added to this clone (empty if none). */
        private List<String> added;
        /** Permissions that would be removed (delta removals present on the clone). */
        private List<String> removed;
        /** Adds skipped because they exceed the firm's current FIRM_ADMIN ceiling. */
        private List<String> skippedByCeiling;
        /** Permissions stripped by the narrowing cascade (over new FIRM_ADMIN ceiling). */
        private List<String> cascadeStripped;

        /** Machine-usable forms of the same delta — the sync job persists these. */
        private List<UUID> addedPermissionIds;
        private List<UUID> removedPermissionIds;
    }
}
