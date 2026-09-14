package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.dto.firm.request.CreateClientRequest;
import com.lawfirm.erp.dto.firm.response.ClientResponse;

import java.util.UUID;

public interface ClientService {

    ClientResponse createClient(CreateClientRequest request);

    PagedResponse<ClientResponse> getAllClients(int page, int size);

    ClientResponse getClientById(UUID clientId);

    ClientResponse togglePortalAccess(UUID clientId, Boolean portalAccessEnabled);
}
