# Fix batch — 2026-09-23

Scope: the six bugs raised against the QA checklist (`docs/ui-test-checklist.md`) plus the
client-portal login report. Branch `devG`, base commit `5cfd59b` (2026-09-21).

**Result:** all six fixed, `./mvnw test` → **461 tests, 0 failures, 0 errors, BUILD SUCCESS**
(63 of them in the QA suites).

| # | Bug (checklist) | Status | Where it was |
|---|---|---|---|
| 1 | Client portal cannot log in (1.12) | ✅ fixed | `AuthServiceImpl` |
| 2 | Reset-password / change-password APIs (1.8, 1.9) | ✅ fixed | `UserManagementController`, `SuperAdminController`, `MeController` |
| 3 | `isTrial` missing from the firm response (2.8) | ✅ fixed | `FirmAdminResponse`, `FirmProfileResponse` |
| 4 | Bulk deactivate not working (4.10) | ✅ fixed | `UserManagementController` |
| 5 | Assignment sends no e-mail (5.7) | ✅ fixed | `NotificationType` |
| 6 | An unassigned employee can still list cases/matters | ✅ fixed | `MatterServiceImpl`, `ReadScopeGuard` |

---

## How to run

```bash
cd /g/Projects/LAW-ERP/law-erp-backend
JAVA_HOME=/c/Users/Dev/.jdks/ms-21.0.12 ./mvnw -o test                 # full suite
JAVA_HOME=/c/Users/Dev/.jdks/ms-21.0.12 ./mvnw -o test -Dtest='*QaTest'  # the QA suites only
```

The shell's default `JAVA_HOME` points at JDK 17, which cannot build this module (`release 21`);
use the path above or the build fails before compiling anything.

---

## 1. Client portal login (bug 7 / checklist 1.12)

**Symptom.** A client created by the firm could not sign into the portal; correct credentials
returned `Invalid credentials`.

**Root cause.** `AuthServiceImpl.login` resolved a client **only** by mobile number:

```java
userRepository.findByMobileNoAndFirmId(request.getUsername(), firm.getId())
```

The portal has a single identifier field labelled *username*, and the client is handed a
generated username — but that username was never looked up. Whatever they typed, only the mobile
arm could match. `forgotPassword` had the mirror-image gap: it resolved username and e-mail but
not the mobile number clients are asked for.

**Fix** (`auth/service/AuthServiceImpl.java`)

- New `findClientAccount(identifier, firmId)` — tries mobile first, then username, so either one
  reaches the same account. The client branch of `login` now uses it.
- `forgotPassword` lookup gains `.or(() -> findByMobileNoAndFirmId(...))`.

**Test.** `AuthSecurityQaTest` → `AUTH-09b` creates a client, reads the username back off the
persisted user, and asserts both the username and the mobile number log in (200).

**Manual.** Create a client, then sign into the portal with (a) the mobile number and (b) the
username shown on the client's row — both must land in the portal.

---

## 2. Reset-password and change-password APIs (1.8, 1.9)

Three separate defects sat behind this one report.

### 2.1 The admin "reset password" dialog sends no body → 400

**Symptom.** Clicking reset in the user console failed with a request error; the action looked
unimplemented.

**Root cause.** The endpoint required `@Valid @RequestBody ApiRequest<ResetPasswordRequest>` with
a mandatory `newPassword`, but the frontend's reset is a confirmation dialog — it posts no body at
all:

```ts
const resetPassword = (id: string) =>
  LawFirmCRMClient.post(api.USER_MANAGEMENT.USERS.RESET_PASSWORD.replace("{userId}", id));
```

The envelope binding failed before the controller ran, so nothing was ever reset.

**Fix.** `POST /api/v1/modules/users/{userId}/reset-password` and
`POST /api/v1/super-admin/users/{userId}/reset-password` now take an optional body through the new
`RequestBodyBinder`, and the service **generates** a policy-compliant temporary password when none
is supplied. The generated password is returned once in the response for the admin to hand over
(e-mail carries no password) and the holder must rotate it on first login.

### 2.2 The same reset accepted `aaaaaa` (1.9)

**Root cause.** Two different policies: client creation accepted 6 characters, the change-password
endpoints demanded 8, and the admin reset relied on a DTO `@NotBlank` only — so `aaaaaa` was
accepted by one dialog and rejected by another.

**Fix.** New `common/util/PasswordPolicy.java` is the single rule (**8–50**) plus
`generateTemporary()`. It is applied on the admin reset (both firm and Super Admin), the
self-service change, and `CreateClientRequest` (was `@Size(min = 6)`).

### 2.3 No self-service "change my own password" endpoint

**Symptom.** Nothing in the API let an already-signed-in user change their own password. The only
change-password route (`POST /api/v1/auth/change-password`) redeems the one-time
`passwordChangeToken` from a forced rotation, which is a different flow.

