# Project State

## Current Focus
**Trial period + firm lifecycle** (344 tests green) — firm creation supports trial period (configurable days), daily scheduler sends expiry notifications, suspended firms blocked at `/me`, extend/convert-to-permanent endpoints. Also fixed: firm count dashboard (only FIRM_ADMIN firms), simplified enable-module request.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (this session)
- **Trial period feature** — `Firm` entity has `isTrial`, `trialDays`, `trialStartedAt`, `trialExpiresAt`; SA creates trial firms; `/me` gates on suspended/expired; `TrialExpiryScheduler` sends daily notifications; endpoints: suspend, activate, extend-trial, convert-to-permanent
- **Dashboard firm count fix** — `FirmStats.totalFirms` now counts only firms with FIRM_ADMIN users
- **Enable module simplified** — `EnableModuleRequest` reduced to `moduleId` + `isEnabled`
- **Postman updated** — `RBAC-V3.postman_collection.json` now includes trial firm creation, Get Me, and firm lifecycle endpoints (suspend/activate/extend-trial/convert-to-permanent)
- **RBAC simplification** — removed template layer, simplified ceiling model, 325 tests green

## Deep History Index
- `memory/2026-09-12.md` — RBAC simplification, trial period feature, dashboard fixes, enable-module simplification (344 tests)
- `memory/2026-09-09.md` — notification module design (v1 scope locked), preview endpoint GET→POST fix
- `memory/2026-09-08.md` — delegation chain built (all 5 phases): template editing, diff-sync async job, both-direction cascade, SA firm-role visibility, `docs/rbac-delegation-chain-design.md`
- `memory/2026-09-07.md` — RBAC state-of-the-world audit + `docs/rbac-roles-and-permissions.md` (gaps: immutable system templates, no SA firm-role read API)
- `memory/2026-09-06.md` — scraper module completion, verified court registry (77 districts), CourtSeeder helper, Nepali+English court names
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
