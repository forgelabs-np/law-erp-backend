# Configuration System — Setup & Integration Guide

## Overview

The ERP uses a **two-table config system** for all platform and per-firm settings:

| Table | Scope | Entity | Service | Purpose |
|-------|-------|--------|---------|---------|
| `system_config` | GLOBAL | `SystemConfig` | `SystemConfigService` | Platform-wide settings (SMTP, MFA, trial, app name, production flag) |
| `firm_configs` | FIRM | `FirmConfig` | `FirmConfigService` | Per-firm settings (brand colors, email footer, timezone) |

**Why two tables?**
- `system_config` has `UNIQUE(config_key)` — one row per key, no `firm_id`.
- `firm_configs` has `UNIQUE(firm_id, config_key)` — one row per firm per key, `firm_id NOT NULL` with FK cascade.
- Firm admins cannot write GLOBAL keys. GLOBAL keys cannot accidentally sit next to 30 firms' overrides.
- `getEffectiveConfig(firmId)` merges GLOBAL + FIRM, with firm values winning on conflict.

All keys are declared once in `ConfigKeyRegistry` — the single source of truth for key names, types, validation, defaults, and which scope owns them.

---

## Architecture

### ConfigKeyRegistry (`common/config/ConfigKeyRegistry.java`)

Declares every key as a `SettingDef`:

```java
public static final List<SettingDef> GLOBAL = List.of(
    SettingDef.def("SMTP_HOST", "EMAIL", "TEXT", null, null, true, "SMTP host", false),
    SettingDef.def("APP_PRODUCTION", "APP", "RADIO", "Y,N", "N", true, "Production mode", true),
    // ... 18 keys total
);

public static final List<SettingDef> FIRM = List.of(
    SettingDef.def("BRAND_COLOR_PRIMARY", "BRAND", "TEXT", null, null, true, "Primary brand color (hex)", false),
    // ... 5 keys total
);
```

Each `SettingDef` carries:
- `key` — the config key name (e.g. `SMTP_HOST`)
- `group` — UI grouping (APP, SECURITY, EMAIL, TRIAL, NOTIFICATION, BRAND)
- `inputType` — UI control + validation: `TEXT`, `NUMBER`, `RADIO`, `URL`, `PASSWORD`
- `allowedValues` — comma-separated for RADIO/DROPDOWN (e.g. `"Y,N"`)
- `defaultValue` — seeded on first boot if `seed=true`
- `allowEdit` — if `false`, value is locked after first set (e.g. `REGISTRATION_SECRET`)
- `seed` — if `true`, `seedGlobalDefaults()` inserts a row on startup; `false` means the key only appears when someone sets it

### Data Flow

```
┌─────────────────────────────────────────────────────────┐
│  ConfigKeyRegistry (compile-time declarations)          │
│  GLOBAL list ──→ SystemConfigService                    │
│  FIRM list   ──→ FirmConfigService                      │
└──────────────┬──────────────────────────┬───────────────┘
               │                          │
    ┌──────────▼──────────┐    ┌──────────▼──────────┐
    │   system_config     │    │    firm_configs      │
    │ UNIQUE(config_key)  │    │ UNIQUE(firm_id,key)  │
    │ firm_id = NULL      │    │ firm_id NOT NULL     │
    └──────────┬──────────┘    └──────────┬──────────┘
               │                          │
               └──────────┬───────────────┘
                          │
              getEffectiveConfig(firmId)
              = GLOBAL.putAll(FIRM)  ← firm wins
                          │
                          ▼
              Consumers: EmailServiceImpl, TotpUtil,
              MeServiceImpl, AuthServiceImpl, etc.
```

---

## All Configuration Keys

### GLOBAL Keys (system_config)

