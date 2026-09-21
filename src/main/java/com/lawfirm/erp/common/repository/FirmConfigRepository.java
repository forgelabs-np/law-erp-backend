package com.lawfirm.erp.common.repository;

import com.lawfirm.erp.common.entity.FirmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmConfigRepository extends JpaRepository<FirmConfig, UUID> {

    Optional<FirmConfig> findByFirmIdAndConfigKey(UUID firmId, String configKey);

    List<FirmConfig> findByFirmId(UUID firmId);

    void deleteByFirmIdAndConfigKey(UUID firmId, String configKey);
}
