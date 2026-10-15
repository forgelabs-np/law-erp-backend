# Tarikh Mobile App — Implementation Guide

Version 1.0 — 2026-10-08 — branch `devG`
Backend refs: `auth/**`, `modules/me/**`, `modules/notification/**`, `superadmin/**`, `common/dto/**`
Related docs: `docs/frontend-guide.md` (web), `docs/super-admin-firm-admin-guide.md`

---

## 0. Scope (read this first)

This is the **basic** mobile app: **login + a home screen that shows who you are.** Nothing more.

In scope:

- **Super Admin login** (no firm code)
- **Firm Admin / firm employee login** (with firm code)
- **Client login** (with firm code, mobile number or username)
- The MFA setup / MFA verify / first-login-password-change branches those logins can return
- A home screen fed by **`GET /api/v1/me`** (identity, firm, brand colors, modules, notifications bell)
- Sign out / token refresh

**Out of scope for now** — do not build screens for these yet: matters, projects, documents,
invoices, hearings, reports, user management, RBAC admin. The endpoints exist; they just are not part of
this first mobile build. The app uses the **existing API unchanged** — no mobile-specific endpoints, no
backend changes.

Everything below is copied from the running backend, not invented. Follow it exactly.

---

## 1. The two things to get right before anything else

1. **Base URL + port.** Dev backend runs at **`http://localhost:6969`**, and every path is prefixed
   with **`/api/v1`**. So the full login URL is `http://localhost:6969/api/v1/auth/login`.
   - On a physical phone, `localhost` is the phone itself — use your machine's LAN IP
     (e.g. `http://192.168.1.20:6969`) and make sure the backend binds to `0.0.0.0`, or tunnel it.
   - Android emulator: `http://10.0.2.2:6969`. iOS simulator: `http://localhost:6969`.
   - There is **no trailing slash** and paths are case-sensitive.
2. **The envelope.** Two shapes, and mixing them up is the #1 integration mistake.

---

## 2. Request & response envelopes

**Every response** (success or failure) is wrapped:

```json
{
  "success": true,
  "message": "Authenticated",
  "responseCode": 200,
  "data": { }
}
```

| Field | Meaning |
|---|---|
| `success` | `true` / `false`. **Branch on this first.** |
| `message` | Human-readable. Show it on errors. |
| `responseCode` | Mirrors the HTTP status. **Success is `200`** (not `0`). |
| `data` | The payload. **Absent on errors** — never render an error path from `data`. |

**Every request with a JSON body must be wrapped in `{ "data": { … } }`** — even for a single field:

```json
// WRONG — rejected with a 400 about `data`
{ "username": "admin", "password": "secret" }

// RIGHT
{ "data": { "username": "admin", "password": "secret" } }
```

`GET` and `DELETE` take no body. All auth endpoints take `Content-Type: application/json` with JSON bodies.

**Booleans strip their `is` prefix.** Lombok serializes `isTrial`, `isSystem`, `isActive` fields as
`trial`, `system`, `active`. **There is one deliberate exception** — see `isPersonalColor` in §8. Do not
"fix" the rest; the key names below are correct.

Dates (`LocalDateTime`) serialize as **ISO-8601 with no timezone offset**, e.g. `"2026-10-08T10:31:26"`.
Parse them as local datetimes, not as UTC instants.

---

## 3. Authentication model (so you can reason about failures)

- **Stateless JWT bearer tokens.** Send `Authorization: Bearer <accessToken>` on every protected call.
- **CSRF is disabled** — normal REST, no token dance.
- The only **public** (no-token) endpoints are: the three logins, `/auth/refresh`,
  `/auth/mfa/validate`, `/auth/mfa/setup/confirm`, `/auth/change-password`,
  `/auth/forgot-password`, `/auth/reset-password`, and `/super-admin/register`.
  **Everything else requires a valid bearer token.**
- A missing/expired token on a protected call returns **401** with
  `{"success":false,"message":"Authentication failed. Please login again.","responseCode":401,"data":"Authentication failed. Please login again."}`
  — note `data` is a *string* here. Treat any 401 as "session gone → go to login".

