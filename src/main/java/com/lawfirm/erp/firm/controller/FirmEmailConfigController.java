package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.firm.entity.FirmEmailConfig;
import com.lawfirm.erp.firm.service.FirmEmailConfigService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Firm admin configures their firm's SMTP settings. Falls back to platform global SMTP. */
@RestController
@RequestMapping("/api/v1/firm/email-config")
@RequiredArgsConstructor
@Tag(name = "Firm Email Config", description = "Per-firm SMTP configuration — firm admin sets up their own email sender")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmEmailConfigController {

    private final FirmEmailConfigService firmEmailConfigService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "Get firm email config", description = "Returns SMTP config WITHOUT the password. smtpPasswordSet indicates if a password was configured.")
    public ResponseEntity<ApiResponse<FirmEmailConfigResponse>> getConfig() {
        UUID firmId = getRequiredFirmId();
        var config = firmEmailConfigService.getByFirmId(firmId);
        return responseHandler.ok(
                config.map(this::toResponse).orElse(null),
                config.isPresent() ? "Email config fetched" : "No email config configured"
        );
    }

    @PutMapping
    @Operation(summary = "Create or update firm email config",
               description = "Set SMTP settings. Use smtpPassword = \"__UNCHANGED__\" to keep existing password without resending it.")
    public ResponseEntity<ApiResponse<FirmEmailConfigResponse>> saveConfig(
            @Valid @RequestBody FirmEmailConfigRequest request) {
        UUID firmId = getRequiredFirmId();

        FirmEmailConfig config = FirmEmailConfig.builder()
                .smtpHost(request.getSmtpHost())
                .smtpPort(request.getSmtpPort())
                .smtpUsername(request.getSmtpUsername())
                .smtpPassword(request.getSmtpPassword())
                .fromName(request.getFromName())
                .fromAddress(request.getFromAddress())
                .useTls(request.isUseTls())
                .isActive(request.isActive())
                .build();

        FirmEmailConfig saved = firmEmailConfigService.save(firmId, config);
        return responseHandler.ok(toResponse(saved), "Email config saved");
    }

    @PostMapping("/test")
    @Operation(summary = "Test SMTP connection",
               description = "Attempts to connect to the configured SMTP server. Updates testedAt and testPassed fields.")
    public ResponseEntity<ApiResponse<TestResultResponse>> testConnection() {
        UUID firmId = getRequiredFirmId();
        boolean passed = firmEmailConfigService.testConnection(firmId);
        return responseHandler.ok(
                new TestResultResponse(passed, LocalDateTime.now()),
                passed ? "SMTP connection successful" : "SMTP connection failed — check credentials"
        );
    }

    @DeleteMapping
    @Operation(summary = "Delete firm email config",
               description = "Removes the firm's SMTP config. All emails will use platform global SMTP.")
    public ResponseEntity<ApiResponse<Void>> deleteConfig() {
        UUID firmId = getRequiredFirmId();
        firmEmailConfigService.delete(firmId);
        return responseHandler.ok(null, "Email config deleted — falling back to platform SMTP");
    }

    // DTOs

    @Data
    public static class FirmEmailConfigRequest {
        @NotBlank(message = "SMTP host is required")
        private String smtpHost;

        @NotNull(message = "SMTP port is required")
        private Integer smtpPort;

        @NotBlank(message = "SMTP username is required")
        private String smtpUsername;

        private String smtpPassword; // Use "__UNCHANGED__" to keep existing

        @NotBlank(message = "From name is required")
        private String fromName;

        @NotBlank(message = "From address is required")
        @Email(message = "From address must be a valid email")
        private String fromAddress;

        private boolean useTls = true;
        private boolean isActive = true;
    }

    @Data
    @RequiredArgsConstructor
    public static class FirmEmailConfigResponse {
        private final String smtpHost;
        private final Integer smtpPort;
        private final String smtpUsername;
        private final boolean smtpPasswordSet;
        private final String fromName;
        private final String fromAddress;
        private final boolean useTls;
        private final boolean isActive;
        private final LocalDateTime testedAt;
        private final Boolean testPassed;
    }

    @Data
    @RequiredArgsConstructor
    public static class TestResultResponse {
        private final boolean passed;
        private final LocalDateTime testedAt;
    }

    // Helpers

    private FirmEmailConfigResponse toResponse(FirmEmailConfig config) {
        return new FirmEmailConfigResponse(
                config.getSmtpHost(),
                config.getSmtpPort(),
                config.getSmtpUsername(),
                config.getSmtpPassword() != null && !config.getSmtpPassword().isBlank(),
                config.getFromName(),
                config.getFromAddress(),
                config.isUseTls(),
                config.isActive(),
                config.getTestedAt(),
                config.getTestPassed()
        );
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
