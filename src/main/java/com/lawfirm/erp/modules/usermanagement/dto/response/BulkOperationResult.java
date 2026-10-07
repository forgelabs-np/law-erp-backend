package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkOperationResult {
    private int succeeded;
    private int failed;
    private List<String> failedDetails;
    private String message;
}
