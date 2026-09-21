package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.response.ClientMatterResponse;

import java.util.List;

/**
 * Read-only "my cases" surface for the client portal. Every method is implicitly
 * scoped to the calling client account — there is no way to ask for another client's
 * matter through this API.
 */
public interface ClientMatterPortalService {

    List<ClientMatterResponse> listMyMatters();

    ClientMatterResponse getMyMatter(String matterNumber);
}
