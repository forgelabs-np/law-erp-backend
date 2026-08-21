package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalStatus;

import java.util.List;

public interface RenewalService {

    RenewalResponse createRenewal(String projectCode, CreateRenewalRequest request);

    List<RenewalResponse> listRenewals(String projectCode);

    RenewalResponse getRenewal(String projectCode, Long renewalId);

    RenewalResponse updateRenewal(String projectCode, Long renewalId,
                                   UpdateRenewalRequest request);

    RenewalResponse updateRenewalStatus(String projectCode, Long renewalId,
                                         RenewalStatus status);

    RenewalInstanceResponse updateInstanceStatus(String projectCode, Long renewalId,
                                                  Long instanceId,
                                                  UpdateInstanceStatusRequest request);
}