---

## 4. Login — Firm Admin / firm employee

`POST /api/v1/auth/login`  → body wrapped in `data`.

```json
{
  "data": {
    "lawFirmCode": "FIRM-001",
    "username": "admin",
    "password": "secret123"
  }
}
```

| Field | Required | Notes |
|---|---|---|
| `lawFirmCode` | **yes** | The firm's code. Message if blank: `Firm code is required for internal users`. |
| `username` | **yes** | `Username is required`. |
| `password` | **yes** | `Password is required`. |
| `totpCode` | no | Only needed on the second call after an `MFA_REQUIRED` response (§6). 6 digits. |

The server, in order: firm exists & not suspended/expired → user lookup → lockout check → **password
check** → account active/not-blocked → "wrong door" check (a Super Admin here must use the
super-admin URL; a Client here must use `/auth/client/login`) → success. Details are deliberately
masked ("Invalid username or password") so accounts cannot be enumerated.

### Response — always HTTP 200, `success: true`, and a `status` that tells you what to do next

```json
{
  "success": true,
  "message": "Authenticated",
  "responseCode": 200,
  "data": {
    "status": "SUCCESS",
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "expiresIn": 86400000
  }
}
```

`status` is **one of four** values — handle all of them in the client:

```json
{ "data": { "status": "MFA_SETUP_REQUIRED", "mfaToken": "...", "mfaQrCodeUri": "otpauth://totp/...", "mfaManualKey": "JBSW Y3DP" } }
{ "data": { "status": "MFA_REQUIRED",        "mfaToken": "..." } }
{ "data": { "status": "PASSWORD_CHANGE_REQUIRED", "passwordChangeToken": "..." } }
{ "data": { "status": "SUCCESS", "accessToken": "...", "refreshToken": "...", "expiresIn": 86400000 } }
```

- `expiresIn` is in **milliseconds** (`86400000` = 24 h). It is a fixed constant; do not compute it, just
  display/ignore it.
- Unused fields are **absent** (not `null`) — the response omits them, so read defensively
  (`json.data.mfaToken ?? null`).

---

## 5. Login — Client

`POST /api/v1/auth/client/login` — **same body, same response** as §4. `username` accepts **either the
client's mobile number OR their username**.

```json
{ "data": { "lawFirmCode": "FIRM-001", "username": "9800000000", "password": "secret123" } }
```

A **client account** calling `/auth/login` is refused; a **non-client** calling `/auth/client/login` is
refused. Use the correct endpoint per role — the app should not let the user guess: give them a
**role picker** (Firm user / Client / Super Admin) that routes to the right URL (see §10).

---

## 6. Login — Super Admin

`POST /api/v1/super-admin/login` — **no firm code**. Same `LoginResponse` shape as §4.

```json
{ "data": { "username": "superadmin", "password": "secret123" } }
```

| Field | Required | Notes |
|---|---|---|
| `username` | **yes** | `Username is required`. |
| `password` | **yes** | `Password is required`. |
| `totpCode` | no | If supplied, must be exactly 6 digits (`^[0-9]{6}$`), else 400. |

Super Admin accounts **always have MFA**, so in practice the first response is `MFA_SETUP_REQUIRED`
(first ever login) or `MFA_REQUIRED`. Handle both with §7.

---

## 7. Finishing an MFA or password-change login

These are the **follow-up calls** when `status` was not `SUCCESS`. All three are public (no bearer token);
they carry the short-lived token from the login response instead.

### 7a. First-time MFA setup — `status: "MFA_SETUP_REQUIRED"`

Show the QR (`mfaQrCodeUri`) or the manual key (`mfaManualKey`) to the user, then confirm with the
6-digit code their authenticator app now shows:

`POST /api/v1/auth/mfa/setup/confirm`

```json
{ "data": { "mfaToken": "<from login>", "totpCode": "123456" } }
```

Returns a `LoginResponse` — on success it is the full `SUCCESS` payload (`accessToken`/`refreshToken`).
`totpCode` must be exactly 6 digits or you get a 400.

