# System Config — DB-driven settings (how it works + curl testing)

> The main idea: **runtime knobs live in the `system_config` DB table**. A Super Admin
> changes them with `PUT /api/v1/super-admin/config` and the new value is used on the
> next request. No backend rebuild, no restart.
>
> Companion file: `postman/SystemConfig.postman_collection.json` (import into Postman).

---

## 1. How the pieces fit together

| Piece | Where | Role |
|---|---|---|
| `system_config` table | DB | Key-value store, GLOBAL or FIRM scope |
| Metadata columns | `config_group`, `input_type`, `allowed_values`, `active`, `allow_edit`, `description` | Tell the SETTINGS UI how to render each key and let the server validate PUTs |
| `SystemConfigService` | Backend | Reads a key **every time** it is needed (`getGlobal`), so a DB change is live immediately |
| Registry (`REGISTRY`) | `SystemConfigService` | One place defining every known key: group, input type, allowed values, default |
| Boot seed | `DataInitializer` | Inserts defaults **only if missing** — never overwrites an admin's edit |
| Enforcement points | `FirmServiceImpl`, `SuperAdminServiceImpl`, `DataInitializer` | Ask the DB whether MFA is required instead of using hardcoded rules |

The boot-seeded registry (GLOBAL, insert-if-missing):

| Key | Group | Type | Default |
|---|---|---|---|
| `APP_NAME` | APP | TEXT | `NepalCRM` |
| `APP_PRODUCTION` | APP | RADIO `Y,N` | `N` |
| `MFA_ENABLED` | SECURITY | RADIO `Y,N` | `Y` |
| `MFA_REQUIRED_ROLES` | SECURITY | TEXT | `SUPER_ADMIN,FIRM_ADMIN` |
| `REGISTRATION_SECRET` | SECURITY | PASSWORD (locked after first set) | empty |

`SMTP_*` (EMAIL group) and firm `BRAND_*` (BRAND group) keys are **not** seeded —
they are created when an admin first PUTs them, and they keep their metadata from the
registry (that is why `SMTP_PASSWORD` is stored encrypted even on first creation).

---

## 2. Where the password/secret encryption actually happens

### Storage ("at rest") — AES-256-GCM, key NOT in the DB

1. `PUT SMTP_PASSWORD` (or any PASSWORD-type key) → `SystemConfigService.setValue`
   sees `input_type = PASSWORD` → calls `configEncryptionUtil.encrypt(plaintext)`.
2. `ConfigEncryptionUtil` (AES/GCM/NoPadding):
   - generates a **fresh random 12-byte IV** for every value (`SecureRandom`),
   - encrypts with the **256-bit AES key** from `config.encryption.key`
     (Base64-encoded 32-byte value in `application*.yml` / env — never in the DB),
   - stores `Base64( IV(12 bytes) || ciphertext )` in `config_value` and sets `encrypted=true`.
3. Result: a DB dump shows ciphertext. Without the key it cannot be read.

### Reading ("decoding") — same key, server-side only

4. When email is sent, `EmailServiceImpl` calls `systemConfigService.getGlobal(SMTP_PASSWORD)`
   → the row is found → `decryptIfNeeded` Base64-decodes, splits the IV off the front,
   and runs AES-GCM decrypt with the same key → the real password is handed to the mail sender.
5. Admin `GET /config` uses the same decrypt path, so admins see the **real value**
   (no masking — you decided admins own these values and may inspect them).
6. If the AES key ever changes or is lost, decryption fails; the service logs
   *"Failed to decrypt … key has changed"* and returns null/empty for that key rather than crashing.

> Symmetric encryption — any holder of the key can decrypt; that is exactly the point,
> the backend must read the value to send mail or show it to the admin. Encryption-at-rest
> protects against **DB leakage**, not against the key holder. Keep `config.encryption.key`
> in the environment, same value on every instance (horizontal scaling).

Generate a key: `openssl rand -base64 32` → put it in env as `CONFIG_ENCRYPTION_KEY`.

---

## 3. Curl cheat sheet

Replace `TOKEN` once per session (login response stores `data.accessToken`):

