# Project State

## Current Focus
**RBAC delegation chain SHIPPED** (all 5 phases, 260 tests green) — `docs/rbac-delegation-chain-design.md` is the spec. SA can now edit system role templates (except SUPER_ADMIN); edits propagate as diffs to firm clones via async sync job (`sync_jobs`, status endpoint); FIRM_ADMIN narrowing cascades to employee roles AND templates; firm-admin self-narrowing cascades synchronously with effect in response; SA gets firm-role reads + on-behalf custom-role creation. Seeder freeze (`last_sa_edit_at`) prevents reboot resurrection. John/Ron = custom role, no per-user grants (decided).

## Branch
`devG`

## Tech Stack
- Spring Boot (Java), PostgreSQL (Supabase), Hibernate `ddl-auto: update` — no Flyway
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant, Scraper

## Recent Work (this session)
- **Scraper fixes** — WIP compile errors fixed (typo'd response type, nonexistent `notFound`, missing imports); `getActiveCourts()` = client cases ∩ registry kill-switch; matches endpoint ordered newest-first
- **Court registry** — replaced fabricated seed with verified data scraped from supremecourt.gov.np (77 district courts, exact id↔name); high courts on a separate `appeal/syspublic.php` system → deliberately not seeded
- **English court names** — `Court` split into `court_name_nepali` + `court_name_english`; new `CourtSeeder` `@Service` collaborator upserts the registry at startup (never overwrites `isActive`/`courtType`); DTOs/mapper expose both names
- **Pattern alignment** — seeding out of `ScraperServiceImpl` into a dedicated collaborator per the controller/service/Impl/collaborator pattern

## Deep History Index
<!-- pointers to docs/ for full feature writeups -->
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
- `memory/2026-09-06.md` — scraper module completion, verified court registry (77 districts), CourtSeeder helper, Nepali+English court names
- `memory/2026-09-07.md` — RBAC state-of-the-world audit + `docs/rbac-roles-and-permissions.md` (gaps: immutable system templates, no SA firm-role read API)
- `memory/2026-09-08.md` — delegation chain built (all 5 phases): template editing, diff-sync async job, both-direction cascade, SA firm-role visibility, `docs/rbac-delegation-chain-design.md`
