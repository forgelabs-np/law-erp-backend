package com.lawfirm.erp.common.repository;

import com.lawfirm.erp.common.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

    // ── GLOBAL scope ──────────────────────────────────────────────────────────

    /** Get a single global config value by key */
    Optional<SystemConfig> findByScopeAndConfigKey(SystemConfig.ConfigScope scope, String configKey);

    /** Get all global config values */
    List<SystemConfig> findByScope(SystemConfig.ConfigScope scope);

    // ── FIRM scope ────────────────────────────────────────────────────────────

    /** Get a single firm-scoped config value by key */
    Optional<SystemConfig> findByScopeAndFirmIdAndConfigKey(
            SystemConfig.ConfigScope scope, UUID firmId, String configKey);

    /** Get all config values for a specific firm */
    List<SystemConfig> findByScopeAndFirmId(SystemConfig.ConfigScope scope, UUID firmId);

    /** Delete a config by scope + firm + key */
    void deleteByScopeAndFirmIdAndConfigKey(SystemConfig.ConfigScope scope, UUID firmId, String configKey);
}