```bash
BASE=http://localhost:8080

# 1) One-time: register the first super admin (public endpoint).
#    secretKey must equal REGISTRATION_SECRET (empty by default -> falls back to the
#    super-admin.registration-secret value in application-dev.yml).
curl -s -X POST "$BASE/api/v1/super-admin/register" \
  -H "Content-Type: application/json" \
  -d '{"data":{"fullName":"Platform Admin","email":"admin@system.com","mobileNo":"9800000000","username":"admin","password":"Admin@123","secretKey":"cdae9adca9e7cb0ab2afb82e2a2a74b7"}}'

# 2) Login -> grab the JWT (if MFA is enabled you get MFA_SETUP_REQUIRED / MFA_REQUIRED
#    instead of an accessToken — do the /auth/mfa/setup/confirm or /auth/mfa/validate step).
LOGIN=$(curl -s -X POST "$BASE/api/v1/super-admin/login" \
  -H "Content-Type: application/json" \
  -d '{"data":{"username":"admin","password":"Admin@123","totpCode":""}}')
echo "$LOGIN" | jq .data.status
TOKEN=$(echo "$LOGIN" | jq -r .data.accessToken)

# 3) Read every GLOBAL setting (typed rows incl. decrypted PASSWORD values)
curl -s "$BASE/api/v1/super-admin/config" -H "Authorization: Bearer $TOKEN" | jq .

# 4) Flip MFA enforcement OFF (RADIO validation: anything outside Y,N => 400)
curl -s -X PUT "$BASE/api/v1/super-admin/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"MFA_ENABLED":"N"}' | jq .

# 5) Change which roles are REQUIRED to have MFA
curl -s -X PUT "$BASE/api/v1/super-admin/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"MFA_REQUIRED_ROLES":"SUPER_ADMIN,FIRM_ADMIN"}' | jq .

# 6) Configure SMTP — SMTP_PASSWORD is stored AES-256 encrypted
curl -s -X PUT "$BASE/api/v1/super-admin/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"SMTP_HOST":"smtp.gmail.com","SMTP_PORT":"587","SMTP_USERNAME":"noreply@lawfirm.com","SMTP_PASSWORD":"app-pass","SMTP_FROM_NAME":"My Law Firm","SMTP_FROM_ADDRESS":"noreply@lawfirm.com"}' | jq .

# 7) Bad value is rejected before touching the DB (proves validation)
curl -s -X PUT "$BASE/api/v1/super-admin/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"MFA_ENABLED":"MAYBE"}' | jq .

# 8) Reset a key to its default (delete -> code default; re-seeded only if still absent)
curl -s -X DELETE "$BASE/api/v1/super-admin/config/APP_NAME" -H "Authorization: Bearer $TOKEN" | jq .

# 9) Per-firm config, as Super Admin
curl -s "$BASE/api/v1/super-admin/firms/<FIRM_UUID>/config" -H "Authorization: Bearer $TOKEN" | jq .

curl -s -X PUT "$BASE/api/v1/super-admin/firms/<FIRM_UUID>/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"BRAND_COLOR_PRIMARY":"#1A237E"}' | jq .
```

Firm-admin scope (their own firm only, `GET/PUT /api/v1/firm/config`) uses a firm-admin
token from `POST /api/v1/auth/login` (`data.lawFirmCode` + `data.totpCode` optional).

---

## 4. Example GET response shape

`GET /api/v1/super-admin/config` now returns typed rows (was a flat map):

```json
{
  "success": true,
  "message": "Global config fetched",
  "responseCode": 0,
  "data": [
    {
      "configKey": "APP_NAME",
      "configGroup": "APP",
      "value": "NepalCRM",
      "inputType": "TEXT",
      "allowedValues": null,
      "description": "Platform display name",
      "active": true,
      "allowEdit": true,
      "sensitive": false
    },
    {
      "configKey": "SMTP_PASSWORD",
      "configGroup": "EMAIL",
      "value": "app-pass",
      "inputType": "PASSWORD",
      "allowedValues": null,
      "description": "SMTP password",
      "active": true,
      "allowEdit": true,
      "sensitive": true
    }
  ]
}
```

A SETTINGS UI renders one row per item: label = `description`, control = `inputType`
(RADIO/DROPDOWN get options from `allowedValues`), disabled when `!allowEdit`.

---

## 5. Locked keys

`REGISTRATION_SECRET` is seeded with `allow_edit=false`:
- settable once (from empty), afterwards any PUT with a different value → 400
- DELETE after it has a value → 400
- rotation is a deliberate DB-operator action

Everything else is `allow_edit=true`: change it, delete it to reset — that is the product.
