# Project State

## Current Focus
MFA reset, user delete, super admin audit logs, and pagination — all shipped and tested.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java)
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant

## Recent Work (this session)
- **MFA Reset by Super Admin** — `POST /api/v1/super-admin/mfa/reset` clears mfaSecret + mfaVerified, forces re-setup
- **Single User Delete** — `DELETE /api/v1/modules/users/{userId}` soft-deactivates with audit
- **Super Admin Audit Logs** — 4 cross-firm endpoints: all logs, by firm, by user, by entity
- **Pagination** — Added PagedResponse to getAllUsersWithRoles, listUsers, searchUsers
- **Test fix** — ProjectManagementAuthzTest corrected (DASHBOARD_MANAGEMENT:VIEW not PROJECT_MANAGEMENT:VIEW)
- All 230 tests pass, 0 failures

## Deep History Index
<!-- pointers to docs/ for full feature writeups -->
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination session
