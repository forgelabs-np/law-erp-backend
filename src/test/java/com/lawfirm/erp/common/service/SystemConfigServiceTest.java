package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.entity.SystemConfig;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.SystemConfigRepository;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    private static final String TEST_AES_KEY = "udVnpX0p9MRyQ6w3ephfq9NtttKoqpv+miRg0My2TtQ=";

    @Mock
    private SystemConfigRepository systemConfigRepository;

    private ConfigEncryptionUtil configEncryptionUtil;
    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        configEncryptionUtil = new ConfigEncryptionUtil(TEST_AES_KEY);
        systemConfigService = new SystemConfigService(systemConfigRepository, configEncryptionUtil);
    }

    private SystemConfig row(String key, String value) {
        return SystemConfig.builder()
                .scope(SystemConfig.ConfigScope.GLOBAL)
                .configKey(key)
                .configValue(value)
                .active(true)
                .allowEdit(true)
                .inputType("TEXT")
                .build();
    }

    @Nested
    @DisplayName("MFA policy helpers")
    class MfaPolicy {

        @Test
        @DisplayName("MFA enabled defaults to true when the key is absent (safe default)")
        void mfaEnabled_defaultsTrue_whenKeyAbsent() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_ENABLED))
                    .thenReturn(Optional.empty());

            assertTrue(systemConfigService.isMfaEnabled());
        }

        @Test
        @DisplayName("MFA enabled parses Y and N values")
        void mfaEnabled_parsesYAndN() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_ENABLED))
                    .thenReturn(Optional.of(row(SystemConfigService.KEY_MFA_ENABLED, "N")));
            assertFalse(systemConfigService.isMfaEnabled());

            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_ENABLED))
                    .thenReturn(Optional.of(row(SystemConfigService.KEY_MFA_ENABLED, "Y")));
            assertTrue(systemConfigService.isMfaEnabled());
        }

        @Test
        @DisplayName("Required roles default to SUPER_ADMIN,FIRM_ADMIN when absent")
        void requiredRoles_defaults_whenKeyAbsent() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_REQUIRED_ROLES))
                    .thenReturn(Optional.empty());

            Set<String> roles = systemConfigService.mfaRequiredRoleCodes();

            assertEquals(Set.of("SUPER_ADMIN", "FIRM_ADMIN"), roles);
        }

        @Test
        @DisplayName("Required roles parses CSV and trims whitespace")
        void requiredRoles_parsesCsv() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_REQUIRED_ROLES))
                    .thenReturn(Optional.of(row(SystemConfigService.KEY_MFA_REQUIRED_ROLES, " ADVOCATE, SUPER_ADMIN ")));

            Set<String> roles = systemConfigService.mfaRequiredRoleCodes();

            assertEquals(Set.of("ADVOCATE", "SUPER_ADMIN"), roles);
        }
    }

    @Nested
    @DisplayName("PUT validation")
    class Validation {

        @Test
        @DisplayName("Rejects a RADIO value outside its allowed values")
        void setGlobal_rejectsRadioValueOutsideAllowed() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_ENABLED))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class,
                    () -> systemConfigService.setGlobal(SystemConfigService.KEY_MFA_ENABLED, "MAYBE"));
            verify(systemConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects a NUMBER value that does not parse")
        void setGlobal_rejectsInvalidNumber() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_SMTP_PORT))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class,
                    () -> systemConfigService.setGlobal(SystemConfigService.KEY_SMTP_PORT, "abc"));
            verify(systemConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("Accepts a valid RADIO value and stamps registry metadata on a new row")
        void setGlobal_stampsMetadataForKnownKey() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_MFA_ENABLED))
                    .thenReturn(Optional.empty());

            systemConfigService.setGlobal(SystemConfigService.KEY_MFA_ENABLED, "N");

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(systemConfigRepository).save(captor.capture());
            SystemConfig saved = captor.getValue();
            assertEquals("RADIO", saved.getInputType());
            assertEquals("Y,N", saved.getAllowedValues());
            assertEquals("SECURITY", saved.getConfigGroup());
            assertEquals("N", saved.getConfigValue());
            assertFalse(saved.isEncrypted());
        }

        @Test
        @DisplayName("Encrypts PASSWORD-type values at rest")
        void setGlobal_encryptsPasswordType() {
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_SMTP_PASSWORD))
                    .thenReturn(Optional.empty());

            systemConfigService.setGlobal(SystemConfigService.KEY_SMTP_PASSWORD, "s3cret-pass");

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(systemConfigRepository).save(captor.capture());
            SystemConfig saved = captor.getValue();
            assertTrue(saved.isEncrypted());
            assertNotEquals("s3cret-pass", saved.getConfigValue());
            assertEquals("s3cret-pass", configEncryptionUtil.decrypt(saved.getConfigValue()));
        }
    }

    @Nested
    @DisplayName("Locked keys (allowEdit=false)")
    class LockedKeys {

        @Test
        @DisplayName("REGISTRATION_SECRET can be set once from empty, then rejects further changes")
        void registrationSecret_setOnceThenLocked() {
            SystemConfig emptyRow = row(SystemConfigService.KEY_REGISTRATION_SECRET, "");
            emptyRow.setInputType("PASSWORD");
            emptyRow.setAllowEdit(false);

            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_REGISTRATION_SECRET))
                    .thenReturn(Optional.empty(), Optional.of(emptyRow));

            // First set from empty → allowed
            systemConfigService.setGlobal(SystemConfigService.KEY_REGISTRATION_SECRET, "first-secret");
            verify(systemConfigRepository, times(1)).save(any());

            // Second set → rejected
            SystemConfig storedRow = row(SystemConfigService.KEY_REGISTRATION_SECRET,
                    configEncryptionUtil.encrypt("first-secret"));
            storedRow.setInputType("PASSWORD");
            storedRow.setAllowEdit(false);
            storedRow.setEncrypted(true);

            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_REGISTRATION_SECRET))
                    .thenReturn(Optional.of(storedRow));

            assertThrows(BusinessRuleException.class,
                    () -> systemConfigService.setGlobal(SystemConfigService.KEY_REGISTRATION_SECRET, "other-secret"));
            verify(systemConfigRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Deleting a locked key with a value throws")
        void deleteLockedKey_throws() {
            SystemConfig storedRow = row(SystemConfigService.KEY_REGISTRATION_SECRET,
                    configEncryptionUtil.encrypt("first-secret"));
            storedRow.setInputType("PASSWORD");
            storedRow.setAllowEdit(false);
            storedRow.setEncrypted(true);

            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    SystemConfig.ConfigScope.GLOBAL, null, SystemConfigService.KEY_REGISTRATION_SECRET))
                    .thenReturn(Optional.of(storedRow));

            assertThrows(BusinessRuleException.class,
                    () -> systemConfigService.deleteGlobal(SystemConfigService.KEY_REGISTRATION_SECRET));
            verify(systemConfigRepository, never()).deleteByScopeAndFirmIdAndConfigKey(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Admin settings views")
    class SettingsViews {

        @Test
        @DisplayName("Returns decrypted values with metadata for admins (no masking)")
        void getGlobalSettings_returnsPlaintextWithMetadata() {
            SystemConfig smtpPassword = row(SystemConfigService.KEY_SMTP_PASSWORD,
                    configEncryptionUtil.encrypt("s3cret-pass"));
            smtpPassword.setEncrypted(true);
            smtpPassword.setInputType("PASSWORD");
            smtpPassword.setConfigGroup("EMAIL");
            smtpPassword.setDescription("SMTP password");

            when(systemConfigRepository.findByScope(SystemConfig.ConfigScope.GLOBAL))
                    .thenReturn(List.of(smtpPassword));

            List<SystemConfigSettingView> views = systemConfigService.getGlobalSettings();

            assertEquals(1, views.size());
            SystemConfigSettingView view = views.get(0);
            assertEquals(SystemConfigService.KEY_SMTP_PASSWORD, view.getConfigKey());
            assertEquals("s3cret-pass", view.getValue());          // plaintext, no mask
            assertEquals("PASSWORD", view.getInputType());
            assertEquals("EMAIL", view.getConfigGroup());
            assertEquals("SMTP password", view.getDescription());
            assertTrue(view.isSensitive());
            assertTrue(view.isAllowEdit());
        }

        @Test
        @DisplayName("Excludes inactive rows from the settings list")
        void getGlobalSettings_excludesInactive() {
            SystemConfig active = row(SystemConfigService.KEY_APP_NAME, "NepalCRM");
            active.setConfigGroup("APP");
            SystemConfig inactive = row("SOME_DISABLED_KEY", "x");
            inactive.setActive(false);

            when(systemConfigRepository.findByScope(SystemConfig.ConfigScope.GLOBAL))
                    .thenReturn(List.of(active, inactive));

            List<SystemConfigSettingView> views = systemConfigService.getGlobalSettings();

            assertEquals(1, views.size());
            assertEquals(SystemConfigService.KEY_APP_NAME, views.get(0).getConfigKey());
        }
    }

    @Nested
    @DisplayName("Default registry seed")
    class RegistrySeed {

        @Test
        @DisplayName("Inserts only missing defaults, never overwrites existing rows")
        void seed_insertsOnlyMissing() {
            // Broad default stub first, then the key-specific stub — Mockito gives
            // precedence to the most recently declared matching stub.
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    eq(SystemConfig.ConfigScope.GLOBAL), isNull(), anyString()))
                    .thenReturn(Optional.empty());
            when(systemConfigRepository.findByScopeAndFirmIdAndConfigKey(
                    eq(SystemConfig.ConfigScope.GLOBAL), isNull(), eq(SystemConfigService.KEY_APP_NAME)))
                    .thenReturn(Optional.of(row(SystemConfigService.KEY_APP_NAME, "Custom Name")));

            systemConfigService.seedGlobalDefaults();

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(systemConfigRepository, times(4)).save(captor.capture()); // APP_NAME exists → skipped

            List<SystemConfig> saved = captor.getAllValues();
            assertTrue(saved.stream().noneMatch(c -> SystemConfigService.KEY_APP_NAME.equals(c.getConfigKey())));

            SystemConfig mfaEnabled = saved.stream()
                    .filter(c -> SystemConfigService.KEY_MFA_ENABLED.equals(c.getConfigKey()))
                    .findFirst().orElseThrow();
            assertEquals("Y", mfaEnabled.getConfigValue());
            assertEquals("RADIO", mfaEnabled.getInputType());
            assertEquals("Y,N", mfaEnabled.getAllowedValues());
            assertEquals("SECURITY", mfaEnabled.getConfigGroup());

            SystemConfig registrationSecret = saved.stream()
                    .filter(c -> SystemConfigService.KEY_REGISTRATION_SECRET.equals(c.getConfigKey()))
                    .findFirst().orElseThrow();
            assertEquals("PASSWORD", registrationSecret.getInputType());
            assertFalse(registrationSecret.isAllowEdit());
        }
    }
}
