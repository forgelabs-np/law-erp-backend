package com.lawfirm.erp.security;

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

            // Extract claims and set firm context
            Claims claims = jwtUtil.extractAllClaims(token);
            String firmId = claims.get("firmId", String.class);
            String firmCode = claims.get("firmCode", String.class);
            String userType = claims.get("userType", String.class);
            String userIdStr = claims.get("userId", String.class);

            if (userIdStr != null && !"SUPER_ADMIN".equals(userType)) {
                UUID userId = UUID.fromString(userIdStr);
                Integer tokenVersion = claims.get("permVersion", Integer.class);

                if (tokenVersion == null) {
                    log.warn("Token missing permission version for user: {}", userIdStr);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(),
                            "Your session is outdated. Please login again.");
                    return;
                }

                Integer currentVersion = userRepository.findPermissionVersionById(userId);
                if (currentVersion == null || !currentVersion.equals(tokenVersion)) {
                    log.warn("Token stale for user {}: token version {} != current version {}",
                            userIdStr, tokenVersion, currentVersion);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(),
                            "Your permissions have changed. Please login again.");
                    return;
                }
            }

            // Set firm context for non-super-admin users
            if (firmId != null && !"SUPER_ADMIN".equals(userType)) {
                FirmContextHolder.set(UUID.fromString(firmId), firmCode);
            } else {
                FirmContextHolder.clear();
            }

            Authentication auth = jwtUtil.getAuthentication(token, request);
            String deviceId = request.getHeader("deviceId");
            deviceId = deviceId != null ? deviceId + "_" + jwtUtil.extractUserId(token) : "UNKNOWN_";
            request = new HeaderWrapper(request, Map.of("deviceId", deviceId));
            SecurityContextHolder.getContext().setAuthentication(auth);

            if (auth != null && auth.getPrincipal() instanceof AuthenticatedDetail) {
                AuthenticatedDetail detail = (AuthenticatedDetail) auth.getPrincipal();

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