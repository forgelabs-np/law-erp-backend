package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.dto.firm.request.CreateEmployeeRequest;
import com.lawfirm.erp.dto.firm.request.UpdateEmployeeRequest;
import com.lawfirm.erp.dto.firm.request.UpdateEmployeeRoleRequest;
import com.lawfirm.erp.dto.firm.response.EmployeeResponse;

import java.util.UUID;

public interface EmployeeService {

    EmployeeResponse createEmployee(CreateEmployeeRequest request);

    PagedResponse<EmployeeResponse> getAllEmployees(int page, int size);

    EmployeeResponse getEmployeeById(UUID employeeId);

    EmployeeResponse updateEmployee(UUID employeeId, UpdateEmployeeRequest request);

    EmployeeResponse updateEmployeeRole(UUID employeeId, UpdateEmployeeRoleRequest request);

    EmployeeResponse toggleEmployeeStatus(UUID employeeId);
}
