package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.response.FirmAdminResponse;

import java.util.List;
import java.util.UUID;

public interface FirmAdminService {

    List<FirmAdminResponse> getAllFirmAdmins();

    List<FirmAdminResponse> getFirmAdminsByFirmId(UUID firmId);

    FirmAdminResponse getFirmAdminById(UUID adminId);

    FirmAdminResponse toggleFirmAdminStatus(UUID adminId);
}
