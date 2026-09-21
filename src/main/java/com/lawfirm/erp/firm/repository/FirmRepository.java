package com.lawfirm.erp.firm.repository;

import com.lawfirm.erp.firm.entity.Firm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FirmRepository extends JpaRepository<Firm, UUID> {
    Optional<Firm> findByLawFirmCode(String lawFirmCode);
    Optional<Firm> findByName(String name);
    boolean existsByLawFirmCode(String lawFirmCode);

    /** Count active firms — avoids loading all firms into memory. */
    @Query("SELECT COUNT(f) FROM Firm f WHERE f.status = :status")
    long countByStatus(@Param("status") com.lawfirm.erp.common.enums.FirmStatus status);

    /** Find all trial firms. */
    List<Firm> findByIsTrialTrue();

    /** Count firms on trial. */
    @Query("SELECT COUNT(f) FROM Firm f WHERE f.isTrial = true")
    long countByIsTrialTrue();

    /** Count trial firms whose expiry window overlaps [from, to). */
    @Query("SELECT COUNT(f) FROM Firm f WHERE f.isTrial = true " +
           "AND f.trialExpiresAt >= :from AND f.trialExpiresAt < :to")
    long countTrialExpiringBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Count trial firms that expired before the given instant. */
    @Query("SELECT COUNT(f) FROM Firm f WHERE f.isTrial = true AND f.trialExpiresAt < :before")
    long countTrialExpiredBefore(@Param("before") LocalDateTime before);

    /** Just the lifecycle status — read per request to enforce firm suspension without loading the firm. */
    @Query("SELECT f.status FROM Firm f WHERE f.id = :id")
    com.lawfirm.erp.common.enums.FirmStatus findStatusById(@Param("id") UUID id);

    /** Count firms that have at least one FIRM_ADMIN user — only these should be counted as 'real' firms. */
    @Query("SELECT COUNT(DISTINCT u.firm.id) FROM User u WHERE u.userType = 'FIRM' AND u.firm IS NOT NULL")
    long countFirmsWithFirmAdmin();

    /** Count active firms that have at least one FIRM_ADMIN user. */
    @Query("SELECT COUNT(DISTINCT u.firm.id) FROM User u JOIN u.firm f WHERE u.userType = 'FIRM' AND f.status = 'ACTIVE'")
    long countActiveFirmsWithFirmAdmin();

    // ── Trend queries ─────────────────────────────────────────────────────

    /** Daily new firm counts grouped by date. */
    @Query("SELECT FUNCTION('DATE', f.createdAt) as d, COUNT(f) as total, " +
           "SUM(CASE WHEN f.status = 'ACTIVE' THEN 1 ELSE 0 END) as active, " +
           "SUM(CASE WHEN f.status = 'SUSPENDED' THEN 1 ELSE 0 END) as suspended " +
           "FROM Firm f WHERE f.createdAt >= :from AND f.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', f.createdAt) ORDER BY d ASC")
    List<Object[]> countDailyByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}