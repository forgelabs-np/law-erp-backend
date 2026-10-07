package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.ClientMatterResponse;

import java.util.List;

public interface ClientMatterPortalService {

    List<ClientMatterResponse> listMyMatters();

    ClientMatterResponse getMyMatter(String matterNumber);
}
