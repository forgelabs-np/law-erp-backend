package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.auth.AuthenticatedDetail;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token");
                return;
            }

            if (jwtUtil.isTokenExpired(token)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token is expired");
                return;
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String firmId   = claims.get("firmId",   String.class);
            String firmCode = claims.get("firmCode", String.class);
            String userType = claims.get("userType", String.class);
            String userIdStr = claims.get("userId",  String.class);

            if (jwtUtil.isLimitedScopeToken(token)) {
                String path = request.getRequestURI();
                boolean allowedPath = path.startsWith("/api/v1/auth/mfa/")
                        || path.startsWith("/api/v1/auth/change-password");

                if (!allowedPath) {
                    response.sendError(HttpStatus.UNAUTHORIZED.value(),
                            "This token is only valid for authentication steps. Please complete login.");
                    return;
                }
                // For the allowed paths, skip all further checks and let it through
                filterChain.doFilter(request, response);
                return;
            }
            // ── Permission version staleness check ────────────────────────
            // Only for non-super-admin users
            if (userIdStr != null && !"SUPER_ADMIN".equals(userType)) {
                UUID userId = UUID.fromString(userIdStr);
                Integer tokenVersion = claims.get("permVersion", Integer.class);

                // FIX 1: If token has no permVersion claim (old token format), reject it
                // so user re-logs in and gets a fresh token with the claim.
                if (tokenVersion == null) {
                    log.warn("Token missing permVersion claim for user: {} — forcing re-login", userIdStr);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(),
                            "Your session is outdated. Please login again.");
                    return;
                }

                Integer currentVersion = userRepository.findPermissionVersionById(userId);

                // FIX 2: If DB has null (user existed before permissionVersion column was added),
                // treat DB null as 0 — same as the default. Don't reject the token.
                // This prevents all pre-existing users from being locked out.
                int dbVersion = (currentVersion != null) ? currentVersion : 0;

                if (tokenVersion != dbVersion) {
                    log.warn("Token stale for user {}: token version {} != db version {}",
                            userIdStr, tokenVersion, dbVersion);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(),
                            "Your permissions have changed. Please login again.");
                    return;
                }
            }

            // ── Firm context ──────────────────────────────────────────────
            if (firmId != null && !"SUPER_ADMIN".equals(userType)) {
                FirmContextHolder.set(UUID.fromString(firmId), firmCode);
            } else {
                FirmContextHolder.clear();
            }

            // ── Build authentication ──────────────────────────────────────
            Authentication auth = jwtUtil.getAuthentication(token, request);

            String deviceId = request.getHeader("deviceId");
            deviceId = deviceId != null ? deviceId + "_" + jwtUtil.extractUserId(token) : "UNKNOWN_";
            request = new HeaderWrapper(request, Map.of("deviceId", deviceId));

            SecurityContextHolder.getContext().setAuthentication(auth);

            if (auth != null && auth.getPrincipal() instanceof AuthenticatedDetail detail) {
                AuthenticatedUser authenticatedUser = new AuthenticatedUser();
                authenticatedUser.setId(detail.getId());
                authenticatedUser.setUsername(detail.getUsername());
                authenticatedUser.setEmail(detail.getEmail());
                authenticatedUser.setFullName(detail.getFullName());
                authenticatedUser.setUserType(detail.getUserType());
                authenticatedUser.setFirmCode(detail.getFirmCode());
                if (detail.getFirmId() != null) {
                    authenticatedUser.setFirmId(UUID.fromString(detail.getFirmId()));
                }
                if (detail.getRole() != null) {
                    authenticatedUser.setRoles(List.of(detail.getRole()));
                }

                @SuppressWarnings("unchecked")
                List<String> permissions = claims.get("permissions", List.class);
                if (permissions != null) {
                    authenticatedUser.setPermissions(permissions);
                }

                request.setAttribute("authenticatedUser", authenticatedUser);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            FirmContextHolder.clear();
        }
    }
}