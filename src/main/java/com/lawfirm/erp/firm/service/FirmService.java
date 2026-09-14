package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;
import com.lawfirm.erp.dto.firm.response.FirmListResponse;

import java.util.List;
import java.util.UUID;

public interface FirmService {

    FirmCreationResponse createFirm(CreateFirmRequest request);

    List<FirmListResponse> getAllFirms();

    void suspendFirm(UUID firmId);

    void activateFirm(UUID firmId);

    void extendTrial(UUID firmId, int additionalDays);

    void convertToPermanent(UUID firmId);
}
