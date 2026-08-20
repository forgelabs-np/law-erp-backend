package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.request.UpdateFirmProfileRequest;
import com.lawfirm.erp.dto.firm.response.FirmProfileResponse;

public interface FirmProfileService {

    FirmProfileResponse getMyFirmProfile();

    FirmProfileResponse updateMyFirmProfile(UpdateFirmProfileRequest request);
}
