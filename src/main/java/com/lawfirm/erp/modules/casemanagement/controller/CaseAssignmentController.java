package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.casemanagement.dto.request.AssignCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CaseAssignmentResponse;
import com.lawfirm.erp.modules.casemanagement.service.CaseAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/matters/{matterNumber}/assignments")
@RequiredArgsConstructor
@Tag(name = "Case Assignments", description = "Assign/revoke employees to matters")
public class CaseAssignmentController {

    private final CaseAssignmentService assignmentService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Assign employee to matter",
            description = "Assigns an employee (advocate, paralegal, junior) to a matter with a role")
    public ResponseEntity<ApiResponse<CaseAssignmentResponse>> assign(
            @PathVariable String matterNumber,
            @Valid @RequestBody ApiRequest<AssignCaseRequest> request) {
        permissionEvaluator.require("CASE_MANAGEMENT:CREATE");
        return responseHandler.ok(assignmentService.assign(matterNumber, request.getData()),
                "Employee assigned successfully");
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Revoke assignment",
            description = "Removes all assignments for a user from a matter")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable String matterNumber,
            @PathVariable UUID userId) {
        permissionEvaluator.require("CASE_MANAGEMENT:DELETE");
        assignmentService.revoke(matterNumber, userId);
        return responseHandler.ok(null, "Assignment revoked successfully");
    }

    @GetMapping
    @Operation(summary = "List assignments for matter",
            description = "Returns all employees assigned to this matter")
    public ResponseEntity<ApiResponse<List<CaseAssignmentResponse>>> listByMatter(
            @PathVariable String matterNumber) {
        permissionEvaluator.require("CASE_MANAGEMENT:VIEW");
        return responseHandler.ok(assignmentService.listByMatter(matterNumber),
                "Assignments fetched successfully");
    }
}
