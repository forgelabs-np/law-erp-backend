# Project State

## Current Focus
Scraper module finished and green (245 tests): compile fixes, real court registry data, court seeding moved to `CourtSeeder` helper, `Court` now carries Nepali + English names.

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
