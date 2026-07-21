package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.entity.SystemConfig;
import com.lawfirm.erp.common.repository.SystemConfigRepository;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the system_config key-value store.
 *
 * Two scopes:
 *   - GLOBAL — super admin only, platform-wide settings
 *   - FIRM   — per-firm settings (brand colors, timezone, email footer)
 *
 * Sensitive keys (SMTP_PASSWORD) are stored encrypted.
 * All other values are stored as plaintext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final ConfigEncryptionUtil configEncryptionUtil;

    // ── Keys that should be stored encrypted ─────────────────────────────────
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "SMTP_PASSWORD"
    );

    // ── Well-known config keys ──────────────────────────────────────────────

    // GLOBAL scope keys
    public static final String KEY_SMTP_HOST = "SMTP_HOST";
    public static final String KEY_SMTP_PORT = "SMTP_PORT";
    public static final String KEY_SMTP_USERNAME = "SMTP_USERNAME";
    public static final String KEY_SMTP_PASSWORD = "SMTP_PASSWORD";
    public static final String KEY_SMTP_FROM_NAME = "SMTP_FROM_NAME";
    public static final String KEY_SMTP_FROM_ADDRESS = "SMTP_FROM_ADDRESS";
    public static final String KEY_APP_PRODUCTION = "APP_PRODUCTION";
    public static final String KEY_APP_NAME = "APP_NAME";

    // FIRM scope keys
    public static final String KEY_BRAND_COLOR_PRIMARY = "BRAND_COLOR_PRIMARY";
    public static final String KEY_BRAND_COLOR_SECONDARY = "BRAND_COLOR_SECONDARY";
    public static final String KEY_EMAIL_FOOTER_TEXT = "EMAIL_FOOTER_TEXT";
    public static final String KEY_EMAIL_SIGNATURE = "EMAIL_SIGNATURE";
    public static final String KEY_TIMEZONE = "TIMEZONE";

    // ═══════════════════════════════════════════════════════════════════════
    // GLOBAL scope operations (super admin)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get a single global config value, decrypting if encrypted.
     */
    public Optional<String> getGlobal(String key) {
        return getDecrypted(SystemConfig.ConfigScope.GLOBAL, null, key);
    }

    /**
     * Get a single global config value, raw from DB (may be encrypted).
     */
    public Optional<String> getGlobalRaw(String key) {
        return systemConfigRepository.findByScopeAndConfigKey(SystemConfig.ConfigScope.GLOBAL, key)
                .map(SystemConfig::getConfigValue);
    }

    /**
     * Get all global config values.
     */
    public Map<String, String> getAllGlobal() {
        return systemConfigRepository.findByScope(SystemConfig.ConfigScope.GLOBAL)
                .stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        this::decryptIfNeeded
                ));
    }

    /**
     * Set a single global config value.
     * Auto-encrypts if key is in SENSITIVE_KEYS.
     */
    @Transactional
    public SystemConfig setGlobal(String key, String value, String description) {
        return setValue(SystemConfig.ConfigScope.GLOBAL, null, key, value, description);
    }

    /**
     * Delete a global config value.
     */
    @Transactional
    public void deleteGlobal(String key) {
        systemConfigRepository.deleteByScopeAndFirmIdAndConfigKey(
                SystemConfig.ConfigScope.GLOBAL, null, key);
        log.info("Deleted global config: {}", key);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIRM scope operations (firm admin)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get a single firm-scoped config value, decrypting if encrypted.
     */
    public Optional<String> getFirm(UUID firmId, String key) {
        return getDecrypted(SystemConfig.ConfigScope.FIRM, firmId, key);
    }

    /**
     * Get all firm-scoped config values as a flat map.
     */
    public Map<String, String> getAllFirm(UUID firmId) {
        return systemConfigRepository.findByScopeAndFirmId(SystemConfig.ConfigScope.FIRM, firmId)
                .stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        this::decryptIfNeeded
                ));
    }

    /**
     * Get all config values for a firm as a simple key-value map.
     * Merges FIRM-scoped values on top of GLOBAL defaults.
     * Firm values override global defaults when both exist.
     */
    public Map<String, String> getEffectiveConfig(UUID firmId) {
        Map<String, String> effective = new HashMap<>(getAllGlobal());
        effective.putAll(getAllFirm(firmId));
        return effective;
    }

    /**
     * Set a firm-scoped config value.
     */
    @Transactional
    public SystemConfig setFirm(UUID firmId, String key, String value, String description) {
        return setValue(SystemConfig.ConfigScope.FIRM, firmId, key, value, description);
    }

    /**
     * Delete a firm-scoped config value.
     */
    @Transactional
    public void deleteFirm(UUID firmId, String key) {
        systemConfigRepository.deleteByScopeAndFirmIdAndConfigKey(
                SystemConfig.ConfigScope.FIRM, firmId, key);
        log.info("Deleted firm config: firmId={}, key={}", firmId, key);
    }

    @Transactional
    public SystemConfig setFirm(UUID firmId, String key, String value) {
        return setFirm(firmId, key, value, null);
    }

    @Transactional
    public SystemConfig setGlobal(String key, String value) {
        return setGlobal(key, value, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bulk operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Set multiple firm-scoped values at once.
     */
    @Transactional
    public List<SystemConfig> setFirmBulk(UUID firmId, Map<String, String> values) {
        List<SystemConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            saved.add(setValue(SystemConfig.ConfigScope.FIRM, firmId, entry.getKey(), entry.getValue(), null));
        }
        return saved;
    }

    /**
     * Set multiple global values at once.
     */
    @Transactional
    public List<SystemConfig> setGlobalBulk(Map<String, String> values) {
        List<SystemConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            saved.add(setValue(SystemConfig.ConfigScope.GLOBAL, null, entry.getKey(), entry.getValue(), null));
        }
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════════════════

    private Optional<String> getDecrypted(SystemConfig.ConfigScope scope, UUID firmId, String key) {
        return systemConfigRepository.findByScopeAndFirmIdAndConfigKey(scope, firmId, key)
                .map(this::decryptIfNeeded);
    }

    private String decryptIfNeeded(SystemConfig config) {
        if (config.isEncrypted() && config.getConfigValue() != null) {
            try {
                return configEncryptionUtil.decrypt(config.getConfigValue());
            } catch (Exception e) {
                log.warn("Failed to decrypt config '{}': {}", config.getConfigKey(), e.getMessage());
                return null;
            }
        }
        return config.getConfigValue();
    }

    private SystemConfig setValue(SystemConfig.ConfigScope scope, UUID firmId, String key, String value, String description) {
        if (value == null) {
            // Null value = delete
            if (firmId != null) {
                deleteFirm(firmId, key);
            } else {
                deleteGlobal(key);
            }
            return null;
        }

        boolean isSensitive = SENSITIVE_KEYS.contains(key);
        String storedValue = isSensitive ? configEncryptionUtil.encrypt(value) : value;

        SystemConfig config = systemConfigRepository
                .findByScopeAndFirmIdAndConfigKey(scope, firmId, key)
                .orElse(SystemConfig.builder()
                        .scope(scope)
                        .firmId(firmId)
                        .configKey(key)
                        .build());

        config.setConfigValue(storedValue);
        config.setEncrypted(isSensitive);
        if (description != null) config.setDescription(description);
        if (config.getDescription() == null) config.setDescription("");

        SystemConfig saved = systemConfigRepository.save(config);
        log.debug("Set config: scope={}, firmId={}, key={}, encrypted={}", scope, firmId, key, isSensitive);
        return saved;
    }
}