| Key | Group | Type | Default | Seed | Description |
|-----|-------|------|---------|------|-------------|
| `APP_NAME` | APP | TEXT | `NepalCRM` | ✅ | Platform display name |
| `APP_PRODUCTION` | APP | RADIO (Y,N) | `N` | ✅ | Production mode — derived from `app.production` yml on first seed |
| `LOGIN_URL` | APP | URL | `https://app.nepalcrm.com/login` | ✅ | Login link in outgoing emails |
| `CLIENT_PORTAL_URL` | APP | URL | `https://app.nepalcrm.com/portal` | ✅ | Client portal link in emails |
| `MFA_ENABLED` | SECURITY | RADIO (Y,N) | `Y` | ✅ | Enable/disable MFA enforcement |
| `MFA_REQUIRED_ROLES` | SECURITY | TEXT | `SUPER_ADMIN,FIRM_ADMIN` | ✅ | Comma-separated roles requiring MFA |
| `REGISTRATION_SECRET` | SECURITY | PASSWORD | `""` | ✅ | Secret for first SA registration. `allowEdit=false` — locked after first set |
| `LOGIN_MAX_ATTEMPTS` | SECURITY | NUMBER | `5` | ✅ | Failed logins before lockout |
| `LOGIN_LOCK_MINUTES` | SECURITY | NUMBER | `30` | ✅ | Lockout duration in minutes |
| `TRIAL_DEFAULT_DAYS` | TRIAL | NUMBER | `14` | ✅ | Default trial length for new firms |
| `TRIAL_WARNING_DAYS` | TRIAL | NUMBER | `3` | ✅ | Days before expiry to send warning |
| `NOTIFICATION_MAX_ATTEMPTS` | NOTIFICATION | NUMBER | `3` | ✅ | Delivery attempts before DEAD |
| `SMTP_HOST` | EMAIL | TEXT | `null` | ❌ | SMTP host (metadata only, no seed) |
| `SMTP_PORT` | EMAIL | NUMBER | `null` | ❌ | SMTP port |
| `SMTP_USERNAME` | EMAIL | TEXT | `null` | ❌ | SMTP username |
| `SMTP_PASSWORD` | EMAIL | PASSWORD | `null` | ❌ | SMTP password (AES-256 encrypted) |
| `SMTP_FROM_NAME` | EMAIL | TEXT | `null` | ❌ | From name on outgoing email |
| `SMTP_FROM_ADDRESS` | EMAIL | TEXT | `null` | ❌ | From address on outgoing email |

### FIRM Keys (firm_configs)

| Key | Group | Type | Default | Seed | Description |
|-----|-------|------|---------|------|-------------|
| `BRAND_COLOR_PRIMARY` | BRAND | TEXT | `null` | ❌ | Primary brand color (hex) |
| `BRAND_COLOR_SECONDARY` | BRAND | TEXT | `null` | ❌ | Secondary brand color (hex) |
| `EMAIL_FOOTER_TEXT` | BRAND | TEXT | `null` | ❌ | Email footer text |
| `EMAIL_SIGNATURE` | BRAND | TEXT | `null` | ❌ | Email signature |
| `TIMEZONE` | BRAND | TEXT | `null` | ❌ | Firm timezone |

> FIRM keys are `seed=false` by design. `FirmConfigService.getSettings(firmId)` fills in registry defaults at read time for any key a firm hasn't set yet, so the settings UI is never empty.

---

## RBAC & Permissions

### Module Structure

```
CONFIGURATION (parent)
├── GLOBAL_CONFIG   — platform-wide settings
└── FIRM_CONFIG     — per-firm settings
```

### Permissions per Sub-Module

| Permission Code | Actions |
|----------------|---------|
| `GLOBAL_CONFIG:ACCESS` | Module access gate |
| `GLOBAL_CONFIG:VIEW` | Read global settings |
| `GLOBAL_CONFIG:EDIT` | Write/update global settings |
| `GLOBAL_CONFIG:DELETE` | Delete a global config key |
| `FIRM_CONFIG:ACCESS` | Module access gate |
| `FIRM_CONFIG:VIEW` | Read firm settings |
| `FIRM_CONFIG:EDIT` | Write/update firm settings |
| `FIRM_CONFIG:DELETE` | Delete a firm config key |

### Role Assignment Matrix

| Role | GLOBAL_CONFIG | FIRM_CONFIG |
|------|--------------|-------------|
| `SUPER_ADMIN` | FULL (all 4 actions) | FULL (all 4 actions) |
| `FIRM_ADMIN` | READ_ONLY (ACCESS + VIEW) | FULL (all 4 actions) |
| `ADVOCATE` | NO_ACCESS | NO_ACCESS |
| `PARALEGAL` | NO_ACCESS | NO_ACCESS |
| `CLIENT` | NO_ACCESS | NO_ACCESS |