### 7b. MFA verify — `status: "MFA_REQUIRED"`

`POST /api/v1/auth/mfa/validate`

```json
{ "data": { "mfaToken": "<from login>", "totpCode": "123456" } }
```

Returns the full `SUCCESS` `LoginResponse`.

### 7c. Forced first-login password change — `status: "PASSWORD_CHANGE_REQUIRED"`

`POST /api/v1/auth/change-password` (unauthenticated; uses the short-lived token)

```json
{ "data": { "passwordChangeToken": "<from login>", "newPassword": "newSecret1", "confirmPassword": "newSecret1" } }
```

- `newPassword`: **8–50 characters** (`Password must be 8-50 characters`).
- Returns the full `SUCCESS` `LoginResponse` on success.

---

## 8. `GET /api/v1/me` — the home screen payload

Send the bearer token. This is the **only** call the home screen needs; it drives everything: who you
are, your firm, brand colors, which modules to show, and (via a second call) the notification badge.

```json
{
  "success": true,
  "message": "User identity fetched",
  "responseCode": 200,
  "data": {
    "id": "c17b…",
    "username": "admin",
    "fullName": "Firm Admin",
    "email": "admin@firm.com",
    "mobileNo": "9800000000",
    "profilePhotoUrl": null,
    "userType": "FIRM_ADMIN",
    "firm": {
      "id": "8f3c…",
      "name": "Sharma & Associates",
      "lawFirmCode": "FIRM-001",
      "email": "info@firm.com",
      "phone": "01-4444444",
      "address": "Kathmandu",
      "jurisdiction": "Nepal",
      "logoUrl": "http://localhost:9000/tarikh-logos/…?X-Amz-…",
      "logoAllowed": true,
      "brandPrimaryHex": "#1E3A8A",
      "brandSecondaryHex": "#F59E0B",
      "isPersonalColor": true,
      "status": "ACTIVE",
      "trialExpiresAt": null,
      "daysRemaining": null,
      "trial": false
    },
    "role": { "id": "…", "name": "Firm Admin", "code": "FIRM_ADMIN", "system": true },
    "permissions": ["MATTER:VIEW", "DOCUMENT_MANAGEMENT:VIEW", "…"],
    "modules": [
      {
        "moduleCode": "MATTER",
        "moduleName": "Matters",
        "icon": "gavel",
        "path": "/matters",
        "enabled": true,
        "actions": ["VIEW", "CREATE"],
        "subModules": []
      }
    ],
    "brandColorPrimary": "#1E3A8A",
    "brandColorSecondary": "#F59E0B",
    "appName": "Tarikh",
    "logoAllowed": true,
    "logoUrl": "http://localhost:9000/tarikh-logos/…?X-Amz-…",
    "lastLoginAt": "2026-10-08T10:31:26",
    "active": true
  }
}
```

### Field notes (the traps)

| Field | Note |
|---|---|
| `userType` | `SUPER_ADMIN` \| `FIRM` \| `FIRM_USER` \| `CLIENT`. **`FIRM` means "firm admin".** |
| `firm.isPersonalColor` | **Present and `true` only when the firm set its own colors.** When it has not, the key is **omitted entirely** (not `null`). Fall back to your app default when it is missing. This one **keeps** its `is` prefix. |
| `firm.trial` | The trial flag — **key is `trial`**, not `isTrial`. |
| `role.system` | `role.system`, not `isSystem`. |
| `active` | Top-level `active`, not `isActive`. |
| `firm.status` | `ACTIVE` \| `SUSPENDED` \| `EXPIRED` (others may exist). |
| `logoUrl` | A **presigned, short-lived** object-storage link. It **expires** — do not cache it for long, and never store it as the permanent logo. Re-fetch `/me` to get a fresh one. |
| `modules[].actions` | The actions this role has in that module (`VIEW`, `CREATE`, …). Use it to hide buttons. `modules` may be empty. |
| `permissions` | Flat permission strings. A convenience mirror of `modules[].actions`. |
| `lastLoginAt` | `LocalDateTime`, no offset. |

