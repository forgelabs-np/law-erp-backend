# Project State

## Current Focus
**QA findings F-1..F-5, F-7, F-8 fixed — plus F-15 found in review** — **423 tests green**.
Changes are uncommitted in the working tree (matter↔client binding + OWN scope, suspended-firm
enforcement at login **and** per-request, `GET /super-admin/firms` restored, guarded user-activity
endpoint, create-role applies `permissionIds`, self-service password reset made genuinely single-use).
A same-day adversarial review reproduced and fixed **F-15 (High)**: a firm admin could mint a
`SUPER_ADMIN`-coded firm role and reach `/super-admin/**`. Full report + gap table updated in
`docs/qa-report-2026-09-21.md` / `docs/ui-test-checklist.md`.
Remaining: F-6 (module/plan gating is dead code) and F-9–F-14 (medium/low).
**Owed before release:** the two F-15 follow-ups (defence-in-depth in the authority builder + a
cleanup for existing firm rows carrying a `SUPER_ADMIN` role), a Postgres (not H2) pass for the
native `date(...)` aggregates, and the manual UI checklist sign-off.

## Branch
`production` (last merge: PR #29 from `devG`) — fix batch is uncommitted on top of it.

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (2026-09-21 QA session)
- **Fix batch** — F-1..F-5, F-7, F-8 implemented and their QA tests flipped to assert the fixed
  behaviour; 421 green. See the daily log for the per-finding "why" (esp. F-1 per-request vs
  login-only, and F-7's caller-aware ceiling).
- **56 new QA tests** across 5 suites (SuperAdmin flow, role matrix, case mgmt, project mgmt, auth/security) + `QaBaseTest` harness
- **Findings F-1..F-14** — 5 High: suspended firm not blocked, portal-access revocation ignored, no matter↔client binding + OWN scope unenforced, `GET /super-admin/firms` missing, user-activity endpoint unguarded
- **Verified strong** — cross-firm isolation, permission gating, credential encryption, session revocation on password reset, lockout, MFA, no injection/5xx/leak
- **Client binding** — projects bound (`clientUserId` + portal), matters NOT bound; recommendation + schema sketch in the report
- **Docs** — `docs/qa-report-2026-09-21.md`, `docs/ui-test-checklist.md`

## Recent Work (previous session)
- **Bug 1: GetAllFirms isTrial** — `FirmListResponse` DTO + `FirmService.getAllFirms()` exist and are unit-tested, but the `GET /api/v1/super-admin/firms` **controller mapping is missing** (regression, see finding F-4)
- **Bug 2: Custom perms in grouped** — `upsert()` now creates `ModulePermission` junction rows for custom perms
- **Bug 3: NOTIFICATION_MANAGEMENT seed** — added to DataInitializer moduleDefs + role matrix
- **Bug 4: Default role delete block** — Firm Admin blocked from deleting ADVOCATE/PARALEGAL/CLIENT cloned roles (verified: 400 business rule)
- **Bug 5: departmentId** — confirmed not in backend, frontend-only
- **Bug 6: Firm Admin MFA reset** — `POST /api/v1/modules/users/{userId}/reset-mfa` (verified)
- **SA password reset** — `POST /api/v1/super-admin/users/{userId}/reset-password` (verified)
- **Postman** — sections 8-11 for all new endpoints

## Deep History Index
- `memory/2026-09-21.md` — Full API QA + security pass (56 new tests, 416 green), findings F-1..F-14, case/project client-binding verdict, UI test checklist, test-harness gotchas
- `docs/qa-report-2026-09-21.md` / `docs/ui-test-checklist.md` — QA deliverables
- `memory/2026-09-13.md` — Bug fix batch: GetAllFirms isTrial, custom perms grouped, NOTIFICATION_MANAGEMENT seed, default role delete block, MFA reset for Firm Admin, client password reset confirmed
- `memory/2026-09-12.md` — RBAC simplification, trial period feature, dashboard fixes, enable-module simplification (344 tests)
- `memory/2026-09-09.md` — notification module design (v1 scope locked), preview endpoint GET→POST fix
- `memory/2026-09-08.md` — delegation chain built (all 5 phases): template editing, diff-sync async job, both-direction cascade, SA firm-role visibility, `docs/rbac-delegation-chain-design.md`
- `memory/2026-09-07.md` — RBAC state-of-the-world audit + `docs/rbac-roles-and-permissions.md` (gaps: immutable system templates, no SA firm-role read API)
- `memory/2026-09-06.md` — scraper module completion, verified court registry (77 districts), CourtSeeder helper, Nepali+English court names
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