> Firm Admin can **read** global settings (to display platform defaults) but can only **write** firm-scoped settings for their own firm. Super Admin can manage everything.

### How Permission Checks Work

Controllers use `permissionEvaluator.require("MODULE_CODE:ACTION")`:

```java
// FirmConfigController
@GetMapping
public ResponseEntity<ApiResponse<List<SystemConfigSettingView>>> getConfig() {
    permissionEvaluator.require("FIRM_CONFIG:VIEW");   // ← checked here
    UUID firmId = getRequiredFirmId();                  // ← firmId from JWT, not request body
    return responseHandler.ok(firmConfigService.getSettings(firmId), "Firm config fetched");
}

// SuperAdminConfigController
@PutMapping("/config")
public ResponseEntity<ApiResponse<Void>> updateGlobalConfig(@RequestBody Map<String, String> config) {
    permissionEvaluator.require("GLOBAL_CONFIG:EDIT");  // ← checked here
    systemConfigService.setGlobalBulk(config);
    // ...
}
```

Super Admin bypasses all `require()` checks automatically (see `PermissionEvaluator.require()` — returns early for `isSuperAdmin()`).

---

## API Reference

### Super Admin — Global Config

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| `GET` | `/api/v1/super-admin/config` | `GLOBAL_CONFIG:VIEW` | List all global settings with metadata |
| `PUT` | `/api/v1/super-admin/config` | `GLOBAL_CONFIG:EDIT` | Bulk update global settings |
| `DELETE` | `/api/v1/super-admin/config/{key}` | `GLOBAL_CONFIG:DELETE` | Delete a global config key |

**PUT body example:**
```json
{
  "SMTP_HOST": "smtp.gmail.com",
  "SMTP_PORT": "587",
  "SMTP_USERNAME": "noreply@nepalcrm.com",
  "SMTP_PASSWORD": "app-password-here",
  "SMTP_FROM_NAME": "NepalCRM",
  "SMTP_FROM_ADDRESS": "noreply@nepalcrm.com"
}
```

**GET response:**
```json
{
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
      "value": "********",
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

### Super Admin — Per-Firm Config

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| `GET` | `/api/v1/super-admin/firms/{firmId}/config` | `FIRM_CONFIG:VIEW` | List firm settings with metadata |
| `PUT` | `/api/v1/super-admin/firms/{firmId}/config` | `FIRM_CONFIG:EDIT` | Bulk update firm settings |

### Firm Admin — Firm Config

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| `GET` | `/api/v1/firm/config` | `FIRM_CONFIG:VIEW` | List own firm's settings |
| `PUT` | `/api/v1/firm/config` | `FIRM_CONFIG:EDIT` | Bulk update own firm's settings |

**PUT body example:**
```json
{
  "BRAND_COLOR_PRIMARY": "#1E3A5F",
  "BRAND_COLOR_SECONDARY": "#F5A623",
  "EMAIL_FOOTER_TEXT": "Powered by NepalCRM",
  "EMAIL_SIGNATURE": "Best regards, {{firm_name}}",
  "TIMEZONE": "Asia/Kathmandu"
}
```

> The `firmId` is always resolved from the JWT token — never from the request body. A firm admin cannot target another firm.

### Firm Admin — Email Config (separate entity)

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| `GET` | `/api/v1/firm/email-config` | `FIRM_CONFIG:VIEW` | Get firm SMTP settings |
| `PUT` | `/api/v1/firm/email-config` | `FIRM_CONFIG:EDIT` | Save firm SMTP settings |
| `POST` | `/api/v1/firm/email-config/test` | `FIRM_CONFIG:EDIT` | Test SMTP connection |
| `DELETE` | `/api/v1/firm/email-config` | `FIRM_CONFIG:DELETE` | Delete firm SMTP (falls back to platform) |

> Firm email config lives in `firm_email_configs` (separate table with structured SMTP fields), not in `firm_configs` (key/value). The fallback chain is: firm SMTP → global SMTP → application yml.

---

## Security Invariants

1. **Scope isolation** — `setValue()` calls `ConfigKeyRegistry.requireGlobal(key)` or `requireFirm(key)`. Cross-scope writes are rejected at the service layer.
2. **firmId from JWT** — `CurrentUserResolver.getCurrentFirmId()` reads the authenticated user's firm. Firm admins cannot supply a `firmId` in the request.
3. **Locked keys** — `REGISTRATION_SECRET` has `allowEdit=false`. Once set, it cannot be changed or deleted via API.
4. **URL validation** — `LOGIN_URL` and `CLIENT_PORTAL_URL` are validated as `http://` or `https://` URLs. This closes a phishing vector where a firm admin could repoint password-reset emails.
5. **Encrypted values** — PASSWORD-type values are AES-256 encrypted at rest. Decryption failures are logged and skipped (not fatal), so a changed `config.encryption.key` doesn't crash all config reads.
6. **Read resilience** — `toValueMap()` handles duplicate rows (newest `updated_at` wins) and null/undecryptable values (skipped with warning). This prevents a single bad row from taking out all outbound email.

