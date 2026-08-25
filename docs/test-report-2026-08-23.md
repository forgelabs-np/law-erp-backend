# LAW ERP (NepalCRM) — Full E2E Test Report

**Date:** 2026-08-23 (session started 2026-08-22, Nepal time +05:45)
**Environment:** Local dev (`dev` profile), app on `http://localhost:6969`, Supabase PostgreSQL dev DB
**Test type:** Full-stack API E2E — all modules, all roles, permissions, cross-tenant isolation, data correctness, email flows
**Tester:** Automated agent (curl-based), acting as super admin + firm admin + all employee roles + clients

---

## 1. Test accounts & firms used

| Firm | Role | Username | Notes |
|---|---|---|---|
| SYSTEM | SUPER_ADMIN | `aac1ss` (user-provided) | MFA 123456 (dev bypass) |
| TLAW | FIRM_ADMIN | `testfirm` (user-provided) | Existing firm |
| E2EFIRM (new) | FIRM_ADMIN | `e2eadmin` | Created via super admin during test |
| E2EFIRM | ADVOCATE | `e2efirm_e2eadvocate` | Created via firm admin |
| E2EFIRM | PARALEGAL | `e2efirm_e2eparalegal` | Created via firm admin |
| E2EFIRM | CLIENT | `e2eclient` | Portal access ON |
| SECFIRMA / SECFIRMB | FIRM_ADMIN | `secfirma_admin` / `secfirmb_admin` | Cross-tenant test firms |
| SECFIRMA | CLIENT | `secclient_a` | Cross-tenant client |

Note: employee usernames are auto-prefixed with firm code (`e2efirm_e2eadvocate`, not `e2eadvocate`).
Client login identifier is the **mobile number**, not username (`findByMobileNoAndFirmId`).

---

## 2. Severity legend

- **CRITICAL** — exploitable data exposure / authz bypass, blocks go-live
- **HIGH** — serious security/config issue
- **MEDIUM** — feature broken or significant functional bug
- **LOW** — minor bug / UX / inconsistency

---

## 3. FINDINGS

### 3.1 CRITICAL — Project Management module has NO authorization enforcement

**Endpoints:** all `/api/v1/projects/**` (create, list, get, update, status, members, credentials, reveal, renewals)
**Root cause:** `ProjectController`/`CredentialController`/`RenewalController` have **no `@PreAuthorize` and no permission checks**. Zero occurrences of `permissionEvaluator` (authorization) in the whole module.

