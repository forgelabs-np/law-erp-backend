# Production Readiness — System Config Audit & Test Checklist

Branch: `devG` · Audit date: 2026-10-12 · Test baseline: **671 tests, 0 failures, 0 errors** (`./mvnw -o test`)

This document does two things:

1. **Audits every `system_config` / `firm_configs` key** — is it declared, seeded, and actually *read* by
   production code? A key that is declared + seeded but never consumed is **dead config** and a trap
   (admins change it, nothing happens).
2. **Gives a runnable production test checklist** — one block per feature, with steps and expected result.
   Work top to bottom; tick each box. Anything unchecked is a No-Go.

---

## Live verification already done (2026-10-12)

The app was booted on the dev profile against the configured database and the matrix below was run over
real HTTP. Results, new findings and a role-by-role analysis are in
**`docs/dev-profile-test-report.md`**. Headlines:

- ✅ Works live: MFA enable/disable and the real TOTP path, `MFA_DEV_BYPASS` via the API, config
  validation (radio/number/url/unknown-key), the cross-scope allowlist, token expiry, RBAC denials
  for employee and firm admin on super-admin paths, and the wrong-door login guards.
- ❌ **Found broken live:** login lockout was dead on both login paths — failed attempts rolled back
  on `/auth/login`, and `locked_until` never set on `/super-admin/login`. A firm admin also got
  **403 on its own `/firm/config`**, so firm branding was unusable (NEW-1..NEW-3).
- ✅ **NEW-1..NEW-3 fixed and re-verified live the same day** (see D2 below and “Fixes applied” in
  `docs/dev-profile-test-report.md`): attempts now persist and lock the account on both login paths,
  a firm admin gets **200** on `/firm/config`, and locked accounts can be released through the API.
- ⚠️ Confirmed live: `MFA_ENABLED=N` does not stop MFA for existing users (F-2), and the
  repo-committed `config.encryption.key` decrypts stored MFA secrets (NEW-4, NEW-5).
- Client-portal journey is still unproven: no client fixture exists in this database.

D2 and D9 below are runnable as written again; D2 carries the live re-verification results.

---

## How config works (one paragraph)

Every runtime tunable is declared once in `ConfigKeyRegistry` (GLOBAL → `system_config` table,
FIRM → `firm_configs`). `DataInitializer` calls `seedGlobalDefaults()` at boot (**insert-if-missing**, an
admin edit is never overwritten). Services read the key **on every request** through a runtime helper on
`SystemConfigService` / `FirmConfigService`, so a DB change is live immediately. `PASSWORD`-type values are
AES-256-GCM encrypted at rest with `config.encryption.key`.

---

## A. Config audit — every key

### GLOBAL (`system_config`)

