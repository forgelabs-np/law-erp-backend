# Project State

## Current Focus
All core features shipped. Last task was a code-quality refactoring: extracted all hardcoded `@Operation` strings into centralized constants files.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java)
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant

## Recent Work (this session)
- **Swagger @Operation Constants Refactoring** — extracted ~60 hardcoded `@Operation(summary, description)` strings from 19 controllers into centralized constants files (`TenantConstants`, `MasterDataConstants` + updated `FirmConstants`, `CaseManagementConstants`, `SuperAdminConstants`, `UserManagementConstants`, `RbacConstants`)
- **MFA Reset by Super Admin** — `POST /api/v1/super-admin/mfa/reset` clears mfaSecret + mfaVerified, forces re-setup
- **Single User Delete** — `DELETE /api/v1/modules/users/{userId}` soft-deactivates with audit
- **Super Admin Audit Logs** — 4 cross-firm endpoints: all logs, by firm, by user, by entity
- **Pagination** — Added PagedResponse to getAllUsersWithRoles, listUsers, searchUsers
- All 230 tests pass, 0 failures

## Deep History Index
<!-- pointers to docs/ for full feature writeups -->
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants refactoring session
