package com.lawfirm.erp.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        String errorMessage;
        if (authException instanceof BadCredentialsException) {
            errorMessage = "Invalid username or password.";
            log.warn("Authentication failed: Bad credentials");
        } else if (authException instanceof DisabledException) {
            errorMessage = "Your account has been disabled. Please contact support.";
            log.warn("Authentication failed: Account disabled");
        } else if (authException instanceof LockedException) {
            errorMessage = "Your account has been locked due to too many failed attempts. Please try again later.";
            log.warn("Authentication failed: Account locked");
        } else if (authException instanceof AccountExpiredException) {
            errorMessage = "Your account has expired. Please contact support to renew your account.";
            log.warn("Authentication failed: Account expired");
        } else if (authException instanceof CredentialsExpiredException) {
            errorMessage = "Your credentials have expired. Please update your password.";
            log.warn("Authentication failed: Credentials expired");
        } else {
            errorMessage = "Authentication failed. Please login again.";
            log.warn("Authentication failed: {}", authException.getMessage());
        }

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message(errorMessage)
                .responseCode(HttpStatus.UNAUTHORIZED.value())
                .data(errorMessage)
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}