**Fix.** New `POST /api/v1/me/change-password` (`ChangeOwnPasswordRequest`: current, new, confirm).
It proves the current password, refuses a repeat, applies `PasswordPolicy`, clears any lockout,
audits, and revokes every other session (permission-version bump → old tokens are rejected).

> **Not yet wired in the UI.** The frontend has no profile-screen password form; its only
> password screen is the forced rotation (`auth/change-password`). If the profile menu should offer
> a change-password action, it needs a form calling this new endpoint.

**Related file.** `common/dto/RequestBodyBinder.java` (new) — reads a body that may or may not wear
the `{"data": …}` envelope, tolerates an absent body, and deserializes from raw text (the web layer
has no converter registered for Jackson tree types, so `JsonNode` parameters fail binding).

**Tests.** `PasswordPolicyTest` (5) covers the policy bounds and the generated password;
`SuperAdminPasswordResetTest`, `UserManagementResetTest`, `UserManagementServiceTest` cover the
admin paths; the QA suites exercise the endpoints over HTTP.

**Manual.**
- Admin → Users → reset password on a row: succeeds with no prompt, and the response carries a
  temporary password; the user must change it at next login.
- Admin reset with a password of 7 characters: rejected with `Password must be 8-50 characters`.
- Create a client with a 6-character password: rejected.
- `POST /api/v1/me/change-password` with the wrong current password: `Current password is
  incorrect`; with the right one: 200 and other sessions stop working.

---

## 3. `isTrial` in the firm response (2.8)

**Symptom.** Converting a trial firm left the trial badge/expiry in place because the API never
returned the flag.

