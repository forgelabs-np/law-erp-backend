package com.lawfirm.erp.modules.email.service;

import java.util.UUID;

public interface EmailService {

    void sendWelcomeEmployee(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                             String username, String tempPassword, String firmName, String firmCode);

    void sendWelcomeClient(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                           String username, String tempPassword, String firmName);

    void sendWelcomeFirmAdmin(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                              String username, String tempPassword, String firmName, String firmCode);

    void sendPasswordReset(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                           String tempPassword, String firmName);
}