| Key | Group | Seeded | Read by (main code) | Verdict |
|---|---|---|---|---|
| `APP_NAME` | APP | yes | `MeServiceImpl`, `EmailServiceImpl` | ✅ live |
| `APP_PRODUCTION` | APP | yes (from `app.production`) | `TotpUtil.isProduction()` | ✅ live · **must be `Y` in prod** |
| `LOGIN_URL` | APP | yes | `EmailServiceImpl.loginUrl()` | ✅ live |
| `CLIENT_PORTAL_URL` | APP | yes | `EmailServiceImpl.clientPortalUrl()` | ✅ live |
| `MFA_ENABLED` | SECURITY | yes (`Y`) | `DataInitializer`, `SuperAdminServiceImpl`, `FirmServiceImpl` | ✅ live · see **F-2** |
| `MFA_REQUIRED_ROLES` | SECURITY | yes | same three | ✅ live · see **F-6** |
| `MFA_DEV_BYPASS` | SECURITY | **no** | `TotpUtil.verify()` | ✅ live · **must be absent / `N` in prod** |
| `REGISTRATION_SECRET` | SECURITY | yes (`""`) | `SuperAdminServiceImpl.resolveRegistrationSecret()` | ✅ live · locked after first set |
| `LOGIN_MAX_ATTEMPTS` | SECURITY | yes (`5`) | `AuthServiceImpl`, `SuperAdminServiceImpl` | ✅ live |
| `LOGIN_LOCK_MINUTES` | SECURITY | yes (`30`) | `AuthServiceImpl`, `SuperAdminServiceImpl` | ✅ live |
| `ACCESS_TOKEN_EXPIRY_MINUTES` | SECURITY | **no** | `JwtUtil.generateAccessToken()` | ✅ live · absent → `jwt.access-expiry` |
| `TRIAL_DEFAULT_DAYS` | TRIAL | yes (`14`) | `FirmServiceImpl.createFirm()` | ✅ live |
| `TRIAL_WARNING_DAYS` | TRIAL | yes (`3`) | `TrialExpiryScheduler` | ✅ live |
| `NOTIFICATION_MAX_ATTEMPTS` | NOTIFICATION | yes (`3`) | `NotificationRetryScheduler` | ✅ live |
| `STORAGE_MAX_FILE_SIZE_BYTES` | STORAGE | yes | `DocumentServiceImpl` | ✅ live |
| `STORAGE_UPLOAD_EXPIRY_SECONDS` | STORAGE | yes | **nothing** | ❌ **DEAD** (F-1) |
| `STORAGE_DOWNLOAD_EXPIRY_SECONDS` | STORAGE | yes | `DocumentServiceImpl` | ✅ live |
| `STORAGE_DEFAULT_QUOTA_BYTES` | STORAGE | yes | `StorageQuotaService`, `StorageUsageBootstrap` | ✅ live |
| `DOCUMENT_MAX_FILENAME_LENGTH` | STORAGE | yes | `DocumentServiceImpl` | ✅ live |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` / `SMTP_FROM_NAME` / `SMTP_FROM_ADDRESS` | EMAIL | no | `EmailServiceImpl.resolveMailSender/FromAddress/FromName` | ✅ live |

### FIRM (`firm_configs`)

| Key | Group | Read by (main code) | Verdict |
|---|---|---|---|
| `BRAND_COLOR_PRIMARY` | BRAND | `EmailServiceImpl`, `MeServiceImpl` | ✅ live |
| `BRAND_COLOR_SECONDARY` | BRAND | `MeServiceImpl` (portal theme only — **not** used in emails) | ✅ live |
| `EMAIL_FOOTER_TEXT` | BRAND | `EmailServiceImpl` | ✅ live |
| `EMAIL_SIGNATURE` | BRAND | **nothing** | ❌ **DEAD** (F-3) |
| `TIMEZONE` | BRAND | **nothing** | ❌ **DEAD** (F-4) |

---

## B. Findings (ranked)

### P0 — fix / verify before go-live

- **F-7 · Default Spring profile is `dev`, and `application-dev.yml` points at a *remote* Supabase DB.**
  `application.yml` ships `spring.profiles.active: dev` with hard-coded DB URL/credentials, JWT secret,
  Gmail app password and a registration secret. If the prod container starts **without**
  `SPRING_PROFILES_ACTIVE=prod`, it boots against the dev database with dev secrets. **Prod launch MUST set
  `SPRING_PROFILES_ACTIVE=prod` explicitly** and must not reuse the dev database.
- **F-8 · `APP_PRODUCTION` is seeded insert-if-missing from `app.production`.** A prod DB that was ever
  seeded under the dev profile already holds `APP_PRODUCTION='N'` and boot never corrects it. With prod
  mode off, `MFA_DEV_BYPASS=Y` would re-arm the `123456` bypass. **Verify the row directly (§C) and set it
  to `Y` before launch.**
- **F-9 · `config.encryption.key` has a committed fallback value** in `application.yml`
  (`rFBpF36+KeLlIKw+…`). If prod does not set `CONFIG_ENCRYPTION_KEY`, encrypted SMTP/registration secrets
  are protected by a public key. **Set `CONFIG_ENCRYPTION_KEY` (identical on every instance) via env.**

### P1 — behaviour to know / test

- **F-1 · `STORAGE_UPLOAD_EXPIRY_SECONDS` is dead.** The helper `SystemConfigService.storageUploadExpirySeconds()`
  has **no caller** — it is a leftover from the removed 3-step upload (now one multipart call). Changing it
  does nothing. Remove the key + helper, or leave it and know it is inert.
- **F-2 · `MFA_ENABLED=N` does not switch MFA off for existing users.** The login flow challenges on the
  **per-user** `user.mfaEnabled` flag, not the global key. The global key only affects *newly created*
  admins and the boot migration. To actually stop challenges for an existing admin you must clear the
  per-user flag (`POST /api/v1/modules/users/{userId}/reset-mfa`). **Test this explicitly (§D MFA).**

### P2 — cleanup / minor

- **F-3 · `EMAIL_SIGNATURE` (FIRM) is dead** — declared in the registry, no consumer anywhere. It appears
  in the admin Settings UI but does nothing.
- **F-4 · `TIMEZONE` (FIRM) is dead** — declared (`"Firm timezone"`), never read. Firm scheduling/emails
  ignore it.
- **F-5 · `PASSWORD_RESET_URL` is a dead lookup.** `EmailServiceImpl.sendPasswordResetLink` does
  `cfg.getOrDefault("PASSWORD_RESET_URL", …)`, but the key is **not in the registry**, so it can never be
  set (a PUT is rejected as an unknown key). The reset link always derives from `LOGIN_URL`. Remove the
  lookup or register the key.
- **F-6 · `MFA_REQUIRED_ROLES` has no server-side validation** (plain TEXT). A typo
  (`SUPER_ADMIN , FIRM_ADM` etc.) silently drops that role from enforcement. Guard with a checklist picker
  or accept the risk.
- **F-10 · Hard-coded tunables that bypass the config convention:** password-reset link validity is a
  literal `int validMinutes = 15` in `AuthServiceImpl.forgotPassword()` (and the change/password-reset token
  TTLs live in `JwtUtil`), so they are not runtime-adjustable like every other knob.
- **Note (by design):** `GET /api/v1/super-admin/config` returns **decrypted** `PASSWORD` values
  (`SMTP_PASSWORD`, `REGISTRATION_SECRET`) so an admin can inspect them. Ensure the endpoint stays behind
  `GLOBAL_CONFIG:VIEW` and is never logged to a shared sink.

---

## C. Pre-flight checks (run before the app starts in prod)

Run against the **prod** database:

```sql
-- 1. Production mode MUST be 'Y' (F-8)
SELECT config_key, config_value FROM system_config WHERE config_key = 'APP_PRODUCTION';

