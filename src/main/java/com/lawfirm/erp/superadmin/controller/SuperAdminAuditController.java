package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.constant.SuperAdminConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
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
@RequestMapping("/api/v1/super-admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin Audit Logs", description = "Super Admin audit log viewing across all firms")
public class SuperAdminAuditController {

    private final AuditLogRepository auditLogRepository;
    private final ResponseHandler responseHandler;

    /**
     * Get all audit logs across all firms — no firm context required.
     * Optional filters: action, date range, userType, userId.
     *
     * UserType values: S (SUPER_ADMIN), A (FIRM), F (FIRM_USER), C (CLIENT)
     */
    @GetMapping
    @Operation(summary = SuperAdminConstants.GET_ALL_AUDIT_LOGS_SUMMARY, description = SuperAdminConstants.GET_ALL_AUDIT_LOGS_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAllAuditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        // Normalize userType: accept full enum names (FIRM, FIRM_USER, etc.) and map to char
        String userTypeChar = resolveUserTypeChar(userType);

        Page<AuditLog> result = auditLogRepository.findAllWithFilters(
                action, userTypeChar, userId, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return responseHandler.ok(result, "Audit logs fetched successfully");
    }

    /**
     * Maps UserType enum names to the single-char stored in audit_logs.user_type.
     * Accepts: S, A, F, C (char) or SUPER_ADMIN, FIRM, FIRM_USER, CLIENT (enum name).
     */
    private String resolveUserTypeChar(String userType) {
        if (userType == null || userType.isBlank()) return null;
        String upper = userType.trim().toUpperCase();
        return switch (upper) {
            case "S", "SUPER_ADMIN" -> "S";
            case "A", "FIRM" -> "A";
            case "F", "FIRM_USER" -> "F";
            case "C", "CLIENT" -> "C";
            default -> null;
        };
    }

    /**
     * Get audit logs for a specific firm — super admin doesn't need firm context.
     */
    @GetMapping("/firms/{firmId}")
    @Operation(summary = SuperAdminConstants.GET_FIRM_AUDIT_LOGS_SUMMARY, description = SuperAdminConstants.GET_FIRM_AUDIT_LOGS_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getFirmAuditLogs(
            @PathVariable UUID firmId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByFirmForAdmin(firmId, action, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return responseHandler.ok(result, "Firm audit logs fetched successfully");
    }

    /**
     * Get audit logs for a specific user across all firms.
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = SuperAdminConstants.GET_USER_AUDIT_LOGS_SUMMARY, description = SuperAdminConstants.GET_USER_AUDIT_LOGS_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getUserAuditLogs(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByUserIdWithFilters(userId, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return responseHandler.ok(result, "User audit logs fetched successfully");
    }

    /**
     * Get entity history across all firms.
     */
    @GetMapping("/entities/{entityType}/{entityId}")
    @Operation(summary = SuperAdminConstants.GET_ENTITY_HISTORY_SUMMARY, description = SuperAdminConstants.GET_ENTITY_HISTORY_DESCRIPTION)
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getEntityHistory(
            @PathVariable AuditEntity entityType,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLog> result = auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return responseHandler.ok(result, "Entity history fetched successfully");
    }
}