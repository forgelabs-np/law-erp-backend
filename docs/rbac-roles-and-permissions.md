# RBAC — Roles & Permissions: Current State

> Status as of 2026-09-07, branch `devG`. This doc explains how roles and permissions
> are assigned today, what Super Admin and Firm Admin can actually do, what was built
> to get here, and where the current model falls short of the intended
> "Super Admin → Firm Admin → Employee" delegation chain.

---

## 1. Summary

The system has a **two-layer RBAC**:

1. **System roles** (global templates, `firm IS NULL`, `is_system = true`):
   `SUPER_ADMIN`, `FIRM_ADMIN`, `ADVOCATE`, `PARALEGAL`, `CLIENT` — seeded by
   `DataInitializer` at boot with a fixed module×role access matrix.
2. **Firm-scoped role clones** (per firm, `is_system = false`, `parent_role_id` →
   system template): created when a firm is onboarded. All day-to-day permission
   editing happens on these clones, never on the templates.

Permissions are flat codes `MODULE:ACTION` (e.g. `CASE_MANAGEMENT:VIEW`) with a
`scope` (`TENANT` default, `GLOBAL` reserved for Super Admin, `OWN` = row-level
restriction enforced in the service layer, not by a different permission row).

**Delegation as designed:** Super Admin shapes the Firm Admin's permission set →
Firm Admin distributes (≤ their own ceiling) to firm roles → employees get roles,
not individual permissions. **In practice** the chain has holes (see §7).

---

## 2. Data Model

| Entity | Table | Purpose |
|---|---|---|
| `Role` | `roles` | `roleCode`, `roleName`, `firm_id` (null = system template), `isSystem`, `parentRoleId` (clone → template), `extendsRole`, `applicableTo` (UserType) |
| `Permission` | `permissions` | unique `code` = `MODULE:ACTION`, `action`, `scope`, `moduleCode` |
| `RolePermission` | `role_permissions` | junction role ↔ permission (unique per pair) |
| `UserRole` | `user_roles` | **legacy** join table — only written once, at firm-admin creation. Not used for permission checks (see §5) |
| `Module` / `ModulePermission` | `modules`, `module_permissions` | module registry + which permissions belong to a module |
| `FirmModule` | `firm_modules` | per-firm module **enablement** — a separate gate from permissions (`PermissionEvaluator.requireModuleAccess`) |

`User.role` (direct FK on `users`) is the actual source of truth for "what role does
this user have" — used by `JwtUtil`, `getAuthorities()`, and the permission evaluator.

Constants live in `RoleCode` (never hardcode role strings) and `RbacConstants`
(Swagger summaries).

---

## 3. Seeding (`DataInitializer`)

Boot order: tenant types → SYSTEM firm (home of SUPER_ADMIN) → system roles →
modules & permissions → role-permission matrix → renewal types → firm modules.

- **16 modules**, each with standard actions `ACCESS, VIEW, CREATE, EDIT, DELETE`
  plus extras (e.g. `ASSIGN`, `UPLOAD`, `EXPORT`, `CREDENTIAL_REVEAL`).
- **Access matrix** (module × role):

| Module | SUPER_ADMIN | FIRM_ADMIN | ADVOCATE | PARALEGAL | CLIENT |
|---|---|---|---|---|---|
| CASE_MANAGEMENT | FULL | FULL | FULL | READ_ONLY | OWN |
| DOCUMENT_MANAGEMENT | FULL | FULL | FULL | READ_ONLY | OWN |
| CLIENT_MANAGEMENT | FULL | FULL | READ_ONLY | READ_ONLY | — |
| BILLING | FULL | FULL | READ_ONLY | — | OWN |
| CALENDAR | FULL | FULL | FULL | READ_ONLY | OWN |
| EMPLOYEE | FULL | FULL | — | — | — |
| REPORTS | FULL | FULL | READ_ONLY | — | — |
| AUDIT | FULL | FULL | — | — | — |
| PROJECT_MANAGEMENT | FULL | FULL | READ_ONLY | READ_ONLY | OWN |
| ROLE_MANAGEMENT | FULL | FULL | — | — | — |
| FIRM_MANAGEMENT | FULL | FULL | — | — | — |
| MENU_MANAGEMENT | FULL | FULL | — | — | — |
| PERMISSION_MANAGEMENT | FULL | FULL | — | — | — |
| SCRAPER_MANAGEMENT | FULL | FULL | — | — | — |
| USER_MANAGEMENT | FULL | FULL | — | — | — |
| DASHBOARD_MANAGEMENT | FULL | FULL | READ_ONLY | READ_ONLY | — |