**Fix.** `FirmAdminResponse` (Super Admin firm-admin list, `GET /api/v1/super-admin/firms/admins`)
and `FirmProfileResponse` (the firm's own profile) now carry `isTrial`, `trialDays` and
`trialExpiresAt`, populated in `FirmAdminServiceImpl.toResponse` and
`FirmProfileServiceImpl.toProfileResponse`.

**Note.** `FirmMapper.toFirmAdminResponse` / `toFirmProfileResponse` are older duplicate builders
that do **not** set the trial fields. They have no callers, so they are left as-is — worth deleting
rather than updating.

**Manual.** Convert a trial to permanent, reload the Super Admin firm list and the firm profile:
`isTrial` false and no expiry.

---

## 4. Bulk deactivate (4.10)

**Root cause.** Same binding bug as 2.1: the endpoint required `@Valid @RequestBody
ApiRequest<BulkDeactivateRequest>` while the screen posts the payload bare — `{ "userIds": [...] }`
— so the request was rejected before the service saw it. (`bulk-role-change` had the identical
defect.)

**Fix.** Both endpoints take the body through `RequestBodyBinder` (either shape, body optional), and
the service now rejects an empty selection with `At least one user ID is required` instead of
silently succeeding — the `@Valid` on the envelope never protected these paths.

**Manual.** Select several users → bulk deactivate → the per-user result report appears and the
rows go inactive; repeat with an empty selection → a clear error, no partial write.

---

## 5. Assignment e-mail notification (5.7)

**Root cause.** Assignment *was* published as a notification
(`CaseAssignmentServiceImpl` → `NotificationType.CASE_ASSIGNED`), but e-mail delivery for a type
with no stored preference defaulted to *on* only for `ALERT` categories. `CASE_ASSIGNED` is a
`SYSTEM` type, so the assignee got the in-app row and no e-mail.

**Fix** (`modules/notification/enums/NotificationType.java`). Each type now declares
`emailsByDefault`, and `emailsByDefault()` returns `declared || category == ALERT`. `CASE_ASSIGNED`
is the one routine SYSTEM type that declares `true`; it stays overridable (unlike ALERT, whose
opt-out is locked). `NotificationPreferenceServiceImpl` uses the method for both the delivery
decision and the preference screen, so the toggle now shows as *on*.

**Tests.** `NotificationPreferenceServiceImplTest` asserts the new default and that CASE_ASSIGNED
stays mutable; `NotificationOrchestratorEmailTest` covers the dispatch path.

**Manual.** Assign an advocate to a matter → the assignee receives both the in-app notification and
an e-mail. Silence CASE_ASSIGNED on the preferences screen → the e-mail stops.

---

## 6. An unassigned employee can still list cases/matters

**Symptom.** An employee with no assignments saw the whole firm's matter list.

**Root cause.** `GET /api/v1/firm/matters` narrowed the list for clients only:

```java
if (effectiveClientId != null) { … } else if (filters) { … } else { findByFirmId(...) }
```

Every non-client fell through to the firm-wide query. The dashboard already scoped advocates and
paralegals by their assignments; the list endpoint did not.

**Fix.**

- `ReadScopeGuard.isAssignmentScope()` — true for firm staff who are neither admins nor clients
  (advocate, paralegal, custom roles).
- `MatterServiceImpl.listMatters` and `getStaleMatters` route those callers through a new
  `assignedMatters(...)`, which reads the caller's matter ids from `CaseAssignmentRepository` and
  returns an **empty page** when there are none — the honest answer to "what am I working on?".
- New `MatterRepository.findByFiltersAndIdIn(...)` keeps every existing filter (type, status,
  client, search) on top of the id restriction. Callers must not pass an empty collection (an empty
  `IN` is not valid JPQL), which the empty-page branch guarantees.

Firm admins, Super Admins and clients are unaffected (`isAssignmentScope()` is false for them, and
the client branch keeps its own scope).

**Manual.** Log in as an advocate with no assignments → matters list and stale list are empty. Add
one assignment → exactly that matter appears. As firm admin: unchanged full list.

---

## API surface touched

| Endpoint | Change |
|---|---|
| `POST /api/v1/auth/client/login` | accepts the username **or** the mobile number |
| `POST /api/v1/auth/forgot-password` | also resolves mobile numbers |
| `POST /api/v1/modules/users/{userId}/reset-password` | optional body; returns the generated temporary password |
| `POST /api/v1/modules/users/bulk-deactivate` | accepts the bare payload; rejects an empty selection |
| `POST /api/v1/modules/users/bulk-role-change` | accepts the bare payload; rejects an empty selection |
| `POST /api/v1/super-admin/users/{userId}/reset-password` | optional body; returns the generated temporary password |
| `POST /api/v1/me/change-password` | **new** — self-service change (current password required) |
| `GET /api/v1/firm/matters`, `GET /api/v1/firm/matters/stale` | scoped to the caller's assignments for non-admin staff |
| `GET /api/v1/super-admin/firms/admins`, firm profile | `isTrial`, `trialDays`, `trialExpiresAt` added |

## Files changed

**New (5).** `common/dto/RequestBodyBinder.java`, `common/util/PasswordPolicy.java`,
`modules/me/dto/ChangeOwnPasswordRequest.java`,
`modules/usermanagement/dto/response/PasswordResetResult.java`,
`test/.../common/util/PasswordPolicyTest.java`.

**Modified (25).**
`auth/service/AuthServiceImpl.java`, `auth/security/ReadScopeGuard.java`,
`common/constant/MeConstants.java`, `dto/firm/request/CreateClientRequest.java`,
`dto/firm/response/FirmAdminResponse.java`, `dto/firm/response/FirmProfileResponse.java`,
`firm/service/FirmAdminServiceImpl.java`, `firm/service/FirmProfileServiceImpl.java`,
`modules/casemanagement/repository/MatterRepository.java`,
`modules/casemanagement/service/MatterServiceImpl.java`, `modules/me/controller/MeController.java`,
`modules/me/service/MeService.java`, `modules/me/service/MeServiceImpl.java`,
`modules/notification/enums/NotificationType.java`,
`modules/notification/service/NotificationOrchestrator.java` (javadoc),
`modules/notification/service/NotificationPreferenceServiceImpl.java`,
`modules/usermanagement/controller/UserManagementController.java`,
`modules/usermanagement/dto/request/ResetPasswordRequest.java`,
`modules/usermanagement/service/UserManagementService.java`,
`modules/usermanagement/service/UserManagementServiceImpl.java`,
`superadmin/controller/SuperAdminController.java`, `superadmin/service/SuperAdminService.java`,
`superadmin/service/SuperAdminServiceImpl.java`, `test/.../qa/AuthSecurityQaTest.java`,
`test/.../casemanagement/service/MatterServiceTest.java`,
`test/.../notification/service/NotificationPreferenceServiceImplTest.java`.

## Verification

```
JAVA_HOME=/c/Users/Dev/.jdks/ms-21.0.12 ./mvnw -o test
[INFO] Tests run: 461, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

QA suites (63 tests): `AuthSecurityQaTest` 16, `SuperAdminQaTest` 16, `FirmRoleMatrixQaTest` 12,
`CaseManagementQaTest` 11, `ProjectManagementQaTest` 8.

## Still open (not part of this batch)

- F-6 module/plan gating is dead code; F-9–F-14 remain from the QA report.
- The profile-screen change-password form (see 2.3) and Postman entries for the new endpoint.
- A Postgres (not H2) pass for the native `date(...)` aggregates, as noted in `MEMORY/STATE.md`.
- `FirmMapper`'s duplicate `toFirmAdminResponse` / `toFirmProfileResponse` builders (dead code,
  now out of date with the trial fields).
