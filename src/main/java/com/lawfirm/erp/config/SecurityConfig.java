package com.lawfirm.erp.config;

import com.lawfirm.erp.auth.security.AuthEntryPoint;
import com.lawfirm.erp.auth.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

@Component
@EnableWebSecurity
@EnableAsync
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthEntryPoint authEntryPoint;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.production:false}")
    private boolean production;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                // CSRF is disabled deliberately: every endpoint authenticates with a stateless
                // bearer token, so there is no cookie-carried session to forge.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/client/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/mfa/setup/confirm",
                            "/api/v1/auth/mfa/validate",
                            "/api/v1/auth/change-password",
                            "/api/v1/auth/forgot-password",
                            "/api/v1/auth/reset-password",
                            "/auth/mfa/setup/confirm",
                            "/api/v1/super-admin/login",
                            "/api/v1/super-admin/register"
                    ).permitAll();
                    // API docs are a dev convenience — never expose them in production.
                    if (!production) {
                        auth.requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .anyRequest().authenticated();
                })
                .exceptionHandling(e -> e.authenticationEntryPoint(authEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins;
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            origins = Arrays.asList(corsAllowedOrigins.split(","));
        } else {
            origins = Arrays.asList(
                    "http://localhost:5173",
                    "http://localhost:5175",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173"
            );
        }
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Bearer-token auth does not need cookies, so credentials stay off. This also avoids the
        // invalid "*" origin + allowCredentials=true combination if CORS_ALLOWED_ORIGINS is set to *.
        configuration.setAllowCredentials(false);

        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}