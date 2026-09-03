# DB-Driven System Config / Settings Registry — Design

Date: 2026-09-03
Branch: devG

## 1. Goal

Make `system_config` the place where all runtime-tunable behavior of the platform lives, so
that small operational changes — enable/disable MFA policy, SMTP settings, app branding —
are made by a Super Admin through the existing config API **without a backend rebuild or
redeploy**.

The design adapts the proven mBank `SYSTEM_CONFIG`/`SYSTEM_CONFIG_MASTER` pattern (grouped,
typed, seeded-once config registry with `INPUT_TYPE`, `ALLOWED_VALUES`, `DESCRIPTION`,
`ACTIVE`, `ALLOW_EDIT`) onto this project's existing `SystemConfig` model, which already has
two things the pattern lacks: **scope separation (GLOBAL vs FIRM)** and **AES-256 encryption
of secret values**.

## 2. Current State (verified 2026-09-03)

- `system_config` table: `scope` (GLOBAL/FIRM), `firm_id`, `config_key`, `config_value`,
  `encrypted` flag, `description`, timestamps. Unique `(scope, firm_id, config_key)`.
- `SystemConfigService`: get/set/delete per scope with runtime reads, `.orElse(default)`
  pattern, `getEffectiveConfig(firmId)` merges FIRM over GLOBAL (internal, decrypted).
  Single hardcoded sensitive key: `SMTP_PASSWORD`.
- Super admin API: `GET/PUT /api/v1/super-admin/config`, `GET/PUT /firms/{firmId}/config`,
  `DELETE /config/{key}` — flat `Map<String,String>` bodies.
- Firm admin API: `GET/PUT /api/v1/firm/config` (FIRM scope).
- Email already reads `SMTP_*`, `APP_NAME`, `BRAND_*`, `EMAIL_*` from config at send-time
  (fallback chain: firm email config → global DB → Spring auto-config).
- Enforcement that is still compile-time:
  - `FirmServiceImpl.createFirm()` — new firm admin always `.mfaEnabled(true)` (line ~95)
  - `SuperAdminServiceImpl.registerSuperAdmin()` — new SUPER_ADMIN always `setMfaEnabled(true)` (line ~115)
  - `DataInitializer.migrateExistingSuperAdminMfa()` — forces MFA on all existing SUPER_ADMINs at boot
  - `EmployeeServiceImpl.mfaRequiredFor()` — **dead code** (defined line 408, never called); leftover from an earlier role-required rule
  - `SuperAdminServiceImpl` — registration secret via `@Value("${super-admin.registration-secret:}")`
  - `JwtUtil`, `TotpUtil` — durations hardcoded/`@Value` (explicitly **out of scope** this pass)

## 3. Design Decisions (agreed)

1. **Add a group column to the existing table** — no separate master table. Group is a plain
   string (`SECURITY`, `EMAIL`, `APP`, `BRAND`). Scope already separates global/firm.
2. **Seed a key registry once, insert-if-missing, never overwrite** — DataInitializer inserts
   default rows only when the key is absent; admin edits are never clobbered on restart.
3. **Mask `PASSWORD`-type values on admin GET responses** — internal reads (email, branding)
   keep decrypted values; the admin UI only ever sees "set/not set" state.
4. **Registration secret: DB override with env/yml fallback** — required because no Super
   Admin exists yet at first boot to write it; resolution is `DB value if present, else @Value`.
5. **MFA stays per-user** — `user.mfaEnabled` remains authoritative at login; DB policy keys
   only control *who is required/forced* to have MFA. Once on for a user, always enforced.
6. No auto-seed of *runtime values that admins own* beyond registry defaults; admin changes
   are manual via the existing PUT API. Deleting a key (`DELETE /config/{key}`) reverts to
   the code default — this is the "reset" story.

## 4. Schema — Flyway migration `V2026_09_03__system_config_metadata.sql`

Add columns to `system_config`:

| Column | Type | Notes |
|---|---|---|
| `config_group` | `VARCHAR(50)` | `APP`, `SECURITY`, `EMAIL`, `BRAND`, … nullable → default `APP` |
| `input_type` | `VARCHAR(20)` NOT NULL default `'TEXT'` | `TEXT`, `NUMBER`, `RADIO`, `DROPDOWN`, `PASSWORD`, `URL` |
| `allowed_values` | `TEXT` | comma-separated options for RADIO/DROPDOWN; null otherwise |
| `active` | `BOOLEAN DEFAULT TRUE` | disabled rows are ignored by enforcement reads and hidden from UI |
| `allow_edit` | `BOOLEAN DEFAULT TRUE` | `false` = value cannot be changed via PUT (registry-locked keys) |

Backfill in the same migration (update, not insert):

- `SMTP_PASSWORD` → `input_type='PASSWORD'`, `config_group='EMAIL'`
- `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_FROM_NAME`, `SMTP_FROM_ADDRESS` → `'TEXT'`, `EMAIL`
- `SMTP_PORT` → `'NUMBER'`, `EMAIL`
- `APP_NAME`, `APP_PRODUCTION` → `TEXT`/`RADIO(Y,N)`, `APP`
- `BRAND_COLOR_PRIMARY`, `BRAND_COLOR_SECONDARY`, `EMAIL_FOOTER_TEXT`, `EMAIL_SIGNATURE`,
  `TIMEZONE` → `TEXT`, `BRAND`
