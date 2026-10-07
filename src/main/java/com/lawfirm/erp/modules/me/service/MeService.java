package com.lawfirm.erp.modules.me.service;

import com.lawfirm.erp.modules.me.dto.ChangeOwnPasswordRequest;
import com.lawfirm.erp.modules.me.dto.MeResponse;

public interface MeService {

    MeResponse getMe();

    void changeOwnPassword(ChangeOwnPasswordRequest request);
}
