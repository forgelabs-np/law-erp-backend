package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.request.AssignCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CaseAssignmentResponse;

import java.util.List;
import java.util.UUID;

public interface CaseAssignmentService {

    CaseAssignmentResponse assign(String matterNumber, AssignCaseRequest request);

    void revoke(String matterNumber, UUID userId);

    List<CaseAssignmentResponse> listByMatter(String matterNumber);

    List<CaseAssignmentResponse> listByUser(UUID userId);

    boolean isAssigned(UUID matterId, UUID userId, UUID firmId);

    List<UUID> getAssignedMatterIds(UUID userId, UUID firmId);
}
