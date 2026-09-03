package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.entity.SystemConfig;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.SystemConfigRepository;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the system_config key-value store. GLOBAL or FIRM scope.
 *
 * Rows carry settings metadata (config group, input type, allowed values, active,
 * allow-edit) so the admin SETTINGS UI can render and validate each key, and so
 * runtime behavior (MFA policy, SMTP, branding) can be changed from the DB without
 * a rebuild. Sensitive (PASSWORD-type) values are AES-256 encrypted at rest and
 * decrypted for admins on read.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final ConfigEncryptionUtil configEncryptionUtil;

    // ========================================================================
    // Known keys
    // ========================================================================

    // GLOBAL scope keys
    public static final String KEY_SMTP_HOST = "SMTP_HOST";
    public static final String KEY_SMTP_PORT = "SMTP_PORT";
    public static final String KEY_SMTP_USERNAME = "SMTP_USERNAME";
    public static final String KEY_SMTP_PASSWORD = "SMTP_PASSWORD";
    public static final String KEY_SMTP_FROM_NAME = "SMTP_FROM_NAME";
    public static final String KEY_SMTP_FROM_ADDRESS = "SMTP_FROM_ADDRESS";
    public static final String KEY_APP_PRODUCTION = "APP_PRODUCTION";
    public static final String KEY_APP_NAME = "APP_NAME";

    // SECURITY group keys
    public static final String KEY_MFA_ENABLED = "MFA_ENABLED";
    public static final String KEY_MFA_REQUIRED_ROLES = "MFA_REQUIRED_ROLES";
    public static final String KEY_REGISTRATION_SECRET = "REGISTRATION_SECRET";

    // FIRM scope keys
    public static final String KEY_BRAND_COLOR_PRIMARY = "BRAND_COLOR_PRIMARY";
    public static final String KEY_BRAND_COLOR_SECONDARY = "BRAND_COLOR_SECONDARY";
    public static final String KEY_EMAIL_FOOTER_TEXT = "EMAIL_FOOTER_TEXT";
    public static final String KEY_EMAIL_SIGNATURE = "EMAIL_SIGNATURE";
    public static final String KEY_TIMEZONE = "TIMEZONE";

    // ========================================================================
    // Settings registry — one place defining every known key: its group, input
    // type, allowed values, whether the platform seeds a default on boot, and
    // whether the value is locked after first set.
    // ========================================================================

    private static final String GROUP_APP = "APP";
    private static final String GROUP_SECURITY = "SECURITY";
    private static final String GROUP_EMAIL = "EMAIL";
    private static final String GROUP_BRAND = "BRAND";

    private static final List<SettingDef> REGISTRY = List.of(
            // ── APP (GLOBAL) ────────────────────────────────────────────────
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_APP_NAME, GROUP_APP, "TEXT", null,
                    "NepalCRM", true, "Platform display name", true),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_APP_PRODUCTION, GROUP_APP, "RADIO", "Y,N",
                    "N", true, "Production mode (Y/N)", true),

            // ── SECURITY (GLOBAL) ───────────────────────────────────────────
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_MFA_ENABLED, GROUP_SECURITY, "RADIO", "Y,N",
                    "Y", true, "Enable or disable MFA enforcement for required roles", true),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_MFA_REQUIRED_ROLES, GROUP_SECURITY, "TEXT", null,
                    "SUPER_ADMIN,FIRM_ADMIN", true,
                    "Comma-separated roles that must have MFA enabled", true),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_REGISTRATION_SECRET, GROUP_SECURITY, "PASSWORD", null,
                    "", false, "Secret required to register the first super admin. Leave empty to keep registration disabled.", true),

            // ── EMAIL (GLOBAL, metadata only — no boot seed so empty SMTP keys never shadow yml fallback)
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_SMTP_HOST, GROUP_EMAIL, "TEXT", null,
                    null, true, "SMTP host", false),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_SMTP_PORT, GROUP_EMAIL, "NUMBER", null,
                    null, true, "SMTP port", false),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_SMTP_USERNAME, GROUP_EMAIL, "TEXT", null,
                    null, true, "SMTP username", false),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_SMTP_PASSWORD, GROUP_EMAIL, "PASSWORD", null,
                    null, true, "SMTP password", false),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_SMTP_FROM_NAME, GROUP_EMAIL, "TEXT", null,
                    null, true, "From name used on outgoing email", false),
            SettingDef.def(SystemConfig.ConfigScope.GLOBAL, KEY_SMTP_FROM_ADDRESS, GROUP_EMAIL, "TEXT", null,
                    null, true, "From address used on outgoing email", false),

            // ── BRAND (FIRM scope, metadata only — firms brand themselves)
            SettingDef.def(SystemConfig.ConfigScope.FIRM, KEY_BRAND_COLOR_PRIMARY, GROUP_BRAND, "TEXT", null,
                    null, true, "Primary brand color (hex)", false),
            SettingDef.def(SystemConfig.ConfigScope.FIRM, KEY_BRAND_COLOR_SECONDARY, GROUP_BRAND, "TEXT", null,
                    null, true, "Secondary brand color (hex)", false),
            SettingDef.def(SystemConfig.ConfigScope.FIRM, KEY_EMAIL_FOOTER_TEXT, GROUP_BRAND, "TEXT", null,
                    null, true, "Email footer text", false),
            SettingDef.def(SystemConfig.ConfigScope.FIRM, KEY_EMAIL_SIGNATURE, GROUP_BRAND, "TEXT", null,
                    null, true, "Email signature", false),
            SettingDef.def(SystemConfig.ConfigScope.FIRM, KEY_TIMEZONE, GROUP_BRAND, "TEXT", null,
                    null, true, "Firm timezone", false)
    );

    // ========================================================================
    // GLOBAL scope operations
    // ========================================================================

    public Optional<String> getGlobal(String key) {
        return getDecrypted(SystemConfig.ConfigScope.GLOBAL, null, key);
    }

    public Optional<String> getGlobalRaw(String key) {
        return systemConfigRepository.findByScopeAndConfigKey(SystemConfig.ConfigScope.GLOBAL, key)
                .map(SystemConfig::getConfigValue);
    }

    public Map<String, String> getAllGlobal() {
        return systemConfigRepository.findByScope(SystemConfig.ConfigScope.GLOBAL)
                .stream()
                .filter(SystemConfig::isActive)
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        this::decryptIfNeeded
                ));
    }

    @Transactional
    public SystemConfig setGlobal(String key, String value, String description) {
        return setValue(SystemConfig.ConfigScope.GLOBAL, null, key, value, description);
    }

    @Transactional
    public void deleteGlobal(String key) {
        checkNotLocked(SystemConfig.ConfigScope.GLOBAL, null, key);
        systemConfigRepository.deleteByScopeAndFirmIdAndConfigKey(
                SystemConfig.ConfigScope.GLOBAL, null, key);
        log.info("Deleted global config: {}", key);
    }

    // ========================================================================
    // FIRM scope operations
    // ========================================================================

    public Optional<String> getFirm(UUID firmId, String key) {
        return getDecrypted(SystemConfig.ConfigScope.FIRM, firmId, key);
    }

    public Map<String, String> getAllFirm(UUID firmId) {
        return systemConfigRepository.findByScopeAndFirmId(SystemConfig.ConfigScope.FIRM, firmId)
                .stream()
                .filter(SystemConfig::isActive)
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        this::decryptIfNeeded
                ));
    }

    /** Merges FIRM-scoped values on top of GLOBAL defaults. */
    public Map<String, String> getEffectiveConfig(UUID firmId) {
        Map<String, String> effective = new HashMap<>(getAllGlobal());
        effective.putAll(getAllFirm(firmId));
        return effective;
    }

    @Transactional
    public SystemConfig setFirm(UUID firmId, String key, String value, String description) {
        return setValue(SystemConfig.ConfigScope.FIRM, firmId, key, value, description);
    }

    @Transactional
    public void deleteFirm(UUID firmId, String key) {
        checkNotLocked(SystemConfig.ConfigScope.FIRM, firmId, key);
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

    @Transactional
    public List<SystemConfig> setFirmBulk(UUID firmId, Map<String, String> values) {
        List<SystemConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            saved.add(setValue(SystemConfig.ConfigScope.FIRM, firmId, entry.getKey(), entry.getValue(), null));
        }
        return saved;
    }

    @Transactional
    public List<SystemConfig> setGlobalBulk(Map<String, String> values) {
        List<SystemConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            saved.add(setValue(SystemConfig.ConfigScope.GLOBAL, null, entry.getKey(), entry.getValue(), null));
        }
        return saved;
    }

    // ========================================================================
    // Runtime policy helpers (DB-driven, code defaults when key absent)
    // ========================================================================

    /**
     * Whether MFA enforcement is on. Reads the SECURITY/MFA_ENABLED value (Y/N)
     * from the DB every call — a super admin flipping this takes effect without
     * a rebuild. Defaults to true (enforced) when the key is absent.
     */
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

    /**
     * Role codes that must have MFA enabled. Reads SECURITY/MFA_REQUIRED_ROLES
     * (comma-separated) from the DB every call. Defaults to SUPER_ADMIN,FIRM_ADMIN
     * when the key is absent (matches pre-config behavior).
     */
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

    // ========================================================================
    // Admin settings views (grouped, typed, decrypted plaintext for admins)
    // ========================================================================

    /** All active GLOBAL settings with their metadata, sorted by group then key. */
    public List<SystemConfigSettingView> getGlobalSettings() {
        return toViews(systemConfigRepository.findByScope(SystemConfig.ConfigScope.GLOBAL));
    }

    /** All active FIRM settings for a firm with their metadata, sorted by group then key. */
    public List<SystemConfigSettingView> getFirmSettings(UUID firmId) {
        return toViews(systemConfigRepository.findByScopeAndFirmId(SystemConfig.ConfigScope.FIRM, firmId));
    }

    // ========================================================================
    // Boot seed — inserts registry defaults once, never overwrites edits
    // ========================================================================

    /**
     * Inserts default rows for every registry entry marked {@code seed=true}.
     * Insert-if-missing only: values edited by an admin are never touched on
     * restart. Called by DataInitializer on boot.
     */
    @Transactional
    public void seedGlobalDefaults() {
        int inserted = 0;
        for (SettingDef def : REGISTRY) {
            if (!def.seed || def.scope != SystemConfig.ConfigScope.GLOBAL) {
                continue;
            }
            boolean exists = systemConfigRepository
                    .findByScopeAndFirmIdAndConfigKey(SystemConfig.ConfigScope.GLOBAL, null, def.key)
                    .isPresent();
            if (exists) {
                continue;
            }

            String storedValue = def.defaultValue;
            boolean encrypted = false;
            if (isSensitiveType(def.inputType) && storedValue != null && !storedValue.isBlank()) {
                storedValue = configEncryptionUtil.encrypt(storedValue);
                encrypted = true;
            }

            SystemConfig config = SystemConfig.builder()
                    .scope(SystemConfig.ConfigScope.GLOBAL)
                    .configKey(def.key)
                    .configValue(storedValue)
                    .encrypted(encrypted)
                    .configGroup(def.group)
                    .inputType(def.inputType)
                    .allowedValues(def.allowedValues)
                    .active(true)
                    .allowEdit(def.allowEdit)
                    .description(def.description)
                    .build();
            systemConfigRepository.save(config);
            inserted++;
            log.info("  + SystemConfig default seeded: {} = {}", def.key, def.defaultValue);
        }
        if (inserted > 0) {
            log.info("Seeded {} default system config entries", inserted);
        }
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

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
                        .sensitive(isSensitiveType(row.getInputType()))
                        .build())
                .sorted(Comparator.comparing(SystemConfigSettingView::getConfigGroup,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(SystemConfigSettingView::getConfigKey))
                .collect(Collectors.toList());
    }

    private Optional<String> getDecrypted(SystemConfig.ConfigScope scope, UUID firmId, String key) {
        return systemConfigRepository.findByScopeAndFirmIdAndConfigKey(scope, firmId, key)
                .filter(SystemConfig::isActive)
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

    private boolean isSensitiveType(String inputType) {
        return "PASSWORD".equals(inputType);
    }

    /** Blocks delete of rows whose value is locked (allowEdit=false, already set). */
    private void checkNotLocked(SystemConfig.ConfigScope scope, UUID firmId, String key) {
        systemConfigRepository.findByScopeAndFirmIdAndConfigKey(scope, firmId, key)
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

    private SystemConfig setValue(SystemConfig.ConfigScope scope, UUID firmId, String key,
                                  String value, String description) {
        if (value == null) {
            // Null value = delete
            if (firmId != null) {
                deleteFirm(firmId, key);
            } else {
                deleteGlobal(key);
            }
            return null;
        }

        SettingDef def = findDef(scope, key);
        SystemConfig config = systemConfigRepository
                .findByScopeAndFirmIdAndConfigKey(scope, firmId, key)
                .orElse(null);

        // ── Effective metadata: existing row wins, else registry def, else defaults
        String inputType = config != null && config.getInputType() != null
                ? config.getInputType() : (def != null ? def.inputType : "TEXT");
        String allowedValues = config != null ? config.getAllowedValues()
                : (def != null ? def.allowedValues : null);
        boolean allowEdit = config != null ? config.isAllowEdit()
                : (def == null || def.allowEdit);

        // ── Locked key: only settable from empty, never changed afterwards
        if (!allowEdit && config != null && hasValue(config)) {
            throw new BusinessRuleException(
                    "Config key '" + key + "' is locked after its initial set and cannot be changed via the API");
        }

        // ── Validate against metadata (registry constraints hold even on insert)
        String trimmed = value.trim();
        if (("RADIO".equals(inputType) || "DROPDOWN".equals(inputType))
                && allowedValues != null && !allowedValues.isBlank()) {
            boolean allowed = Arrays.stream(allowedValues.split(","))
                    .map(String::trim)
                    .anyMatch(option -> option.equalsIgnoreCase(trimmed));
            if (!allowed) {
                throw new BusinessRuleException(
                        "Invalid value '" + value + "' for '" + key + "'. Allowed values: " + allowedValues);
            }
        }
        if ("NUMBER".equals(inputType)) {
            try {
                Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                throw new BusinessRuleException(
                        "Invalid value '" + value + "' for '" + key + "'. Expected a number.");
            }
        }

        // ── Encrypt PASSWORD-type values (and anything already encrypted)
        boolean sensitive = isSensitiveType(inputType) || (config != null && config.isEncrypted());
        String storedValue = sensitive ? configEncryptionUtil.encrypt(value) : value;

        if (config == null) {
            config = SystemConfig.builder()
                    .scope(scope)
                    .firmId(firmId)
                    .configKey(key)
                    .build();
            if (def != null) {
                config.setConfigGroup(def.group);
            }
        }
        config.setConfigValue(storedValue);
        config.setEncrypted(sensitive);
        config.setInputType(inputType);
        config.setAllowedValues(allowedValues);
        if (def != null) {
            config.setConfigGroup(def.group);
            config.setAllowEdit(def.allowEdit);
        }
        if (config.getDescription() == null || description != null) {
            config.setDescription(description != null ? description : "");
        }
        if (config.getActive() == null) {
            config.setActive(true);
        }

        SystemConfig saved = systemConfigRepository.save(config);
        log.debug("Set config: scope={}, firmId={}, key={}, encrypted={}", scope, firmId, key, sensitive);
        return saved;
    }

    private static SettingDef findDef(SystemConfig.ConfigScope scope, String key) {
        return REGISTRY.stream()
                .filter(def -> def.scope == scope && def.key.equals(key))
                .findFirst()
                .orElse(null);
    }

    /** Immutable definition of one known config key. */
    private static final class SettingDef {
        final SystemConfig.ConfigScope scope;
        final String key;
        final String group;
        final String inputType;
        final String allowedValues;
        final String defaultValue;
        final boolean allowEdit;
        final String description;
        final boolean seed;

        private SettingDef(SystemConfig.ConfigScope scope, String key, String group, String inputType,
                           String allowedValues, String defaultValue, boolean allowEdit,
                           String description, boolean seed) {
            this.scope = scope;
            this.key = key;
            this.group = group;
            this.inputType = inputType;
            this.allowedValues = allowedValues;
            this.defaultValue = defaultValue;
            this.allowEdit = allowEdit;
            this.description = description;
            this.seed = seed;
        }

        static SettingDef def(SystemConfig.ConfigScope scope, String key, String group, String inputType,
                              String allowedValues, String defaultValue, boolean allowEdit,
                              String description, boolean seed) {
            return new SettingDef(scope, key, group, inputType, allowedValues,
                    defaultValue, allowEdit, description, seed);
        }
    }
}
