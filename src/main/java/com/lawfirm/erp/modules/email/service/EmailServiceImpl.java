package com.lawfirm.erp.modules.email.service;

import com.lawfirm.erp.common.entity.SystemConfig;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.firm.entity.FirmEmailConfig;
import com.lawfirm.erp.firm.service.FirmEmailConfigService;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.modules.casemanagement.entity.HearingReminderLog;
import com.lawfirm.erp.modules.casemanagement.repository.HearingReminderLogRepository;
import com.lawfirm.erp.modules.email.dto.HearingReminderDetails;
import jakarta.mail.MessagingException;
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

import org.springframework.core.io.ByteArrayResource;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final SystemConfigService systemConfigService;
    private final FirmEmailConfigService firmEmailConfigService;
    private final AuditService auditService;
    private final TemplateEngine templateEngine;
    private final JavaMailSender defaultMailSender;
    private final HearingReminderLogRepository hearingReminderLogRepository;

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    @Async
    public void sendHearingReminder(UUID firmId, UUID recipientUserId, String toEmail, String fullName,
                                    HearingReminderDetails details,
                                    HearingReminderLog.RecipientType recipientType,
                                    UUID reminderLogId) {
        Map<String, String> cfg = systemConfigService.getEffectiveConfig(firmId);
        String primaryColor = cfg.getOrDefault(SystemConfigService.KEY_BRAND_COLOR_PRIMARY, "#1A237E");
        String footer = cfg.getOrDefault(SystemConfigService.KEY_EMAIL_FOOTER_TEXT, "");
        String portalUrl = cfg.getOrDefault("CLIENT_PORTAL_URL", "https://app.nepalcrm.com/portal");
        String loginUrl = cfg.getOrDefault("LOGIN_URL", "https://app.nepalcrm.com/login");

        String dateText = details.scheduledDate()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"));
        String timeText = details.scheduledTime() != null
                ? details.scheduledTime().format(DateTimeFormatter.ofPattern("h:mm a"))
                : "To be confirmed";

        String subject = (recipientType == HearingReminderLog.RecipientType.CLIENT
                ? "Reminder: your hearing at " + details.courtName() + " is tomorrow"
                : "Hearing reminder: " + details.courtCaseRef() + " — " + details.courtName() + " tomorrow");

        Context ctx = new Context();
        ctx.setVariable("firmName", details.firmName());
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("recipientType", recipientType.name());
        ctx.setVariable("matterNumber", details.matterNumber());
        ctx.setVariable("matterTitle", details.matterTitle());
        ctx.setVariable("courtCaseRef", details.courtCaseRef());
        ctx.setVariable("courtName", details.courtName());
        ctx.setVariable("hearingDate", dateText);
        ctx.setVariable("hearingTime", timeText);
        ctx.setVariable("courtRoom", details.courtRoom());
        ctx.setVariable("judgeName", details.judgeName());
        ctx.setVariable("portalUrl", portalUrl);
        ctx.setVariable("loginUrl", loginUrl);
        ctx.setVariable("primaryColor", primaryColor);
        ctx.setVariable("emailFooter", footer);

        boolean ok = sendHtmlEmail(firmId, recipientUserId, toEmail, subject,
                "email/hearing-reminder", ctx, toEmail, AuditEntity.COURT_EVENT);
        if (!ok && reminderLogId != null) {
            try {
                hearingReminderLogRepository.findById(reminderLogId)
                        .ifPresent(log -> {
                            log.setStatus(HearingReminderLog.Status.FAILED);
                            hearingReminderLogRepository.save(log);
                        });
            } catch (Exception e) {
                log.warn("Could not update hearing-reminder log {}: {}", reminderLogId, e.getMessage());
            }
        }
    }

    @Override
    @Async
    public void sendInvoiceEmail(UUID firmId, UUID recipientUserId, String toEmail,
                                 String firmName, String invoiceNumber,
                                 BigDecimal total, byte[] pdfBytes) {
        String subject = "Invoice " + invoiceNumber + " from NepalCRM Platform";

        Context ctx = new Context();
        ctx.setVariable("firmName", firmName);
        ctx.setVariable("invoiceNumber", invoiceNumber);
        ctx.setVariable("total", total);
        ctx.setVariable("appName", "NepalCRM Platform");

        try {
            JavaMailSender mailSender = resolveMailSender(firmId);
            String htmlContent = templateEngine.process("email/invoice", ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(pdfBytes), "application/pdf");

            String fromAddress = resolveFromAddress(firmId);
            String fromName = resolveFromName(firmId);
            helper.setFrom(fromAddress, fromName);

            mailSender.send(message);

            log.info("INVOICE_EMAIL_SENT: to={}, invoice={}, firmId={}", toEmail, invoiceNumber, firmId);
            auditService.logExplicit(firmId, recipientUserId, "S",
                    AuditAction.EMAIL_SENT, AuditEntity.INVOICE, null,
                    "Invoice email sent to " + toEmail + ": " + invoiceNumber, null);

        } catch (Exception e) {
            log.error("INVOICE_EMAIL_FAILED: to={}, invoice={}, error={}", toEmail, invoiceNumber, e.getMessage());
            auditService.logExplicit(firmId, recipientUserId, "S",
                    AuditAction.EMAIL_FAILED, AuditEntity.INVOICE, null,
                    "Invoice email failed to " + toEmail + ": " + e.getMessage(), null);
        }
    }

    @Override
    public boolean sendNotificationEmail(UUID firmId, UUID recipientUserId, String toEmail,
                                         String subject,
                                         com.lawfirm.erp.modules.notification.entity.Notification notification) {
        Map<String, String> cfg = systemConfigService.getEffectiveConfig(firmId);
        String primaryColor = cfg.getOrDefault(SystemConfigService.KEY_BRAND_COLOR_PRIMARY, "#1A237E");
        String footer = cfg.getOrDefault(SystemConfigService.KEY_EMAIL_FOOTER_TEXT, "");
        String loginUrl = cfg.getOrDefault("LOGIN_URL", "https://app.nepalcrm.com/login");

        Context ctx = new Context();
        ctx.setVariable("title", notification.getTitle());
        ctx.setVariable("body", notification.getBody());
        ctx.setVariable("category", notification.getCategory() != null
                ? notification.getCategory().name() : "SYSTEM");
        ctx.setVariable("loginUrl", loginUrl);
        ctx.setVariable("primaryColor", primaryColor);
        ctx.setVariable("emailFooter", footer);
        ctx.setVariable("appName", cfg.getOrDefault(SystemConfigService.KEY_APP_NAME, "NepalCRM"));

        return sendHtmlEmail(firmId, recipientUserId, toEmail, subject,
                "email/notification", ctx, toEmail, AuditEntity.USER);
    }

    /** @return true when the email was handed to the SMTP server. */
    private boolean sendHtmlEmail(UUID firmId, UUID triggeredByUserId, String toEmail, String subject,
                                  String template, Context ctx, String recipientIdentifier,
                                  AuditEntity auditEntity) {
        try {
            JavaMailSender mailSender = resolveMailSender(firmId);
            String htmlContent = templateEngine.process(template, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            String fromAddress = resolveFromAddress(firmId);
            String fromName = resolveFromName(firmId);
            helper.setFrom(fromAddress, fromName);

            mailSender.send(message);

            log.info("EMAIL_SENT: to={}, subject={}, firmId={}", toEmail, subject, firmId);
            auditService.logExplicit(firmId, triggeredByUserId, "S",
                    AuditAction.EMAIL_SENT, auditEntity, null,
                    "Email sent to " + toEmail + ": " + subject, null);
            return true;

        } catch (Exception e) {
            log.error("EMAIL_FAILED: to={}, subject={}, firmId={}, error={}",
                    toEmail, subject, firmId, e.getMessage());
            auditService.logExplicit(firmId, triggeredByUserId, "S",
                    AuditAction.EMAIL_FAILED, auditEntity, null,
                    "Email failed to " + toEmail + ": " + e.getMessage(), null);
            return false;
        }
    }

    private JavaMailSender resolveMailSender(UUID firmId) {
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

        log.debug("Using Spring auto-configured mail sender (no DB SMTP config found) for firm: {}", firmId);
        return defaultMailSender;
    }

    private String resolveFromAddress(UUID firmId) {
        Optional<FirmEmailConfig> firmConfig = firmEmailConfigService.getByFirmId(firmId);
        if (firmConfig.isPresent() && firmConfig.get().isActive()) {
            return firmConfig.get().getFromAddress();
        }
        Optional<String> dbFrom = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_FROM_ADDRESS);
        if (dbFrom.isPresent()) {
            return dbFrom.get();
        }
        if (defaultMailSender instanceof JavaMailSenderImpl impl) {
            return impl.getUsername();
        }
        return "noreply@nepalcrm.com";
    }

    private String resolveFromName(UUID firmId) {
        Optional<FirmEmailConfig> firmConfig = firmEmailConfigService.getByFirmId(firmId);
        if (firmConfig.isPresent() && firmConfig.get().isActive()) {
            return firmConfig.get().getFromName();
        }
        Optional<String> dbName = systemConfigService.getGlobal(SystemConfigService.KEY_SMTP_FROM_NAME);
        if (dbName.isPresent()) {
            return dbName.get();
        }
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
