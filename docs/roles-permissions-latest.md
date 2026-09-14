# Roles, Permissions & Modules — Full System Documentation

> **Last updated**: September 12, 2026
> **Purpose**: Complete reference of how RBAC works end-to-end — creation, assignment, enforcement, and firm cloning.

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────┐
│         SUPER_ADMIN (SA)                │
│  Hardcoded bypass — isSuperAdmin()      │
│  Sets FIRM_ADMIN permissions per firm   │
│  via override endpoint (no ceiling)     │
└──────────────┬──────────────────────────┘
               │
    ┌──────────▼──────────┐
    │  Firm A's FIRM_ADMIN │
    │  (permissions set by  │
    │   SA via override)    │
    │  Ceiling for all      │
    │  employee roles       │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │  Employee Roles       │
    │  ADVOCATE, PARALEGAL, │
    │  CLIENT, Custom roles │
    │  (Firm Admin assigns  │
    │   within ceiling)     │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │  Employees            │
    │  User.role → Role     │
    │  Role → Permissions   │
    └─────────────────────┘
```

**Key invariant**: No employee ever holds a permission their Firm Admin doesn't hold. Firm Admin's permissions are set by Super Admin.

---

## 2. How Modules Are Created

### Source: `DataInitializer.createModulesAndPermissions()`

Modules are **seeded at boot** by `DataInitializer` (implements `CommandLineRunner`). Each module defines:

| Field | Purpose |
|-------|---------|
| `code` | Unique identifier (e.g., `CASE_MANAGEMENT`) |
| `name` | Display name |
| `description` | Short description |
| `displayOrder` / `sortOrder` | UI ordering |
| `icon` | Frontend icon name |
| `path` | Frontend route |
| `isSystem` | Always `true` for seeded modules |

**16 modules are seeded:**

| # | Code | Icon | Path |
|---|------|------|------|
| 1 | CASE_MANAGEMENT | FolderIcon | /cases |
| 2 | DOCUMENT_MANAGEMENT | FileIcon | /documents |
| 3 | CLIENT_MANAGEMENT | UsersIcon | /clients |
| 4 | BILLING | DollarSignIcon | /billing |
| 5 | CALENDAR | CalendarIcon | /calendar |
| 6 | EMPLOYEE | BriefcaseIcon | /employees |
| 7 | REPORTS | BarChartIcon | /reports |
| 8 | AUDIT | ShieldIcon | /audit |
| 9 | PROJECT_MANAGEMENT | ClipboardIcon | /projects |
| 10 | ROLE_MANAGEMENT | ShieldCheckIcon | /roles |
| 11 | FIRM_MANAGEMENT | BuildingIcon | /firm |
| 12 | MENU_MANAGEMENT | MenuIcon | /menus |
| 13 | PERMISSION_MANAGEMENT | KeyIcon | /permissions |
| 14 | SCRAPER_MANAGEMENT | GlobeIcon | /scraper |
| 15 | USER_MANAGEMENT | UsersIcon | /users |
| 16 | DASHBOARD_MANAGEMENT | LayoutDashboardIcon | /dashboard |

**Modules are NOT per-firm.** They are global. Firm-level module enable/disable is handled by `FirmModule` (a separate junction table, not part of RBAC).

---

## 3. How Permissions Are Created

### Source: `DataInitializer.createModulesAndPermissions()`

For each module, permissions are generated from:

1. **Standard actions** (every module gets these):
   - `ACCESS`, `VIEW`, `CREATE`, `EDIT`, `DELETE`

2. **Extra actions** (module-specific):
   - CASE_MANAGEMENT: `ASSIGN`, `ARCHIVE`, `UPDATE_STATUS`
   - DOCUMENT_MANAGEMENT: `UPLOAD`, `DOWNLOAD`, `SHARE`
   - BILLING: `APPROVE`, `EXPORT`
   - CALENDAR: `SCHEDULE`
   - REPORTS: `EXPORT`, `PRINT`
   - PROJECT_MANAGEMENT: `CREDENTIAL_VIEW`, `CREDENTIAL_REVEAL`
   - SCRAPER_MANAGEMENT: `EXPORT`

3. **Permission code format**: `MODULE_CODE:ACTION` (e.g., `CASE_MANAGEMENT:VIEW`)

4. **Permission scope** (per action):
   - `TENANT` — default for all seeded permissions
   - `GLOBAL` — reserved for SUPER_ADMIN (never granted to templates)

**Total permissions seeded**: ~120+ (varies by module extra actions)

Each permission is also linked to its module via `ModulePermission` junction table.

---

## 4. How Roles Are Created

### 4.1 System Roles (boot-seeded)

**Source: `DataInitializer.createSystemRoles()`**

5 system roles are seeded:

| Role Code | Description | isSystem | applicableTo |
|-----------|-------------|----------|--------------|
| SUPER_ADMIN | Full system access | true | null |
| FIRM_ADMIN | Manages law firm operations | true | FIRM |
| ADVOCATE | Practicing lawyer | true | FIRM_USER |
| PARALEGAL | Support staff | true | FIRM_USER |
| CLIENT | Client of the firm | true | CLIENT |

**Key fields on `Role` entity:**
- `firm_id` — NULL for system roles, set for firm-scoped roles
- `is_system` — true for system roles, false for firm roles
- `applicable_to` — UserType enum (FIRM, FIRM_USER, CLIENT, SUPER_ADMIN)

### 4.2 Firm Roles (created at firm onboarding)

**Source: `FirmServiceImpl.cloneSystemRolesForFirm()`**

When a new firm is created:
1. Load all system roles (`is_system = true, firm_id IS NULL`)
2. Skip `SUPER_ADMIN` (platform-level only)
3. For each remaining role (FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT):
   - Create a new `Role` with `firm_id = <new firm>`, `is_system = false`
   - Copy name, code, description, applicableTo
   - **Permissions are NOT copied** — the role starts with zero permissions

### 4.3 Custom Roles (created by Firm Admin or SA)

Firm Admin can create custom roles within their firm:
- `firm_id = <current firm>`, `is_system = false`
- Permissions assigned separately via the permission edit endpoint

SA can also create roles on-behalf via `POST /super-admin/firms/{firmId}/roles`.

---

## 5. The Permission Assignment Matrix

### Source: `DataInitializer.assignPermissionsToRoles()`

The **master matrix** defines default permissions for each system role per module:

```
Module                   | SUPER_ADMIN | FIRM_ADMIN | ADVOCATE    | PARALEGAL   | CLIENT
-------------------------|-------------|------------|-------------|-------------|--------
CASE_MANAGEMENT          | FULL        | FULL       | FULL        | READ_ONLY   | OWN
DOCUMENT_MANAGEMENT      | FULL        | FULL       | FULL        | READ_ONLY   | OWN
CLIENT_MANAGEMENT        | FULL        | FULL       | READ_ONLY   | READ_ONLY   | NO_ACCESS
BILLING                  | FULL        | FULL       | READ_ONLY   | NO_ACCESS   | OWN
CALENDAR                 | FULL        | FULL       | FULL        | READ_ONLY   | OWN
EMPLOYEE                 | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
REPORTS                  | FULL        | FULL       | READ_ONLY   | NO_ACCESS   | NO_ACCESS
AUDIT                    | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
PROJECT_MANAGEMENT       | FULL        | FULL       | READ_ONLY   | READ_ONLY   | OWN
ROLE_MANAGEMENT          | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
FIRM_MANAGEMENT          | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
MENU_MANAGEMENT          | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
PERMISSION_MANAGEMENT    | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
SCRAPER_MANAGEMENT       | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
USER_MANAGEMENT          | FULL        | FULL       | NO_ACCESS   | NO_ACCESS   | NO_ACCESS
DASHBOARD_MANAGEMENT     | FULL        | FULL       | READ_ONLY   | READ_ONLY   | NO_ACCESS
```

**Access level meanings:**
- `FULL` → ALL permissions for that module (ACCESS + VIEW + CREATE + EDIT + DELETE + extras)
- `READ_ONLY` → ACCESS + VIEW only
- `OWN` → ACCESS + VIEW (same as READ_ONLY); "own records only" enforced in service layer via `Permission.scope == OWN`
- `NO_ACCESS` → nothing written for that role/module pair

**How it's written:** For each cell, `RolePermission` rows are created linking the Role to each Permission. The seeder is **additive** — it only inserts missing rows, never overwrites.

---

## 6. How Super Admin Gets Permissions

### SA Authorization — Hardcoded Bypass

**Source: `PermissionEvaluator.require()`**

```java
if (currentUser.isSuperAdmin()) {
    return;  // Skip all permission checks
}
```

SA has a **hardcoded bypass** — the `SUPER_ADMIN` role's permission rows are **never read** for SA's own authorization. SA can do everything, always.

---

## 7. What Control Super Admin Has

### 7.1 Set FIRM_ADMIN Permissions (The Ceiling)

SA uses the override endpoint to set what a firm's FIRM_ADMIN can do. This IS the ceiling for all employee roles in that firm.

**Flow:**
1. SA calls `PUT /super-admin/firms/{firmId}/roles/{roleId}/permissions` with the FIRM_ADMIN role
2. Sets the permission set (no ceiling check for SA)
3. This becomes the ceiling for all employee roles in that firm

### 7.2 Override Any Role

SA can also override permissions on any firm role (no ceiling):
- `PUT /super-admin/firms/{firmId}/roles/{roleId}/permissions`

### 7.3 Create Custom Roles On-Behalf

SA can create custom roles inside any firm:
- `POST /super-admin/firms/{firmId}/roles`

### 7.4 View Firm Roles

SA can view any firm's roles and their permissions:
- `GET /super-admin/firms/{firmId}/roles`

---

## 8. What Control Firm Admin Has

### 8.1 View Roles & Permissions

- `GET /api/v1/firm/roles` — List all firm-scoped roles with user counts
- `GET /api/v1/firm/roles/{roleId}/permissions` — View a role's current permissions + available ceiling

### 8.2 Edit Permissions on Firm Roles

- `PUT /api/v1/firm/roles/{roleId}/permissions` — Replace permissions on a firm-scoped role
  - **Ceiling enforced**: cannot exceed what the FIRM_ADMIN has
  - **GLOBAL permissions**: always blocked (SA-only)
  - **Invalidation**: all users holding this role get `permissionVersion` bumped → forced re-login

### 8.3 Create Custom Roles

- `POST /api/v1/firm/roles` — Create a firm-scoped custom role
  - Permissions assigned separately via the permission edit endpoint

### 8.4 Delete Custom Roles

- `DELETE /api/v1/firm/roles/{roleId}` — Delete a custom role
  - Cannot delete FIRM_ADMIN
  - Cannot delete roles with assigned users

### 8.5 Toggle Role Status

- `PATCH /api/v1/firm/roles/{roleId}/toggle` — Activate/deactivate
  - Cannot toggle FIRM_ADMIN

### 8.6 View Role Users

- `GET /api/v1/firm/roles/{roleId}/users` — List users assigned to this role

---

## 9. How Firm Admin Assigns to Employees

### 9.1 Role Assignment (not part of RBAC module)

Employee role assignment is done via the **User Management** module:
- Bulk role change endpoint (not in RBAC controller)
- Sets `User.role` (direct FK) — this is the source of truth for "what role does this user have"

### 9.2 Permission Check at Runtime

**Source: `PermissionEvaluator.require()`**

```
1. Get AuthenticatedUser from request
2. If SA → allow (hardcoded bypass)
3. Check in-memory cache (ConcurrentHashMap, 5min TTL)
4. If cache miss → load from JWT permissions (fast path) or DB fallback
5. Check if permission code is in the user's permission set
6. If not → throw ForbiddenException
```

**Permission loading (fast path):**
- JWT token carries permissions (populated at auth time)
- `JwtAuthFilter` extracts permissions into `AuthenticatedUser.permissions`

**Permission loading (DB fallback):**
- `User.role → RolePermission → Permission` (single query via `findByRole`)
- Only active permissions are included

### 9.3 Session Invalidation

When permissions change:
1. `userRepository.incrementPermissionVersion(userId)` — bumps the version counter
2. `permissionEvaluator.clearUserCache(userId)` — removes from in-memory cache
3. JWT carries `permissionVersion` claim — if it doesn't match DB, user is forced to re-login

---

## 10. The Ceiling Model

### Two-Tier Ceiling

| Role Type | Ceiling |
|-----------|---------|
| FIRM_ADMIN | **No ceiling** — SA sets their permissions via override |
| All other firm roles | Firm's FIRM_ADMIN's currently enabled permissions (minus GLOBAL) |

### Example

1. SA sets Firm A's FIRM_ADMIN: `[CASE:VIEW, CASE:CREATE, BILLING:VIEW]`
2. Firm A's FIRM_ADMIN now has 3 permissions
3. Firm Admin can assign any subset of these 3 to ADVOCATE, PARALEGAL, CLIENT, or custom roles
4. Firm Admin cannot assign BILLING:EXPORT to anyone (they don't have it themselves)

---

## 11. All API Endpoints

### Super Admin (RBAC)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/admin/roles` | Create/update role |
| GET | `/api/v1/admin/roles` | List all roles |
| GET | `/api/v1/admin/roles/active` | List active roles |
| GET | `/api/v1/admin/roles/{roleId}` | Get role by ID |
| DELETE | `/api/v1/admin/roles/{roleId}` | Delete role |
| PATCH | `/api/v1/admin/roles/{roleId}/toggle` | Toggle role status |
| POST | `/api/v1/admin/roles/permissions` | Assign permissions to role |
| GET | `/api/v1/admin/roles/{roleId}/permissions` | Get role permissions |

