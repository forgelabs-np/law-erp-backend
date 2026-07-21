package com.lawfirm.erp.common.service;

import com.lawfirm.erp.firm.entity.FirmEmailConfig;
import com.lawfirm.erp.firm.service.FirmEmailConfigService;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Single email service used by every other service.
 *
 * Key properties:
 *   1. Firm-aware — automatically picks the firm's SMTP config or falls back to global
 *   2. Async (@Async) — never blocks the main request thread
 *   3. Templated — uses Thymeleaf HTML templates with brand color injection
 *   4. Audited — logs success/failure but never throws to the caller
 *
 * Usage from any service:
 *   emailService.sendWelcomeEmployee(firmId, userEmail, userName, tempPassword);
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SystemConfigService systemConfigService;
    private final FirmEmailConfigService firmEmailConfigService;
    private final AuditService auditService;
    private final TemplateEngine templateEngine;

    // Spring auto-configures this from application.yml spring.mail.* properties.
    // Used as the ultimate fallback when no firm or global DB config is set.
    private final JavaMailSender defaultMailSender;

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — called by other services
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    public void sendWelcomeEmployee(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                                    String username, String tempPassword, String firmName, String firmCode) {
        Map<String, String> cfg = systemConfigService.getEffectiveConfig(firmId);
        String primaryColor = cfg.getOrDefault(SystemConfigService.KEY_BRAND_COLOR_PRIMARY, "#1A237E");
        String footer = cfg.getOrDefault(SystemConfigService.KEY_EMAIL_FOOTER_TEXT, "");
        String loginUrl = cfg.getOrDefault("LOGIN_URL", "https://app.nepalcrm.com/login");
        String appName = cfg.getOrDefault(SystemConfigService.KEY_APP_NAME, "NepalCRM");

        String subject = "Your account at " + firmName + " has been created";

        Context ctx = new Context();
        ctx.setVariable("firmName", firmName);
        ctx.setVariable("firmCode", firmCode);
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("username", username);
        ctx.setVariable("tempPassword", tempPassword);
        ctx.setVariable("loginUrl", loginUrl);
        ctx.setVariable("primaryColor", primaryColor);
        ctx.setVariable("emailFooter", footer);
        ctx.setVariable("appName", appName);

        sendHtmlEmail(firmId, triggeredByUserId, toEmail, subject, "email/welcome-employee", ctx,
                username, AuditEntity.USER);
    }

    @Async
    public void sendWelcomeClient(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                                  String username, String tempPassword, String firmName) {
        Map<String, String> cfg = systemConfigService.getEffectiveConfig(firmId);
        String primaryColor = cfg.getOrDefault(SystemConfigService.KEY_BRAND_COLOR_PRIMARY, "#1A237E");
        String footer = cfg.getOrDefault(SystemConfigService.KEY_EMAIL_FOOTER_TEXT, "");
        String loginUrl = cfg.getOrDefault("CLIENT_PORTAL_URL", "https://app.nepalcrm.com/portal");

        String subject = "Welcome to " + firmName + " Client Portal";

        Context ctx = new Context();
        ctx.setVariable("firmName", firmName);
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("username", username);
        ctx.setVariable("tempPassword", tempPassword);
        ctx.setVariable("loginUrl", loginUrl);
        ctx.setVariable("primaryColor", primaryColor);
        ctx.setVariable("emailFooter", footer);

        sendHtmlEmail(firmId, triggeredByUserId, toEmail, subject, "email/welcome-client", ctx,
                username, AuditEntity.CLIENT);
    }

    @Async
    public void sendWelcomeFirmAdmin(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                                     String username, String tempPassword, String firmName, String firmCode) {
        String loginUrl = "https://app.nepalcrm.com/login";

        Context ctx = new Context();
        ctx.setVariable("firmName", firmName);
        ctx.setVariable("firmCode", firmCode);
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("username", username);
        ctx.setVariable("tempPassword", tempPassword);
        ctx.setVariable("loginUrl", loginUrl);

        sendHtmlEmail(firmId, triggeredByUserId, toEmail, "Your firm " + firmName + " is ready on NepalCRM",
                "email/welcome-firm-admin", ctx, username, AuditEntity.FIRM);
    }

    @Async
    public void sendPasswordReset(UUID firmId, UUID triggeredByUserId, String toEmail, String fullName,
                                  String tempPassword, String firmName) {
        Map<String, String> cfg = systemConfigService.getEffectiveConfig(firmId);
        String primaryColor = cfg.getOrDefault(SystemConfigService.KEY_BRAND_COLOR_PRIMARY, "#1A237E");
        String footer = cfg.getOrDefault(SystemConfigService.KEY_EMAIL_FOOTER_TEXT, "");
        String loginUrl = cfg.getOrDefault("LOGIN_URL", "https://app.nepalcrm.com/login");

        String subject = "Password reset for " + firmName;

        Context ctx = new Context();
        ctx.setVariable("firmName", firmName);
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("tempPassword", tempPassword);
        ctx.setVariable("loginUrl", loginUrl);
        ctx.setVariable("primaryColor", primaryColor);
        ctx.setVariable("emailFooter", footer);

        sendHtmlEmail(firmId, triggeredByUserId, toEmail, subject, "email/password-reset", ctx,
                fullName, AuditEntity.USER);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Core send logic
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Core email sender — resolves SMTP config, renders template, sends async.
     * Never throws — failures are logged and audited.
     */
    private void sendHtmlEmail(UUID firmId, UUID triggeredByUserId, String toEmail, String subject,
                               String template, Context ctx, String recipientIdentifier,
                               AuditEntity auditEntity) {
        try {
            // 1. Resolve SMTP config
            JavaMailSender mailSender = resolveMailSender(firmId);

            // 2. Render HTML template
            String htmlContent = templateEngine.process(template, ctx);

            // 3. Build and send
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            // Set from address based on resolved config
            String fromAddress = resolveFromAddress(firmId);
            String fromName = resolveFromName(firmId);
            helper.setFrom(fromAddress, fromName);

            mailSender.send(message);

            log.info("EMAIL_SENT: to={}, subject={}, firmId={}", toEmail, subject, firmId);
            auditService.logExplicit(firmId, triggeredByUserId, "S",
                    AuditAction.EMAIL_SENT, auditEntity, null,
                    "Email sent to " + toEmail + ": " + subject, null);

        } catch (Exception e) {
            log.error("EMAIL_FAILED: to={}, subject={}, firmId={}, error={}",
                    toEmail, subject, firmId, e.getMessage());
            // Never throw — email failure must not break the main request
            auditService.logExplicit(firmId, triggeredByUserId, "S",
                    AuditAction.EMAIL_FAILED, auditEntity, null,
                    "Email failed to " + toEmail + ": " + e.getMessage(), null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SMTP resolution
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resolve which SMTP server to use for a given firm.
     *
     * Priority:
     *   1. Firm's own FirmEmailConfig (if active)
     *   2. Global SystemConfig SMTP values (from DB, set via /super-admin/config)
     *   3. Spring auto-configured JavaMailSender (from application.yml spring.mail.*)
     */
    private JavaMailSender resolveMailSender(UUID firmId) {
        // 1. Firm-specific SMTP config
        Optional<FirmEmailConfig> firmConfig = firmEmailConfigService.getDecrypted(firmId);
        if (firmConfig.isPresent() && firmConfig.get().isActive()) {
            log.debug("Using firm SMTP config for firm: {}", firmId);
            return createMailSender(
                    firmConfig.get().getSmtpHost(),
                    firmConfig.get().getSmtpPort(),
                    firmConfig.get().getSmtpUsername(),
                    firmConfig.get().getSmtpPassword(),
                    firmConfig.get().isUseTls()
            );
        }

        // 2. Global SMTP from DB (system_config GLOBAL scope)
        Optional<String> dbHost = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_HOST);
        if (dbHost.isPresent()) {
            String host = dbHost.get();
            int port = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_PORT)
                    .map(Integer::parseInt).orElse(587);
            String username = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_USERNAME).orElse("");
            String password = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_PASSWORD).orElse("");

            if (!username.isEmpty() && !password.isEmpty()) {
                log.debug("Using global DB SMTP config for firm: {}", firmId);
                return createMailSender(host, port, username, password, true);
            }
        }

        // 3. Fallback to Spring auto-configured JavaMailSender (from application.yml spring.mail.*)
        log.debug("Using Spring auto-configured mail sender (no DB SMTP config found) for firm: {}", firmId);
        return defaultMailSender;
    }

    private String resolveFromAddress(UUID firmId) {
        // 1. Firm-specific config
        Optional<FirmEmailConfig> firmConfig = firmEmailConfigService.getByFirmId(firmId);
        if (firmConfig.isPresent() && firmConfig.get().isActive()) {
            return firmConfig.get().getFromAddress();
        }
        // 2. Global DB config
        Optional<String> dbFrom = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_FROM_ADDRESS);
        if (dbFrom.isPresent()) {
            return dbFrom.get();
        }
        // 3. From the auto-configured bean's username
        if (defaultMailSender instanceof JavaMailSenderImpl impl) {
            return impl.getUsername();
        }
        return "noreply@nepalcrm.com";
    }

    private String resolveFromName(UUID firmId) {
        // 1. Firm-specific config
        Optional<FirmEmailConfig> firmConfig = firmEmailConfigService.getByFirmId(firmId);
        if (firmConfig.isPresent() && firmConfig.get().isActive()) {
            return firmConfig.get().getFromName();
        }
        // 2. Global DB config
        Optional<String> dbName = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_FROM_NAME);
        if (dbName.isPresent()) {
            return dbName.get();
        }
        // 3. Default
        return "NepalCRM Platform";
    }

    private JavaMailSenderImpl createMailSender(String host, int port, String username,
                                                String password, boolean useTls) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", 10000);
        props.put("mail.smtp.timeout", 10000);
        props.put("mail.smtp.writetimeout", 10000);

        return sender;
    }
}