> **Brand colors.** Use `brandColorPrimary` / `brandColorSecondary` (or the `firm.brand*Hex` pair — they
> carry the same value). Apply them as the app theme when `firm.isPersonalColor === true`; otherwise use
> your default Tarikh theme. `logoAllowed` gates whether an uploaded firm logo may be displayed.

---

## 9. Tokens, refresh, logout, notifications

### Refresh — `POST /api/v1/auth/refresh` (public)

Call this when a protected request returns **401** (or proactively near expiry). Body:

```json
{ "data": { "refreshToken": "<stored refreshToken>" } }
```

Returns the same `LoginResponse` `SUCCESS` shape (new `accessToken` + `refreshToken`). Replace both.

### Logout — `POST /api/v1/auth/logout` (requires bearer token)

```json
{ "success": true, "message": "Logged out. Every session for this account has been ended.", "responseCode": 200 }
```

**This kills *all* sessions for the account**, not just this device. Fine for a basic app — just know
that signing out on the phone signs the user out everywhere. Then discard the stored tokens locally.

### Notifications (for the bell on the home screen)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/notifications/unread-count` | Badge number → `data: { "count": 3 }` |
| GET | `/api/v1/notifications?unreadOnly=false&page=0&size=20` | Paged list |
| POST | `/api/v1/notifications/{id}/read` | Mark one read (id is a UUID) |
| POST | `/api/v1/notifications/read-all` | Mark all read → `data: { "updated": 5 }` |

A notification item:

```json
{
  "id": "9d2a…",
  "type": "MATTER_UPDATED",
  "category": "MATTER",
  "title": "Case updated",
  "body": "…",
  "referenceType": "MATTER",
  "referenceId": "…",
  "read": false,
  "readAt": null,
  "createdAt": "2026-10-08T09:12:00"
}
```

`page` is **0-based**; `size` defaults to 20. Paged responses have the shape in §12.

**For this basic build you only need the unread count.** Deep-linking from a notification into a
matter/document screen is a later phase.

### Self-service password change (signed-in user) — `POST /api/v1/me/change-password`

Different from §7c: this one requires the bearer token and the **current** password.

```json
{ "data": { "currentPassword": "old", "newPassword": "newSecret1", "confirmPassword": "newSecret1" } }
```

`newPassword`: 8–50 chars. On success other sessions are signed out.

---

## 10. Screens (basic build)

```
┌─ Splash ────────────────────────────────────────────────┐
│  Read stored tokens → if valid, GET /me → Home           │
│  If none/expired → Login                                │
└──────────────────────────────────────────────────────────┘
                    │
┌─ Login ─────────────────────────────────────────────────┐
│  Role picker: [ Firm User ] [ Client ] [ Super Admin ]    │
│  Firm User / Client → show "Firm code" field              │
│  Super Admin        → hide "Firm code"                    │
│  username, password  → POST the right endpoint (§4/5/6)   │
└──────────────────────────────────────────────────────────┘
                    │  status != SUCCESS
        ┌───────────┴───────────┬───────────────────┐
        ▼                       ▼                   ▼
 MFA_SETUP (§7a)         MFA_VERIFY (§7b)   PASSWORD_CHANGE (§7c)
 code entry + QR         6-digit code       new + confirm password
        └───────────┬───────────┴───────────────────┘
                    ▼
┌─ Home ──────────────────────────────────────────────────┐
│  Avatar + fullName + role.name                          │
│  Firm name + lawFirmCode + status chip                  │
│  Brand colors applied if firm.isPersonalColor === true  │
│  Firm logo if firm.logoAllowed (from firm.logoUrl)      │
│  Grid of firm.modules[].moduleName (icon)               │
│    → these are placeholders for later phases            │
│  Bell with unread count (§9)                            │
│  Sign out                                               │
└──────────────────────────────────────────────────────────┘
```

**Screen rules**

- **Persist** `accessToken`, `refreshToken`, and enough of `/me` for the splash (e.g. `fullName`,
  `userType`) in secure storage (Keychain / EncryptedSharedPreferences).
