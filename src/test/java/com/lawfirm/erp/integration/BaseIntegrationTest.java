package com.lawfirm.erp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Base class for full integration tests with H2 in-memory database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected FirmRepository firmRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JWT Generation
    // ═══════════════════════════════════════════════════════════════════════

    protected String generateAccessToken(User user) {
        var claims = new HashMap<String, Object>();
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("fullName", user.getFullName());
        claims.put("roleCode", user.getRole().getRoleCode());
        claims.put("userType", user.getUserType().name());
        claims.put("permVersion", user.getPermissionVersion() != null ? user.getPermissionVersion() : 0);

        if (user.getFirm() != null) {
            claims.put("firmId", user.getFirm().getId().toString());
            claims.put("firmCode", user.getFirm().getLawFirmCode());
        }

        return Jwts.builder()
                .id(user.getId().toString())
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    protected String generateCustomToken(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .subject(claims.getOrDefault("username", "test").toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Auth Helpers
    // ═══════════════════════════════════════════════════════════════════════

    protected String loginAs(String firmCode, String username, String password) throws Exception {
        var request = Map.of("data", Map.of(
                "lawFirmCode", firmCode,
                "username", username,
                "password", password
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // Handle PASSWORD_CHANGE_REQUIRED — return the passwordChangeToken
        String status = json.path("data").path("status").asText();
        if ("PASSWORD_CHANGE_REQUIRED".equals(status)) {
            return json.path("data").path("passwordChangeToken").asText();
        }
        return json.path("data").path("accessToken").asText();
    }

    protected String loginAsSuperAdmin(String username, String password) throws Exception {
        var request = Map.of("data", Map.of(
                "username", username,
                "password", password
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/super-admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // Handle MFA — super admin always has MFA enabled
        String status = json.path("data").path("status").asText();
        if ("MFA_SETUP_REQUIRED".equals(status) || "MFA_REQUIRED".equals(status)) {
            return json.path("data").path("mfaToken").asText();
        }
        return json.path("data").path("accessToken").asText();
    }

    protected JsonNode registerSuperAdmin(String username, String email, String password,
                                           String fullName, String mobileNo, String secretKey) throws Exception {
        var request = Map.of("data", Map.of(
                "username", username,
                "email", email,
                "password", password,
                "fullName", fullName,
                "mobileNo", mobileNo,
                "secretKey", secretKey
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/super-admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Firm & User Setup (Database)
    // ═══════════════════════════════════════════════════════════════════════

    protected Firm createFirm(String code, String name) {
        Firm firm = Firm.builder()
                .lawFirmCode(code)
                .name(name)
                .firmType(FirmType.FIRM)
                .status(FirmStatus.ACTIVE)
                .build();
        return firmRepository.save(firm);
    }

    protected User createUser(String username, String email, String password,
                               UserType userType, Role role, Firm firm) {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(password)
                .fullName(username + " Full Name")
                .mobileNo("98" + String.format("%08d", Math.abs(username.hashCode()) % 100000000))
                .userType(userType)
                .role(role)
                .firm(firm)
                .isEmailVerified(true)
                .isMobileVerified(true)
                .isBlocked(false)
                .loginAttempts(0)
                .mustChangePassword(false)
                .mfaEnabled(false)
                .permissionVersion(0)
                .build();
        user.setActive(true);
        return userRepository.save(user);
    }

    protected User createSystemUser(String username, String email, String password,
                                     UserType userType, Role role) {
        return createUser(username, email, password, userType, role, null);
    }

    protected Role getSystemRole(String roleCode) {
        return roleRepository.findSystemRoleByCode(roleCode)
                .orElseThrow(() -> new RuntimeException("System role not found: " + roleCode));
    }

    protected List<Role> getAllSystemRoles() {
        return roleRepository.findByFirmIsNullAndIsSystemTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Request/Response Helpers
    // ═══════════════════════════════════════════════════════════════════════

    protected Map<String, Object> apiRequest(Object data) {
        return Map.of("data", data);
    }

    protected JsonNode parseResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected MvcResult authGet(String token, String url) throws Exception {
        return mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    protected MvcResult authPost(String token, String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    protected MvcResult authPut(String token, String url, Object body) throws Exception {
        return mockMvc.perform(put(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    protected MvcResult authPatch(String token, String url, Object body) throws Exception {
        return mockMvc.perform(patch(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body != null ? objectMapper.writeValueAsString(body) : ""))
                .andReturn();
    }

    protected MvcResult authDelete(String token, String url) throws Exception {
        return mockMvc.perform(delete(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Assertion Helpers
    // ═══════════════════════════════════════════════════════════════════════

    protected void assertSuccess(MvcResult result) throws Exception {
        JsonNode json = parseResponse(result);
        if (!json.path("success").asBoolean()) {
            throw new AssertionError("Expected success but got: " + json.path("message").asText());
        }
    }

    protected void assertForbidden(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status != 403) {
            throw new AssertionError("Expected 403 but got " + status + ": " +
                    result.getResponse().getContentAsString());
        }
    }

    protected void assertUnauthorized(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status != 401) {
            throw new AssertionError("Expected 401 but got " + status + ": " +
                    result.getResponse().getContentAsString());
        }
    }

    protected void assertBadRequest(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status != 400) {
            throw new AssertionError("Expected 400 but got " + status + ": " +
                    result.getResponse().getContentAsString());
        }
    }
}
