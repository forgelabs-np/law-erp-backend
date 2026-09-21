package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.modules.usermanagement.dto.response.ClientDashboardResponse;

public interface ClientDashboardService {
    ClientDashboardResponse getDashboard(DashboardScope scope);
}
