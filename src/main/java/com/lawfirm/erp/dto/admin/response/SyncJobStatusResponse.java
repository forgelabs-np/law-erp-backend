package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** SA polling payload for a template sync job (spec §5, §6). */
@Data
@Builder
public class SyncJobStatusResponse {
    private UUID jobId;
    private UUID templateId;
    private String templateCode;
    private String status;
    private int firmsTotal;
    private int firmsCompleted;
    private int firmsFailed;
    private String errorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
