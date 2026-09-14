package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.modules.usermanagement.dto.response.GlobalDashboardResponse;

public interface GlobalDashboardService {

    GlobalDashboardResponse getDashboard();

    GlobalDashboardResponse getDashboard(int days);
}
