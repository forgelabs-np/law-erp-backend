package com.lawfirm.erp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.dto.ApiResponse;
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

        log.error("Response Status::{}", response.getStatus());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        log.error(authException.getMessage(), authException);

        String errorMessage;
        if (authException instanceof BadCredentialsException) {
            errorMessage = "Invalid username or password.";
        } else if (authException instanceof DisabledException) {
            errorMessage = "Your account has been disabled. Please contact support.";
        } else if (authException instanceof LockedException) {
            errorMessage = "Your account has been locked due to too many failed attempts. Please try again later.";
        } else if (authException instanceof AccountExpiredException) {
            errorMessage = "Your account has expired. Please contact support to renew your account.";
        } else if (authException instanceof CredentialsExpiredException) {
            errorMessage = "Your credentials have expired. Please update your password.";
        } else {
            errorMessage = "Authentication failed. Please login again.";
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
