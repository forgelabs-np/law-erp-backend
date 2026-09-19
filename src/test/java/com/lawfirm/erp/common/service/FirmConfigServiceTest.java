package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.entity.FirmConfig;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.FirmConfigRepository;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirmConfigServiceTest {

    private static final String TEST_AES_KEY = "udVnpX0p9MRyQ6w3ephfq9NtttKoqpv+miRg0My2TtQ=";

    @Mock
    private FirmConfigRepository firmConfigRepository;

    @Mock
    private SystemConfigService systemConfigService;

    private ConfigEncryptionUtil configEncryptionUtil;
    private FirmConfigService firmConfigService;

    private final UUID firmId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        configEncryptionUtil = new ConfigEncryptionUtil(TEST_AES_KEY);
        firmConfigService = new FirmConfigService(
                firmConfigRepository, systemConfigService, configEncryptionUtil);
    }

    private FirmConfig row(String key, String value) {
        return FirmConfig.builder()
                .firmId(firmId)
                .configKey(key)
                .configValue(value)
                .active(true)
                .allowEdit(true)
                .inputType("TEXT")
                .build();
    }

    @Nested
    @DisplayName("Scope allowlist")
    class ScopeAllowlist {

        @Test
        @DisplayName("Rejects a GLOBAL-only key")
        void set_rejectsGlobalOnlyKey() {
            assertThrows(BusinessRuleException.class, () -> firmConfigService.set(
                    firmId, SystemConfigService.KEY_LOGIN_URL, "https://evil.example/login"));
            verify(firmConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects an undeclared key")
        void set_rejectsUnknownKey() {
            assertThrows(BusinessRuleException.class,
                    () -> firmConfigService.set(firmId, "TOTALLY_MADE_UP", "x"));
            verify(firmConfigRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Writes")
    class Writes {

        @Test
        @DisplayName("Stamps registry metadata and stores the value for that firm")
        void set_stampsRegistryMetadata() {
            when(firmConfigRepository.findByFirmIdAndConfigKey(firmId, FirmConfigService.KEY_BRAND_COLOR_PRIMARY))
                    .thenReturn(Optional.empty());

            firmConfigService.set(firmId, FirmConfigService.KEY_BRAND_COLOR_PRIMARY, "#123456");

            ArgumentCaptor<FirmConfig> captor = ArgumentCaptor.forClass(FirmConfig.class);
            verify(firmConfigRepository).save(captor.capture());
            FirmConfig saved = captor.getValue();
            assertEquals(firmId, saved.getFirmId());
            assertEquals("BRAND", saved.getConfigGroup());
            assertEquals("#123456", saved.getConfigValue());
            assertFalse(saved.isEncrypted());
        }

        @Test
        @DisplayName("setBulk writes every entry")
        void setBulk_writesAll() {
            when(firmConfigRepository.findByFirmIdAndConfigKey(any(), any())).thenReturn(Optional.empty());

            firmConfigService.setBulk(firmId, Map.of(
                    FirmConfigService.KEY_BRAND_COLOR_PRIMARY, "#123456",
                    FirmConfigService.KEY_TIMEZONE, "Asia/Kathmandu"));

            verify(firmConfigRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("A null value deletes the key instead of storing it")
        void set_nullDeletes() {
            firmConfigService.set(firmId, FirmConfigService.KEY_TIMEZONE, null);

            verify(firmConfigRepository).deleteByFirmIdAndConfigKey(firmId, FirmConfigService.KEY_TIMEZONE);
            verify(firmConfigRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Reads")
    class Reads {

        @Test
        @DisplayName("get returns the stored value for that firm")
        void get_returnsStoredValue() {
            when(firmConfigRepository.findByFirmIdAndConfigKey(firmId, FirmConfigService.KEY_TIMEZONE))
                    .thenReturn(Optional.of(row(FirmConfigService.KEY_TIMEZONE, "Asia/Kathmandu")));

            assertEquals("Asia/Kathmandu",
                    firmConfigService.get(firmId, FirmConfigService.KEY_TIMEZONE).orElseThrow());
        }

        @Test
        @DisplayName("getSettings lists registry defaults for keys with no row")
        void getSettings_includesRegistryDefaults() {
            when(firmConfigRepository.findByFirmId(firmId)).thenReturn(List.of());

            List<SystemConfigSettingView> views = firmConfigService.getSettings(firmId);

            assertEquals(5, views.size());
            assertTrue(views.stream().anyMatch(view ->
                    FirmConfigService.KEY_BRAND_COLOR_PRIMARY.equals(view.getConfigKey())));
        }

        @Test
        @DisplayName("getSettings keeps the stored value when a row exists")
        void getSettings_prefersStoredRow() {
            when(firmConfigRepository.findByFirmId(firmId))
                    .thenReturn(List.of(row(FirmConfigService.KEY_BRAND_COLOR_PRIMARY, "#123456")));

            List<SystemConfigSettingView> views = firmConfigService.getSettings(firmId);

            assertEquals(5, views.size());
            assertEquals("#123456", views.stream()
                    .filter(view -> FirmConfigService.KEY_BRAND_COLOR_PRIMARY.equals(view.getConfigKey()))
                    .findFirst().orElseThrow().getValue());
        }

        @Test
        @DisplayName("getAll skips a row that cannot be decrypted instead of failing")
        void getAll_skipsUndecryptableRow() {
            FirmConfig broken = row(FirmConfigService.KEY_EMAIL_SIGNATURE, "not-valid-base64!!!");
            broken.setEncrypted(true);

            when(firmConfigRepository.findByFirmId(firmId))
                    .thenReturn(List.of(broken, row(FirmConfigService.KEY_TIMEZONE, "Asia/Kathmandu")));

            Map<String, String> values = firmConfigService.getAll(firmId);

            assertEquals("Asia/Kathmandu", values.get(FirmConfigService.KEY_TIMEZONE));
            assertFalse(values.containsKey(FirmConfigService.KEY_EMAIL_SIGNATURE));
        }

        @Test
        @DisplayName("Duplicate rows no longer break reads — newest wins")
        void getAll_toleratesDuplicateRows() {
            FirmConfig older = row(FirmConfigService.KEY_TIMEZONE, "Old");
            older.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            FirmConfig newer = row(FirmConfigService.KEY_TIMEZONE, "New");
            newer.setUpdatedAt(LocalDateTime.of(2026, 2, 1, 0, 0));

            when(firmConfigRepository.findByFirmId(firmId)).thenReturn(List.of(older, newer));

            assertEquals("New", firmConfigService.getAll(firmId).get(FirmConfigService.KEY_TIMEZONE));
        }
    }

    @Nested
    @DisplayName("Effective config")
    class EffectiveConfig {

        @Test
        @DisplayName("Firm values win over platform defaults, global keys still present")
        void effective_firmOverridesGlobal() {
            when(systemConfigService.getAllGlobal()).thenReturn(Map.of(
                    SystemConfigService.KEY_APP_NAME, "NepalCRM",
                    FirmConfigService.KEY_BRAND_COLOR_PRIMARY, "#GLOBAL"));
            when(firmConfigRepository.findByFirmId(firmId))
                    .thenReturn(List.of(row(FirmConfigService.KEY_BRAND_COLOR_PRIMARY, "#FIRM")));

            Map<String, String> effective = firmConfigService.getEffectiveConfig(firmId);

            assertEquals("#FIRM", effective.get(FirmConfigService.KEY_BRAND_COLOR_PRIMARY));
            assertEquals("NepalCRM", effective.get(SystemConfigService.KEY_APP_NAME));
        }

        @Test
        @DisplayName("Global values pass through untouched when the firm has nothing set")
        void effective_fallsBackToGlobal() {
            when(systemConfigService.getAllGlobal()).thenReturn(Map.of(
                    FirmConfigService.KEY_BRAND_COLOR_PRIMARY, "#GLOBAL"));
            when(firmConfigRepository.findByFirmId(firmId)).thenReturn(List.of());

            assertEquals("#GLOBAL", firmConfigService.getEffectiveConfig(firmId)
                    .get(FirmConfigService.KEY_BRAND_COLOR_PRIMARY));
        }
    }
}
