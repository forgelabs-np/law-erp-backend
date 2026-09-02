# Project State

## Current Focus
RBAC system fully wired: Super Admin → Firm Admin → Employee permission flow with ceiling enforcement and Super Admin override.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java)
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant

## Recent Work (this session)
- **Firm Admin Role CRUD** — `POST/DELETE/PATCH /api/v1/firm/roles` for custom role management with firm scoping
- **Super Admin Override** — `PUT /api/v1/super-admin/firms/{firmId}/roles/{roleId}/permissions` bypasses all ceilings
- **Module Hard Delete Fix** — cascade deletes `firm_modules` + `module_permissions` + children before deleting module row
- **Swagger @Operation Constants Refactoring** — extracted ~60 hardcoded strings from 19 controllers into centralized constants
- All 230 tests pass, 0 failures

## Deep History Index
<!-- pointers to docs/ for full feature writeups -->
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
