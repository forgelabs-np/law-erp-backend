package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateRenewalTypeRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalTypeResponse;

import java.util.List;

public interface RenewalTypeService {

    List<RenewalTypeResponse> listTypes();

    RenewalTypeResponse createType(CreateRenewalTypeRequest request);

    RenewalTypeResponse updateType(Long typeId, CreateRenewalTypeRequest request);

    void deleteType(Long typeId);
}
