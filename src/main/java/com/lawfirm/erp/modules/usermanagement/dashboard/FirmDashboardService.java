package com.lawfirm.erp.modules.usermanagement.dashboard;

import com.lawfirm.erp.modules.usermanagement.dto.response.FirmDashboardResponse;

public interface FirmDashboardService {
    FirmDashboardResponse getDashboard(DashboardScope scope);
}
