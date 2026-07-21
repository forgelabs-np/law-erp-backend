package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import com.lawfirm.erp.firm.entity.FirmEmailConfig;
import com.lawfirm.erp.firm.repository.FirmEmailConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages per-firm SMTP email configuration.
 *
 * Each firm can configure their own SMTP server so outgoing emails
 * come FROM the firm's domain. Falls back to platform global SMTP
 * if not configured or inactive.
 *
 * SMTP password is stored AES-256 encrypted and NEVER returned in API responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirmEmailConfigService {

    private final FirmEmailConfigRepository firmEmailConfigRepository;
    private final ConfigEncryptionUtil configEncryptionUtil;

    /**
     * Get the email config for a firm.
     * Returns null if not configured.
     */
    public Optional<FirmEmailConfig> getByFirmId(UUID firmId) {
        return firmEmailConfigRepository.findByFirmId(firmId);
    }

    /**
     * Get the email config for a firm, with password decrypted for actual sending.
     * Returns null if not configured.
     */
    public Optional<FirmEmailConfig> getDecrypted(UUID firmId) {
        return firmEmailConfigRepository.findByFirmId(firmId)
                .map(this::decryptPassword);
    }

    /**
     * Create or update firm email config.
     * The smtpPassword is encrypted before storage.
     * The response DTO never includes the actual password.
     */
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

    /**
     * Test the SMTP connection for a firm's config.
     * Updates testedAt and testPassed fields.
     */
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

    /**
     * Delete the email config for a firm (reset to platform SMTP).
     */
    @Transactional
    public void delete(UUID firmId) {
        firmEmailConfigRepository.deleteByFirmId(firmId);
        log.info("Deleted email config for firm: {}", firmId);
    }

    private FirmEmailConfig decryptPassword(FirmEmailConfig config) {
        try {
            String decrypted = configEncryptionUtil.decrypt(config.getSmtpPassword());
            config.setSmtpPassword(decrypted);
        } catch (Exception e) {
            log.error("Failed to decrypt SMTP password for firm {}: {}", config.getFirmId(), e.getMessage());
            throw new RuntimeException("Failed to decrypt SMTP password", e);
        }
        return config;
    }

    /**
     * Attempt to connect to the SMTP server with the given credentials.
     * This is a best-effort check — it tries to open a transport connection.
     */
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
