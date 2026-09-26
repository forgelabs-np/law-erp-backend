package com.lawfirm.erp.modules.me.service;

import com.lawfirm.erp.modules.me.dto.ChangeOwnPasswordRequest;
import com.lawfirm.erp.modules.me.dto.MeResponse;

public interface MeService {

    MeResponse getMe();

    /**
     * Changes the signed-in user's own password: proves the current one, refuses a repeat of it,
     * and revokes every other session the account holds.
     */
    void changeOwnPassword(ChangeOwnPasswordRequest request);
}