-- 2. Dev MFA bypass MUST be absent or 'N' (else '123456' works when APP_PRODUCTION is off)
SELECT config_key, config_value FROM system_config WHERE config_key = 'MFA_DEV_BYPASS';

-- 3. MFA policy sane
SELECT config_key, config_value FROM system_config
WHERE config_key IN ('MFA_ENABLED','MFA_REQUIRED_ROLES','LOGIN_MAX_ATTEMPTS','LOGIN_LOCK_MINUTES');

-- 4. No duplicate rows per key (duplicates silently win by latest updated_at)
SELECT config_key, COUNT(*) FROM system_config GROUP BY config_key HAVING COUNT(*) > 1;

-- 5. Who still has MFA on per-user (matters if MFA_ENABLED is being turned off — F-2)
SELECT username, user_type, mfa_enabled, mfa_verified FROM users WHERE mfa_enabled = true;
```

Environment / secrets present on the prod host:

- [ ] `SPRING_PROFILES_ACTIVE=prod` (**F-7**)
- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- [ ] `JWT_SECRET` (≥ 64 chars)
- [ ] `CONFIG_ENCRYPTION_KEY` (base64 32 bytes — **must match the key used to encrypt any existing rows**, **F-9**)
- [ ] `SUPER_ADMIN_REGISTRATION_SECRET`
- [ ] `CORS_ALLOWED_ORIGINS` (real frontend origin, not localhost)
- [ ] `MINIO_ENDPOINT` reachable **from the browser**; `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `MINIO_REGION`
- [ ] `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASS` (or set SMTP via the config API)
- [ ] `DDL_AUTO=update`, and every migration in `src/main/resources/db/migration/` applied (esp. `V2026_10_06__widen_mfa_secret.sql`, `V2026_10_07__add_firm_brand_columns.sql` — earlier unrun migrations caused real outages)

---

## D. Feature test checklist (production)

Legend: **SA** = Super Admin token, **FA** = Firm Admin token. `BASE` = prod API base URL.

### D1 · MFA — enable / disable / hard-coded `123456`

- [ ] **D1.1 — `123456` bypass is OFF in prod.** With `APP_PRODUCTION=Y`, `MFA_DEV_BYPASS` absent, log in as
      an MFA-enabled admin and submit TOTP `123456` → **rejected** (`Invalid authenticator code`).
- [ ] **D1.2 — `123456` stays off even if the bypass row is armed.** `PUT /super-admin/config {"MFA_DEV_BYPASS":"Y"}`
      while `APP_PRODUCTION=Y`, then retry `123456` → still **rejected** (prod mode is the second gate). Restore to `N`/delete after.
- [ ] **D1.3 — MFA enforced for a required role.** With `MFA_ENABLED=Y`, `MFA_REQUIRED_ROLES=SUPER_ADMIN,FIRM_ADMIN`,
      register a fresh SA / create a fresh firm → that admin's first login returns `MFA_SETUP_REQUIRED`
      (QR + secret), then `/auth/mfa/setup/confirm` with a real code issues tokens.
