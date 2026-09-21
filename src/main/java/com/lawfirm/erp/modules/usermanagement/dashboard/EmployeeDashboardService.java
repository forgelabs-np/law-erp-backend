package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.modules.usermanagement.dto.response.EmployeeDashboardResponse;

public interface EmployeeDashboardService {
    EmployeeDashboardResponse getDashboard(DashboardScope scope);
}