- any row left NULL → `input_type='TEXT'`, `config_group='APP'`

Uniqueness stays `(scope, firm_id, config_key)`.

## 5. Seeded Key Registry (DataInitializer, insert-if-missing)

`seedSystemConfigDefaults()` runs in `DataInitializer.run()` (after system roles; independent
of tenant seeding). For each entry: if `(GLOBAL, null, key)` already exists → skip; else insert
with the default value. Never updates existing rows.

| config_group | config_key | input_type | allowed_values | default | allow_edit |
|---|---|---|---|---|---|
| APP | `APP_NAME` | TEXT | – | `NepalCRM` | true |
| APP | `APP_PRODUCTION` | RADIO | `Y,N` | `N` | true |
| SECURITY | `MFA_ENABLED` | RADIO | `Y,N` | `Y` | true |
| SECURITY | `MFA_REQUIRED_ROLES` | TEXT | – | `SUPER_ADMIN,FIRM_ADMIN` | true |
| SECURITY | `REGISTRATION_SECRET` | PASSWORD | – | *(empty)* | false |

`REGISTRATION_SECRET`:
- `allow_edit=false` → once set via PUT it cannot be overwritten or deleted through the API
  (only a DB operator can rotate it). Not seeded with a value.
- Registration secret resolution (SuperAdminServiceImpl): DB value if non-empty, else
  `@Value` yml fallback. Empty in both → registration disabled (current behavior).
- Not returned on GET even masked — the UI shows "set/not set" only.

MFA policy semantics:
- `MFA_ENABLED=Y`: current forcing behavior applies to roles in `MFA_REQUIRED_ROLES`
  (super admin sweep at boot, firm-admin creation, role-required checks).
- `MFA_ENABLED=N`: no role is *forced*; users who already enabled MFA keep it and are still
  challenged at login (per-user rule unchanged).
- Code fallback when key absent: `MFA_ENABLED → Y`, `MFA_REQUIRED_ROLES → SUPER_ADMIN,FIRM_ADMIN`
  (identical to today's behavior, so an empty DB is never less secure).

FIRM scope: no seed (firms brand themselves through the existing firm-config UI).

## 6. Service changes — `SystemConfigService`

- New key constants: `KEY_MFA_ENABLED`, `KEY_MFA_REQUIRED_ROLES`, `KEY_REGISTRATION_SECRET`.
- `SENSITIVE_KEYS` set replaced by: **sensitive ⇔ `input_type == PASSWORD`** (legacy
  `SMTP_PASSWORD` row gets backfilled to `PASSWORD` by the migration, so no special-casing).
- `setValue(...)` gains validation: on write, validate against `input_type`/`allowed_values`
  when the row already carries metadata — RADIO/DROPDOWN value must be in `allowed_values`
  (`Y`/`N` case-insensitive), NUMBER must parse as `Long`/`Integer`. Reject with 400.
- `allow_edit=false` rows: PUT of the same value is a no-op success; a *different* value is
  rejected; DELETE is rejected.
- New admin-facing read methods returning typed metadata — `getGlobalSettings()` and
  `getFirmSettings(firmId)` — each returning `List<SystemConfigSettingView>` with fields:
  `configKey`, `configGroup`, `value` (masked for PASSWORD rows: `"••••"` when set, `""` when
  empty), `inputType`, `allowedValues`, `description`, `active`, `allowEdit`, `sensitive`.
  **Internal decrypted reads are untouched**: `getEffectiveConfig()`, `getGlobal()`,
  `getFirm()` keep returning plaintext (email/branding/Me depend on them).
- Enforcement helpers: `isMfaEnabled()` (Y/N, default Y) and
  `mfaRequiredRoleCodes()` (CSV split, default `SUPER_ADMIN,FIRM_ADMIN`).

## 7. Enforcement rewiring (reads at decision-time, defaults safe)

| Location | Today | After |
|---|---|---|
| `FirmServiceImpl.createFirm()` | `.mfaEnabled(true)` always | `mfaEnabled = isMfaEnabled() && requiredRoles.contains("FIRM_ADMIN")` |
| `SuperAdminServiceImpl.registerSuperAdmin()` | `setMfaEnabled(true)` always | `mfaEnabled = isMfaEnabled() && requiredRoles.contains("SUPER_ADMIN")` |
| `DataInitializer.migrateExistingSuperAdminMfa()` | force MFA on all SUPER_ADMINs | only when `isMfaEnabled()` and `SUPER_ADMIN ∈ requiredRoles` (absent key → default on) |
| `EmployeeServiceImpl.mfaRequiredFor(role)` | dead code | repurpose: read `isMfaEnabled() && requiredRoles.contains(roleCode)` so future role-required enforcement uses the same policy (or delete) |
| `SuperAdminServiceImpl` registration secret | `@Value` secret | DB `REGISTRATION_SECRET` if set, else `@Value` fallback |
| `AuthServiceImpl` / `SuperAdminServiceImpl` login | per-user `mfaEnabled` challenge | unchanged (user-level stays authoritative) |

`SystemConfigService` is a repository-backed bean — reading it from `DataInitializer` and the
service layer creates no cycle (verify at implementation; if needed, read via
`ApplicationContext` in DataInitializer as last resort).

## 8. API surface

- `GET /api/v1/super-admin/config` → now returns the typed setting views (grouped in response
  by `configGroup`); existing clients of the flat map get a superset-shaped payload (flat map
  no longer returned — breaking change, acceptable pre-1.0; document in Swagger).
- `PUT /api/v1/super-admin/config` → unchanged shape `Map<String,String>`; server validates
  per metadata rules above; audit `CONFIG_UPDATED` as today.
- `DELETE /api/v1/super-admin/config/{key}` → rejects for `allow_edit=false`.
- `GET/PUT /api/v1/firm/config` (firm admin) and `GET /api/v1/super-admin/firms/{firmId}/config`
  → same typed views for FIRM scope. Firm admins see masked values for any PASSWORD row.
- Enforcement points read config through the service, never through the controllers.

Frontend rendering contract (for the future SETTINGS UI):
- SETTINGS → **System Config** subpage = GLOBAL rows grouped `APP` + `SECURITY`
- SETTINGS → **Email Config** subpage = GLOBAL rows grouped `EMAIL`
- Firm branding page = FIRM rows grouped `BRAND`
- A row renders as: label (`description`), control by `inputType`, options from
  `allowedValues`, disabled when `!allowEdit || !active || sensitive`.

## 9. Edge cases

- **Empty DB**: defaults in code keep today's behavior (MFA forced for the two roles,
  email falls back to Spring auto-config, registration closed).