- [ ] **D1.4 — `MFA_ENABLED=N` stops *new* admins getting MFA.** Set `MFA_ENABLED=N`, create a new firm admin
      → the new admin logs in **without** an MFA step.
- [ ] **D1.5 — F-2: `MFA_ENABLED=N` does NOT disable an existing MFA user.** An admin who already completed
      MFA setup still gets the MFA challenge. Only `POST /api/v1/modules/users/{userId}/reset-mfa` clears it.
      Decide if that is the intended prod behaviour.
- [ ] **D1.6 — Validation.** `PUT /super-admin/config {"MFA_ENABLED":"MAYBE"}` → **400**, no DB change.
- [ ] **D1.7 — MFA brute-force throttle.** Enter wrong TOTP `LOGIN_MAX_ATTEMPTS` times → account locks for
      `LOGIN_LOCK_MINUTES`.

### D2 · Login lockout  ← **RE-VERIFIED LIVE 2026-10-12 (NEW-1, NEW-2 fixed)**

- [x] **D2.1** 5 consecutive wrong passwords on `/api/v1/auth/login` → `login_attempts` counts 1..5 and
      `locked_until` stamps on the 5th; the **correct** password is then refused with
      *“Account locked. Try again in 29 minute(s).”* and the counter does not move.
      *Live 2026-10-12, after the fix: attempts 1→2→3→4→5, `locked_until=12:23:51`, correct password → 401
      locked. Before the fix: 5 failures left `login_attempts=0`.*
- [x] **D2.1b** Same on `/api/v1/super-admin/login`: 5 failures → `login_attempts=5` **and** `locked_until`
      set; the correct password is refused with *“Account locked. Please try again later.”*
      *Before the fix: attempts incremented but `locked_until` stayed `null`.*
- [ ] **D2.2** `PUT {"LOGIN_LOCK_MINUTES":"1"}` changes the stated lock duration on the next lock (live, no restart).
- [ ] **D2.3** A successful self-service password reset clears the lock (`login_attempts=0`, `locked_until=null`).
- [x] **D2.4 (new)** A locked account can be released **through the API, without touching the database**:
      `POST /api/v1/super-admin/users/{userId}/unlock` (super admin, any firm) and
      `POST /api/v1/modules/users/{userId}/unlock` (firm admin, own firm only) → 200, then
      `login_attempts=0`, `locked_until=null`, and the user signs in again. Both audited as `USER_UNBLOCKED`.
      *Live 2026-10-12: both endpoints returned 200 and the released employee logged in successfully.*
      Note: a locked **sole** super admin can only be released by an already-issued SA token or by
      waiting out `LOGIN_LOCK_MINUTES` — the login that would mint the token is the locked one.
      Full recovery matrix — lockout vs blocked vs deactivated vs suspended firm, API first and SQL as
      the last resort: **`docs/account-recovery-runbook.md`**.
- [ ] **D2.5** Break-glass drill: lock the super admin out on purpose, then recover it **without** the
      unlock endpoint (wait out the lock, or the `SYSTEM`-firm self-service reset) and record which path
      works in prod with real SMTP. A locked, token-less, sole SA has no in-app path today.

### D3 · Access-token lifetime

- [ ] **D3.1** `PUT {"ACCESS_TOKEN_EXPIRY_MINUTES":"1"}` → new login's access token expires in ~1 min
      (decode and check `exp`). Delete the key → falls back to `jwt.access-expiry`.

### D4 · Super-admin registration gate

- [ ] **D4.1** With `REGISTRATION_SECRET` set, `POST /super-admin/register` with the wrong secret → rejected.
- [ ] **D4.2** With a second SA already existing, registration is refused (singleton guard).
- [ ] **D4.3** `REGISTRATION_SECRET` is settable **once**; a second PUT with a different value → 400 and DELETE → 400.

### D5 · Trial & notification settings

- [ ] **D5.1** `PUT {"TRIAL_DEFAULT_DAYS":"30"}` → a new firm created without explicit days gets `30` days / `isTrial=true`.
- [ ] **D5.2** `PUT {"TRIAL_WARNING_DAYS":"2"}` → the trial warning fires when ≤ 2 days remain.
- [ ] **D5.3** `PUT {"NOTIFICATION_MAX_ATTEMPTS":"1"}` → a failing notification is marked `DEAD` after 1 retry.

### D6 · Storage & documents

