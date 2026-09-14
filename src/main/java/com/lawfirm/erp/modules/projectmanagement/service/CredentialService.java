package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;

import java.util.List;

public interface CredentialService {

    CredentialResponse addCredential(String projectCode, AddCredentialRequest request);

    List<CredentialResponse> listCredentials(String projectCode);

    CredentialResponse getCredential(String projectCode, Long credentialId);

    CredentialResponse updateCredential(String projectCode, Long credentialId,
                                         UpdateCredentialRequest request);

    void deleteCredential(String projectCode, Long credentialId);

    String revealPassword(String projectCode, Long credentialId);
}
