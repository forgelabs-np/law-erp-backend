package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;

import java.util.UUID;

public interface FirmService {

    FirmCreationResponse createFirm(CreateFirmRequest request);

    void suspendFirm(UUID firmId);

    void activateFirm(UUID firmId);

    void extendTrial(UUID firmId, int additionalDays);

    void convertToPermanent(UUID firmId);
}
