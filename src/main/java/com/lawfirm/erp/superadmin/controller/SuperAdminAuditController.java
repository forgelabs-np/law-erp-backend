package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.exception.ResponseHandler;
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
@Tag(name = "Super Admin - Audit Logs", description = "Platform-wide audit oversight")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAuditController {

    private final AuditLogRepository auditLogRepository;
    private final ResponseHandler responseHandler;

    @GetMapping("/firms/{firmId}")
    @Operation(summary = "Get all audit activity for a specific firm")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getFirmAudit(
            @PathVariable UUID firmId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByFirmForAdmin(
                firmId, action, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return responseHandler.ok(result, "Firm audit logs fetched");
    }

    @GetMapping("/actions")
    @Operation(summary = "Platform-wide activity by action type")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getGlobalByAction(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<AuditLog> result = auditLogRepository.findByActionGlobal(
                action, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return responseHandler.ok(result, "Global action logs fetched");
    }
}