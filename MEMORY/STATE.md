# Project State

## Current Focus
**2026-09-23 — six reported UI bugs fixed, e-mail repaired, F-9 closed — 466 tests green.** Client-portal login (username or
mobile), the reset/change-password APIs (bare/absent body, one 8–50 policy, generated temporary
password, new self-service `POST /api/v1/me/change-password`), `isTrial` on the firm responses,
bulk deactivate/role-change binding, the assignment e-mail default (`CASE_ASSIGNED` now e-mails),
and assignment-scoped matter lists for non-admin staff. Full write-up in `fixes.md`.
**Follow-up same day (fixes.md F1/F2):** all e-mail was dead because `application-dev.yml`'s
`spring.mail.username` had trailing garbage (`…@gmail.com to d`) + spaces in the app password —
fixed and verified `235 Accepted` auth-only; and the admin reset now accepts `password`/`pwd`/
`new_password` as aliases for `newPassword` (`AUTH-14`) so an admin-typed password can't be
dropped into "generate" silently. Frontend still owes a password field on the reset dialog.
**F-9 closed same day:** refresh tokens now live in a `refresh_tokens` store keyed by JWT `jti`
(single-use rotation, family revoke on replay), `POST /api/v1/auth/logout` bumps
`permissionVersion` so every access + refresh token dies server-side on every device, refresh
tokens carry the `permVersion` claim (an admin reset now kills them — that hole was unreported),
and `JwtAuthFilter` no longer exempts Super Admin from the staleness check. Tests `AUTH-15`..`AUTH-18`.
Uncommitted on `devG`.

### Previous focus (2026-09-21)
**QA findings F-1..F-5, F-7, F-8 fixed — plus F-15 found in review** — **423 tests green**.
Changes are uncommitted in the working tree (matter↔client binding + OWN scope, suspended-firm
enforcement at login **and** per-request, `GET /super-admin/firms` restored, guarded user-activity
endpoint, create-role applies `permissionIds`, self-service password reset made genuinely single-use).
A same-day adversarial review reproduced and fixed **F-15 (High)**: a firm admin could mint a
`SUPER_ADMIN`-coded firm role and reach `/super-admin/**`. Full report + gap table updated in
`docs/qa-report-2026-09-21.md` / `docs/ui-test-checklist.md`.
Remaining: F-6 (module/plan gating is dead code) and F-10–F-14 (medium/low).
**Owed before release:** the two F-15 follow-ups (defence-in-depth in the authority builder + a
cleanup for existing firm rows carrying a `SUPER_ADMIN` role), a Postgres (not H2) pass for the
native `date(...)` aggregates, and the manual UI checklist sign-off.

## Branch
`devG` — the 2026-09-23 six-bug batch is uncommitted here. (`production` still carries the
uncommitted 2026-09-21 F-1..F-15 batch; PR #29 was merged into it from `devG`.)

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (2026-09-23 fix batch)
- **Six bugs fixed**, per-bug symptom → root cause → manual steps in `fixes.md` (repo root)
- **New shared pieces** — `PasswordPolicy` (the single 8–50 rule + `generateTemporary()`) and
  `RequestBodyBinder` (envelope-or-bare body; also fixes that `JsonNode` parameters cannot bind here)
- **New endpoint** — `POST /api/v1/me/change-password` (self-service, proves the current password,
  revokes other sessions). No frontend form calls it yet.
- **Scoping** — `ReadScopeGuard.isAssignmentScope()` + `MatterRepository.findByFiltersAndIdIn` so an
  employee's matter lists are their assignments (empty page when none)
- **462 tests green** (64 in the QA suites); regression tests `AUTH-09b` and `AUTH-14`
- **Mail repair** — corrupted `spring.mail.username` in `application-dev.yml` (trailing ` to d`,
  spaces in the app password) killed every send; creds verified against Gmail (`235 Accepted`,
  auth-only). `resolveFromAddress` guards the From fallback; `resolveMailSender` logs its source
  at INFO
- **Admin reset binding** — `ResetPasswordRequest` gained `@JsonAlias({"password","pwd",
  "new_password"})` + both endpoints document the two body shapes in `@Operation`
- **F-9 closed** — new `refresh_tokens` store keyed by JWT `jti` (rotation + family revoke on
  replay), `permVersion` claim on refresh tokens, new `POST /api/v1/auth/logout` (all access +
  refresh tokens dead on every device), SA no longer exempt from the staleness check.
  `refreshToken()` must stay non-`@Transactional` (revoke-then-throw gets rolled back).
  Tests `AUTH-15`..`AUTH-18`; **466 green**

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
- `senior dev[ponytail]` — ponytail skill install commands (kept, not re-run) + the
  "review this senior" workflow: review → list simpler/faster options → wait for approval → fix
- `memory/2026-09-23.md` — Six-bug fix batch: client-portal login, reset/change password APIs + unified password policy, `isTrial`, bulk deactivate, assignment e-mail, assignment-scoped matter lists; `JsonNode` body-binding gotcha; follow-up: SMTP username corruption (e-mail fix, Gmail 235 verified) + admin-reset `@JsonAlias` (`AUTH-14`); F-9 closed — refresh store/rotation/family-revoke, server-side `POST /auth/logout`, SA staleness (`AUTH-15`..`AUTH-18`)
- `fixes.md` — the per-bug deliverable for the 2026-09-23 batch
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
