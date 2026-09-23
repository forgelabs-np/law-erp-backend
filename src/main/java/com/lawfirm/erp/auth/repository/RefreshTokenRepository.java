package com.lawfirm.erp.auth.repository;

import com.lawfirm.erp.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * The account's still-active refresh tokens — the "family" that dies together when one
     * of its members is replayed. Read (rather than a bulk UPDATE) on purpose: the caller
     * revokes through these entities so the persistence context it keeps reading through
     * can never hand back a stale, still-"active" sibling row.
     */
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * Opportunistic cleanup — runs inside the refresh transaction instead of a scheduler,
     * because refresh traffic is exactly where dead rows are noticed.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
