package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.modules.projectmanagement.dto.response.ClientProjectResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalInstanceResponse;

import java.util.List;

public interface ClientPortalService {

    List<ClientProjectResponse> listMyProjects();

    ClientProjectResponse getProject(String projectCode);

    List<RenewalInstanceResponse> listMyRenewals(String projectCode);
}
