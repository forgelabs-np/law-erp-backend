package com.lawfirm.erp.security;

import com.lawfirm.erp.common.exception.UnauthorizedException;
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
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

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
// After getting auth, populate AuthenticatedUser
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
                // TODO: Load roles and permissions from DB/cache

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