- The **role picker decides the endpoint**, not one shared `POST /auth/login`:
  Firm user → `/auth/login`, Client → `/auth/client/login`, Super Admin → `/super-admin/login`.
- One `AuthGate`: on app start, no token → Login; token present → splash calls `GET /me`; a 401 there →
  clear tokens → Login; 200 → Home.
- The **module grid is read-only placeholders** in this phase. Tapping a module can show a
  "coming soon" state. Do not build the module screens yet.
- **Do not** store `logoUrl` or any `*Url` as a permanent value — they are presigned and expire.

---

## 11. Error handling

All non-2xx responses use the same envelope with `success: false` and a `message`. **Render `message` —
do not invent text from `responseCode`** (many causes share a 400).

| HTTP | `message` (examples) | Cause | What to do |
|---|---|---|---|
| 400 | `Firm code is required for internal users` / `Username is required` / `Password is required` | missing field | inline field error |
| 400 | `TOTP code must be exactly 6 digits` | bad MFA code format | inline |
| 400 | `Password must be 8-50 characters` | bad new password | inline |
| 400 | `Provide either a case (matterNumber)…` etc. | form validation | inline |
| 401 | `Invalid username or password` / `Authentication failed. Please login again.` | bad credentials **or** dead session | bad creds → inline; dead session → clear tokens → Login |
| 401 | `Your account has been locked due to too many failed attempts…` | lockout | show message, disable retry briefly |
| 401 | `Your account has been disabled. Please contact support.` | disabled | show message |
| 403 | `You do not have permission to perform this action` | role lacks the action/module | hide the affordance; do not retry |
| 404 | `Endpoint not found` / `User not found` | wrong URL / missing record | bug or stale data |
| 409 | `Username already exists` / `Email already exists` / `Mobile number already exists` | duplicate | inline |
| 405 | `HTTP method not supported for this endpoint` | wrong verb | client bug |
| 500 | `An unexpected error occurred` | server bug | generic toast |
| 502 | `Document storage is temporarily unavailable. Please try again.` | storage down | retryable toast |

**On a 401 from any protected call: try `/auth/refresh` once. If that also fails, clear tokens and go to
Login.** Do not loop.

---

## 12. Types to copy

```ts
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  responseCode: number;   // mirrors HTTP status; 200 on success
  data: T;                // absent on errors
}

export interface ApiRequest<T> { data: T; }

export interface PagedResponse<T> {
  content: T[];
  page: number;           // 0-based
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export type AuthStatus = 'SUCCESS' | 'MFA_SETUP_REQUIRED' | 'MFA_REQUIRED' | 'PASSWORD_CHANGE_REQUIRED';

/** Every login response. Only the fields relevant to `status` are present. */
export interface LoginResponse {
  status: AuthStatus;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;            // milliseconds
  mfaToken?: string;
  mfaQrCodeUri?: string;
  mfaManualKey?: string;
  passwordChangeToken?: string;
}

export interface LoginRequest {
  lawFirmCode: string;           // omit for super admin
  username: string;
  password: string;
  totpCode?: string;
}

export interface SuperAdminLoginRequest {
  username: string;
  password: string;
  totpCode?: string;             // 6 digits if supplied
}

export type UserType = 'SUPER_ADMIN' | 'FIRM' | 'FIRM_USER' | 'CLIENT';
export type FirmStatus = 'ACTIVE' | 'SUSPENDED' | 'EXPIRED';

export interface MeResponse {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  mobileNo: string | null;
  profilePhotoUrl: string | null;
  userType: UserType;
  firm: FirmInfo | null;
  role: RoleInfo | null;
  permissions: string[];
  modules: ModuleAccess[];
  brandColorPrimary: string | null;
  brandColorSecondary: string | null;
  appName: string | null;
  logoAllowed: boolean;
  logoUrl: string | null;         // presigned — expires
  lastLoginAt: string | null;     // LocalDateTime, no offset
  active: boolean;
}

export interface FirmInfo {
  id: string;
  name: string;
  lawFirmCode: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  jurisdiction: string | null;
  logoUrl: string | null;         // presigned — expires
  logoAllowed: boolean;
  brandPrimaryHex: string | null;
  brandSecondaryHex: string | null;
  isPersonalColor?: boolean;      // OMITTED when the firm has no custom colors
  status: FirmStatus;
  trialExpiresAt: string | null;
  daysRemaining: number | null;
  trial: boolean;                 // key is `trial`, not `isTrial`
}

export interface RoleInfo {
  id: string;
  name: string;
  code: string;
  system: boolean;                // key is `system`, not `isSystem`
}

export interface ModuleAccess {
  moduleCode: string;
  moduleName: string;
  icon: string | null;
  path: string | null;
  enabled: boolean;
  actions: string[];
  subModules?: ModuleAccess[];
}

export interface NotificationResponse {
  id: string;
  type: string | null;
  category: string | null;
  title: string;
  body: string | null;
  referenceType: string | null;
  referenceId: string | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}
```