### Super Admin (Firm Override)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/super-admin/firms/{firmId}/roles` | View firm's roles + permissions |
| PUT | `/api/v1/super-admin/firms/{firmId}/roles/{roleId}/permissions` | Override role permissions (no ceiling) |
| POST | `/api/v1/super-admin/firms/{firmId}/roles` | Create role on-behalf |

### Firm Admin

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/firm/roles` | List firm roles |
| POST | `/api/v1/firm/roles` | Create custom role |
| DELETE | `/api/v1/firm/roles/{roleId}` | Delete custom role |
| PATCH | `/api/v1/firm/roles/{roleId}/toggle` | Toggle role status |
| GET | `/api/v1/firm/roles/{roleId}/permissions` | Get role permissions + ceiling |
| PUT | `/api/v1/firm/roles/{roleId}/permissions` | Update role permissions (within ceiling) |
| GET | `/api/v1/firm/roles/{roleId}/users` | List role users |

---

## 12. Key Entities & Tables

| Entity | Table | Purpose |
|--------|-------|---------|
| `Module` | `modules` | System modules (16 seeded) |
| `Permission` | `permissions` | Flat permission catalog (code = MODULE:ACTION) |
| `ModulePermission` | `module_permissions` | Junction: module → its permissions |
| `Role` | `roles` | System roles + firm roles + custom roles |
| `RolePermission` | `role_permissions` | Junction: role → its permissions |
| `UserRole` | `user_roles` | Legacy join table (not used for auth) |
| `User.role` | `users.role_id` | Direct FK — source of truth for user's role |
| `FirmModule` | `firm_modules` | Firm-level module enable/disable (not RBAC) |

---

## 13. Current Flow Walkthrough

### Scenario 1: New Firm Onboarding

```
1. Admin creates firm via SA panel
2. FirmServiceImpl.createFirm():
   a. Save firm
   b. cloneSystemRolesForFirm(firm):
      - Skip SUPER_ADMIN
      - Clone FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT
      - Each clone: firm_id=<firm>, is_system=false
      - Permissions: EMPTY
   c. Create FIRM_ADMIN user, assign FIRM_ADMIN role
