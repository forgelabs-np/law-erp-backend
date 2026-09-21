package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.config.ConfigKeyRegistry;
import com.lawfirm.erp.common.config.ConfigKeyRegistry.SettingDef;
import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.entity.FirmConfig;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.FirmConfigRepository;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-firm settings. Values live in {@code firm_configs}, one row per firm + key, and are
 * read from the DB every time they are needed so a Firm Admin change is live on the next
 * request. Platform-wide values live in {@link SystemConfigService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirmConfigService {

    private final FirmConfigRepository firmConfigRepository;
    private final SystemConfigService systemConfigService;
    private final ConfigEncryptionUtil configEncryptionUtil;

    // FIRM scope keys
    public static final String KEY_BRAND_COLOR_PRIMARY = "BRAND_COLOR_PRIMARY";
    public static final String KEY_BRAND_COLOR_SECONDARY = "BRAND_COLOR_SECONDARY";
    public static final String KEY_EMAIL_FOOTER_TEXT = "EMAIL_FOOTER_TEXT";
    public static final String KEY_EMAIL_SIGNATURE = "EMAIL_SIGNATURE";
    public static final String KEY_TIMEZONE = "TIMEZONE";

    // ========================================================================
    // Reads
    // ========================================================================

    public Optional<String> get(UUID firmId, String key) {
        return firmConfigRepository.findByFirmIdAndConfigKey(firmId, key)
                .filter(FirmConfig::isActive)
                .map(this::decryptIfNeeded);
    }

    public Map<String, String> getAll(UUID firmId) {
        return toValueMap(firmConfigRepository.findByFirmId(firmId));
    }

    /** This firm's values over the platform defaults. */
    public Map<String, String> getEffectiveConfig(UUID firmId) {
        Map<String, String> effective = new HashMap<>(systemConfigService.getAllGlobal());
        effective.putAll(getAll(firmId));
        return effective;
    }

    /**
     * This firm's settings with metadata. Registry keys with no row for this firm get
     * their default value, so a new firm's settings screen still lists everything it can
     * set — FIRM keys are deliberately not boot-seeded.
     */
    public List<SystemConfigSettingView> getSettings(UUID firmId) {
        List<FirmConfig> rows = firmConfigRepository.findByFirmId(firmId);
        List<SystemConfigSettingView> views = new ArrayList<>(toViews(rows));
        Set<String> present = rows.stream()
                .filter(FirmConfig::isActive)
                .map(FirmConfig::getConfigKey)
                .collect(Collectors.toSet());

        for (SettingDef def : ConfigKeyRegistry.FIRM) {
            if (present.contains(def.key)) {
                continue;
            }
            views.add(SystemConfigSettingView.builder()
                    .configKey(def.key)
                    .configGroup(def.group)
                    .value(def.defaultValue)
                    .inputType(def.inputType)
                    .allowedValues(def.allowedValues)
                    .description(def.description)
                    .active(true)
                    .allowEdit(def.allowEdit)
                    .sensitive(ConfigKeyRegistry.isSensitiveType(def.inputType))
                    .build());
        }
        return views.stream()
                .sorted(Comparator.comparing(SystemConfigSettingView::getConfigGroup,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(SystemConfigSettingView::getConfigKey))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // Writes
    // ========================================================================

    @Transactional
    public FirmConfig set(UUID firmId, String key, String value) {
        return set(firmId, key, value, null);
    }

    @Transactional
    public FirmConfig set(UUID firmId, String key, String value, String description) {
        return setValue(firmId, key, value, description);
    }

    @Transactional
    public List<FirmConfig> setBulk(UUID firmId, Map<String, String> values) {
        List<FirmConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            saved.add(setValue(firmId, entry.getKey(), entry.getValue(), null));
        }
        return saved;
    }

    @Transactional
    public void delete(UUID firmId, String key) {
        checkNotLocked(firmId, key);
        firmConfigRepository.deleteByFirmIdAndConfigKey(firmId, key);
        log.info("Deleted firm config: firmId={}, key={}", firmId, key);
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private List<SystemConfigSettingView> toViews(List<FirmConfig> rows) {
        return rows.stream()
                .filter(FirmConfig::isActive)
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
                .collect(Collectors.toList());
    }

    /** Hard-unique per firm (firm_id is NOT NULL), but decrypt failures are still skipped rather than fatal. */
    private Map<String, String> toValueMap(List<FirmConfig> rows) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, LocalDateTime> stamps = new HashMap<>();

        for (FirmConfig row : rows) {
            if (!row.isActive()) continue;

            String value = decryptIfNeeded(row);
            if (value == null) {
                log.warn("Firm config '{}' could not be decrypted and was skipped — check that "
                        + "config.encryption.key matches the key it was written with", row.getConfigKey());
                continue;
            }

            String key = row.getConfigKey();
            if (!values.containsKey(key)) {
                values.put(key, value);
                stamps.put(key, row.getUpdatedAt());
                continue;
            }

            log.warn("Duplicate firm config row for firm {} key '{}' — using the most recently updated value",
                    row.getFirmId(), key);
            LocalDateTime existing = stamps.get(key);
            if (row.getUpdatedAt() != null
                    && (existing == null || row.getUpdatedAt().isAfter(existing))) {
                values.put(key, value);
                stamps.put(key, row.getUpdatedAt());
            }
        }
        return values;
    }

    private String decryptIfNeeded(FirmConfig config) {
        if (config.isEncrypted() && config.getConfigValue() != null) {
            try {
                return configEncryptionUtil.decrypt(config.getConfigValue());
            } catch (Exception e) {
                log.warn("Failed to decrypt firm config '{}': {}", config.getConfigKey(), e.getMessage());
                return null;
            }
        }
        return config.getConfigValue();
    }

    /** Blocks delete of rows whose value is locked (allowEdit=false, already set). */
    private void checkNotLocked(UUID firmId, String key) {
        firmConfigRepository.findByFirmIdAndConfigKey(firmId, key)
                .ifPresent(row -> {
                    if (!row.isAllowEdit() && hasValue(row)) {
                        throw new BusinessRuleException(
                                "Config key '" + key + "' is locked and cannot be deleted");
                    }
                });
    }

    private boolean hasValue(FirmConfig row) {
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

    private FirmConfig setValue(UUID firmId, String key, String value, String description) {
        if (value == null) {
            delete(firmId, key);
            return null;
        }

        // Only registry-declared FIRM keys are writable — without this a firm admin could
        // create rows for platform keys and override the GLOBAL value.
        SettingDef def = ConfigKeyRegistry.requireFirm(key);
        FirmConfig config = firmConfigRepository.findByFirmIdAndConfigKey(firmId, key).orElse(null);

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
            config = FirmConfig.builder()
                    .firmId(firmId)
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

        FirmConfig saved = firmConfigRepository.save(config);
        log.debug("Set firm config: firmId={}, key={}, encrypted={}", firmId, key, sensitive);
        return saved;
    }
}
