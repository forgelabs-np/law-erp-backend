package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CaseAssignmentResponse {

    private UUID id;
    private UUID matterId;
    private String matterNumber;
    private String matterTitle;
    private UUID userId;
    private String userName;
    private AssignmentRole assignmentRole;
    private LocalDateTime createdAt;
}
