# Project State

## Current Focus
**RBAC simplified** (325 tests green) — removed entire template/sync infrastructure (~900+ lines). New model: SA sets FIRM_ADMIN permissions via override (no ceiling for FIRM_ADMIN), Firm Admin distributes subset to employee roles (ceilinged by FIRM_ADMIN's permissions). Custom roles supported. Per-firm isolation confirmed.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (this session)
- **RBAC simplification** — removed TemplatePermissionService, TemplateSyncPlanner, TemplateSyncRunner, FirmSyncExecutor, SyncJob, DTOs, cascade logic; simplified Role entity (removed parent_role_id, last_sa_edit_at, extends_role); new ceiling model: FIRM_ADMIN=no ceiling, others=FIRM_ADMIN perms; 8 new E2E tests; 325 tests green
- **Scraper fixes** — WIP compile errors fixed (typo'd response type, nonexistent `notFound`, missing imports); `getActiveCourts()` = client cases ∩ registry kill-switch; matches endpoint ordered newest-first
- **Court registry** — replaced fabricated seed with verified data scraped from supremecourt.gov.np (77 district courts, exact id↔name); high courts on a separate `appeal/syspublic.php` system → deliberately not seeded
- **English court names** — `Court` split into `court_name_nepali` + `court_name_english`; new `CourtSeeder` `@Service` collaborator upserts the registry at startup (never overwrites `isActive`/`courtType`); DTOs/mapper expose both names
- **Pattern alignment** — seeding out of `ScraperServiceImpl` into a dedicated collaborator per the controller/service/Impl/collaborator pattern

## Deep History Index
- `memory/2026-09-12.md` — RBAC simplification: removed template layer, simplified ceiling model, 325 tests green
- `memory/2026-09-09.md` — notification module design (v1 scope locked), preview endpoint GET→POST fix
- `memory/2026-09-08.md` — delegation chain built (all 5 phases): template editing, diff-sync async job, both-direction cascade, SA firm-role visibility, `docs/rbac-delegation-chain-design.md`
- `memory/2026-09-07.md` — RBAC state-of-the-world audit + `docs/rbac-roles-and-permissions.md` (gaps: immutable system templates, no SA firm-role read API)
- `memory/2026-09-06.md` — scraper module completion, verified court registry (77 districts), CourtSeeder helper, Nepali+English court names
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