- **Seed vs manual edits**: insert-if-missing only; restarts never overwrite admin values.
- **Deleting a seeded key**: allowed for `allow_edit=true` rows; enforcement falls back to
  code default (documented as "reset to default"). Restart re-seeds only if the row is still
  absent.
- **Case handling**: `Y/N` values accepted case-insensitively; stored uppercase.
- **Encryption**: PASSWORD values encrypted at rest (existing `encrypted` flag mechanism).
  Migration backfills `SMTP_PASSWORD` correctly (already encrypted — only metadata columns
  change).
- **Bootstrap**: `REGISTRATION_SECRET` never DB-only; env fallback preserved.

## 10. Out of scope (this pass)

- JWT access/refresh/MFA-token durations, TOTP time-step/clock-drift (`JwtUtil`, `TotpUtil`)
- Rate limiting, schedulers, SMS/email retry runners (future keys in same registry)
- Frontend code (no frontend in this repo) — API contract above is the deliverable
- Per-firm "which roles require MFA" (firm-scoped policy) — global policy only; can be added
  later with the same FIRM-scope mechanism

## 11. Testing

- Migration/backfill: existing `SMTP_PASSWORD` row ends up `PASSWORD`/`EMAIL` (test via
  schema or service read of metadata).
- Seed idempotency: run initializer twice → no duplicate keys, no overwrite of a preset value.
- PUT validation: RADIO with value outside `Y,N` → 400; NUMBER with non-numeric → 400;
  `allow_edit=false` change → rejected; delete of locked key → rejected.
- Masking: `GET /config` never returns plaintext for PASSWORD rows (`••••`), while
  `getEffectiveConfig()` internal path still returns decrypted SMTP password (email tests
  keep passing).
- MFA policy: `MFA_ENABLED=N` → employee/firm-admin creation does not force MFA; `=Y` with
  default roles → forces FIRM_ADMIN (existing behavior preserved); role CSV parse handles
  whitespace/empty.
- Registration secret: DB value wins over yml fallback; both empty → disabled.
- Full suite: all existing 230 tests remain green.

## 12. Files touched (anticipated)

- `src/main/resources/db/migration/V2026_09_03__system_config_metadata.sql` (new)
- `src/main/java/com/lawfirm/erp/common/entity/SystemConfig.java` (new columns)
- `src/main/java/com/lawfirm/erp/common/service/SystemConfigService.java` (constants, helpers,
  validation, admin views)
- `src/main/java/com/lawfirm/erp/common/dto/.../SystemConfigSettingView.java` (new DTO)
- `src/main/java/com/lawfirm/erp/config/DataInitializer.java` (registry seed + MFA sweep)
- `src/main/java/com/lawfirm/erp/firm/service/EmployeeServiceImpl.java` (repurpose/remove dead `mfaRequiredFor`)
- `src/main/java/com/lawfirm/erp/firm/service/FirmServiceImpl.java`
- `src/main/java/com/lawfirm/erp/superadmin/service/SuperAdminServiceImpl.java`
- `src/main/java/com/lawfirm/erp/superadmin/controller/SuperAdminConfigController.java`
- `src/main/java/com/lawfirm/erp/firm/controller/FirmConfigController.java`
- New tests: seed idempotency, PUT validation, masking, MFA policy, registration secret
