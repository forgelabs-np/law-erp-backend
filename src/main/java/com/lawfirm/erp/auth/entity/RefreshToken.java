package com.lawfirm.erp.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One issued refresh token, tracked server-side so it can be rotated, revoked and
 * detected on reuse.
 *
 * <p>Before this existed the JWT alone was bearer-for-life (7 days): nothing on the server
 * knew a token had been issued, so logout could not kill it and a stolen copy kept minting
 * access tokens until expiry — finding F-9. The row's {@code id} IS the token's {@code jti}
 * claim. {@code usedAt} makes it single-use (a replay means two copies exist, so the account's
 * whole family is revoked); {@code revokedAt} is set by that family revoke.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** When the JWT itself expires — bookkeeping only; the JWT expiry is still enforced. */
    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
