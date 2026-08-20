package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;

public interface FirmService {

    FirmCreationResponse createFirm(CreateFirmRequest request);
}
