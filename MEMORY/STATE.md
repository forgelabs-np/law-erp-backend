# Project State

## Current Focus
SystemConfig is now a DB-driven settings registry (grouped/typed, seeded defaults, MFA policy + registration secret + SMTP read from DB at runtime — no rebuild). UserType FIRM expansion + audit filtering done earlier.

## Branch
`devG`

## Tech Stack
- Spring Boot (Java)
- Multi-tenant ERP system
- Modules: Auth, RBAC, Case Management, Invoicing, Super Admin, Customer, Firm, Tenant

## Recent Work (this session)
- **UserType Expansion** — added `FIRM` (firm admin) between SUPER_ADMIN and FIRM_USER; firm admins now use FIRM type
- **Audit Log Filtering** — `GET /super-admin/audit` supports `userType` + `userId` params at DB level; `FIRM` maps to char 'A'
- **DB Migration** — `V2026_09_03` updates CHECK constraint, backfills existing firm admins, updates system FIRM_ADMIN role
- **DB-Driven System Config** — settings metadata columns + migration `V2026_09_03_1`; MFA policy/registration secret/SMTP configurable from DB (no rebuild); boot-seeded defaults (insert-if-missing); typed admin GET views w/ decrypted values; 243 tests green

## Deep History Index
<!-- pointers to docs/ for full feature writeups -->
- `memory/2026-09-02.md` — MFA reset, user delete, SA audit logs, pagination, @Operation constants, module delete fix, RBAC role CRUD + override
- `memory/2026-09-03.md` — UserType FIRM expansion, audit log filtering by userType/userId
