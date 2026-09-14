package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.firm.entity.FirmEmailConfig;

import java.util.Optional;
import java.util.UUID;

public interface FirmEmailConfigService {

    Optional<FirmEmailConfig> getByFirmId(UUID firmId);

    Optional<FirmEmailConfig> getDecrypted(UUID firmId);

    FirmEmailConfig save(UUID firmId, FirmEmailConfig config);

    boolean testConnection(UUID firmId);

    void delete(UUID firmId);
}
