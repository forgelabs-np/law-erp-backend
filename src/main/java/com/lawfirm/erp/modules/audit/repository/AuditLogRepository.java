package com.lawfirm.erp.modules.audit.repository;

import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // ── Firm Admin queries ──────────────────────────────────────────────────

    /**
     * Get all audit logs for a firm with optional date range.
     * Uses COALESCE for null-safe date filtering.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId " +
            "AND (a.createdAt >= COALESCE(:from, a.createdAt)) " +
            "AND (a.createdAt <= COALESCE(:to, a.createdAt)) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFirm(
            @Param("firmId") UUID firmId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Get audit logs for a specific user within a firm.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId AND a.userId = :userId " +
            "AND (a.createdAt >= COALESCE(:from, a.createdAt)) " +
            "AND (a.createdAt <= COALESCE(:to, a.createdAt)) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFirmAndUser(
            @Param("firmId") UUID firmId,
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Get full history for a specific entity (case, document, user, etc.)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId " +
            "AND a.entityType = :entityType AND a.entityId = :entityId " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFirmAndEntity(
            @Param("firmId") UUID firmId,
            @Param("entityType") AuditEntity entityType,
            @Param("entityId") UUID entityId,
            Pageable pageable);

    /**
     * Filter audit logs by action type within a firm.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId AND a.action = :action " +
            "AND (a.createdAt >= COALESCE(:from, a.createdAt)) " +
            "AND (a.createdAt <= COALESCE(:to, a.createdAt)) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFirmAndAction(
            @Param("firmId") UUID firmId,
            @Param("action") AuditAction action,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    // ── Super Admin queries ──────────────────────────────────────────────────

    /**
     * Super Admin: Get all audit logs for a specific firm with optional filters.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId " +
            "AND (:action IS NULL OR a.action = :action) " +
            "AND (a.createdAt >= COALESCE(:from, a.createdAt)) " +
            "AND (a.createdAt <= COALESCE(:to, a.createdAt)) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFirmForAdmin(
            @Param("firmId") UUID firmId,
            @Param("action") AuditAction action,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);


    @Query("SELECT a FROM AuditLog a WHERE a.action = :action " +
            "AND (a.createdAt >= COALESCE(:from, a.createdAt)) " +
            "AND (a.createdAt <= COALESCE(:to, a.createdAt)) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByActionGlobal(
            @Param("action") AuditAction action,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
    // ── Additional useful queries ──────────────────────────────────────────

    /**
     * Get recent audit logs for a firm (no date filter).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findRecentByFirm(
            @Param("firmId") UUID firmId,
            Pageable pageable);

    /**
     * Count audit logs by action type for a firm.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.firmId = :firmId AND a.action = :action")
    long countByFirmAndAction(
            @Param("firmId") UUID firmId,
            @Param("action") AuditAction action);

    /**
     * Get audit logs by IP address.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.firmId = :firmId AND a.ipAddress = :ipAddress " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFirmAndIp(
            @Param("firmId") UUID firmId,
            @Param("ipAddress") String ipAddress,
            Pageable pageable);
}