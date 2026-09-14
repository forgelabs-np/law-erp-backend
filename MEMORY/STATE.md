# Project State

## Current Focus
**Bug fix batch + enhancements** (360 tests green) — fixed 6 bugs + added password reset for SA. GetAllFirms isTrial, custom perms in grouped, NOTIFICATION_MANAGEMENT seed, default role delete block, MFA reset for Firm Admin, SA password reset. Postman updated.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (this session)
- **Bug 1: GetAllFirms isTrial** — new `FirmListResponse` DTO + `GET /api/v1/super-admin/firms` endpoint
- **Bug 2: Custom perms in grouped** — `upsert()` now creates `ModulePermission` junction rows for custom perms
- **Bug 3: NOTIFICATION_MANAGEMENT seed** — added to DataInitializer moduleDefs + role matrix
- **Bug 4: Default role delete block** — Firm Admin blocked from deleting ADVOCATE/PARALEGAL/CLIENT cloned roles
- **Bug 5: departmentId** — confirmed not in backend, frontend-only
- **Bug 6: Firm Admin MFA reset** — new endpoint `POST /api/v1/modules/users/{userId}/reset-mfa`
- **SA password reset** — new endpoint `POST /api/v1/super-admin/users/{userId}/reset-password`
- **Client password reset** — already existed via `POST /api/v1/modules/users/{userId}/reset-password`
- **Tests** — 16 new tests (FirmServiceGetAll, FirmRoleDeleteDefault, UserManagementReset, SuperAdminPasswordReset)
- **Postman** — added sections 8-11 for all new endpoints

## Deep History Index
- `memory/2026-09-13.md` — Bug fix batch: GetAllFirms isTrial, custom perms grouped, NOTIFICATION_MANAGEMENT seed, default role delete block, MFA reset for Firm Admin, client password reset confirmed
- `memory/2026-09-12.md` — RBAC simplification, trial period feature, dashboard fixes, enable-module simplification (344 tests)
- `memory/2026-09-09.md` — notification module design (v1 scope locked), preview endpoint GET→POST fix
- `memory/2026-09-08.md` — delegation chain built (all 5 phases): template editing, diff-sync async job, both-direction cascade, SA firm-role visibility, `docs/rbac-delegation-chain-design.md`
- `memory/2026-09-07.md` — RBAC state-of-the-world audit + `docs/rbac-roles-and-permissions.md` (gaps: immutable system templates, no SA firm-role read API)
- `memory/2026-09-06.md` — scraper module completion, verified court registry (77 districts), CourtSeeder helper, Nepali+English court names
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