**Proven with a CLIENT token** (lowest privilege) in E2EFIRM:
- ✅ Created a project (`"Project created"`)
- ✅ Listed **all firm projects** (incl. other clients' projects + member emails)
- ✅ **Revealed plaintext portal credential** → `{"password":"E2E-SuperSecret-Portal-Pass!"}` (CREDENTIAL_REVEAL permission bypassed)
- ✅ Updated/renamed the firm admin's project (persisted: `"name":"Renamed by client!"`)
- ✅ Changed project status to COMPLETED
- ✅ Deleted the credential (`"Credential deleted"`)
- ✅ Added a project member
- ✅ Marked a renewal instance COMPLETED

**Impact:** The encrypted-credentials feature is defeated; any client can read/delete firm data. Only firm-scoping (404 for other firms) limits the blast radius.

### 3.2 CRITICAL — RBAC permission layer is decorative (not enforced anywhere)

**Evidence:**
- `PermissionEvaluator` is injected in 5 services but only ever used for `clearUserCache()` — **no `hasPermission`-style authorization call exists anywhere in main code**
- Controllers use only role checks (`hasAnyRole('FIRM_ADMIN','ADVOCATE','PARALEGAL')`) — never module/action permissions
- **PARALEGAL (READ_ONLY per matrix) created a matter successfully** (`POST /firm/matters` → 200)
- Removing permissions from a role changes the JWT claims but **nothing checks them**
- `PROJECT_MANAGEMENT:CREDENTIAL_VIEW/CREDENTIAL_REVEAL` exist in the DB and JWT but are never evaluated

**Impact:** The entire RBAC design (READ_ONLY/OWN/FULL matrix, per-action permissions, ceiling logic) does not actually restrict anything beyond role presence + firm scoping. Blocking for go-live.

### 3.3 HIGH — MFA bypass code `123456` (KNOWN & intentional for testing — do not ship)

`TotpUtil.DEV_BYPASS_CODE = "123456"` passes whenever `app.production != true`; the flag is not set in any yml (defaults to false). → See Go-Live checklist.

### 3.4 HIGH — Secrets committed to repo (KNOWN & intentional for testing — move to env/SystemConfig)

- JWT HS512 secret in `application-dev.yml`
- `config.encryption.key` (AES, decrypts stored credentials) in `application.yml`
- Super-admin registration secret in `application-dev.yml` + registration endpoint is `permitAll`
→ See Go-Live checklist.

### 3.5 MEDIUM — Cannot add parties to a matter (feature broken) — ✅ FIXED 2026-08-23

`PartyEntryRequest.roleType` was `@NotBlank` on an enum → validation always failed with `HV000030: No validator could be found for constraint 'NotBlank' validating type 'PartyType'`. Affected both `POST /firm/matters` (with parties) and `POST /firm/matters/{no}/parties`.
**Fix:** `@NotNull` instead of `@NotBlank` on `PartyEntryRequest.roleType` and `PartyRoleRequest.roleType` (same latent bug).

### 3.6 MEDIUM — Firm-wide timeline always 500 — ✅ FIXED 2026-08-23

`GET /api/v1/firm/matters/timeline` → `JDBC exception: could not determine data type of parameter $6` (PostgreSQL 42P18). The nullable-filter JPQL (`:from is null or ...`) can't infer the parameter type. Matter-level timeline (`/firm/matters/{no}/timeline`) works fine.
**Fix:** `MatterServiceImpl.getFirmTimeline` now passes sentinel window bounds (1900-01-01 / 2999-12-31) instead of null `LocalDateTime`s — the `IS NULL` probes remain only on enum params, which bind fine. (JPQL `CAST(:from AS timestamp)` was tried first but Hibernate 7 binds the cast param as `bytea` — not usable.)

### 3.7 MEDIUM — Role-permission editing broken for ADVOCATE / PARALEGAL / CLIENT — ✅ FIXED 2026-08-23

`PUT /api/v1/firm/roles/{roleId}/permissions` always failed for these roles with:
`Permission 'CASE_MANAGEMENT:ACCESS' (scope: TENANT) is not allowed for role type 'ADVOCATE'. Exceeds system ceiling.`
Cause: every permission is seeded with `scope=TENANT` (DataInitializer), but the ceiling for ADVOCATE/PARALEGAL was `ASSIGNED` and CLIENT `OWN` (`getMaxScopeForParent`) → even re-saving the *current* permission set was rejected. Only FIRM_ADMIN/SUPER_ADMIN role editing worked.
**Fix:** the ceiling is now **the parent system role's actual permission set** (`rolePermissionRepository.findPermissionsByRoleId(parentRole.getId())`) — the same set the UI already shows as "available permissions". A hard guard still rejects GLOBAL-scope permissions for firm roles. Applied to both `FirmRoleServiceImpl` (firm admin path) and `RolePermissionServiceImpl` (super-admin path).

### 3.8 MEDIUM — Wrong HTTP method returns 500 instead of 405 (app-wide) — ✅ FIXED 2026-08-23

Reproduced on: `GET /super-admin/firms`, `GET /firm/matters/{no}/court-cases`, `GET /firm/matters/{no}/parties`, `PATCH /court-cases/{ref}/stage`, `GET /super-admin/config/{key}`, `PUT /super-admin/config/{key}`.
`GlobalExceptionHandler` didn't map `HttpRequestMethodNotSupportedException` → `500 "An unexpected error occurred"`.
**Fix:** added handlers — wrong method → **405**, unknown path (`NoResourceFoundException`) → **404**.

### 3.9 MEDIUM — Advocate/Paralegal matter list is unscoped

Advocate (assigned to 1 matter) sees **all firm matters** in `GET /firm/matters`, while the dashboard correctly scopes by assignment (showed 0 before assignment, 1 after). Design intent (dashboard doc) says ADVOCATE/PARALEGAL should only see assigned matters. Inconsistent data visibility.

### 3.10 MEDIUM — No way to delete a project

`DELETE /api/v1/projects/{projectCode}` → 500 (no mapping; only `/projects/{code}/members/{userId}` is deletable). Orphan projects cannot be removed.

### 3.11 LOW — Misc

- `markHeld` response returns `matterNumber:null, matterTitle:null` (schedule response includes them) — mapper omission
- Super admin gets 403 on scraper admin endpoints (`hasAnyRole('FIRM_ADMIN')` excludes SUPER_ADMIN)
- Cross-tenant lookups return 403 (clients/employees) vs 404 (matters/projects) — minor existence oracle
- Client login "username" is actually the mobile number (error message says "Firm code is required for internal users" on client login — confusing)
- `GET /firm/audit/actions` returns empty content — semantics unclear / needs review
- New matters flagged `stale` immediately (stale logic appears to count matters with no held real peshi)
- Password policy minimum 6 chars

---

## 3.12 ✅ FIXED (2026-08-23) — CRITICAL authz gaps closed

Implemented permission enforcement on all write/read endpoints of:

- **Project Management module** (all 5 controllers): class-level `@PreAuthorize(hasAnyRole('SUPER_ADMIN','FIRM_ADMIN','ADVOCATE','PARALEGAL'))` (clients blocked from firm API — portal only) + per-endpoint `permissionEvaluator.require(...)`:
  - `PROJECT_MANAGEMENT:CREATE` (create project/renewal/renewal-type)
  - `PROJECT_MANAGEMENT:VIEW` (list/get/dashboard/members/renewals)
  - `PROJECT_MANAGEMENT:EDIT` (update/status/members/instances/credentials add+update)
  - `PROJECT_MANAGEMENT:DELETE` (credentials, renewal-types)
  - `PROJECT_MANAGEMENT:CREDENTIAL_VIEW` (credential list/get)
  - `PROJECT_MANAGEMENT:CREDENTIAL_REVEAL` (reveal plaintext password)
- **Case Management writes** (MatterController, CourtEventController, CaseAssignmentController, CourtCaseController): `CASE_MANAGEMENT:CREATE/EDIT/DELETE/VIEW` — READ_ONLY roles (PARALEGAL) can no longer create matters/events/assignments; ADVOCATE keeps full case-management rights.

`PermissionEvaluator.require()` (already implemented, was never called) is now wired in: super-admin bypass, DB-backed role→permission source of truth, cached per user, cache invalidated on role/permission change.

**Verified live (all roles):**

| Scenario | Before | After |
|---|---|---|
| CLIENT lists firm projects | 200 | **403** |
| CLIENT reveals plaintext credential | 200 (password leaked) | **403** |
| CLIENT creates project | 200 | **403** |
| PARALEGAL creates matter | 200 | **403** |
| PARALEGAL views credentials | 200 | **403** |
| PARALEGAL reveals credential | 200 | **403** |
| ADVOCATE views project | 200 | 200 |
| ADVOCATE creates project (READ_ONLY) | 200 | **403** |
| ADVOCATE creates matter (FULL) | 200 | 200 |
| FIRM_ADMIN create/reveal | 200 | 200 |
| CLIENT portal (own projects) | 200 | 200 |

**Tests:** +16 new (ProjectManagementAuthzTest, CaseManagementAuthzTest) locking the endpoint→permission mapping and forbidden propagation. Full suite: 157 tests, 0 failures.

**Still open (not part of this fix):** RBAC permission checks are now enforced in project + case management only; other future modules must follow the same pattern. Super admin cannot use firm-context endpoints (`/projects`, `/firm/*`) — pre-existing "Firm context required" behavior, by design.

## 3.13 ✅ FIXED (2026-08-23) — MEDIUM bugs (parties, timeline, ceiling, 405)

| # | Bug | Before | After |
|---|---|---|---|
| 3.5 | Add party to matter / create matter with parties | 400 HV000030 | **200** — party + role created |
| 3.6 | Firm timeline (plain, from/to, matterType filters) | 500 42P18 | **200** — all filter combos |
| 3.7 | Role-permission PUT (same set, ADVOCATE) | 403 "exceeds ceiling" | **200** "Role permissions updated" |
| 3.7 | Role-permission PUT (BILLING:DELETE, outside ceiling) | — | **403** clean ceiling message (still enforced) |
| 3.8 | GET on POST-only endpoint | 500 | **405** |
| 3.8 | Unknown path | 500 | **404** |

**Tests:** `RolePermissionServiceTest` ceiling tests rewritten to the parent-set contract (+GLOBAL guard); new `FirmRoleServiceImplTest` (4), `GlobalExceptionHandlerTest` (2), `PartyEntryRequestValidationTest` (4). Full suite: **168 tests, 0 failures**.

**New minor note found while verifying:** `RolePermissionRequest.roleId` is `@NotNull`, but the controller overwrites it from the path *after* validation — so the body must redundantly include `roleId`, else 400 "Role ID is required". Harmless (UI sends it) but the body field is redundant with the path; consider dropping `@NotNull` later.

## 3.14 ✅ NEW FEATURE (2026-08-23) — Hearing reminder emails (T-1)

Go-live requirement (§5 item 6) implemented per approved design (`docs/superpowers/specs/2026-08-23-hearing-reminder-emails-design.md`):

- **`HearingReminderScheduler`** — daily `0 0 8 * * *` (same cadence as RenewalScheduler) → `HearingReminderServiceImpl.sendRemindersForDate(tomorrow)`
- **Selection:** PESHI events, `status = SCHEDULED`, `scheduledDate = tomorrow` (TARIK excluded by design)
- **Recipients:** attending advocate (`attendingAdvocateId` → user email) + every linked our-client (`MatterParty.isOurClient && clientId` → distinct client user emails); batch-loaded (no N+1)
- **Delivery:** `EmailService.sendHearingReminder` (@Async, Thymeleaf `email/hearing-reminder.html`, per-firm SMTP resolution — firm config → global DB config → default), client vs advocate subject + CTA (portal vs login), `EMAIL_SENT`/`EMAIL_FAILED` audit rows (recipient's user id as trigger — `AuditLog.userId` is NOT NULL and the job is unauthenticated)
- **Idempotency:** `hearing_reminder_log` table (unique courtEventId+recipientType+recipientEmail+scheduledDate) — claim row inserted before dispatch; duplicate claims skipped; async failure flips the row to FAILED

**Live verification (dev DB, E2EFIRM):**
- Scheduled a PESHI for 2026-08-24 15:00 (advocate `e2efirm_e2eadvocate`, linked our-client party `e2eclient`) — scheduling conflict check correctly blocked an overlapping 10:30 time first
- First job run: **2 emails dispatched** (advocate + client, correct recipient-specific subjects)
- Second run: **0 dispatched** — idempotent, no duplicates
- Send attempted, failed only on dev Gmail auth (`EMAIL_FAILED ... error=Authentication failed`) — same known dev limitation as welcome emails; log rows flipped to **FAILED** as designed; template rendered fine
- Real delivery requires per-firm SMTP creds (prod go-live item — config UI/API already verified working)

**Tests:** +9 (`HearingReminderServiceImplTest` — recipient resolution, idempotent claims, CANCELED/HELD exclusion, duplicate-client dedup, per-event failure isolation, details completeness). Full suite: **177 tests, 0 failures**.

## 4. Verified WORKING (PASS)

### Auth & account lifecycle
- Super admin login, firm admin login (forced password change → MFA setup → token) ✅
- Employee/client login flows ✅
- Password reset by firm admin: old password + old JWT immediately invalid ✅
- Bulk deactivate → login blocked → reactivate ✅
- Bulk role change (PARALEGAL→ADVOCATE→PARALEGAL) ✅
- JWT tampering rejected (401) ✅ · refresh-token abuse rejected ✅ · no-auth = 401 everywhere ✅

### Case management
- Matter creation (numbering `E2EFIRM-MAT-2026-00001`), court case auto-created ✅
- Stage machine: valid transition OK, invalid transition → clean 400 ("Judgment can only be recorded from JUDGMENT_AWAITED") ✅
- Tarik/Peshi scheduling with conflict-warning field ✅
- Mark-held → auto-creates next chained event ✅
- Calendar today/upcoming ✅ · hearing-status lookup ✅ · appeal-deadlines ✅
- Judgment endpoint guarded by state machine ✅
- Matter-level timeline ✅

### Project management
- Project creation linked to client (`clientUserId`) ✅ · member add (OWNER/MEMBER/VIEWER) ✅
- Credential add (password masked in responses) ✅ · reveal works + audit-logged (for admin) ✅
- Renewal recurrence engine: YEARLY start 2026-08-01 → instances 2026/2027/2028... ✅
- Instance status update ✅ · projects dashboard counts ✅
- Client portal: sees ONLY own project + renewals ✅

### User management & RBAC administration
- Employee creation (advocate w/ bar council no., paralegal; codes E2EFIRM-0001/-0002) ✅
- User search, profile, permissions view (JWT claim list correct) ✅
- Firm roles list / role users ✅ · role permission ceiling check rejects over-assignment (FIRM_ADMIN) ✅
- Super admin: create firm + admin, list users, firm admins, firm config ✅

### SystemConfig (future home for hardcoded values)
- Global: GET list ✅, PUT bulk upsert ✅, DELETE key ✅
- Per-firm: GET/PUT ✅ (tested with E2EFIRM)

### Email
- Per-firm SMTP config: save ✅, password never returned (smtpPasswordSet flag) ✅, POST /test actually connects to SMTP and reports pass/fail correctly ✅
- Welcome emails (firm admin / employee / client) + password-reset email **trigger**; dev Gmail sender has no password → EMAIL_FAILED in logs (expected in dev; real creds required in prod)

### Data correctness
- Pagination (`page/size`, totalElements) ✅ · search filters ✅
- Audit trail records actions (user role change, deactivate, config, logins) ✅
- Dashboards: firm dashboard, advocate dashboard (assignment-scoped), global modules dashboard, projects dashboard — counts consistent with test data ✅
- Master data (provinces) ✅ · firm profile update ✅

### Cross-tenant isolation (3 firms: E2EFIRM / SECFIRMA / SECFIRMB / TLAW)
- Firm A admin → Firm B resources: projects 404, credentials 404, matters 404, court cases 404, clients 403, employees 403 ✅
- Client (SECFIRMA) → E2EFIRM project: 404 ✅ · client portal shows only own firm data ✅
- Audit logs firm-scoped ✅ · calendar firm-scoped ✅
- SQL injection payloads (OR, UNION, pg_sleep) — all treated as literals, no timing ✅ (parameterized queries)

---

## 5. Go-Live checklist (TODO items — user-approved)

1. **[TODO] JWT secret → env var.** `application-dev.yml` `jwt.secret` → `${JWT_SECRET}` (no committed default). Generate strong secret at deploy time. (User: "We have set the JWT in the appprop. set it in env")
2. **[TODO] AES config-encryption key → env var.** `config.encryption.key` → `${CONFIG_ENCRYPTION_KEY}`. **Caution:** rotating the key breaks decryption of existing stored credentials — plan a re-encryption migration.
3. **[TODO] Super-admin registration secret → SystemConfig.** Move from `application-dev.yml` (`super-admin.registration-secret`) into SystemConfig (`PUT /api/v1/super-admin/config` — verified working). Service already disables registration when the secret is empty.
4. **[TODO] Remove MFA bypass.** Delete `DEV_BYPASS_CODE` from `TotpUtil` (or enforce `app.production=true` in prod profile — prefer removing the bypass entirely). All test accounts use 123456; real TOTP secrets already stored per user.
5. **[TODO] Enforce RBAC permissions.** Implement authorization checks (module:action) in services/controllers — minimum: project management module (critical), matter/event create for READ_ONLY roles. Fix `PermissionEvaluator` to actually evaluate.
6. ~~**[TODO] Hearing reminder emails (missing feature)**~~ — ✅ DONE 2026-08-23 (see §3.14). Daily 8 AM T-1 reminders to advocate + linked clients via firm SMTP; `hearing_reminder_log` idempotency table; verified live in dev (delivery blocked only by missing SMTP creds).
7. **[TODO] Configure real SMTP per firm** (FirmEmailConfig UI/API — verified working; welcome/password emails currently fail on dev Gmail default).
8. ~~**[TODO] Fix parties validation**~~ — ✅ DONE 2026-08-23 (`@NotNull` on `PartyType`, both DTOs).
9. ~~**[TODO] Fix firm timeline query**~~ — ✅ DONE 2026-08-23 (sentinel window bounds; all filter combos verified).
10. ~~**[TODO] Fix role-permission ceiling**~~ — ✅ DONE 2026-08-23 (ceiling = parent system role's permission set; GLOBAL still guarded).
11. ~~**[TODO] Map wrong-method to 405**~~ — ✅ DONE 2026-08-23 (405 for wrong method, 404 for unknown path).
12. **[TODO] Scope matter list by assignment** for ADVOCATE/PARALEGAL (or confirm intended).
13. **[TODO] Add project DELETE endpoint** (admin only).
14. **[TODO] Decide: super admin access to scraper endpoints; client-login identifier (mobile vs username); password policy ≥8; 24h access-token lifetime.**
15. **[TODO] Cleanup:** remove test firms/users (E2EFIRM, SECFIRMA, SECFIRMB, TLAW test users, sectestadmin) before production DB seeding; also `E2E_FIRM_KEY` left in E2EFIRM's SystemConfig.

---

## 6. Test data created (for reference / cleanup)

- Firms: E2EFIRM (f1324b72-e89e-4b15-a20f-1c69d1bbf1d5), SECFIRMA, SECFIRMB (earlier session)
- Users: e2eadmin, e2efirm_e2eadvocate, e2efirm_e2eparalegal, e2eclient (+ SEC*/TLAW users)
- Matters: E2EFIRM-MAT-2026-00001 (stage SUMMONS_ISSUED, 2 events chained), -00002, -00003
- Court case ref: E2EFIRM-MAT-2026-00001-DC1 (E2E-CIV-001)
- Projects: E2EFIRM-PRJ-2026-00001 (status COMPLETED, renamed by client during test, credential deleted during test), client-created orphan project (no delete endpoint), SECFIRMA-PRJ-2026-00001
- Renewal id 1 (Trademark Renewal, YEARLY) with instances 1-3 (instance 1 completed during test)
- E2EFIRM email config (dummy Gmail creds) + E2E_FIRM_KEY SystemConfig entry

*Full request/response evidence available in session log; key PoCs reproduced in section 3.*
