# State
_Last updated: 2026-08-27_

## Active work
- Two-Tier Permission Ceiling implemented — firm roles start empty, FIRM_ADMIN enables from system ceiling, distributes to other roles
- Invoice Generator module complete — SUPER_ADMIN only for now
- Query optimization done — 6 hotspots fixed, 178+ tests pass
- Dashboard trends feature done

## Recent decisions (keep ~6, drop the oldest)
- Two-Tier Ceiling: FIRM_ADMIN ceiling = parent system role (SUPER_ADMIN); other roles ceiling = firm FIRM_ADMIN enabled perms
- Firm roles start with ZERO permissions on creation (no more auto-cloning from system roles)
- SUPER_ADMIN manually assigns permissions to system FIRM_ADMIN role — no auto-sync
- When new modules/permissions added, SUPER_ADMIN assigns to system FIRM_ADMIN, then all firms see them as available
- FIRM_ADMIN enables for itself, then distributes to PARALEGAL/LAWYER/custom roles
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
- Two-Tier Permission Ceiling overhaul → memory/2026-08-27.md
- Invoice Generator module + query optimization + dashboard trends → memory/2026-08-25.md
- Full E2E test session + authz fixes + hearing reminders → memory/2026-08-23.md
- Strix security skills install + manual-only policy → memory/2026-08-22.md
- N+1 query optimization + Project Management module → memory/2026-08-21.md
- Complete module refactoring (all modules) → memory/2026-08-20.md
- Dashboards documentation → memory/2026-08-19.md