3. SA sets FIRM_ADMIN permissions via override endpoint
   (this IS the ceiling for all employee roles)
4. SA may also create custom roles on-behalf
```

### Scenario 2: SA Sets Firm Permissions

```
1. SA: GET /super-admin/firms/{firmId}/roles → sees roles with 0 permissions
2. SA: PUT /super-admin/firms/{firmId}/roles/{FIRM_ADMIN_ID}/permissions
   → Request: [CASE:VIEW, CASE:CREATE, BILLING:VIEW, ...]
   → FIRM_ADMIN now has those permissions
3. This becomes the ceiling for all employee roles in the firm
```

### Scenario 3: Firm Admin Distributes to Employees

```
1. Firm Admin: GET /firm/roles/{ADVOCATE_ID}/permissions
   → Current: [] (empty)
   → Available ceiling: [CASE:VIEW, CASE:CREATE, BILLING:VIEW, ...]
2. Firm Admin: PUT /firm/roles/{ADVOCATE_ID}/permissions
   → Request: [CASE:VIEW, CASE:CREATE]
   → Within ceiling → allowed
3. Firm Admin: PUT /firm/roles/{PARALEGAL_ID}/permissions
   → Request: [CASE:VIEW, BILLING:VIEW]
   → Within ceiling → allowed
