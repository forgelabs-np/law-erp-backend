# Security Architecture Guide — NepalCRM / Law ERP

> **Purpose:** This document explains how Spring Security, JWT authentication, RBAC (Role-Based Access Control), multi-tenancy, and permission evaluation are structured in this project. Use this as a reference to replicate the same security pattern in another project.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Package Structure](#2-package-structure)
3. [JWT Authentication](#3-jwt-authentication)
4. [Spring Security Configuration](#4-spring-security-configuration)
5. [The Filter Chain (JwtAuthFilter)](#5-the-filter-chain)
6. [Multi-Tenancy — Firm Context](#6-multi-tenancy)
7. [RBAC — Entity Model](#7-rbac-entity-model)
8. [Permission Evaluation](#8-permission-evaluation)
9. [DataInitializer — Seeding the System](#9-datainitializer-seeding)
10. [Controller Security Patterns](#10-controller-security-patterns)
11. [Scope Ceiling Enforcement](#11-scope-ceiling-enforcement)
12. [MFA / TOTP Integration](#12-mfa-totp-integration)
13. [Login Flow (End-to-End)](#13-login-flow)
14. [Full Code Reference by File](#14-full-code-reference)

---

## 1. Architecture Overview

The security architecture is composed of these layers (from request entry to resource):

```
                    ┌──────────────────────────────────┐
                    │         HTTP Request              │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   CorsFilter (CORS config)        │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   JwtAuthFilter                  │
                    │   (OncePerRequestFilter)          │
                    │   • Validates JWT                 │
                    │   • Checks permVersion staleness  │
                    │   • Sets FirmContext              │
                    │   • Builds Authentication         │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   AuthEntryPoint                 │
                    │   (AuthenticationEntryPoint)      │
                    │   Handles 401 responses           │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   Spring Security                │
                    │   • Session: STATELESS            │
                    │   • Permit: login, mfa, swagger   │
                    │   • AnyRequest: authenticated     │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   @PreAuthorize checks           │
                    │   hasRole('FIRM_ADMIN')           │
                    │   hasAnyRole('FIRM_ADMIN', ...)   │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   PermissionEvaluator (optional)  │
                    │   Runtime permission code check   │
                    └──────────┬───────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │   Service Layer / Controller      │
                    └──────────────────────────────────┘
```

---

## 2. Package Structure

All security-related files are in two locations:

```
src/main/java/com/lawfirm/erp/
├── config/
│   └── SecurityConfig.java           ← Spring Security config, CORS, PasswordEncoder, AuthManager
│   └── DataInitializer.java          ← Seeds roles, modules, permissions on startup
│
├── auth/
│   ├── controller/
│   │   └── AuthController.java       ← /api/v1/auth/* endpoints (login, mfa, refresh)
│   ├── service/
│   │   └── AuthService.java          ← Auth business logic (password verify, MFA, token issue)
│   └── security/
│       ├── JwtUtil.java              ← JWT generate, parse, validate
│       ├── JwtAuthFilter.java        ← OncePerRequestFilter — the core auth filter
│       ├── CustomUserDetailsService.java ← Loads User entity for Spring Security
│       ├── AuthEntryPoint.java       ← Handles 401 unauthorized responses
│       ├── AuthenticatedUser.java    ← Request-scoped DTO for current user
│       ├── CurrentUserResolver.java  ← Utility to extract current user from request
│       ├── PermissionEvaluator.java  ← Runtime permission code checking
│       ├── FirmContextHolder.java    ← ThreadLocal for firm context
│       ├── FirmInterceptor.java      ← Clears FirmContext after request completes
│       ├── HeaderWrapper.java        ← Wraps HttpServletRequest to add headers
│       ├── TotpUtil.java             ← TOTP (RFC 6238) for Google Authenticator
│       ├── SubdomainGenerator.java   ← Generates firm subdomain slugs
│       └── UsernameGenerator.java    ← Generates usernames from names
│
├── rbac/
│   ├── entity/
│   │   ├── Module.java              ← Modules (CASE_MANAGEMENT, BILLING, etc.)
│   │   ├── Permission.java          ← Permission codes (CASE_MANAGEMENT:VIEW)
│   │   ├── ModulePermission.java    ← Junction: Module ↔ Permission
│   │   ├── Role.java                ← Roles (SUPER_ADMIN, FIRM_ADMIN, etc.)
│   │   ├── RolePermission.java      ← Junction: Role ↔ Permission
│   │   └── UserRole.java            ← (Reserved for multi-role future)
│   ├── repository/
│   │   ├── ModuleRepository.java
│   │   ├── PermissionRepository.java
│   │   ├── ModulePermissionRepository.java
│   │   ├── RoleRepository.java
│   │   ├── RolePermissionRepository.java
│   │   └── UserRoleRepository.java
│   ├── service/
│   │   ├── ModuleService.java
│   │   ├── PermissionService.java
│   │   ├── RoleManagementService.java
│   │   └── RolePermissionService.java
│   └── controller/
│       ├── ModuleController.java
│       ├── PermissionController.java
│       └── RoleController.java
│
├── common/
│   └── enums/
│       ├── UserType.java            ← SUPER_ADMIN, FIRM_USER, CLIENT
│       ├── PermissionAction.java    ← VIEW, CREATE, EDIT, DELETE, etc.
│       ├── PermissionScope.java     ← GLOBAL, TENANT, ASSIGNED, OWN
│       ├── AuthStatus.java          ← SUCCESS, MFA_REQUIRED, PASSWORD_CHANGE_REQUIRED
│       └── ...
│
└── firm/
    └── controller/
        └── FirmRoleController.java  ← Firm-level role management
```

---

## 3. JWT Authentication

### 3.1 Token Types

Three types of tokens exist:

| Token Type | Claim `type` | Expiry | Valid Endpoints |
|-----------|-------------|--------|-----------------|
| **Access Token** | (none) | 24h (configurable) | All authenticated endpoints |
| **MFA Token** | `mfa` | 10 min | `/api/v1/auth/mfa/*` only |
| **Password Change Token** | `pwd_change` | 10 min | `/api/v1/auth/change-password` only |

### 3.2 Access Token Claims

Generated by `JwtUtil.generateAccessToken(User)`:

```json
{
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "sub": "john.doe",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "userUuid": "...",
  "email": "john@firm.com",
  "fullName": "John Doe",
  "roleCode": "FIRM_ADMIN",
  "userType": "FIRM_USER",
  "permVersion": 3,
  "firmId": "660e8400-e29b-41d4-a716-446655440000",
  "firmCode": "APEXLAW",
  "permissions": ["CASE_MANAGEMENT:VIEW", "CASE_MANAGEMENT:CREATE", ...],
  "iat": 1700000000,
  "exp": 1700086400
}
```

### 3.3 Refresh Token

Generated by `JwtUtil.generateRefreshToken(User)`. Separate token with longer expiry (7 days by default). Used by `POST /api/v1/auth/refresh` endpoint.

### 3.4 JWT Configuration (`application.yml`)

```yaml
jwt:
  secret: "your-256-bit-secret-key-here-minimum-32-characters"
  access-expiry: 86400000       # 24 hours in ms
  refresh-expiry: 604800000     # 7 days in ms
```

### 3.5 Key Helper Methods in `JwtUtil.java`

```java
// Generate tokens
generateAccessToken(User)          → String (24h, full access)
generateRefreshToken(User)         → String (7d, refresh only)
generateMfaToken(User)             → String (10 min, /mfa/* endpoints only)
generatePasswordChangeToken(User)  → String (10 min, /change-password only)

// Extract claims
extractAllClaims(token)            → Claims (parse + verify signature)
extractUserId(token)               → UUID
extractUsername(token)             → String (subject)
extractRoleCode(token)             → String
extractUserType(token)             → String
extractFirmId(token)               → UUID (nullable)
extractFirmCode(token)             → String (nullable)

// Validation
validateToken(token)               → boolean (signature check)
isTokenExpired(token)              → boolean
isRefreshToken(token)              → boolean
isLimitedScopeToken(token)         → boolean (mfa or pwd_change)

// Scoped token extraction
extractUserIdFromMfaToken(token)           → UUID (rejects non-mfa tokens)
extractUserIdFromPasswordChangeToken(token) → UUID (rejects non-pwd tokens)

// Auth object building
getAuthentication(token, request)  → Authentication (UsernamePasswordAuthenticationToken)
```

### 3.6 Permission Version Staleness Check

The JWT includes a `permVersion` integer claim. When a user's role permissions change on the server, the `permVersion` on the `User` entity is incremented. `JwtAuthFilter` compares the token's `permVersion` against the database — if they differ, the token is rejected and the user must re-login. This ensures permission changes take effect immediately without waiting for token expiry.

```java
// In JwtAuthFilter.doFilterInternal():
Integer tokenVersion = claims.get("permVersion", Integer.class);
Integer currentVersion = userRepository.findPermissionVersionById(userId);
if (tokenVersion != dbVersion) {
    response.sendError(HttpStatus.UNAUTHORIZED.value(),
            "Your permissions have changed. Please login again.");
    return;
}
```

---

## 4. Spring Security Configuration

### 4.1 `SecurityConfig.java`

This is the central security configuration using **Spring Security 7.x** style (lambda DSL, no `WebSecurityConfigurerAdapter`).

```java
@Component
@EnableWebSecurity
@EnableAsync
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
            .csrf(AbstractHttpConfigurer::disable)                          // Stateless — no CSRF needed
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // No HTTP sessions
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/client/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/mfa/setup/confirm",
                    "/api/v1/auth/mfa/validate",
                    "/api/v1/auth/change-password",
                    "/api/v1/super-admin/login",
                    "/api/v1/super-admin/register",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/webjars/**"
                ).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e.authenticationEntryPoint(authEntryPoint))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### 4.2 Key Configuration Details

| Setting | Value | Reason |
|---------|-------|--------|
| CSRF | Disabled | Stateless JWT auth doesn't need CSRF |
| Sessions | STATELESS | No `HttpSession` — every request is self-contained |
| Auth Entry Point | `AuthEntryPoint` | Returns JSON 401 with meaningful messages |
| JWT Filter Position | Before `UsernamePasswordAuthenticationFilter` | Runs before standard auth |
| Allowed paths | Login, refresh, MFA, swagger | Public endpoints |
| PreAuthorize | Enabled | `@PreAuthorize("hasRole(...)")` works on controllers |
| Password Encoding | BCrypt | Spring Security default |

### 4.3 AuthEntryPoint — JSON Error Responses

`AuthEntryPoint` implements `AuthenticationEntryPoint` and returns structured JSON errors with context-specific messages:

- `BadCredentialsException` → "Invalid username or password."
- `DisabledException` → "Your account has been disabled."
- `LockedException` → "Your account has been locked."
- `AccountExpiredException` → "Your account has expired."
- `CredentialsExpiredException` → "Your credentials have expired."

Response format:
```json
{
  "success": false,
  "message": "Invalid username or password.",
  "responseCode": 401,
  "data": "Invalid username or password."
}
```

### 4.4 CORS Configuration

Allows multiple frontend origins during development. In production, restrict to just your deployed domain.

---

## 5. The Filter Chain

### 5.1 `JwtAuthFilter` (extends `OncePerRequestFilter`)

This is the heart of the authentication system. Flow:

```
Request comes in
  │
  ├── Has "Authorization: Bearer <token>"?
  │   YES → continue
  │   NO  → skip to filterChain.doFilter() → Spring Security rejects (401)
  │
  ├── validateToken(token)? (signature check)
  │   YES → continue
  │   NO  → 401 "Invalid token"
  │
  ├── isTokenExpired(token)?
  │   NO  → continue
  │   YES → 401 "Token is expired"
  │
  ├── isLimitedScopeToken(token)?
  │   YES → Is request path /auth/mfa/* or /auth/change-password?
  │         YES → let through (skip all further checks)
  │         NO  → 401 "Token only valid for authentication steps"
  │   NO  → continue
  │
  ├── permVersion staleness check (non-SUPER_ADMIN users only)
  │   token.permVersion == DB.permVersion?
  │   YES → continue
  │   NO  → 401 "Your permissions have changed. Please login again."
  │
  ├── Set FirmContext (ThreadLocal for multi-tenancy)
  │   firmId + firmCode from claims → FirmContextHolder.set(firmId, firmCode)
  │
  ├── Build Authentication object
  │   Claims → AuthenticatedDetail → UsernamePasswordAuthenticationToken
  │   Authorities: ROLE_{roleCode}
  │
  ├── Wrap request with deviceId header
  │
  ├── Set SecurityContext
  │   SecurityContextHolder.getContext().setAuthentication(auth)
  │
  ├── Set request attribute "authenticatedUser"
  │   Controllers/services use this via CurrentUserResolver
  │
  ├── filterChain.doFilter(request, response)
  │
  └── Finally → FirmContextHolder.clear()   ← Critical! Prevents ThreadLocal leaks
```

### 5.2 `CustomUserDetailsService`

Implements `UserDetailsService`. Used by Spring Security's `AuthenticationManager` during password validation (login flow).

```java
public class CustomUserDetailsService implements UserDetailsService {
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
```

The `User` entity itself implements `UserDetails`:

```java
public class User extends ActiveAuditableEntity implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
    }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return !Boolean.TRUE.equals(isBlocked); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return isActive(); }
}
```

### 5.3 `HeaderWrapper`

Wraps `HttpServletRequest` to inject additional headers. Used to add `deviceId` to the request:

```java
String deviceId = request.getHeader("deviceId");
deviceId = deviceId != null ? deviceId + "_" + jwtUtil.extractUserId(token) : "UNKNOWN_";
request = new HeaderWrapper(request, Map.of("deviceId", deviceId));
```

---

## 6. Multi-Tenancy

### 6.1 Firm Context Holder (`FirmContextHolder`)

Uses `ThreadLocal` to store the current firm's ID and code during request processing:

```java
public class FirmContextHolder {
    private static final ThreadLocal<UUID> FIRM_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> FIRM_CODE = new ThreadLocal<>();

    public static void set(UUID firmId, String firmCode) { ... }
    public static UUID getFirmId() { ... }
    public static String getFirmCode() { ... }
    public static void clear() { ... }
    public static boolean isSuperAdminMode() {
        return FIRM_ID.get() == null;   // ← Super admin has no firm context
    }
}
```

### 6.2 Firm Interceptor (`FirmInterceptor`)

Clears the `ThreadLocal` after every request to prevent memory leaks:

```java
@Component
public class FirmInterceptor implements HandlerInterceptor {
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        FirmContextHolder.clear();
    }
}
```

### 6.3 Where Firm Context is Set

In `JwtAuthFilter.doFilterInternal()`:

```java
if (firmId != null && !"SUPER_ADMIN".equals(userType)) {
    FirmContextHolder.set(UUID.fromString(firmId), firmCode);
} else {
    FirmContextHolder.clear();  // Super admin has no firm
}
```

### 6.4 Database-Level Tenancy

Every firm-scoped entity has a `firm_id` foreign key. Queries filter by `FirmContextHolder.getFirmId()` in the service layer. Super admins skip firm filtering (they see all).

---

## 7. RBAC Entity Model

### 7.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────┐
│                      MODULES                             │
│  id, name, code (unique), parent_id, level,             │
│  display_order, sort_order, icon, path, is_system       │
└─────────────────────┬───────────────────────────────────┘
                      │ 1
                      │
                      │ *
┌─────────────────────▼───────────────────────────────────┐
│                MODULE_PERMISSIONS                        │
│  module_id (FK) + permission_id (FK)  ← junction table   │
└─────────────────────┬───────────────────────────────────┘
                      │ *
                      │ 1
┌─────────────────────▼───────────────────────────────────┐
│                    PERMISSIONS                           │
│  id, module_code, action (enum), scope (enum),          │
│  code (unique: "MODULE:ACTION"), description            │
└─────────────────────┬───────────────────────────────────┘
                      │ 1
                      │
                      │ *
┌─────────────────────▼───────────────────────────────────┐
│                 ROLE_PERMISSIONS                         │
│  role_id (FK) + permission_id (FK)  ← junction table     │
│  UNIQUE(role_id, permission_id)                         │
└─────────────────────┬───────────────────────────────────┘
                      │ *
                      │ 1
┌─────────────────────▼───────────────────────────────────┐
│                      ROLES                               │
│  id, role_name, role_code (unique+firm), is_system,     │
│  applicable_to (UserType), description,                  │
│  extends_role_id (optional hierarchy)                   │
└─────────────────────┬───────────────────────────────────┘
                      │ 1
                      │
                      │ *
┌─────────────────────▼───────────────────────────────────┐
│                      USERS                               │
│  username (unique+firm), email (unique+firm),            │
│  password, user_type (enum), role_id (direct FK!),       │
│  firm_id, permission_version, must_change_password,      │
│  mfa_enabled, mfa_secret, mfa_verified, ...             │
└─────────────────────────────────────────────────────────┘
```

### 7.2 Key Design Decision: Direct Role FK vs. UserRole Junction

The `User` entity has a **direct FK** to `Role` (not via the `UserRole` join table). This is the authoritative source of truth for "what role does this user have?" The `UserRole` table exists in the schema for future multi-role support but is not currently used for permission checks.

**Why this matters:** `PermissionEvaluator` reads permissions from `User.role → RolePermission → Permission`, NOT from `UserRole → RolePermission → Permission`.

### 7.3 Role Entity

```java
public class Role extends ActiveAuditableEntity {
    private Firm firm;              // Null for system roles (SUPER_ADMIN)
    private String roleName;
    private String roleCode;        // SUP-ER_ADMIN, FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT
    private Boolean isSystem;       // System roles can't be deleted
    private UUID parentRoleId;
    private Role extendsRole;       // Optional role hierarchy
    private UserType applicableTo;  // FIRM_USER or CLIENT
    private String description;
    private List<RolePermission> permissions;
}
```

**Pre-defined system roles:**

| Role Code | Type | Description |
|-----------|------|-------------|
| `SUPER_ADMIN` | System | Full platform access |
| `FIRM_ADMIN` | System | Law firm operations manager |
| `ADVOCATE` | System | Practicing lawyer |
| `PARALEGAL` | System | Support staff |
| `CLIENT` | System | Client of the firm |

Custom roles can be created per-firm (non-system, with `firm_id` set and `is_system = false`).

### 7.4 Permission Entity

```java
public class Permission extends ActiveAuditableEntity {
    private String moduleCode;          // Which module owns this
    private PermissionAction action;    // Enum: ACCESS, VIEW, CREATE, EDIT, DELETE, etc.
    private PermissionScope scope;      // Enum: GLOBAL, TENANT, OWN
    private String code;                // "MODULE:ACTION" e.g. "CASE_MANAGEMENT:VIEW"
    private String description;
}
```

**Standard permission actions (defined in `PermissionAction` enum):**

```
ACCESS, VIEW, CREATE, EDIT, DELETE,
UPLOAD, DOWNLOAD, SHARE,
EXPORT, SCHEDULE, UPDATE_STATUS, ASSIGN,
APPROVE, REJECT, REVIEW,
ARCHIVE, RESTORE, PRINT, FORWARD
```

**Scopes (defined in `PermissionScope` enum):**

| Scope | Meaning | Used For |
|-------|---------|----------|
| `GLOBAL` | Across all firms | Super Admin only |
| `TENANT` | All records in the firm | Firm users |
| `ASSIGNED` | Only assigned records | Advocates |
| `OWN` | Own records only | Clients |

### 7.5 Module Entity

```java
public class Module extends ActiveAuditableEntity {
    private String name;                // Display name
    private String code;                // Unique code, e.g. "CASE_MANAGEMENT"
    private String description;
    private Module parent;              // For sub-modules
    private List<Module> subModules;
    private Integer level;              // Depth in hierarchy
    private Integer displayOrder;
    private Integer sortOrder;
    private String icon;                // Frontend icon name
    private String path;                // Frontend route path
    private Boolean isSystem;
}
```

**Pre-defined modules (seeded by DataInitializer):**

| Code | Name | Display Order |
|------|------|--------------|
| CASE_MANAGEMENT | Case Management | 1 |
| DOCUMENT_MANAGEMENT | Document Management | 2 |
| CLIENT_MANAGEMENT | Client Management | 3 |
| BILLING | Billing & Invoices | 4 |
| CALENDAR | Calendar | 5 |
| EMPLOYEE | Employee Management | 6 |
| REPORTS | Reports | 7 |
| AUDIT | Audit Logs | 8 |

---

## 8. Permission Evaluation

### 8.1 `PermissionEvaluator` — Runtime Permission Checks

The `PermissionEvaluator` is a Spring `@Component` that checks whether the current user has a specific permission code (e.g., `"CASE_MANAGEMENT:VIEW"`). It uses a **ConcurrentHashMap cache** keyed by user ID to avoid repeated database hits.

```java
@Component
public class PermissionEvaluator {

    private final ConcurrentHashMap<UUID, Set<String>> permissionCache = new ConcurrentHashMap<>();

    public void require(String permissionCode) {
        AuthenticatedUser currentUser = getCurrentUser();
        if (currentUser == null) throw new ForbiddenException("No authenticated user");
        if (currentUser.isSuperAdmin()) return;  // Super admin has everything

        Set<String> permissions = getUserPermissions(currentUser);
        if (!permissions.contains(permissionCode)) {
            throw new ForbiddenException("Missing required permission: " + permissionCode);
        }
    }

    public boolean has(String permissionCode) {
        try { require(permissionCode); return true; }
        catch (ForbiddenException e) { return false; }
    }

    public boolean hasModuleAccess(UUID firmId, String moduleCode) {
        return firmModuleRepository.existsByFirmIdAndModuleCodeAndIsEnabledTrue(firmId, moduleCode);
    }

    public void requireModuleAccess(String moduleCode) {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId != null && !hasModuleAccess(firmId, moduleCode)) {
            throw new ForbiddenException("Module '" + moduleCode + "' is not enabled for your firm.");
        }
    }

    public void clearUserCache(UUID userId) { permissionCache.remove(userId); }
    public void clearAllCache() { permissionCache.clear(); }
}
```

### 8.2 How Permissions Are Loaded for a User

```java
private Set<String> getUserPermissions(AuthenticatedUser authUser) {
    return permissionCache.computeIfAbsent(authUser.getId(), id -> {
        Set<String> permissions = new HashSet<>();
        User user = userRepository.findById(id).orElse(null);
        if (user == null || user.getRole() == null) return permissions;

        var rolePermissions = rolePermissionRepository.findByRole(user.getRole());
        for (var rp : rolePermissions) {
            if (Boolean.TRUE.equals(rp.getPermission().isActive())) {
                permissions.add(rp.getPermission().getCode());
            }
        }
        return permissions;
    });
}
```

### 8.3 `CurrentUserResolver` — Accessing the Current User

A utility to extract the authenticated user from anywhere in the request scope:

```java
@Component
public class CurrentUserResolver {
    public AuthenticatedUser getCurrentUser() { ... }   // From request attribute
    public UUID getCurrentUserId() { ... }
    public UUID getCurrentFirmId() { ... }
    public boolean isSuperAdmin() { ... }
    public boolean isAuthenticated() { ... }
}
```

Usage in service layer:
```java
@Autowired private CurrentUserResolver currentUserResolver;

UUID currentUserId = currentUserResolver.getCurrentUserId();
UUID currentFirmId = currentUserResolver.getCurrentFirmId();
```

### 8.4 `AuthenticatedUser` — Request-Scoped DTO

Populated by `JwtAuthFilter` and stored as a request attribute:

```java
@Data
@Component
@RequestScope
public class AuthenticatedUser {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private UUID firmId;
    private String firmCode;
    private String userType;
    private List<String> roles;
    private List<String> permissions;    // Loaded from JWT claims
    private String accessToken;

    public boolean isSuperAdmin() { return "SUPER_ADMIN".equals(userType); }
    public boolean isFirmUser()  { return "FIRM_USER".equals(userType); }
    public boolean isClient()    { return "CLIENT".equals(userType); }
}
```

### 8.5 Cache Invalidation

The permission cache is invalidated whenever role permissions change:

```java
// In RolePermissionService or wherever permissions are updated:
permissionEvaluator.clearUserCache(userId);
```

---

## 9. DataInitializer Seeding

### 9.1 Startup Seed Order

```
1. Tenant Types           (SOLO, LAW_FIRM)
2. System Firm            ("SYSTEM" — home of SUPER_ADMIN)
3. System Roles           (SUPER_ADMIN, FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT)
4. Modules + Permissions  (8 modules, 5-8 permissions each)
5. ModulePermissions      (junction table)
6. RolePermissions        (access-level matrix)
```

### 9.2 Access Level Matrix

```
                    SUPER_ADMIN  FIRM_ADMIN  ADVOCATE  PARALEGAL  CLIENT
CASE_MANAGEMENT      FULL         FULL        FULL      READ_ONLY  OWN
DOCUMENT_MANAGEMENT  FULL         FULL        FULL      READ_ONLY  OWN
CLIENT_MANAGEMENT    FULL         FULL        READ_ONLY  READ_ONLY  NO_ACCESS
BILLING              FULL         FULL        READ_ONLY  NO_ACCESS  OWN
CALENDAR             FULL         FULL        FULL       READ_ONLY  OWN
EMPLOYEE             FULL         FULL        NO_ACCESS  NO_ACCESS  NO_ACCESS
REPORTS              FULL         FULL        READ_ONLY  NO_ACCESS  NO_ACCESS
AUDIT                FULL         FULL        NO_ACCESS  NO_ACCESS  NO_ACCESS
```

**Access level definitions:**
- **FULL** → All permissions for the module (ACCESS, VIEW, CREATE, EDIT, DELETE + extras)
- **READ_ONLY** → Only ACCESS + VIEW permissions
- **OWN** → Same as READ_ONLY but enforced at service layer (client sees only their records)
- **NO_ACCESS** → No permissions assigned

### 9.3 Permission Code Convention

Permissions follow the format `{MODULE_CODE}:{ACTION}`, e.g.:
```
CASE_MANAGEMENT:ACCESS
CASE_MANAGEMENT:VIEW
CASE_MANAGEMENT:CREATE
CASE_MANAGEMENT:EDIT
CASE_MANAGEMENT:DELETE
CASE_MANAGEMENT:ASSIGN
CASE_MANAGEMENT:ARCHIVE
CASE_MANAGEMENT:UPDATE_STATUS
```

### 9.4 How To Add a New Module

1. Add the module definition to `createModulesAndPermissions()` in `DataInitializer`
2. Add the module's row to the access level matrix in `assignPermissionsToRoles()`
3. Define any extra actions beyond the standard 5 (ACCESS, VIEW, CREATE, EDIT, DELETE)
4. Restart the application — permissions seed automatically (idempotently)

---

## 10. Controller Security Patterns

### 10.1 Role-Based Access with `@PreAuthorize`

Controllers use `@PreAuthorize` with Spring Security's `hasRole()` / `hasAnyRole()`:

```java
@RestController
@RequestMapping("/api/v1/firm/cases")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE', 'PARALEGAL')")
public class CaseController { ... }
```

**Common patterns:**

```java
// All roles can access
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FIRM_ADMIN', 'ADVOCATE')")

// Admin only
@PreAuthorize("hasRole('SUPER_ADMIN')")
@PreAuthorize("hasRole('FIRM_ADMIN') or hasRole('SUPER_ADMIN')")

// Firm admin (not super admin)
@PreAuthorize("hasRole('FIRM_ADMIN')")

// Specific permission check (via PermissionEvaluator)
// (Not a built-in Spring expression — use service-layer check)
```

### 10.2 Permission-Level Checks in Service Layer

Fine-grained permission checks go in the service layer using `PermissionEvaluator`:

```java
@Service
public class CaseService {
    private final PermissionEvaluator permissionEvaluator;

    public CaseResponse createCase(CreateCaseRequest request) {
        permissionEvaluator.require("CASE_MANAGEMENT:CREATE");
        // ... business logic
    }

    public void deleteCase(UUID caseId) {
        permissionEvaluator.require("CASE_MANAGEMENT:DELETE");
        // ... business logic
    }
}
```

### 10.3 API Response Wrapper Pattern

All responses follow a consistent wrapper using `ApiRequest<T>` and `ApiResponse<T>`:

```java
// Request body always wrapped:
POST /api/v1/auth/login
{
    "data": {
        "lawFirmCode": "APEXLAW",
        "username": "john.doe",
        "password": "securePassword123"
    }
}

// Response always wrapped:
{
    "success": true,
    "message": "Authenticated",
    "responseCode": 200,
    "data": {
        "status": "SUCCESS",
        "accessToken": "eyJhbGci...",
        "refreshToken": "eyJhbGci...",
        "expiresIn": 86400000
    }
}
```

Error responses:
```json
{
    "success": false,
    "message": "Missing required permission: CASE_MANAGEMENT:DELETE",
    "responseCode": 403,
    "data": null
}
```

### 10.4 Super Admin vs. Firm Admin Separation

Two distinct controller namespaces:

| Path | Role | Description |
|------|------|-------------|
| `/api/v1/admin/*` | SUPER_ADMIN | Platform-level administration |
| `/api/v1/firm/*` | FIRM_ADMIN, ADVOCATE, etc. | Firm-level operations |
| `/api/v1/auth/*` | Mixed | Authentication (public + authenticated) |
| `/api/v1/super-admin/*` | (public) | Super admin login/register |

---

## 11. Scope Ceiling Enforcement

### 11.1 The Problem

Not all permissions are available to all roles. For example, `USER_MANAGEMENT:VIEW` with scope `GLOBAL` is a super-admin-only permission. If a `FIRM_ADMIN` tries to assign it, the system rejects it.

### 11.2 Enforcement in Role Permission Assignment

When assigning permissions to a role, the `RolePermissionService` checks permission scopes against the role's ceiling:

```java
// In RolePermissionService or RoleManagementService:
private void validatePermissionScope(Role role, List<UUID> permissionIds) {
    // Get all requested permissions
    List<Permission> permissions = permissionRepository.findAllById(permissionIds);

    for (Permission permission : permissions) {
        // GLOBAL scope permissions are only for SUPER_ADMIN role
        if (permission.getScope() == PermissionScope.GLOBAL
                && !"SUPER_ADMIN".equals(role.getRoleCode())) {
            throw new ForbiddenException(
                "Permission '" + permission.getCode() + "' (scope: GLOBAL) " +
                "exceeds your role's ceiling. This scope is not available " +
                "for role type '" + role.getRoleCode() + "'."
            );
        }
    }
}
```

### 11.3 Scope Definitions

| Scope | Who Can Assign | Description |
|-------|---------------|-------------|
| `GLOBAL` | SUPER_ADMIN only | Platform-level, cross-firm |
| `TENANT` | FIRM_ADMIN+ | Firm-wide operations |
| `OWN` | FIRM_ADMIN+ | User's own records only |

### 11.4 Role Ceiling Rules

- **SUPER_ADMIN**: Can have any permission (GLOBAL + TENANT + OWN)
- **FIRM_ADMIN**: Can have TENANT + OWN only (not GLOBAL)
- **ADVOCATE, PARALEGAL, CLIENT**: Can have TENANT + OWN only

---

## 12. MFA / TOTP Integration

### 12.1 TOTP Utility (`TotpUtil`)

Implements RFC 6238, compatible with Google Authenticator:

```java
@Component
public class TotpUtil {
    public String generateSecret()                  // Random Base32 secret (20 bytes)
    public String buildQrCodeUri(secret, username, firmCode)  // otpauth:// URI for QR code
    public String formatSecretForDisplay(secret)    // For manual entry (JBSW Y3DP EHPK 3PXP)
    public boolean verify(secret, code)             // Verify 6-digit TOTP code
    public boolean isProductionMode()               // Dev bypass mode (123456 always works)
}
```

### 12.2 MFA Flow (in `AuthService.performAuthentication()`)

```
Login Request
   │
   ├── Step 1: Firm code → Resolve Firm
   ├── Step 2: Find User (username + firmId)
   ├── Step 3: Type guard (client vs. internal)
   ├── Step 4: Account status checks (active, blocked, locked)
   ├── Step 5: Password verification via AuthenticationManager
   │   ├── Success → Reset lockout counters
   │   └── Failure → Increment lockout, throw BadCredentialsException
   │
   ├── Step 6: Must change password?
   │   YES → Return PASSWORD_CHANGE_REQUIRED + passwordChangeToken (10 min)
   │
   ├── Step 7: MFA enabled?
   │   ├── MFA NOT verified → Return MFA_SETUP_REQUIRED + mfaToken + QR code URI
   │   ├── MFA verified, no TOTP code → Return MFA_REQUIRED + mfaToken
   │   └── MFA verified + valid TOTP → Issue full tokens
   │
   └── Step 8: No MFA → Issue full tokens
```

### 12.3 AuthStatus Enum

Tells the frontend what to do next:

```java
public enum AuthStatus {
    SUCCESS,                    // → Store accessToken, proceed to app
    PASSWORD_CHANGE_REQUIRED,   // → Show change-password screen
    MFA_SETUP_REQUIRED,         // → Show QR code for scanning
    MFA_REQUIRED                // → Show 6-digit code input
}
```

### 12.4 MFA Enforcement Rules

| User Type | MFA Required? | Notes |
|-----------|--------------|-------|
| SUPER_ADMIN | YES (forced in code) | Cannot be disabled |
| FIRM_ADMIN | YES (forced in code) | Cannot be disabled |
| ADVOCATE | Optional | Firm admin can bulk-enable |
| PARALEGAL | No | Not enforced |
| CLIENT | No | Not enforced |

---

## 13. Login Flow (End-to-End)

```
Frontend                             Backend
   │                                    │
   │  POST /api/v1/auth/login           │
   │  { "data": {                       │
   │    "lawFirmCode": "APEXLAW",       │
   │    "username": "john.doe",         │
   │    "password": "..."               │
   │  }}                                │
   │─────────────────────────────────►  │
   │                                    │
   │  AuthService.performAuthentication │
   │  ├── Resolve Firm                  │
   │  ├── Find User                     │
   │  ├── Check active/blocked/locked   │
   │  ├── authenticationManager.        │
   │  │   authenticate(...)             │
   │  ├── Check mustChangePassword      │
   │  ├── Check MFA enabled             │
   │  └── issueFullTokens(user)         │
   │                                    │
   │  ◄─────────────────────────────────│
   │  {                                 │
   │    "status": "SUCCESS",           │
   │    "accessToken": "eyJ...",        │
   │    "refreshToken": "eyJ...",       │
   │    "expiresIn": 86400000           │
   │  }                                 │
   │                                    │
   │  Store token in memory/localStorage│
   │                                    │
   │  GET /api/v1/firm/cases            │
   │  Authorization: Bearer eyJ...      │
   │─────────────────────────────────►  │
   │                                    │
   │  JwtAuthFilter.doFilterInternal    │
   │  ├── Extract token from header     │
   │  ├── Validate signature            │
   │  ├── Check expiry                  │
   │  ├── Check permVersion staleness   │
   │  ├── Set FirmContextHolder         │
   │  ├── Build Authentication object   │
   │  ├── Set SecurityContext           │
   │  └── Continue filter chain         │
   │                                    │
   │  CaseController                    │
   │  @PreAuthorize("hasAnyRole(...)")  │
   │  → caseService.listCases(...)      │
   │                                    │
   │  ◄─────────────────────────────────│
   │  { "success": true, ... }          │
   │                                    │
```

### 13.1 Limited-Scope Token Flow (MFA)

```
Frontend                         Backend
   │                                │
   │  (First login — MFA enabled)   │
   │  POST /auth/login              │
   │─────────────────────────►      │
   │  ◄── MFA_SETUP_REQUIRED        │
   │  { status: "MFA_REQUIRED",     │
   │    mfaToken: "eyJ..." }        │
   │                                │
   │  POST /auth/mfa/validate       │
   │  { data: {                     │
   │    mfaToken: "eyJ...",         │
   │    totpCode: "482916"          │
   │  }}                            │
   │─────────────────────────►      │
   │  JwtAuthFilter:                │
   │  isLimitedScopeToken = true    │
   │  Path starts with /auth/mfa/   │
   │  → Let through                 │
   │  (no permVersion check,        │
   │   no firm context,             │
   │   no full auth principal)      │
   │                                │
   │  AuthService.validateMfa()     │
   │  ├── Extract userId from       │
   │  │   mfaToken (rejects if      │
   │  │   not "mfa" type claim)     │
   │  ├── Verify TOTP code          │
   │  └── issueFullTokens(user)     │
   │                                │
   │  ◄── SUCCESS                   │
   │  { status: "SUCCESS",          │
   │    accessToken: "eyJ...",      │
   │    refreshToken: "eyJ..." }    │
   │                                │
```

---

## 14. Full Code Reference

### 14.1 When You Create a New Module (Step-by-Step)

**Step 1: Define module in DataInitializer**

Add to `DataInitializer.java`, method `createModulesAndPermissions()`:

```java
{"AUDIT", "Audit Logs", "View system audit logs", 8, 80, "ShieldIcon", "/audit",
    new PermissionAction[]{}}
```

Step: Add extra actions array — the module gets the standard 5 (ACCESS, VIEW, CREATE, EDIT, DELETE) plus any extras.

**Step 2: Set access levels in the matrix**

Add row to `assignPermissionsToRoles()` matrix:

```java
{"AUDIT", FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
```

**Step 3: Create entity, repository, service, controller**

Follow the existing pattern — extend `ActiveAuditableEntity`, use `@PreAuthorize`, `PermissionEvaluator`.

**Step 4: Add permission checks in service layer**

```java
permissionEvaluator.require("AUDIT:VIEW");
```

### 14.2 When Adding a New Endpoint

```java
@RestController
@RequestMapping("/api/v1/firm/example")
@PreAuthorize("hasAnyRole('FIRM_ADMIN', 'ADVOCATE')")
@RequiredArgsConstructor
public class ExampleController {

    private final ResponseHandler responseHandler;
    private final ExampleService exampleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExampleResponse>>> list() {
        return responseHandler.ok(exampleService.getAll(), "Retrieved");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExampleResponse>> create(
            @Valid @RequestBody ApiRequest<CreateExampleRequest> request) {
        return responseHandler.ok(exampleService.create(request.getData()), "Created");
    }
}
```

### 14.3 When Creating a New Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ExampleService {

    private final ExampleRepository repository;
    private final PermissionEvaluator permissionEvaluator;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;

    public ExampleResponse create(CreateExampleRequest request) {
        permissionEvaluator.require("EXAMPLE:CREATE");
        UUID firmId = currentUserResolver.getCurrentFirmId();
        // ... business logic
        auditService.log(AuditAction.CREATE, AuditEntity.EXAMPLE, userId, description);
        return response;
    }
}
```

### 14.4 Key Application Properties

```yaml
jwt:
  secret: "your-secret-key-at-least-32-chars-long"
  access-expiry: 86400000       # 24h
  refresh-expiry: 604800000     # 7d

app:
  name: NepalCRM
  production: false             # Set true in prod (disables TOTP dev bypass)

security:
  max-login-attempts: 5         # Lockout threshold
```

---

## Summary of Patterns to Replicate

| Pattern | Implementation | Key File(s) |
|---------|---------------|-------------|
| Stateless JWT | `OncePerRequestFilter` before `UsernamePasswordAuthenticationFilter` | `JwtAuthFilter.java`, `SecurityConfig.java` |
| Multi-tenancy | `ThreadLocal` + request filter | `FirmContextHolder.java`, `FirmInterceptor.java` |
| Role-based access | `@PreAuthorize("hasRole(...)")` on controllers | `SecurityConfig.java` (enables `@EnableMethodSecurity`) |
| Permission check | `PermissionEvaluator.require(code)` in service layer | `PermissionEvaluator.java` |
| Token scope restriction | `isLimitedScopeToken()` + path whitelist | `JwtAuthFilter.java`, `JwtUtil.java` |
| Permission staleness | `permVersion` claim in JWT, compared on every request | `JwtAuthFilter.java` |
| Scope ceiling | Validate `PermissionScope` vs. `Role` during assignment | `RoleManagementService.java` |
| MFA/TOTP | RFC 6238 with `TotpUtil`, `AuthStatus` flow | `TotpUtil.java`, `AuthService.java` |
| RBAC seeding | `CommandLineRunner` + access-level matrix | `DataInitializer.java` |
| API wrapper | `ApiRequest<T>` + `ApiResponse<T>` + `ResponseHandler` | `ApiRequest.java`, `ApiResponse.java` |
| Audit logging | `@Audit` annotation or `AuditService.log()` | `AuditService.java`, `AuditAspect.java` |
| User identity | `CurrentUserResolver` + `AuthenticatedUser` (request attribute) | `CurrentUserResolver.java` |
| Error responses | `AuthEntryPoint` (401), `GlobalExceptionHandler` (all others) | `AuthEntryPoint.java`, `GlobalExceptionHandler.java` |
| Password encoding | `BCryptPasswordEncoder` | `SecurityConfig.java` |