---

## Consumers — How Code Reads Config

### SystemConfigService (GLOBAL)

```java
@Autowired private SystemConfigService systemConfigService;

// Single key
Optional<String> smtpHost = systemConfigService.getGlobal("SMTP_HOST");

// All global settings as a map
Map<String, String> allGlobal = systemConfigService.getAllGlobal();

// Typed accessors (with code-level defaults)
boolean prodMode = systemConfigService.productionFlag().orElse(false);
int maxLoginAttempts = systemConfigService.loginMaxAttempts();  // default 5
int trialDays = systemConfigService.trialDefaultDays();         // default 14
String loginUrl = systemConfigService.loginUrl();               // default https://app.nepalcrm.com/login
```

### FirmConfigService (FIRM + GLOBAL merge)

```java
@Autowired private FirmConfigService firmConfigService;

// Single firm key
Optional<String> brandColor = firmConfigService.get(firmId, "BRAND_COLOR_PRIMARY");

// Effective config = GLOBAL + FIRM (firm wins on conflict)
Map<String, String> effective = firmConfigService.getEffectiveConfig(firmId);

// Settings with metadata (for UI rendering)
List<SystemConfigSettingView> settings = firmConfigService.getSettings(firmId);
```

### EmailServiceImpl — SMTP Resolution Chain

```java
// 1. Active firm SMTP config (firm_email_configs table)
// 2. GLOBAL SMTP_HOST + SMTP_USERNAME + SMTP_PASSWORD (all three must be non-empty)
// 3. application.yml defaultMailSender
```

### TotpUtil — Production Flag

```java
// DB-first, yml fallback
Optional<Boolean> prod = systemConfigService.productionFlag();
if (prod.isEmpty()) {
    // fall back to app.production from yml
}
```

---

## Seed Behavior

### On Application Start

`DataInitializer.run()` calls `systemConfigService.seedGlobalDefaults()` which:

1. Iterates `ConfigKeyRegistry.GLOBAL`
2. For each key with `seed=true`: inserts a row if the key doesn't exist yet
3. `APP_PRODUCTION` default is derived from `app.production` in `application.yml` (not the registry default)
4. PASSWORD-type values are encrypted before insert

**FIRM keys are NOT seeded.** They appear in `getSettings(firmId)` via registry defaults at read time.

### Migration Notes

Two manual migrations exist:
- `V2026_09_19_1__system_config_scope_uniqueness.sql` — partial unique indexes for the old single-table design. Only needed for databases that haven't run the split.
- `V2026_09_19_2__split_firm_config_table.sql` — creates `firm_configs`, copies FIRM rows, drops `scope`/`firm_id` from `system_config`. Run manually, verify counts before drops.

---

## Frontend Integration Guide

### 1. Settings Page Structure

Create a `/settings` route with two tabs (or sidebar sections):

```
/settings
├── /settings/global    ← SUPER_ADMIN only
└── /settings/firm      ← FIRM_ADMIN only
```

### 2. Fetching Settings

```typescript
// Global settings (Super Admin)
const fetchGlobalSettings = async () => {
  const response = await fetch('/api/v1/super-admin/config', {
    headers: { Authorization: `Bearer ${token}` }
  });
  const { data } = await response.json();
  // data = [{ configKey, configGroup, value, inputType, allowedValues, description, active, allowEdit, sensitive }]
  return data;
};

// Firm settings (Firm Admin)
const fetchFirmSettings = async () => {
  const response = await fetch('/api/v1/firm/config', {
    headers: { Authorization: `Bearer ${token}` }
  });
  const { data } = await response.json();
  return data;
};
```