- [ ] **D6.1** `PUT {"STORAGE_MAX_FILE_SIZE_BYTES":"1048576"}` → a 2 MB upload is rejected; a 0.5 MB upload succeeds.
- [ ] **D6.2** `PUT {"STORAGE_DOWNLOAD_EXPIRY_SECONDS":"60"}` → a `download-url` is valid for ~60 s.
- [ ] **D6.3** `PUT {"DOCUMENT_MAX_FILENAME_LENGTH":"20"}` → a long filename is truncated to 20 chars in the object key.
- [ ] **D6.4** Per-firm quota: SA sets `GET/PUT /super-admin/firms/{firmId}/storage-quota`; `0` = unlimited;
      a fresh firm's first write allocates `STORAGE_DEFAULT_QUOTA_BYTES`.
- [ ] **D6.5 — F-1** Changing `STORAGE_UPLOAD_EXPIRY_SECONDS` has **no observable effect** (documented dead key).
- [ ] **D6.6** Real MinIO round-trip: upload → object ACTIVE → presigned download returns byte-identical content.

### D7 · Email / SMTP

- [ ] **D7.1** Set SMTP via `PUT /super-admin/config {"SMTP_HOST":…,"SMTP_PORT":…,"SMTP_USERNAME":…,"SMTP_PASSWORD":…,"SMTP_FROM_ADDRESS":…}`
      → a welcome/employee-create email is delivered, `From` matches `SMTP_FROM_ADDRESS`/`FROM_NAME`.
- [ ] **D7.2** `SMTP_PASSWORD` is stored **encrypted** (`SELECT encrypted, config_value FROM system_config WHERE config_key='SMTP_PASSWORD'`
      → `encrypted=true`, value is base64 ciphertext). `GET /config` shows the decrypted value.
- [ ] **D7.3** Update `LOGIN_URL` / `CLIENT_PORTAL_URL` → the "Sign in" / portal links in new emails use the new value, and the links must be `http(s)` (bad value → 400).
- [ ] **D7.4** Firm SMTP (per-firm) overrides GLOBAL SMTP for that firm.

### D8 · Brand / firm config

- [ ] **D8.1** FA `PUT /firm/config {"BRAND_COLOR_PRIMARY":"#0D47A1"}` → emails render the new primary colour.
- [ ] **D8.2** `BRAND_COLOR_SECONDARY` reflects in `GET /me` portal theme (`isPersonalColor=true` when set).
- [ ] **D8.3** `EMAIL_FOOTER_TEXT` appears in the email footer.
- [ ] **D8.4 — F-3/F-4** `EMAIL_SIGNATURE` and `TIMEZONE` can be saved but have **no effect** (documented dead keys) — do not advertise them as working settings.
- [ ] **D8.5** A FA cannot write GLOBAL keys (`PUT /firm/config {"LOGIN_URL": …}` → 400) and cannot read another firm's config.

### D9 · Config API integrity & security

- [ ] **D9.1** Non-SA token on `/super-admin/config` → **403**; SA token on `/firm/config` → **403**.
- [ ] **D9.2** Unknown key PUT → **400** (`Unknown config key … for scope GLOBAL`).
- [ ] **D9.3** `PUT {"LOGIN_URL":"javascript:alert(1)"}` → **400** (URL validation).
- [ ] **D9.4** Settings are **live** — a change takes effect on the next request with **no restart**.
- [ ] **D9.5** `DELETE /super-admin/config/{key}` removes a value → code default applies; a locked key with a value → 400.
- [ ] **D9.6** Config changes are audited (`CONFIG_UPDATED` / `CONFIG_DELETED` rows exist).

### D10 · General go-live smoke

- [ ] **D10.1** App boots on the **prod** profile and connects to the **prod** DB (log shows `Tarikh-HikariPool`, not the Supabase dev pooler).
- [ ] **D10.2** Full lifecycle over real HTTP: SA register → MFA → create firm + firm admin → forced password rotation → employee create/login → client portal.
- [ ] **D10.3** `GET /api/v1/super-admin/config` returns all expected rows with correct `inputType`/`allowedValues`.
- [ ] **D10.4** No `5xx` on any endpoint exercised above; logs free of decryption-failure warnings.

---

## E. Sign-off

| Area | Owner | Result | Date |
|---|---|---|---|
| Pre-flight SQL + env (C) | | | |
| MFA (D1) | | | |
| Lockout / token (D2–D3) | | | |
| Registration / trial / notification (D4–D5) | | | |
| Storage (D6) | | | |
| Email / SMTP (D7) | | | |
| Brand / firm config (D8) | | | |
| Config API integrity (D9) | | | |
| Smoke (D10) | | | |

**Go-live rule:** every D-block ticked, all P0 findings (F-7, F-8, F-9) verified, and no unresolved `5xx`.
