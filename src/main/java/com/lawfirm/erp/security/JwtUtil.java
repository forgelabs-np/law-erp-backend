package com.lawfirm.erp.security;

import com.lawfirm.erp.dto.auth.AuthenticatedDetail;
import com.lawfirm.erp.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

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
        claims.put("userUuid", user.getUuid().toString());
        claims.put("email", user.getEmail());
        claims.put("fullName", user.getFullName());
        claims.put("roleCode", user.getRole().getRoleCode());
        claims.put("userType", user.getUserType().name());

        if (user.getFirm() != null) {
            claims.put("firmId", user.getFirm().getId().toString());
            claims.put("firmCode", user.getFirm().getLawFirmCode());
        }

        return Jwts.builder()
                .setId(user.getId().toString())
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiry))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiry))
                .signWith(key)
                .compact();
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

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
            extractAllClaims(token);
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

        AuthenticatedDetail authenticatedDetail = AuthenticatedDetail.builder()
                .id(UUID.fromString(claims.get("userId", String.class)))
                .username(claims.getSubject())
                .email(claims.get("email", String.class))
                .fullName(claims.get("fullName", String.class))
                .role(claims.get("roleCode", String.class))
                .userType(claims.get("userType", String.class))
                .firmId(claims.get("firmId", String.class))
                .firmCode(claims.get("firmCode", String.class))
                .accessToken(token)
                .build();

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(authenticatedDetail, null, null);
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authenticationToken;
    }
}