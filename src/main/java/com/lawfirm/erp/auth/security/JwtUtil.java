package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.dto.auth.AuthenticatedDetail;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final RolePermissionRepository rolePermissionRepository;
    private final SystemConfigService systemConfigService;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiry:86400000}")
    private Long accessExpiry;

    @Value("${jwt.refresh-expiry:604800000}")
    private Long refreshExpiry;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        if (user.getUuid() != null) {
            claims.put("userUuid", user.getUuid().toString());
        }
        claims.put("email", user.getEmail());
        claims.put("fullName", user.getFullName());
        claims.put("roleCode", user.getRole().getRoleCode());
        claims.put("userType", user.getUserType().name());

        claims.put("permVersion", user.getPermissionVersion() != null ? user.getPermissionVersion() : 0);

        if (user.getFirm() != null) {
            claims.put("firmId", user.getFirm().getId().toString());
            claims.put("firmCode", user.getFirm().getLawFirmCode());
        }

        // FIX: Add permissions to JWT claims so JwtAuthFilter can populate them
        if (user.getRole() != null) {
            List<String> permissionCodes = rolePermissionRepository
                    .findPermissionsByRoleId(user.getRole().getId())
                    .stream()
                    .map(p -> p.getCode())
                    .toList();
            claims.put("permissions", permissionCodes);
        }

        long expiryMs = systemConfigService.accessTokenExpiryMs();
        if (expiryMs <= 0) {
            expiryMs = accessExpiry;
        }

        return Jwts.builder()
                .id(user.getId().toString())
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "refresh");
        // against an older version — otherwise a stolen refresh token outlives the very
        claims.put("permVersion", user.getPermissionVersion() != null ? user.getPermissionVersion() : 0);

        String token = Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiry))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        log.debug("Generated refresh token for user: {}", user.getUsername());
        return token;
    }

    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    public long getRefreshExpiryMs() {
        return refreshExpiry;
    }

    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Failed to extract claims: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public UUID extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String userId = claims.get("userId", String.class);
            if (userId != null) {
                return UUID.fromString(userId);
            }
            String id = claims.getId();
            if (id != null && !id.isEmpty()) {
                return UUID.fromString(id);
            }
            log.error("No userId found in token");
            return null;
        } catch (Exception e) {
            log.error("Failed to extract userId: {}", e.getMessage());
            return null;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRoleCode(String token) {
        return extractClaim(token, claims -> claims.get("roleCode", String.class));
    }

    public String extractUserType(String token) {
        return extractClaim(token, claims -> claims.get("userType", String.class));
    }

    public UUID extractFirmId(String token) {
        try {
            String firmId = extractClaim(token, claims -> claims.get("firmId", String.class));
            return firmId != null ? UUID.fromString(firmId) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String extractFirmCode(String token) {
        return extractClaim(token, claims -> claims.get("firmCode", String.class));
    }

    public boolean isRefreshToken(String token) {
        try {
            String type = extractClaim(token, claims -> claims.get("type", String.class));
            return "refresh".equals(type);
        } catch (Exception e) {
            log.error("Failed to check refresh token type: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.error("Token is null or empty");
            return false;
        }

        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("Token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("Invalid token: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
        }
        return false;
    }

    public Authentication getAuthentication(String token, HttpServletRequest request) {
        Claims claims = extractAllClaims(token);

        String roleCode = claims.get("roleCode", String.class);

        // Create authorities/roles for Spring Security
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roleCode != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }

        AuthenticatedDetail authenticatedDetail = AuthenticatedDetail.builder()
                .id(UUID.fromString(claims.get("userId", String.class)))
                .username(claims.getSubject())
                .email(claims.get("email", String.class))
                .fullName(claims.get("fullName", String.class))
                .role(roleCode)
                .userType(claims.get("userType", String.class))
                .firmId(claims.get("firmId", String.class))
                .firmCode(claims.get("firmCode", String.class))
                .accessToken(token)
                .build();

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(authenticatedDetail, null, authorities);
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authenticationToken;
    }


    public String generateMfaToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "mfa");

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 10 * 60 * 1000L))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generatePasswordChangeToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "pwd_change");

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 10 * 60 * 1000L))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generatePasswordResetToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "pwd_reset");
        claims.put("permVersion", user.getPermissionVersion() != null ? user.getPermissionVersion() : 0);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000L))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public UUID extractUserIdFromMfaToken(String token) {
        return extractUserIdFromScopedToken(token, "mfa");
    }

    public UUID extractUserIdFromPasswordResetToken(String token) {
        return extractUserIdFromScopedToken(token, "pwd_reset");
    }

    public Integer extractPasswordResetTokenVersion(String token) {
        try {
            if (!validateToken(token)) return null;
            Claims claims = extractAllClaims(token);
            if (!"pwd_reset".equals(claims.get("type", String.class))) return null;
            return claims.get("permVersion", Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    public UUID extractUserIdFromPasswordChangeToken(String token) {
        return extractUserIdFromScopedToken(token, "pwd_change");
    }

    private UUID extractUserIdFromScopedToken(String token, String expectedType) {
        try {
            if (!validateToken(token)) return null;
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);
            if (!expectedType.equals(type)) return null;
            String userId = claims.get("userId", String.class);
            return userId != null ? UUID.fromString(userId) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isLimitedScopeToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);
            return "mfa".equals(type) || "pwd_change".equals(type) || "pwd_reset".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

}