- `FULL` = every permission of the module; `READ_ONLY`/`OWN` = `ACCESS` + `VIEW`
  (row-level narrowing for `OWN` happens in services via `scope == OWN`).
- Seeding is **additive and idempotent** — it never removes or overwrites rows
  that were customized after boot.

---

## 4. How Roles Get Assigned to Users

### 4.1 Firm onboarding (Super Admin)
`POST /api/v1/super-admin/firms` → `FirmServiceImpl.createFirm()`:
1. Clones every system role for the new firm — **skipping `SUPER_ADMIN`**
   (platform-level only) — as `isSystem=false` rows with `parentRoleId` set to
   the template id.
2. Looks up the firm-scoped `FIRM_ADMIN` clone.
3. Creates the admin `User` with `userType=FIRM`, `role=firm FIRM_ADMIN clone`,
   `mustChangePassword=true`, MFA per DB-driven policy.
4. Writes one legacy `UserRole` row (kept for future multi-role support only).
5. Emails credentials, audits `FIRM_CREATED` + `USER_CREATED`.

### 4.2 Employees (Firm Admin)
`POST /api/v1/modules/users/bulk-role-change` (requires `USER_MANAGEMENT:EDIT`,
seeded FULL for FIRM_ADMIN) → `UserManagementServiceImpl.bulkRoleChange()`:
- Sets `User.role` FK directly (no `user_roles` row).
- Guards: role must belong to caller's firm, must not be a system role, must be
  active, **must not be `FIRM_ADMIN`** ("Only Super Admin can create Firm Admins"),
  and role's `applicableTo` must match the user's type (`FIRM_USER` vs `CLIENT`).
- Bumps `permissionVersion` per affected user and clears the permission cache.

There is also `UpdateEmployeeRoleRequest` / employee-creation paths that follow the
same pattern: **users are assigned roles, never individual permissions.**

---

## 5. How Permissions Are Evaluated (`PermissionEvaluator`)

`require(code)` / `has(code)` on every guarded endpoint:

1. `AuthenticatedUser.isSuperAdmin()` → **bypass, always allowed.**
2. Otherwise: permissions from the **JWT claims** (fast path, no DB hit) —
   fallback loads from DB via `User.role → role_permissions` (only active
   permissions). Historically this read the `user_roles` join table, which was
   only written at firm creation, so every employee silently had **zero**
   permissions — fixed by switching to the `User.role` FK.
3. In-memory per-user cache with 5-min TTL (`permissions.cache.ttl-ms`).
4. `JwtAuthFilter` compares the token's `permVersion` claim against the DB value
   on **every request** for non-super-admins — mismatch (or missing claim) → 401
   "permissions have changed", forcing re-login. Any role/permission change calls
   `incrementPermissionVersion` + `clearUserCache` for every affected user, so
   permission edits take effect **immediately**, not at TTL expiry.

Super Admin additionally skips staleness checks (their token carries no
permission list — they bypass everything anyway).

---

## 6. What Each Admin Can Do Today (Endpoints)

### 6.1 Super Admin

