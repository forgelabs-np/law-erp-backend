package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Generic result for bulk operations (deactivate, role change).
 * Returns per-item success/failure so the caller knows exactly what happened.
 */
@Data
@Builder
public class BulkOperationResult {
    private int succeeded;
    private int failed;
    private List<String> failedDetails; // e.g. ["uuid1: cannot deactivate yourself"]
    private String message;             // e.g. "3 of 4 users deactivated"
}