### 3. Rendering Settings Forms

Group settings by `configGroup` and render each input based on `inputType`:

```tsx
import { useState, useEffect } from 'react';

interface SettingView {
  configKey: string;
  configGroup: string;
  value: string | null;
  inputType: string;      // TEXT | NUMBER | RADIO | URL | PASSWORD
  allowedValues: string | null;  // "Y,N" for RADIO
  description: string;
  active: boolean;
  allowEdit: boolean;
  sensitive: boolean;     // true for PASSWORD type
}

function SettingsForm({ settings, onSave }: { settings: SettingView[], onSave: (values: Record<string, string>) => void }) {
  const [formValues, setFormValues] = useState<Record<string, string>>({});

  useEffect(() => {
    const initial: Record<string, string> = {};
    settings.forEach(s => { initial[s.configKey] = s.value ?? ''; });
    setFormValues(initial);
  }, [settings]);

  // Group by configGroup
  const grouped = settings.reduce((acc, s) => {
    (acc[s.configGroup] ??= []).push(s);
    return acc;
  }, {} as Record<string, SettingView[]>);

  const handleChange = (key: string, value: string) => {
    setFormValues(prev => ({ ...prev, [key]: value }));
  };

  const handleSubmit = () => {
    onSave(formValues);
  };

  return (
    <form onSubmit={e => { e.preventDefault(); handleSubmit(); }}>
      {Object.entries(grouped).map(([group, items]) => (
        <div key={group} className="settings-group">
          <h3>{group}</h3>
          {items.map(setting => (
            <div key={setting.configKey} className="settings-field">
              <label htmlFor={setting.configKey}>
                {setting.description}
                {setting.sensitive && <span className="badge">Encrypted</span>}
              </label>

              {/* TEXT / URL / PASSWORD */}
              {(setting.inputType === 'TEXT' || setting.inputType === 'URL' || setting.inputType === 'PASSWORD') && (
                <input
                  id={setting.configKey}
                  type={setting.inputType === 'PASSWORD' ? 'password' : 'text'}
                  value={formValues[setting.configKey] ?? ''}
                  onChange={e => handleChange(setting.configKey, e.target.value)}
                  disabled={!setting.allowEdit}
                  placeholder={setting.inputType === 'URL' ? 'https://example.com' : ''}
                />
              )}

              {/* NUMBER */}
              {setting.inputType === 'NUMBER' && (
                <input
                  id={setting.configKey}
                  type="number"
                  value={formValues[setting.configKey] ?? ''}
                  onChange={e => handleChange(setting.configKey, e.target.value)}
                  disabled={!setting.allowEdit}
                />
              )}

              {/* RADIO (Y/N toggle) */}
              {setting.inputType === 'RADIO' && setting.allowedValues && (
                <div className="radio-group">
                  {setting.allowedValues.split(',').map(opt => (
                    <label key={opt} className="radio-label">
                      <input
                        type="radio"
                        name={setting.configKey}
                        value={opt.trim()}
                        checked={formValues[setting.configKey] === opt.trim()}
                        onChange={e => handleChange(setting.configKey, e.target.value)}
                        disabled={!setting.allowEdit}
                      />
                      {opt.trim() === 'Y' ? 'Yes' : opt.trim() === 'N' ? 'No' : opt.trim()}
                    </label>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      ))}

      <button type="submit" className="btn-primary">Save Settings</button>
    </form>
  );
}
```

### 4. Saving Settings

```typescript
// Only send changed values
const saveGlobalSettings = async (values: Record<string, string>, original: Record<string, string>) => {
  const changed: Record<string, string> = {};
  for (const [key, value] of Object.entries(values)) {
    if (value !== original[key]) {
      changed[key] = value;
    }
  }

  if (Object.keys(changed).length === 0) return;

  const response = await fetch('/api/v1/super-admin/config', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(changed)
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to save settings');
  }
};

// Firm settings — same pattern but different endpoint
const saveFirmSettings = async (values: Record<string, string>) => {
  const response = await fetch('/api/v1/firm/config', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(values)
  });
  // ...
};
```