| Endpoint | What it does | Guard rails |
|---|---|---|
| `/api/v1/admin/roles` CRUD + toggle | Manage non-system roles | `hasRole('SUPER_ADMIN')`; **system roles are immutable** (`validateNotSystemRole`) |
| `POST /api/v1/admin/roles/permissions` | Replace a role's permission set | Non-system roles only; **parent-template ceiling** (see below); `GLOBAL` scope refused |
| `GET /api/v1/admin/roles/{id}/permissions` | Read a role's permissions | — |
| `PUT /api/v1/super-admin/firms/{firmId}/roles/{roleId}/permissions` | **Ceiling-bypass override** — force any permission onto any firm role of that firm | `hasRole('SUPER_ADMIN')`; validates role belongs to firm |
| `POST /api/v1/super-admin/firms/{firmId}/modules` | Enable/disable modules per firm (plan gate) | — |
| Firm/admin CRUD, MFA reset, audit logs, config | Platform administration | `hasRole('SUPER_ADMIN')` |

**Parent-template ceiling** (`RolePermissionServiceImpl.assignPermissionsToRole`):
a firm role clone may only hold permissions its `parentRoleId` system template
holds, minus anything `GLOBAL`. Effective today for every clone — but since
templates are immutable (§7), the ceiling equals the **static seed matrix**.

### 6.2 Firm Admin

All under `/api/v1/firm/roles`, guarded `hasRole('FIRM_ADMIN')` + firm context:

| Endpoint | What it does |
|---|---|
| `POST /api/v1/firm/roles` | Create custom role (`applicableTo=FIRM_USER`, unique code per firm) |
| `GET /api/v1/firm/roles` | Firm's own roles (+ legacy fallback to FIRM_USER system templates if no clones) with user counts |
| `GET /api/v1/firm/roles/{id}/permissions` | Current permissions **and** the `availablePermissions` ceiling for that role (drives the checkbox UI) |
| `PUT /api/v1/firm/roles/{roleId}/permissions` | Replace permissions **within ceiling**; invalidates all holder sessions; audits `ROLE_PERMISSION_CHANGED` |
| `PATCH .../toggle`, `DELETE .../{roleId}` | Toggle/delete custom roles — never FIRM_ADMIN, never role with assigned users |
| `GET /{roleId}/users` | Who holds the role |

**Two-tier ceiling** (`FirmRoleServiceImpl.computeCeiling`):
- Editing the firm's **FIRM_ADMIN** role → ceiling = parent system `FIRM_ADMIN`
  template ("Super Admin controls this").
- Editing **any other** role (ADVOCATE clone, PARALEGAL clone, custom) → ceiling =
  **the firm's FIRM_ADMIN's currently enabled permissions** (minus `GLOBAL`).

This is the intended delegation: **Firm Admin can never grant an employee more
than the Firm Admin themselves hold.**

---

## 7. Gaps vs. the Desired Model

Desired chain: **Super Admin grants permissions to Firm Admin → Firm Admin
assigns permissions to employees** — with Super Admin able to manage everything,
including employee permissions, when needed.

What already matches:
- ✅ Firm Admin editing employee-role permissions within their own ceiling.
- ✅ Super Admin ceiling-bypass override per firm role (`PUT .../override`).
- ✅ Super Admin *can* technically edit any non-system (firm-scoped) role via
  `/admin/roles/permissions` — including employee role clones.

Where it breaks down today:

1. **System role templates are immutable** — `validateNotSystemRole` and the
   `isSystem` guard in `assignPermissionsToRole` reject any edit to
   `SUPER_ADMIN` / `FIRM_ADMIN` / `ADVOCATE` / `PARALEGAL` / `CLIENT` templates.
   The comment "FIRM_ADMIN ceiling comes from the system role (SUPER_ADMIN sets
   this)" is aspirational: **there is no endpoint through which the Super Admin
   can actually change a template**, so the ceiling for every firm's Firm Admin
   is frozen at whatever the seed matrix says. If the seed grants FIRM_ADMIN
   `FULL` everywhere, Super Admin cannot *narrow* Firm Admins platform-wide, and
   cannot *expand* what any template allows.
2. **Super Admin cannot grant employees more than their template holds** through
   the normal path — the parent-template ceiling blocks it. Only the per-firm,
   per-role override endpoint escapes it, which must be called once per role per
   firm — there is no bulk or template-level lever.
