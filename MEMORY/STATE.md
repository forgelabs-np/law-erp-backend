# Project State

## Current Focus
**Dashboard redesign + CONFIGURATION module** — 4 clean dashboard endpoints (super-admin, firm, employee, client) with typed responses and shared DashboardScope; CONFIGURATION module with GLOBAL_CONFIG/FIRM_CONFIG sub-modules, RBAC, and full docs. Next: point the prod profile at the new production database (needs `SPRING_PROFILES_ACTIVE=prod`, `DDL_AUTO`, `--env-file .env` — `.env` is not auto-loaded, and `Dockerfile.prod` doesn't set the profile).

## Branch
`devG`

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (this session)
- **Sub-module module access** — `ModuleAccessResolver` makes a sub-module inherit its parent's enable flag (nearest row wins, explicit child row overrides, expiry respected); `PermissionEvaluator.hasModuleAccess`, `/me`'s sidebar and `isModuleEnabled` all use it, and `enableModuleForFirm` cascades to the sub-tree. Root cause: the sidebar marked sub-modules enabled via the parent while the API guard looked only for the sub-module's own `firm_modules` row → menu visible, every call 403
- **Two-table split** — `system_config` is GLOBAL-only (scope/firm_id columns dropped), new `firm_configs` + `FirmConfig`/`FirmConfigRepository`/`FirmConfigService` hold per-firm values with `firm_id` NOT NULL + FK cascade; `ConfigKeyRegistry` declares both key sets; migration `V2026_09_19_2` copies FIRM rows and drops the columns
- **Read resilience** — `Collectors.toMap` threw on duplicate keys and on null (undecryptable) values, taking out every config read incl. all email; replaced with `toValueMap()` (newest `updated_at` wins, bad rows skipped with a warning)
- **Scope allowlist** — `setValue` refuses keys the registry doesn't declare for that scope; closes a real phish vector (`PUT /firm/config {"LOGIN_URL": ...}` repointed real password-reset emails)
- **`getFirmSettings`** — now falls back to registry defaults so a new firm's settings screen isn't empty (FIRM keys are `seed=false`)
- **`APP_PRODUCTION`** — `TotpUtil` reads DB-first with yml fallback; seed default derived from `app.production` so a prod DB can't ship with the `123456` MFA bypass on
- **Trial fix** — `isTrial` + null `trialDays` created a trial with no expiry that the scheduler skipped forever; now falls back to `TRIAL_DEFAULT_DAYS`
- **Settings → DB** — `security.max-login-attempts` removed from all profiles (→ `LOGIN_MAX_ATTEMPTS` + `LOGIN_LOCK_MINUTES`); new `TRIAL_DEFAULT_DAYS`, `TRIAL_WARNING_DAYS`, `NOTIFICATION_MAX_ATTEMPTS`, `LOGIN_URL`, `CLIENT_PORTAL_URL`
- **Migration** — `V2026_09_19_1__system_config_scope_uniqueness.sql` (partial unique indexes; Postgres NULLs are distinct so the old constraint never protected GLOBAL rows) — run manually
- **Tests** — `SystemConfigServiceTest` 13→27; full suite 360→374 green
- **Left in yml on purpose** — `jwt.*`, `config.encryption.key`, DB/mail/hikari, `cors.allowed-origins`, `permissions.cache.ttl-ms`; later candidate: `scraper.*`
- **CONFIGURATION module** — parent module (SettingsIcon, `/settings`) with GLOBAL_CONFIG + FIRM_CONFIG sub-modules; permissions seeded + role matrix assigned; controllers wired to permission checks
- **docs/config-setup.md** — full backend + frontend reference for the config system
- **Dashboard redesign** — 4 typed endpoints under `usermanagement/dashboard/`: SuperAdmin (platform aggregates), FirmAdmin (firm-scoped), Employee (personal assigned), Client (personal matters); shared `DashboardScope` + `DashboardScopeFactory`; spec revised from dynamic engine to clean endpoints

## Deep History Index
- `memory/2026-09-19.md` — system config hardening, two-table split, CONFIGURATION module + docs, sub-module permissions fix, dashboard redesign
- `memory/2026-09-13.md` — Bug fix batch: GetAllFirms isTrial, custom perms grouped, NOTIFICATION_MANAGEMENT seed, default role delete block, MFA reset for Firm Admin, client password reset confirmed
- `memory/2026-09-12.md` — RBAC simplification, trial period feature, dashboard fixes, enable-module simplification (344 tests)
- `memory/2026-09-09.md` — notification module design (v1 scope locked), preview endpoint GET→POST fix
- `memory/2026-09-08.md` — delegation chain built (all 5 phases): template editing, diff-sync async job, both-direction cascade, SA firm-role visibility, `docs/rbac-delegation-chain-design.md`
- `memory/2026-09-07.md` — RBAC state-of-the-world audit + `docs/rbac-roles-and-permissions.md` (gaps: immutable system templates, no SA firm-role read API)
- `memory/2026-09-06.md` — scraper module completion, verified court registry (77 districts), CourtSeeder helper, Nepali+English court names
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