### 5. Permission-Gated UI

Check permissions before rendering settings sections:

```typescript
// In your auth/permissions context
const hasPermission = (code: string) => userPermissions.includes(code);

// Render global settings only if user has GLOBAL_CONFIG:VIEW
{hasPermission('GLOBAL_CONFIG:VIEW') && (
  <SettingsTab label="Global Settings" path="/settings/global" />
)}

// Render firm settings only if user has FIRM_CONFIG:VIEW
{hasPermission('FIRM_CONFIG:VIEW') && (
  <SettingsTab label="Firm Settings" path="/settings/firm" />
)}
```

### 6. Handling Sensitive Fields

- `sensitive: true` fields show `********` in the GET response
- When saving, send the actual value — the backend encrypts it
- If the user doesn't change a PASSWORD field, don't send it (the backend keeps the existing encrypted value)
- Consider a "Reveal" toggle for sensitive fields in the UI

### 7. Validation

The backend validates on write. For a better UX, also validate on the frontend:

```typescript
const validateSetting = (key: string, value: string, inputType: string, allowedValues?: string) => {
  if (inputType === 'NUMBER' && isNaN(Number(value))) {
    return `${key} must be a number`;
  }
  if (inputType === 'URL' && !value.startsWith('http://') && !value.startsWith('https://')) {
    return `${key} must be a valid URL`;
  }
  if (inputType === 'RADIO' && allowedValues) {
    const options = allowedValues.split(',').map(o => o.trim().toLowerCase());
    if (!options.includes(value.trim().toLowerCase())) {
      return `${key} must be one of: ${allowedValues}`;
    }
  }
  return null;
};
```

### 8. Bulk Save with Loading State

```typescript
const [saving, setSaving] = useState(false);
const [error, setError] = useState<string | null>(null);

const handleSave = async (values: Record<string, string>) => {
  setSaving(true);
  setError(null);
  try {
    await saveFirmSettings(values);
    // Show success toast
  } catch (e) {
    setError(e.message);
  } finally {
    setSaving(false);
  }
};
```

---

## What's Left in application.yml (by design)

These are NOT moved to DB because they're read once at construction time or are infrastructure-level:

- `jwt.*` — JWT secret/issuer, read once
- `config.encryption.key` — AES key, must match DB values
- `spring.datasource.*` — DB connection
- `spring.mail.*` — fallback mail config
- `hikari.*` — connection pool
- `cors.allowed-origins` — CORS policy
- `permissions.cache.ttl-ms` — permission cache TTL
- `app.production` — only used as seed default for `APP_PRODUCTION`

---

## Adding New Config Keys

### To add a new GLOBAL key:

1. Add the constant to `SystemConfigService`:
   ```java
   public static final String KEY_MY_NEW_SETTING = "MY_NEW_SETTING";
   ```

2. Add the `SettingDef` to `ConfigKeyRegistry.GLOBAL`:
   ```java
   SettingDef.def(SystemConfigService.KEY_MY_NEW_SETTING, "GROUP_NAME", "TEXT", null,
           "default_value", true, "Description for the admin UI", true),
   ```

3. Use it in your service:
   ```java
   String value = systemConfigService.getGlobal("MY_NEW_SETTING").orElse("default_value");
   // or use the typed accessor pattern:
   public String myNewSetting() {
       return getGlobal(KEY_MY_NEW_SETTING).filter(v -> !v.isBlank()).orElse("fallback");
   }
   ```

4. If `seed=true`, it will be auto-inserted on next boot. If `seed=false`, it only appears when someone sets it via the API.

### To add a new FIRM key:

1. Add the constant to `FirmConfigService`:
   ```java
   public static final String KEY_MY_FIRM_SETTING = "MY_FIRM_SETTING";
   ```

2. Add the `SettingDef` to `ConfigKeyRegistry.FIRM`:
   ```java
   SettingDef.def(FirmConfigService.KEY_MY_FIRM_SETTING, "GROUP_NAME", "TEXT", null,
           null, true, "Description", false),
   ```

3. Use it:
   ```java
   Optional<String> value = firmConfigService.get(firmId, "MY_FIRM_SETTING");
   ```

4. FIRM keys are `seed=false` by default. The settings UI shows them with registry defaults until a firm admin sets them.
