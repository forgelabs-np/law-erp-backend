package com.lawfirm.erp.common.config;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.service.FirmConfigService;
import com.lawfirm.erp.common.service.SystemConfigService;

import java.util.Arrays;
import java.util.List;

public final class ConfigKeyRegistry {

    public static final String GROUP_APP = "APP";
    public static final String GROUP_SECURITY = "SECURITY";
    public static final String GROUP_EMAIL = "EMAIL";
    public static final String GROUP_TRIAL = "TRIAL";
    public static final String GROUP_NOTIFICATION = "NOTIFICATION";
    public static final String GROUP_BRAND = "BRAND";
    public static final String GROUP_STORAGE = "STORAGE";

    public static final List<SettingDef> GLOBAL = List.of(
            SettingDef.def(SystemConfigService.KEY_APP_NAME, GROUP_APP, "TEXT", null,
                    "NepalCRM", true, "Platform display name", true),
            SettingDef.def(SystemConfigService.KEY_APP_PRODUCTION, GROUP_APP, "RADIO", "Y,N",
                    "N", true, "Production mode (Y/N)", true),
            SettingDef.def(SystemConfigService.KEY_LOGIN_URL, GROUP_APP, "URL", null,
                    SystemConfigService.DEFAULT_LOGIN_URL, true, "Login link embedded in outgoing emails", true),
            SettingDef.def(SystemConfigService.KEY_CLIENT_PORTAL_URL, GROUP_APP, "URL", null,
                    SystemConfigService.DEFAULT_CLIENT_PORTAL_URL, true,
                    "Client portal link embedded in outgoing emails", true),

            // SECURITY
            SettingDef.def(SystemConfigService.KEY_MFA_ENABLED, GROUP_SECURITY, "RADIO", "Y,N",
                    "Y", true, "Enable or disable MFA enforcement for required roles", true),
            SettingDef.def(SystemConfigService.KEY_MFA_REQUIRED_ROLES, GROUP_SECURITY, "TEXT", null,
                    "SUPER_ADMIN,FIRM_ADMIN", true,
                    "Comma-separated roles that must have MFA enabled", true),
            // Not seeded: the yml app.security.mfa-dev-bypass value is the fallback, so a dev
            // database keeps the bypass with no row. Production mode blocks it regardless.
            SettingDef.def(SystemConfigService.KEY_MFA_DEV_BYPASS, GROUP_SECURITY, "RADIO", "Y,N",
                    "N", true, "Dev TOTP bypass — never effective while production mode is on", false),
            SettingDef.def(SystemConfigService.KEY_REGISTRATION_SECRET, GROUP_SECURITY, "PASSWORD", null,
                    "", false, "Secret required to register the first super admin. Leave empty to keep registration disabled.", true),
            SettingDef.def(SystemConfigService.KEY_LOGIN_MAX_ATTEMPTS, GROUP_SECURITY, "NUMBER", null,
                    "5", true, "Failed logins allowed before the account is locked", true),
            SettingDef.def(SystemConfigService.KEY_LOGIN_LOCK_MINUTES, GROUP_SECURITY, "NUMBER", null,
                    "30", true, "Minutes an account stays locked after too many failed logins", true),
            // Not seeded: blank means "use jwt.access-expiry from yml", so the value stays optional.
            SettingDef.def(SystemConfigService.KEY_ACCESS_TOKEN_EXPIRY_MINUTES, GROUP_SECURITY, "NUMBER", null,
                    "1440", true, "Access-token lifetime in minutes (unset → jwt.access-expiry)", false),

            SettingDef.def(SystemConfigService.KEY_TRIAL_DEFAULT_DAYS, GROUP_TRIAL, "NUMBER", null,
                    "14", true, "Trial length used when a firm is created without explicit trial days", true),
            SettingDef.def(SystemConfigService.KEY_TRIAL_WARNING_DAYS, GROUP_TRIAL, "NUMBER", null,
                    "3", true, "Days before trial expiry that the warning notification is sent", true),

            SettingDef.def(SystemConfigService.KEY_NOTIFICATION_MAX_ATTEMPTS, GROUP_NOTIFICATION, "NUMBER", null,
                    "3", true, "Delivery attempts before a notification is marked DEAD", true),

            SettingDef.def(SystemConfigService.KEY_STORAGE_MAX_FILE_SIZE_BYTES, GROUP_STORAGE, "NUMBER", null,
                    String.valueOf(SystemConfigService.DEFAULT_STORAGE_MAX_FILE_SIZE_BYTES), true,
                    "Maximum size of a single uploaded file, in bytes", true),
            SettingDef.def(SystemConfigService.KEY_STORAGE_UPLOAD_EXPIRY_SECONDS, GROUP_STORAGE, "NUMBER", null,
                    String.valueOf(SystemConfigService.DEFAULT_STORAGE_UPLOAD_EXPIRY_SECONDS), true,
                    "How long an upload ticket stays valid, in seconds", true),
            SettingDef.def(SystemConfigService.KEY_STORAGE_DOWNLOAD_EXPIRY_SECONDS, GROUP_STORAGE, "NUMBER", null,
                    String.valueOf(SystemConfigService.DEFAULT_STORAGE_DOWNLOAD_EXPIRY_SECONDS), true,
                    "How long a download link stays valid, in seconds", true),
            SettingDef.def(SystemConfigService.KEY_STORAGE_DEFAULT_QUOTA_BYTES, GROUP_STORAGE, "NUMBER", null,
                    String.valueOf(SystemConfigService.DEFAULT_STORAGE_QUOTA_BYTES), true,
                    "Storage given to a firm on first use, in bytes (0 = unlimited)", true),
            SettingDef.def(SystemConfigService.KEY_DOCUMENT_MAX_FILENAME_LENGTH, GROUP_STORAGE, "NUMBER", null,
                    String.valueOf(SystemConfigService.DEFAULT_DOCUMENT_MAX_FILENAME_LENGTH), true,
                    "Maximum length of the filename segment stored in the object key", true),

            SettingDef.def(SystemConfigService.KEY_SMTP_HOST, GROUP_EMAIL, "TEXT", null,
                    null, true, "SMTP host", false),
            SettingDef.def(SystemConfigService.KEY_SMTP_PORT, GROUP_EMAIL, "NUMBER", null,
                    null, true, "SMTP port", false),
            SettingDef.def(SystemConfigService.KEY_SMTP_USERNAME, GROUP_EMAIL, "TEXT", null,
                    null, true, "SMTP username", false),
            SettingDef.def(SystemConfigService.KEY_SMTP_PASSWORD, GROUP_EMAIL, "PASSWORD", null,
                    null, true, "SMTP password", false),
            SettingDef.def(SystemConfigService.KEY_SMTP_FROM_NAME, GROUP_EMAIL, "TEXT", null,
                    null, true, "From name used on outgoing email", false),
            SettingDef.def(SystemConfigService.KEY_SMTP_FROM_ADDRESS, GROUP_EMAIL, "TEXT", null,
                    null, true, "From address used on outgoing email", false)
    );

