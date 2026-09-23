package com.lawfirm.erp.modules.email.service;

import com.lawfirm.erp.common.service.FirmConfigService;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.firm.service.FirmEmailConfigService;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.repository.HearingReminderLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin-issued reset credential has exactly one delivery path that the console actually
 * shows the user, so the mail must contain it. Both halves are asserted separately: the service
 * putting the password in the template context, and the template rendering it next to the
 * configurable Sign in link.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetEmailTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String PASSWORD = "Tmp#12345678";
    private static final String LOGIN_URL = "http://localhost:5173/";

    @Mock private SystemConfigService systemConfigService;
    @Mock private FirmConfigService firmConfigService;
    @Mock private FirmEmailConfigService firmEmailConfigService;
    @Mock private AuditService auditService;
    @Mock private TemplateEngine templateEngine;
    @Mock private JavaMailSender defaultMailSender;
    @Mock private HearingReminderLogRepository hearingReminderLogRepository;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(systemConfigService, firmConfigService,
                firmEmailConfigService, auditService, templateEngine, defaultMailSender,
                hearingReminderLogRepository);

        when(firmConfigService.getEffectiveConfig(any())).thenReturn(Map.of());
        when(firmEmailConfigService.getDecrypted(any())).thenReturn(Optional.empty());
        when(firmEmailConfigService.getByFirmId(any())).thenReturn(Optional.empty());
        when(systemConfigService.loginUrl()).thenReturn(LOGIN_URL);
        when(defaultMailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(templateEngine.process(eq("email/password-reset-notice"), any(Context.class)))
                .thenReturn("<html>rendered</html>");
    }

    @Test
    @DisplayName("sendPasswordReset puts the password and the configured login URL in the mail")
    void passwordIsPassedIntoTheTemplate() {
        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);

        emailService.sendPasswordReset(FIRM_ID, ADMIN_ID, "advocate@test.com", "Test Advocate",
                PASSWORD, "Y Law");

        verify(templateEngine).process(eq("email/password-reset-notice"), ctxCaptor.capture());

        Context ctx = ctxCaptor.getValue();
        assertEquals(PASSWORD, ctx.getVariable("tempPassword"),
                "the reset mail must carry the credential the admin was shown");
        assertEquals(LOGIN_URL, ctx.getVariable("loginUrl"),
                "the Sign in link must come from the GLOBAL LOGIN_URL config");
        assertEquals("Y Law", ctx.getVariable("firmName"));
        assertEquals("Test Advocate", ctx.getVariable("fullName"));
    }

    @Test
    @DisplayName("the notice template renders the password, the Sign in link and no false claim")
    void templateRendersPasswordAndSignInLink() {
        String html = renderNotice(PASSWORD);

        assertTrue(html.contains(PASSWORD), "the temporary password must appear in the e-mail body");
        assertTrue(html.contains(LOGIN_URL), "the Sign in button must point at the configured LOGIN_URL");
        assertFalse(html.contains("not</strong> included"),
                "the copy must not still claim the password is withheld");
    }

    @Test
    @DisplayName("no password in the context renders no empty credential box")
    void templateOmitsCredentialBoxWhenNoPassword() {
        String html = renderNotice(null);

        assertFalse(html.contains("New temporary password"),
                "an absent password must not leave an empty box in the mail");
        assertTrue(html.contains(LOGIN_URL));
    }

    /**
     * Renders the real template the way EmailServiceImpl does (SpringTemplateEngine, as
     * spring-boot-starter-thymeleaf wires it). Plain TemplateEngine would demand OGNL, which
     * this project does not carry.
     */
    private String renderNotice(String tempPassword) {
        TemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);

        Context ctx = new Context();
        ctx.setVariable("firmName", "Y Law");
        ctx.setVariable("fullName", "Test Advocate");
        ctx.setVariable("tempPassword", tempPassword);
        ctx.setVariable("loginUrl", LOGIN_URL);
        ctx.setVariable("primaryColor", "#1A237E");
        ctx.setVariable("emailFooter", "Y Law");

        return engine.process("email/password-reset-notice", ctx);
    }
}
