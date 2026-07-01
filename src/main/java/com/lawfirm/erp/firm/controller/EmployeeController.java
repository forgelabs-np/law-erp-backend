package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.CreateEmployeeRequest;
import com.lawfirm.erp.dto.firm.request.UpdateEmployeeRoleRequest;
import com.lawfirm.erp.dto.firm.response.EmployeeResponse;
import com.lawfirm.erp.firm.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/employees")
@RequiredArgsConstructor
@Tag(name = "Employee Management", description = "Firm employee management APIs")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create employee")
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody ApiRequest<CreateEmployeeRequest> request) {
        return responseHandler.ok(
                employeeService.createEmployee(request.getData()),
                "Employee created successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all employees")
    public ResponseEntity<ApiResponse<List<EmployeeResponse>>> getAllEmployees() {
        return responseHandler.ok(
                employeeService.getAllEmployees(),
                "Employees fetched successfully"
        );
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Get employee by ID")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployeeById(@PathVariable UUID employeeId) {
        return responseHandler.ok(
                employeeService.getEmployeeById(employeeId),
                "Employee fetched successfully"
        );
    }

    @PutMapping("/{employeeId}")
    @Operation(summary = "Update employee")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployee(
            @PathVariable UUID employeeId,
            @Valid @RequestBody ApiRequest<CreateEmployeeRequest> request) {
        return responseHandler.ok(
                employeeService.updateEmployee(employeeId, request.getData()),
                "Employee updated successfully"
        );
    }

    @PatchMapping("/{employeeId}/role")
    @Operation(summary = "Update employee role")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployeeRole(
            @PathVariable UUID employeeId,
            @Valid @RequestBody ApiRequest<UpdateEmployeeRoleRequest> request) {
        return responseHandler.ok(
                employeeService.updateEmployeeRole(employeeId, request.getData()),
                "Employee role updated successfully"
        );
    }

    @PatchMapping("/{employeeId}/toggle")
    @Operation(summary = "Toggle employee status")
    public ResponseEntity<ApiResponse<EmployeeResponse>> toggleEmployee(@PathVariable UUID employeeId) {
        return responseHandler.ok(
                employeeService.toggleEmployeeStatus(employeeId),
                "Employee status toggled successfully"
        );
    }
}