    public static final List<SettingDef> FIRM = List.of(
            SettingDef.def(FirmConfigService.KEY_BRAND_COLOR_PRIMARY, GROUP_BRAND, "TEXT", null,
                    null, true, "Primary brand color (hex)", false),
            SettingDef.def(FirmConfigService.KEY_BRAND_COLOR_SECONDARY, GROUP_BRAND, "TEXT", null,
                    null, true, "Secondary brand color (hex)", false),
            SettingDef.def(FirmConfigService.KEY_EMAIL_FOOTER_TEXT, GROUP_BRAND, "TEXT", null,
                    null, true, "Email footer text", false),
            SettingDef.def(FirmConfigService.KEY_EMAIL_SIGNATURE, GROUP_BRAND, "TEXT", null,
                    null, true, "Email signature", false),
            SettingDef.def(FirmConfigService.KEY_TIMEZONE, GROUP_BRAND, "TEXT", null,
                    null, true, "Firm timezone", false)
    );

    private ConfigKeyRegistry() {
    }

    public static SettingDef findGlobal(String key) {
        return find(GLOBAL, key);
    }

    public static SettingDef findFirm(String key) {
        return find(FIRM, key);
    }

    public static SettingDef requireGlobal(String key) {
        return require(GLOBAL, key, "GLOBAL");
    }

    public static SettingDef requireFirm(String key) {
        return require(FIRM, key, "FIRM");
    }

    public static boolean isSensitiveType(String inputType) {
        return "PASSWORD".equals(inputType);
    }

    public static void validate(SettingDef def, String key, String value) {
        String trimmed = value.trim();
        if (("RADIO".equals(def.inputType) || "DROPDOWN".equals(def.inputType))
                && def.allowedValues != null && !def.allowedValues.isBlank()) {
            boolean allowed = Arrays.stream(def.allowedValues.split(","))
                    .map(String::trim)
                    .anyMatch(option -> option.equalsIgnoreCase(trimmed));
            if (!allowed) {
                throw new BusinessRuleException(
                        "Invalid value '" + value + "' for '" + key + "'. Allowed values: " + def.allowedValues);
            }
        }
        if ("NUMBER".equals(def.inputType)) {
            try {
                Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                throw new BusinessRuleException(
                        "Invalid value '" + value + "' for '" + key + "'. Expected a number.");
            }
        }
        if ("URL".equals(def.inputType)
                && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BusinessRuleException(
                    "Invalid value '" + value + "' for '" + key + "'. Expected an http(s) URL.");
        }
    }

    private static SettingDef find(List<SettingDef> defs, String key) {
        return defs.stream().filter(def -> def.key.equals(key)).findFirst().orElse(null);
    }

    private static SettingDef require(List<SettingDef> defs, String key, String scope) {
        SettingDef def = find(defs, key);
        if (def == null) {
            throw new BusinessRuleException(
                    "Unknown config key '" + key + "' for scope " + scope
                            + ". Only keys declared for that scope can be set.");
        }
        return def;
    }

    public static final class SettingDef {
        public final String key;
        public final String group;
        public final String inputType;
        public final String allowedValues;
        public final String defaultValue;
        public final boolean allowEdit;
        public final String description;
        public final boolean seed;

        private SettingDef(String key, String group, String inputType, String allowedValues,
                           String defaultValue, boolean allowEdit, String description, boolean seed) {
            this.key = key;
            this.group = group;
            this.inputType = inputType;
            this.allowedValues = allowedValues;
            this.defaultValue = defaultValue;
            this.allowEdit = allowEdit;
            this.description = description;
            this.seed = seed;
        }

        static SettingDef def(String key, String group, String inputType, String allowedValues,
                              String defaultValue, boolean allowEdit, String description, boolean seed) {
            return new SettingDef(key, group, inputType, allowedValues,
                    defaultValue, allowEdit, description, seed);
        }
    }
}
