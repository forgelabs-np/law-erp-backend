package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.modules.usermanagement.dto.response.SuperAdminDashboardResponse;

public interface SuperAdminDashboardService {
    SuperAdminDashboardResponse getDashboard(DashboardScope scope);
}
