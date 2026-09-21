package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.config.ConfigKeyRegistry;
import com.lawfirm.erp.common.config.ConfigKeyRegistry.SettingDef;
import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.entity.SystemConfig;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.SystemConfigRepository;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GLOBAL (platform-wide) settings. Values live in {@code system_config} and are read from
 * the DB every time they are needed, so a Super Admin change takes effect on the next
 * request without a rebuild. Per-firm values live in {@link FirmConfigService}.
 *
 * Sensitive (PASSWORD-type) values are AES-256 encrypted at rest and decrypted for admins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final ConfigEncryptionUtil configEncryptionUtil;

    /** application.yml app.production — seed default for APP_PRODUCTION only. */
    @Value("${app.production:false}")
    private boolean appProductionFallback;

    // GLOBAL scope keys
    public static final String KEY_SMTP_HOST = "SMTP_HOST";
    public static final String KEY_SMTP_PORT = "SMTP_PORT";
    public static final String KEY_SMTP_USERNAME = "SMTP_USERNAME";
    public static final String KEY_SMTP_PASSWORD = "SMTP_PASSWORD";
    public static final String KEY_SMTP_FROM_NAME = "SMTP_FROM_NAME";
    public static final String KEY_SMTP_FROM_ADDRESS = "SMTP_FROM_ADDRESS";
    public static final String KEY_APP_PRODUCTION = "APP_PRODUCTION";
    public static final String KEY_APP_NAME = "APP_NAME";
    public static final String KEY_LOGIN_URL = "LOGIN_URL";
    public static final String KEY_CLIENT_PORTAL_URL = "CLIENT_PORTAL_URL";
    public static final String KEY_MFA_ENABLED = "MFA_ENABLED";
    public static final String KEY_MFA_REQUIRED_ROLES = "MFA_REQUIRED_ROLES";
    public static final String KEY_REGISTRATION_SECRET = "REGISTRATION_SECRET";
    public static final String KEY_LOGIN_MAX_ATTEMPTS = "LOGIN_MAX_ATTEMPTS";
    public static final String KEY_LOGIN_LOCK_MINUTES = "LOGIN_LOCK_MINUTES";
    public static final String KEY_TRIAL_DEFAULT_DAYS = "TRIAL_DEFAULT_DAYS";
    public static final String KEY_TRIAL_WARNING_DAYS = "TRIAL_WARNING_DAYS";
    public static final String KEY_NOTIFICATION_MAX_ATTEMPTS = "NOTIFICATION_MAX_ATTEMPTS";

    /** Fallbacks, mirrored as registry defaults. */
    public static final String DEFAULT_LOGIN_URL = "https://app.nepalcrm.com/login";
    public static final String DEFAULT_CLIENT_PORTAL_URL = "https://app.nepalcrm.com/portal";

    // ========================================================================
    // Reads
    // ========================================================================

    public Optional<String> getGlobal(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .filter(SystemConfig::isActive)
                .map(this::decryptIfNeeded);
    }

    public Map<String, String> getAllGlobal() {
        return toValueMap(systemConfigRepository.findAll());
    }

    /** All active GLOBAL settings with their metadata, sorted by group then key. */
    public List<SystemConfigSettingView> getGlobalSettings() {
        return toViews(systemConfigRepository.findAll());
    }

    // ========================================================================
    // Writes
    // ========================================================================

    @Transactional
    public SystemConfig setGlobal(String key, String value) {
        return setGlobal(key, value, null);
    }

    @Transactional
    public SystemConfig setGlobal(String key, String value, String description) {
        return setValue(key, value, description);
    }

    @Transactional
    public List<SystemConfig> setGlobalBulk(Map<String, String> values) {
        List<SystemConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            saved.add(setValue(entry.getKey(), entry.getValue(), null));
        }
        return saved;
    }

    @Transactional
    public void deleteGlobal(String key) {
        checkNotLocked(key);
        systemConfigRepository.deleteByConfigKey(key);
        log.info("Deleted global config: {}", key);
    }

    /**
     * Inserts default rows for every registry entry marked seed. Insert-if-missing only:
     * a value an admin has edited is never touched on restart.
     */
    @Transactional
    public void seedGlobalDefaults() {
        int inserted = 0;
        for (SettingDef def : ConfigKeyRegistry.GLOBAL) {
            if (!def.seed) {
                continue;
            }
            if (systemConfigRepository.findByConfigKey(def.key).isPresent()) {
                continue;
            }

            String storedValue = seedValueFor(def);
            boolean encrypted = false;
            if (ConfigKeyRegistry.isSensitiveType(def.inputType) && storedValue != null && !storedValue.isBlank()) {
                storedValue = configEncryptionUtil.encrypt(storedValue);
                encrypted = true;
            }

            systemConfigRepository.save(SystemConfig.builder()
                    .configKey(def.key)
                    .configValue(storedValue)
                    .encrypted(encrypted)
                    .configGroup(def.group)
                    .inputType(def.inputType)
                    .allowedValues(def.allowedValues)
                    .active(true)
                    .allowEdit(def.allowEdit)
                    .description(def.description)
                    .build());
            inserted++;
            log.info("  + SystemConfig default seeded: {} = {}", def.key, def.defaultValue);
        }
        if (inserted > 0) {
            log.info("Seeded {} default system config entries", inserted);
        }
    }

    // ========================================================================
    // Runtime policy helpers (DB-driven, code defaults when key absent)
    // ========================================================================

    /** MFA enforcement on/off. Defaults to true (fail closed) when the key is absent. */
    public boolean isMfaEnabled() {
        Optional<String> raw = getGlobal(KEY_MFA_ENABLED);
        if (raw.isEmpty()) {
            return true;
        }
        String value = raw.get().trim();
        if (value.equalsIgnoreCase("Y") || value.equalsIgnoreCase("TRUE")) {
            return true;
        }
        if (value.equalsIgnoreCase("N") || value.equalsIgnoreCase("FALSE")) {
            return false;
        }
        log.warn("MFA_ENABLED has unrecognized value '{}' — defaulting to enabled", value);
        return true;
    }

    /** Role codes that must have MFA enabled. Defaults to SUPER_ADMIN,FIRM_ADMIN when absent. */
    public Set<String> mfaRequiredRoleCodes() {
        Optional<String> raw = getGlobal(KEY_MFA_REQUIRED_ROLES);
        if (raw.isEmpty()) {
            return Set.of("SUPER_ADMIN", "FIRM_ADMIN");
        }
        Set<String> roles = Arrays.stream(raw.get().split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toSet());
        return roles.isEmpty() ? Set.of() : roles;
    }

    /** Failed logins before lockout. Default 5. */
    public int loginMaxAttempts() {
        return intValue(KEY_LOGIN_MAX_ATTEMPTS, 5);
    }

    /** Lockout duration in minutes. Default 30. */
    public int loginLockMinutes() {
        return intValue(KEY_LOGIN_LOCK_MINUTES, 30);
    }

    /** Trial days when a firm is created without one. Default 14. */
    public int trialDefaultDays() {
        return intValue(KEY_TRIAL_DEFAULT_DAYS, 14);
    }

    /** Days before trial expiry to warn. Default 3. */
    public int trialWarningDays() {
        return intValue(KEY_TRIAL_WARNING_DAYS, 3);
    }

    /** Attempts before a notification is marked DEAD. Default 3. */
    public int notificationMaxAttempts() {
        return intValue(KEY_NOTIFICATION_MAX_ATTEMPTS, 3);
    }

    /** APP_PRODUCTION (Y/N); empty when unset so callers can fall back to application.yml. */
    public Optional<Boolean> productionFlag() {
        return getGlobal(KEY_APP_PRODUCTION)
                .filter(value -> !value.isBlank())
                .map(this::parseYesNo);
    }

    /** Login link for email. GLOBAL-only, so a firm cannot repoint it. */
    public String loginUrl() {
        return getGlobal(KEY_LOGIN_URL).filter(value -> !value.isBlank()).orElse(DEFAULT_LOGIN_URL);
    }

    /** Client portal link for email. GLOBAL-only. */
    public String clientPortalUrl() {
        return getGlobal(KEY_CLIENT_PORTAL_URL).filter(value -> !value.isBlank())
                .orElse(DEFAULT_CLIENT_PORTAL_URL);
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    /** Registry default, except APP_PRODUCTION which follows application.yml. */
    private String seedValueFor(SettingDef def) {
        if (KEY_APP_PRODUCTION.equals(def.key)) {
            return appProductionFallback ? "Y" : "N";
        }
        return def.defaultValue;
    }

    private List<SystemConfigSettingView> toViews(List<SystemConfig> rows) {
        return rows.stream()
                .filter(SystemConfig::isActive)
                .map(row -> SystemConfigSettingView.builder()
                        .configKey(row.getConfigKey())
                        .configGroup(row.getConfigGroup())
                        .value(decryptIfNeeded(row))
                        .inputType(row.getInputType() != null ? row.getInputType() : "TEXT")
                        .allowedValues(row.getAllowedValues())
                        .description(row.getDescription())
                        .active(row.isActive())
                        .allowEdit(row.isAllowEdit())
                        .sensitive(ConfigKeyRegistry.isSensitiveType(row.getInputType()))
                        .build())
                .sorted(Comparator.comparing(SystemConfigSettingView::getConfigGroup,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(SystemConfigSettingView::getConfigKey))
                .collect(Collectors.toList());
    }

    /**
     * Key→value map that survives duplicate rows (Postgres does not enforce uniqueness
     * when firm_id is NULL) and values that fail to decrypt (a changed encryption key).
     * Both used to throw from Collectors.toMap and break every read. Newest row wins.
     */
    private Map<String, String> toValueMap(List<SystemConfig> rows) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, LocalDateTime> stamps = new HashMap<>();

        for (SystemConfig row : rows) {
            if (!row.isActive()) continue;

            String value = decryptIfNeeded(row);
            if (value == null) {
                log.warn("Config '{}' could not be decrypted and was skipped — check that "
                        + "config.encryption.key matches the key it was written with", row.getConfigKey());
                continue;
            }

            String key = row.getConfigKey();
            if (!values.containsKey(key)) {
                values.put(key, value);
                stamps.put(key, row.getUpdatedAt());
                continue;
            }

            log.warn("Duplicate GLOBAL config row for key '{}' — using the most recently updated value", key);
            LocalDateTime existing = stamps.get(key);
            if (row.getUpdatedAt() != null
                    && (existing == null || row.getUpdatedAt().isAfter(existing))) {
                values.put(key, value);
                stamps.put(key, row.getUpdatedAt());
            }
        }
        return values;
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

    /** GLOBAL numeric setting; code default when absent or invalid. */
    private int intValue(String key, int fallback) {
        Optional<String> raw = getGlobal(key);
        if (raw.isEmpty() || raw.get().isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.get().trim());
        } catch (NumberFormatException e) {
            log.warn("Config '{}' has non-numeric value '{}' — using default {}", key, raw.get(), fallback);
            return fallback;
        }
    }

    /** Y/N or TRUE/FALSE → boolean; unrecognized values are treated as N. */
    private boolean parseYesNo(String value) {
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("Y") || trimmed.equalsIgnoreCase("TRUE")) return true;
        if (trimmed.equalsIgnoreCase("N") || trimmed.equalsIgnoreCase("FALSE")) return false;
        log.warn("Config value '{}' is not Y/N — treating as N", value);
        return false;
    }

    /** Blocks delete of rows whose value is locked (allowEdit=false, already set). */
    private void checkNotLocked(String key) {
        systemConfigRepository.findByConfigKey(key)
                .ifPresent(row -> {
                    if (!row.isAllowEdit() && hasValue(row)) {
                        throw new BusinessRuleException(
                                "Config key '" + key + "' is locked and cannot be deleted");
                    }
                });
    }

    private boolean hasValue(SystemConfig row) {
        String raw = row.getConfigValue();
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (!row.isEncrypted()) {
            return true;
        }
        try {
            String decrypted = configEncryptionUtil.decrypt(raw);
            return decrypted != null && !decrypted.isBlank();
        } catch (Exception e) {
            return true;
        }
    }

    private SystemConfig setValue(String key, String value, String description) {
        if (value == null) {
            deleteGlobal(key);
            return null;
        }

        // Only registry-declared GLOBAL keys are writable — a FIRM key set here would be
        // invisible to the firm that owns it.
        SettingDef def = ConfigKeyRegistry.requireGlobal(key);
        SystemConfig config = systemConfigRepository.findByConfigKey(key).orElse(null);

        String inputType = config != null && config.getInputType() != null
                ? config.getInputType() : def.inputType;
        String allowedValues = config != null ? config.getAllowedValues() : def.allowedValues;
        boolean allowEdit = config != null ? config.isAllowEdit() : def.allowEdit;

        if (!allowEdit && config != null && hasValue(config)) {
            throw new BusinessRuleException(
                    "Config key '" + key + "' is locked after its initial set and cannot be changed via the API");
        }

        ConfigKeyRegistry.validate(def, key, value);

        boolean sensitive = ConfigKeyRegistry.isSensitiveType(inputType) || (config != null && config.isEncrypted());
        String storedValue = sensitive ? configEncryptionUtil.encrypt(value) : value;

        if (config == null) {
            config = SystemConfig.builder()
                    .configKey(key)
                    .configGroup(def.group)
                    .build();
        }
        config.setConfigValue(storedValue);
        config.setEncrypted(sensitive);
        config.setInputType(inputType);
        config.setAllowedValues(allowedValues);
        config.setConfigGroup(def.group);
        config.setAllowEdit(def.allowEdit);
        if (config.getDescription() == null || description != null) {
            config.setDescription(description != null ? description : "");
        }
        if (config.getActive() == null) {
            config.setActive(true);
        }

        SystemConfig saved = systemConfigRepository.save(config);
        log.debug("Set global config: key={}, encrypted={}", key, sensitive);
        return saved;
    }
}
