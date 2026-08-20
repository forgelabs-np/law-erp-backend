package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.firm.entity.FirmEmailConfig;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import com.lawfirm.erp.firm.repository.FirmEmailConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Per-firm SMTP config. Passwords AES-256 encrypted, never returned in API. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirmEmailConfigServiceImpl implements FirmEmailConfigService {

    private final FirmEmailConfigRepository firmEmailConfigRepository;
    private final ConfigEncryptionUtil configEncryptionUtil;

    public Optional<FirmEmailConfig> getByFirmId(UUID firmId) {
        return firmEmailConfigRepository.findByFirmId(firmId);
    }

    /** Returns config with password decrypted for sending. */
    public Optional<FirmEmailConfig> getDecrypted(UUID firmId) {
        return firmEmailConfigRepository.findByFirmId(firmId)
                .map(this::decryptPassword);
    }

    @Transactional
    public FirmEmailConfig save(UUID firmId, FirmEmailConfig config) {
        FirmEmailConfig existing = firmEmailConfigRepository.findByFirmId(firmId)
                .orElse(FirmEmailConfig.builder()
                        .firmId(firmId)
                        .build());

        existing.setSmtpHost(config.getSmtpHost());
        existing.setSmtpPort(config.getSmtpPort());
        existing.setSmtpUsername(config.getSmtpUsername());

        // Only encrypt+store if a new password was provided
        if (config.getSmtpPassword() != null && !config.getSmtpPassword().isBlank()
                && !config.getSmtpPassword().equals("__UNCHANGED__")) {
            existing.setSmtpPassword(configEncryptionUtil.encrypt(config.getSmtpPassword()));
            log.debug("Encrypted SMTP password for firm: {}", firmId);
        }

        existing.setFromName(config.getFromName());
        existing.setFromAddress(config.getFromAddress());
        existing.setUseTls(config.isUseTls());
        existing.setActive(config.isActive());

        FirmEmailConfig saved = firmEmailConfigRepository.save(existing);
        log.info("Saved email config for firm: {}", firmId);
        return saved;
    }

    @Transactional
    public boolean testConnection(UUID firmId) {
        FirmEmailConfig config = firmEmailConfigRepository.findByFirmId(firmId)
                .orElseThrow(() -> new RuntimeException("Email config not found for firm: " + firmId));

        FirmEmailConfig decrypted = decryptPassword(config);
        boolean passed = tryConnect(decrypted);

        config.setTestedAt(LocalDateTime.now());
        config.setTestPassed(passed);
        firmEmailConfigRepository.save(config);

        log.info("SMTP test for firm {}: {}", firmId, passed ? "PASSED" : "FAILED");
        return passed;
    }

    @Transactional
    public void delete(UUID firmId) {
        firmEmailConfigRepository.deleteByFirmId(firmId);
        log.info("Deleted email config for firm: {}", firmId);
    }

    /** Returns unmanaged copy with decrypted password. Never mutate managed JPA entities in place. */
    private FirmEmailConfig decryptPassword(FirmEmailConfig config) {
        try {
            String decrypted = configEncryptionUtil.decrypt(config.getSmtpPassword());

            // Build a NEW, unmanaged FirmEmailConfig with decrypted password
            FirmEmailConfig copy = FirmEmailConfig.builder()
                    .id(config.getId())
                    .firmId(config.getFirmId())
                    .smtpHost(config.getSmtpHost())
                    .smtpPort(config.getSmtpPort())
                    .smtpUsername(config.getSmtpUsername())
                    .smtpPassword(decrypted)
                    .fromName(config.getFromName())
                    .fromAddress(config.getFromAddress())
                    .useTls(config.isUseTls())
                    .isActive(config.isActive())
                    .build();
            return copy;
        } catch (Exception e) {
            log.error("Failed to decrypt SMTP password for firm {}: {}", config.getFirmId(), e.getMessage());
            throw new RuntimeException("Failed to decrypt SMTP password", e);
        }
    }

    private boolean tryConnect(FirmEmailConfig config) {
        try {
            java.util.Properties props = new java.util.Properties();
            props.put("mail.smtp.host", config.getSmtpHost());
            props.put("mail.smtp.port", config.getSmtpPort());
            props.put("mail.smtp.auth", "true");
            if (config.isUseTls()) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            props.put("mail.smtp.connectiontimeout", 10000);
            props.put("mail.smtp.timeout", 10000);

            jakarta.mail.Session session = jakarta.mail.Session.getInstance(props, null);
            jakarta.mail.Transport transport = session.getTransport("smtp");
            transport.connect(config.getSmtpHost(), config.getSmtpPort(),
                    config.getSmtpUsername(), config.getSmtpPassword());
            transport.close();
            return true;
        } catch (Exception e) {
            log.warn("SMTP test connection failed for firm {}: {}", config.getFirmId(), e.getMessage());
            return false;
        }
    }
}
