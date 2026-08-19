package com.lawfirm.erp.auth.security;

import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.auth.AuthenticatedDetail;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userRepository);

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    @Test
    void populatesRolesFromRoleCodeClaim() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();

        Claims claims = mock(Claims.class);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(claims.get("firmId", String.class)).thenReturn(firmId.toString());
        when(claims.get("firmCode", String.class)).thenReturn("APEX-LAW");
        when(claims.get("userType", String.class)).thenReturn("FIRM_USER");
        when(claims.get("permVersion", Integer.class)).thenReturn(0);
        when(claims.get("permissions", List.class)).thenReturn(List.of("DASHBOARD:VIEW"));

        AuthenticatedDetail detail = AuthenticatedDetail.builder()
                .id(userId)
                .username("admin")
                .email("admin@apex.com")
                .fullName("Firm Admin")
                .role("FIRM_ADMIN")
                .userType("FIRM_USER")
                .firmId(firmId.toString())
                .firmCode("APEX-LAW")
                .build();

        when(jwtUtil.validateToken(anyString())).thenReturn(true);
        when(jwtUtil.isTokenExpired(anyString())).thenReturn(false);
        when(jwtUtil.isLimitedScopeToken(anyString())).thenReturn(false);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId);
        when(jwtUtil.getAuthentication(anyString(), any())).thenReturn(
                new UsernamePasswordAuthenticationToken(
                        detail, null, List.of(new SimpleGrantedAuthority("ROLE_FIRM_ADMIN"))));
        when(userRepository.findPermissionVersionById(userId)).thenReturn(0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute("authenticatedUser");
        assertNotNull(user, "authenticatedUser must be set on the request");
        assertNotNull(user.getRoles(), "roles must be populated from the JWT roleCode claim");
        assertTrue(user.getRoles().contains("FIRM_ADMIN"), "FIRM_ADMIN role must be present");
        assertEquals(firmId, user.getFirmId());
    }
}
