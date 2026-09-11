package com.lawfirm.erp.modules.audit.controller;

import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/audit")
@RequiredArgsConstructor
@Tag(name = "Firm Audit Logs", description = "Firm admin activity timeline")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    /**
     * Full firm timeline — all activity, paginated.
     * Optional filters: from, to date range.
     */
    @GetMapping
    @Operation(summary = "Get full firm activity timeline")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getFirmTimeline(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID firmId = getRequiredFirmId();

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByFirm(
                firmId, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return responseHandler.ok(result, "Audit logs fetched");
    }

    /**
     * User timeline — "what did Advocate1 do?"
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "Get activity timeline for a specific user")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getUserTimeline(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID firmId = getRequiredFirmId();

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByFirmAndUser(
                firmId, userId, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return responseHandler.ok(result, "User activity fetched");
    }

    /**
     * Entity history — "show me everything that happened to Case #142"
     */
    @GetMapping("/entities/{entityType}/{entityId}")
    @Operation(summary = "Get full history of a specific entity")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getEntityHistory(
            @PathVariable AuditEntity entityType,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID firmId = getRequiredFirmId();

        Page<AuditLog> result = auditLogRepository.findByFirmAndEntity(
                firmId, entityType, entityId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return responseHandler.ok(result, "Entity history fetched");
    }

   
    @GetMapping("/actions")
    @Operation(summary = "Filter activity by action type")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getByAction(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID firmId = getRequiredFirmId();

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByFirmAndAction(
                firmId, action, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return responseHandler.ok(result, "Action logs fetched");
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}