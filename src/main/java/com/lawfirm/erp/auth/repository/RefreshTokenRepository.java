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

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    // Atomic single-use consume: only one concurrent caller can flip usedAt from NULL.
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken t SET t.usedAt = :now " +
           "WHERE t.id = :id AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int markUsed(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
