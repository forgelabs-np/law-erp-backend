# State
_Last updated: 2026-08-25_

## Active work
- Invoice Generator module complete — entities, CRUD, PDF generation (Thymeleaf + OpenHTMLtoPDF), email send. SUPER_ADMIN only for now.
- Query optimization done — 6 hotspots fixed, 178 tests pass.
- Dashboard trends feature done — configurable `days` param, cumulative daily data.

## Recent decisions (keep ~6, drop the oldest)
- Invoice module scoped to SUPER_ADMIN only — firm-scoped billing is a follow-up
- OpenHTMLtoPDF chosen for PDF generation (Thymeleaf HTML → PDF) — requires openhtmltopdf-core + openhtmltopdf-pdfbox 1.0.10
- Dashboard trends use GROUP BY DATE(createdAt) with cumulative sums — no snapshot table, reconstructed from entity creation timestamps
- Query optimization approach: COUNT/aggregate SQL instead of findAll+stream; batch repository methods for N+1 elimination; JWT fast-path in PermissionEvaluator
- Strix removed entirely (skills, CLI, runs) — not needed; manual/curl testing instead
- Test-mode security items (MFA 123456, committed JWT/AES/registration secrets) INTENTIONAL for now — user will move to env/SystemConfig before go-live

## Recent decisions (keep ~6, drop the oldest)
- All modules follow Controller → Service (interface) → ServiceImpl → Mapper pattern
- Response building extracted from services into mapper classes per module
- Swagger summaries/descriptions centralized as constants in `common/constant/`
- Project mgmt module + RBAC permission layer have NO enforcement — top go-live blockers
- @Transactional must be on public methods only, never private
- Permission scope is GLOBAL for SUPER_ADMIN, TENANT for FIRM_ADMIN, ASSIGNED for ADVOCATE/PARALEGAL, OWN for CLIENT

## Key endpoints
- Super Admin: `/api/v1/super-admin/*` (firms, users, roles, permissions, invoices)
- Firm Admin: `/api/v1/firm/*` (users, matters, calendar, projects, audit)
- Auth: `/api/v1/auth/*` (register, login, refresh, MFA)
- Global Dashboard: `/api/v1/modules/dashboard?days=7|30|90|180|365`

## Architecture
- Multi-tenant with firm-scoped data isolation
- Permission-based RBAC: `PermissionEvaluator.require("MODULE:ACTION")` on every endpoint
- JWT with permissions embedded — zero DB hits for auth checks after login
- Email via `EmailService` — JavaMailSender with Thymeleaf HTML templates
- PDF via Thymeleaf + OpenHTMLtoPDF

## Session history
- Invoice Generator module + query optimization + dashboard trends → memory/2026-08-25.md
- Full E2E test session + authz fixes + hearing reminders → memory/2026-08-23.md
- Strix security skills install + manual-only policy → memory/2026-08-22.md
- N+1 query optimization + Project Management module → memory/2026-08-21.md
- Complete module refactoring (all modules) → memory/2026-08-20.md
- Dashboards documentation → memory/2026-08-19.md
