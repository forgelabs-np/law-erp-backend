package com.lawfirm.erp.superadmin.controller;

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
import java.util.stream.Collectors;

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
     * Optional filters: action, date range, userType.
     */
    @GetMapping
    @Operation(summary = "Get all audit logs", description = "Super Admin views all audit logs across all firms with optional filters")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAllAuditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(required = false) String userType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result;

        if (action != null) {
            result = auditLogRepository.findByActionGlobal(action, from, to,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        } else {
            result = auditLogRepository.findAllWithFilters(from, to,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        }

        // Filter by userType if provided
        if (userType != null && !userType.isBlank()) {
            result = new org.springframework.data.domain.PageImpl<>(
                    result.getContent().stream()
                            .filter(log -> userType.equalsIgnoreCase(log.getUserType()))
                            .collect(Collectors.toList()),
                    result.getPageable(),
                    result.getTotalElements()
            );
        }

        return responseHandler.ok(result, "Audit logs fetched successfully");
    }

    /**
     * Get audit logs for a specific firm — super admin doesn't need firm context.
     */
    @GetMapping("/firms/{firmId}")
    @Operation(summary = "Get audit logs for a firm", description = "Super Admin views audit logs for a specific firm")
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
    @Operation(summary = "Get audit logs for a user", description = "Super Admin views audit logs for a specific user across all firms")
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
    @Operation(summary = "Get entity history", description = "Super Admin views history for a specific entity across all firms")
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