3. **No Super Admin visibility into a firm's roles** — the override endpoint
   requires knowing the `roleId`, but listing firm-scoped roles is only exposed
   on the Firm Admin controller (`hasRole('FIRM_ADMIN')`). Super Admin has no
   "show me firm X's roles and their permissions" read API.
4. **Super Admin permission edits to firm roles need firm context plumbing** —
   `/admin/roles` is firm-agnostic, so a Super Admin editing an employee clone
   can't see which firm it belongs to or who holds it.
5. **`UserRole` is legacy** — written once at firm creation, never read for
   authorization. Candidate for cleanup or future multi-role support.
6. **Custom firm roles carry no `parentRoleId`** — the admin-side ceiling check
   silently skips them (only the Firm Admin flow computes a ceiling for them).
   Fine today, but worth a guard if Super Admin endpoints ever target them.

---

## 8. What We Achieved So Far (Timeline)

- **RBAC foundation** — entities, `MODULE:ACTION` permission codes with
  `TENANT/GLOBAL/OWN` scopes, idempotent boot seeding, module×role access matrix.
- **Permission evaluator fix** — switched from the write-once `user_roles` join
  table to the `User.role` FK; employees went from silently zero permissions to
  correct evaluation. Kept `UserRole` for future multi-role.
- **Instant invalidation** — `permissionVersion` claim checked on every request;
  role/permission edits bump the version and clear caches → affected users are
  forced to re-login instead of serving stale permissions for up to 5 minutes.
- **Permission management APIs** — module/permission CRUD, grouped-by-module
  read for the checkbox UI (`GET /admin/permissions/grouped`).
- **Firm Admin role CRUD** (2026-09-02) — create/delete/toggle custom roles,
  list role users, within-firm uniqueness, FIRM_ADMIN protection.
- **Super Admin override** (2026-09-02) —
  `PUT /super-admin/firms/{firmId}/roles/{roleId}/permissions` bypasses all
  ceilings; established the documented flow *SA → system roles → Firm Admin →
  firm roles → employees*.
- **Two-tier ceiling enforcement** — Firm Admin can never out-grant themselves;
  `GLOBAL` scope reserved for Super Admin everywhere.
- **UserType.FIRM expansion** (2026-09-03) — firm owner/admin (`FIRM`) is now
  distinct from employees (`FIRM_USER`); role `applicableTo` prevents assigning
  admin-only semantics to the wrong user class; bulk-role-change and audit
  filtering respect it.
- **Audit trail** — `ROLE_CREATED/UPDATED/DELETED/ACTIVATED/DEACTIVATED`,
  `ROLE_PERMISSION_CHANGED` (with affected-session counts), `USER_ROLE_CHANGED`
  (old → new role), filterable by `userType`/`userId` from the Super Admin panel.
- **Tests** — `RolePermissionServiceTest`, `PermissionServiceTest`,
  `FirmRoleServiceImplTest`, `RolePermissionE2ETest`, `PermissionEvaluatorE2ETest`
  — part of the 245-test green suite.

---

## 9. Follow-ups (to complete the desired delegation model)

1. **Make system templates editable by Super Admin** (or introduce a
   "template management" mode): a guarded path that lets SA reshape the
   `FIRM_ADMIN` / `ADVOCATE` / `PARALEGAL` templates, with the firm clones
   optionally synced. This turns the static ceiling into a real SA control.
2. **Super Admin read API for a firm's roles** —
   `GET /super-admin/firms/{firmId}/roles` (+ permissions per role) so the
   override flow is discoverable from the SA panel.
3. **Direct SA management of employee permissions** — decide whether SA edits
   flow through templates (preferred: one lever, all firms) or per-firm override
   (today's only option).
4. **Sync policy for template changes** — when a template changes, do existing
   firm clones auto-update, or only new firms? Needs a product decision.
5. **Cleanup candidates** — legacy `UserRole` writes, custom roles without
   `parentRoleId` (add explicit ceiling or forbid SA edits on them).