---

## 13. "What NOT to do"

1. **Do not** send a bare JSON body — always wrap in `{ "data": { … } }`.
2. **Do not** read `isTrial`, `isSystem`, or `isActive` — they are `trial`, `system`, `active`.
3. **Do not** expect `isPersonalColor` to always exist — it is **omitted** when the firm has no custom
   colors. Default instead.
4. **Do not** treat `logoUrl` / `firm.logoUrl` as permanent — presigned links expire; re-fetch `/me`.
5. **Do not** call one login endpoint for everyone — pick by role (§10).
6. **Do not** build screens for matters/projects/documents/invoices yet — out of scope for this build.
7. **Do not** render an error from `responseCode`; show `message`.
8. **Do not** assume `expiresIn` varies — it is a fixed 24 h in ms; just store/ignore it.
9. **Do not** retry a 401 in a loop — refresh once, then force login.
10. **Do not** `POST /auth/logout` casually expecting per-device logout — it ends **all** sessions.

---

## 14. Quick manual test script

Backend: `./mvnw spring-boot:run` (dev profile → Supabase), then `http://localhost:6969`.
Swagger (dev): `http://localhost:6969/swagger-ui.html`, api-docs at `/api-docs`.

1. **Firm admin happy path** — `POST /api/v1/auth/login` with a valid firm code; expect
   `status: "SUCCESS"` + tokens; then `GET /api/v1/me` with the bearer token; confirm `userType`,
   `firm.*`, `modules[]`, and brand colors render on Home.
2. **First-login password change** — a user with `mustChangePassword` returns
   `PASSWORD_CHANGE_REQUIRED`; complete §7c and confirm you get tokens.
3. **MFA** — a fresh Super Admin returns `MFA_SETUP_REQUIRED`: show the QR, confirm §7a with a code, land
   on Home. A Super Admin with MFA returns `MFA_REQUIRED`; confirm §7b.
4. **Client** — `POST /api/v1/auth/client/login` with the client's **mobile number** as `username`; Home
   shows `userType: "CLIENT"` and the client's modules.
5. **Wrong door** — a client on `/auth/login` and a firm user on `/auth/client/login` are both refused.
6. **Bad password** → 401 `Invalid username or password`, shown inline.
7. **Dead token** — corrupt the stored access token, hit `GET /me` → 401 → app forces Login.
8. **Refresh** — call `POST /api/v1/auth/refresh` with the stored refresh token; both tokens change.
9. **Brand fallback** — a firm with no colors: `firm.isPersonalColor` is **absent**; the app uses its
   default theme (and single-color brand chrome gracefully).
10. **Unread badge** — `GET /api/v1/notifications/unread-count` shows a number on the bell.

---

## 15. Open / deferred items for the next phase

- Module screens (matters, projects, documents, invoices) — endpoints exist, UI not built.
- Notification deep-linking.
- Firm logo upload / brand editing from mobile (exists on the web; not needed for basic mobile).
- Push notifications (the API exposes in-app notifications only).
- Client self-signup / onboarding flows.

> **Known environment caveat:** object storage (MinIO) is currently **down** in this dev environment, so
> presigned `logoUrl` values may not resolve. The login/`/me` contract is unaffected; brand colors and
> identity render without the logo.