```

### Scenario 4: Firm Admin Narrows Own Permissions

```
1. Firm Admin: PUT /firm/roles/{FIRM_ADMIN_ID}/permissions
   → Removes BILLING:VIEW from their own set
2. Now Firm Admin cannot assign BILLING:VIEW to any employee
3. Existing employee roles that had BILLING:VIEW keep it
   (no cascade — Firm Admin must manually remove if needed)
```

---

## 14. Key Files Reference

| File | Module | Role |
|------|--------|------|
| `DataInitializer.java` | config | Seeds modules, permissions, roles, assignment matrix |
| `Role.java` | rbac/entity | Role entity (system + firm + custom) |
| `Permission.java` | rbac/entity | Permission entity (code = MODULE:ACTION) |
| `RolePermission.java` | rbac/entity | Junction: role → permission |
| `Module.java` | rbac/entity | Module entity |
| `ModulePermission.java` | rbac/entity | Junction: module → permission |
| `RoleController.java` | rbac/controller | SA role management |
| `RoleManagementServiceImpl.java` | rbac/service | SA role CRUD |
| `RolePermissionServiceImpl.java` | rbac/service | Permission assignment (GLOBAL check) |
| `FirmRoleServiceImpl.java` | firm/service | Firm admin role management + ceiling |
| `FirmServiceImpl.java` | firm/service | Firm onboarding + role cloning |
| `SuperAdminServiceImpl.java` | superadmin/service | SA firm override + firm role visibility |
| `PermissionEvaluator.java` | auth/security | Runtime permission check + cache |
| `JwtAuthFilter.java` | auth/security | JWT parsing + permission extraction |
| `AuthenticatedUser.java` | auth/security | Security context user |
| `RoleCode.java` | common/constant | System role code constants |
| `PermissionAction.java` | common/enums | ACCESS, VIEW, CREATE, EDIT, DELETE, etc. |
| `PermissionScope.java` | common/enums | TENANT, GLOBAL, OWN, ASSIGNED |
