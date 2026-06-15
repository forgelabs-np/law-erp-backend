//package com.lawfirm.erp.firm.controller;
//
//import com.lawfirm.erp.common.dto.ApiRequest;
//import com.lawfirm.erp.common.dto.ApiResponse;
//import com.lawfirm.erp.common.exception.ResponseHandler;
//import com.lawfirm.erp.dto.firm.request.DepartmentRequest;
//import com.lawfirm.erp.dto.firm.response.DepartmentResponse;
//import com.lawfirm.erp.firm.service.DepartmentService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/api/v1/firm/departments")
//@RequiredArgsConstructor
//@Tag(name = "Department Management", description = "Firm department/practice area APIs")
//@PreAuthorize("hasRole('FIRM_ADMIN')")
//public class DepartmentController {
//
//    private final DepartmentService departmentService;
//    private final ResponseHandler responseHandler;
//
//    @PostMapping
//    @Operation(summary = "Create department")
//    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
//            @Valid @RequestBody ApiRequest<DepartmentRequest> request) {
//        return responseHandler.ok(
//                departmentService.createDepartment(request.getData()),
//                "Department created successfully"
//        );
//    }
//
//    @GetMapping
//    @Operation(summary = "Get all departments")
//    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllDepartments() {
//        return responseHandler.ok(
//                departmentService.getAllDepartments(),
//                "Departments fetched successfully"
//        );
//    }
//
//    @GetMapping("/{departmentId}")
//    @Operation(summary = "Get department by ID")
//    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(@PathVariable UUID departmentId) {
//        return responseHandler.ok(
//                departmentService.getDepartmentById(departmentId),
//                "Department fetched successfully"
//        );
//    }
//
//    @PutMapping("/{departmentId}")
//    @Operation(summary = "Update department")
//    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
//            @PathVariable UUID departmentId,
//            @Valid @RequestBody ApiRequest<DepartmentRequest> request) {
//        return responseHandler.ok(
//                departmentService.updateDepartment(departmentId, request.getData()),
//                "Department updated successfully"
//        );
//    }
//
//    @DeleteMapping("/{departmentId}")
//    @Operation(summary = "Delete department")
//    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable UUID departmentId) {
//        departmentService.deleteDepartment(departmentId);
//        return responseHandler.ok(null, "Department deleted successfully");
//    }
//}