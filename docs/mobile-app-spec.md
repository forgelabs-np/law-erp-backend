# Tarikh — Mobile App Specification (v1)

**Purpose:** the single source of truth for designing the Tarikh mobile app. Every endpoint, field name
and JSON shape below was read out of the running backend, not invented — so a UI/UX tool can build
against it without guessing.

**Audience:** whoever (or whatever AI) turns this into wireframes, screens and a component library.

**Read this with:** `docs/dev-profile-test-report.md` (backend behaviour) and `checklist-prod.md`
(release checklist).

---

## 0. How to use this document

1. **Scope first** — §1 says what the app is and §2 says exactly which backend capabilities exist today.
   Design only what §2 proves; §9 lists what is *not* reachable yet.
2. **Build the shell from §3–§5** (design system, session rules, navigation).
3. **Design screens from §6** — one block per screen: what it is for, what to show, the endpoint, and the
   real field names it receives.
4. **§7 is the field dictionary** (enums, formats) and **§10 is out of scope** for v1.
5. **§11** is a copy-paste system prompt for an AI design tool.

Two ground rules that shape every screen:

- **The app is a viewer.** Read, search, filter, open, download, plus the handful of "basic things" in
  §1.1. No case creation, no document upload, no billing edits in v1.
- **Everything is permission-gated server-side.** A screen the user lacks access to must not be drawn as
  an empty screen — hide the tab/entry (the backend returns `403` and deliberately tells you nothing).

---

## 1. What this app is

A companion app for the web ERP. People are in court, in a client meeting or travelling; they need the
answer to "what is happening today and what needs my attention?", not a full desktop workspace.

Three personas, in two user types:

| Persona | `userType` | Role code | What they need on a phone |
|---|---|---|---|
| **Firm Admin** | `FIRM` | `FIRM_ADMIN` | Firm-wide pulse: today's cause list, team caseload, overdue invoices, renewals, approvals-ish visibility, announcements to staff |
| **Employee** (advocate / paralegal) | `FIRM_USER` | `ADVOCATE`, `PARALEGAL` | My day: my cases, my hearings, my deadlines, what changed on my matters |
| **Client** | `CLIENT` | `CLIENT` | Reassurance and transparency: my case status, next hearing, shared documents, my invoices' status |

### 1.1 "Basic things" that are allowed to write (v1)

Only these, deliberately small:

- mark a notification read / mark all read · change notification preferences
- change own password · set up / validate MFA · log out (all sessions)
- forgot-password / reset-password flow

Everything else is read-only in v1. §10 lists what moves to phase 2.

---

## 2. Verified capability matrix

Grepped from the controllers' `@PreAuthorize`, `permissionEvaluator.require(...)` and `@RequiresModule`
annotations. ✅ = phone-ready today, ⚠️ = works but with a caveat, ⛔ = **will 403 with the data as it
sits today** (see §9 — these are backend gaps, not design choices).

| Capability | Firm Admin | Employee | Client |
|---|---|---|---|
| Login + MFA + session | ✅ | ✅ | ✅ |
| `GET /api/v1/me` (identity, firm, role, permissions, modules, **branding**) | ✅ | ✅ | ✅ |
| Role dashboard (`/dashboard/firm` \| `/employee` \| `/client`) | ✅ | ✅ | ⛔ needs `DASHBOARD_MANAGEMENT:VIEW` and `CLIENT` currently holds **0 permissions** |
| Notifications list / unread count / read / preferences | ✅ | ✅ | ⚠️ same endpoint, but permission-driven — verify the CLIENT role is granted the notification module (it has none today) |
| Cases / matters list + detail + timeline | ✅ | ✅ | ✅ via `/api/v1/client/matters` (own matters only) |
| Calendar: today / upcoming / range | ✅ | ✅ | ⛔ no client calendar endpoint exists — client sees hearings through the dashboard + matter detail |
| Documents list + download URL | ✅ | ⛔ employee role has **no `DOCUMENT_MANAGEMENT`** permission today | ⛔ `/api/v1/client/documents` requires `DOCUMENT_MANAGEMENT:VIEW`, which `CLIENT` lacks |
| Clients list (firm's clients) | ✅ | ⛔ | — |
| Employees / team list | ✅ | ⛔ | — |
| Firm profile + brand theme + logo | ✅ | ⛔ | — |
| Announcement broadcast to staff | ✅ | — | — |
| Projects + renewals | ✅ | ⛔ | ✅ `/api/v1/client/projects` |
| Invoices | ⚠️ **summary only** (dashboard stats) | ⛔ | ⚠️ **summary only** — there is no client-facing invoice list/detail/PDF endpoint |
| Reports, RBAC, audit, scraper, super-admin | ⛔ never in this app | ⛔ | ⛔ |

> **Design consequence:** build the client app's home around `/api/v1/client/matters` + `/api/v1/me` +
> notifications until §9.1 is fixed, or make §9.1 a blocking pre-build task. Do not design a client
> dashboard screen that depends on `/dashboard/client` before it returns 200.

---

## 3. Global technical rules

### 3.1 Base and transport

- Base URL: `https://<host>` (dev today: `http://localhost:6969`). All paths below are relative.
- HTTPS only. Send `Authorization: Bearer <accessToken>` on everything except the auth endpoints.
- `Content-Type: application/json` except the document download (GET, no body) and the logo upload
  (multipart — phase 2).

### 3.2 The envelope (memorise this)

Every JSON response is wrapped:

```json
{ "success": true, "message": "Matters fetched successfully", "responseCode": 200, "data": { } }
```

- Read `success` first; show `message` verbatim as the user-facing error string — it is written for
  humans ("Account locked. Try again in 29 minute(s).").
- `data` is `null` for actions with no payload.

**Every JSON request body is wrapped in `data` too**, with exactly one exception:
`PUT /api/v1/firm/config` takes a flat map. So:

```json
POST /api/v1/auth/login
{ "data": { "lawFirmCode": "TLAW", "username": "tlaw", "password": "…", "totpCode": "123456" } }
```

### 3.3 Three different list shapes — check per screen

| Shape | Where | Keys |
|---|---|---|
| Wrapped page | `/api/v1/notifications`, `/api/v1/firm/clients`, `/api/v1/firm/employees`, `/api/v1/firm/documents`, `/api/v1/client/documents` | `data.content[]`, `data.page`, `data.size`, `data.totalElements`, `data.totalPages`, `data.first`, `data.last`, `data.empty` |
| Spring `Page` | `/api/v1/firm/matters`, `/api/v1/firm/matters/stale`, `/api/v1/firm/matters/timeline` | `data.content[]`, `data.totalElements`, `data.totalPages`, `data.number` (page index), `data.size`, `data.first`, `data.last`, `data.empty`, plus `data.pageable` |
| Plain array | `/api/v1/client/matters`, `/api/v1/firm/calendar`, `/api/v1/firm/calendar/today`, `/api/v1/firm/calendar/upcoming`, `/api/v1/firm/matters/{matterNumber}/timeline`, `/api/v1/client/projects` | `data[]` — **no paging**: render with infinite-scroll-less "show all" or a client-side cap |

Paging params are always `page` (0-based) and `size` (default 20).

### 3.4 Errors and status codes

| Code | Meaning for UX |
|---|---|
| `200` + `success:false` | Business rejection — show `message` inline (e.g. bad credentials come back as 200-wrapped 401 below) |
| `400` | Validation / business rule, or "account locked" on the super-admin path |
| `401` | Not signed in, bad credentials, expired/rotated token, or locked account on `/auth/login` |
| `403` | Missing permission (`Missing required permission: X`) or module disabled for the firm (`Module 'X' is not enabled for your firm`). Treat as "not available for your role" — hide, don't apologise |
| `404` | Gone / not yours — treat as "not found" |
| `5xx` | Generic "Something went wrong — try again" |

Session rules the app must implement:

- **Access token** is a JWT; read `exp` from it or treat `expiresIn` as a hint only — `expiresIn` is
  currently hardcoded to `86400000` (24 h) **even when the firm's config sets a 1-minute expiry**. The
  authoritative value is the `exp` claim; a live test set `ACCESS_TOKEN_EXPIRY_MINUTES=1` and the token
  really died after 60 s. Refresh proactively at ~80 % of the *decoded* lifetime.
- **Refresh** with `POST /api/v1/auth/refresh` (`{ "data": { "refreshToken": "…" } }`). The response has
  the same shape as login. Reuse detection revokes **all** sessions for that user — on a failed refresh,
  drop to the login screen with `message` shown.
- **`403` does not invalidate the token.** Only `401` should trigger refresh-then-retry-once.
- **Logout** is server-side and global: `POST /api/v1/auth/logout` ends every session for the account
  (all devices). Confirm with the user before calling it.
- Store tokens in the platform secure store (Keychain / Keystore). Never in `SharedPreferences`/`NSUserDefaults`.

### 3.5 Field formats

| Kind | Format | Note |
|---|---|---|
| `LocalDate` | `"YYYY-MM-DD"` | e.g. `"2026-10-14"` |
| `LocalDateTime` | `"YYYY-MM-DDTHH:mm:ss.SSS"` | **no timezone offset** — it is firm-local wall time; the firm timezone setting owns the meaning |
| `LocalTime` | `"HH:mm:ss"` | Calendar/event times |
| `Instant` (download link expiry) | ISO-8601 with `Z` | |
| Money (`BigDecimal`) | number, but **parse as a decimal string** and format with 2 dp + currency | never float |
| `UUID` | string | stable ids |
| `Long` (documents) | number | `DocumentResponse.id` is a `Long`, not a UUID; `uuid` is the UUID |

`@JsonInclude(NON_NULL)` is used on auth responses: **absent keys are normal** — never assume
`refreshToken`, `mfaToken` etc. exist.

### 3.6 Branding / theming (the app is white-labelled per firm)

`GET /api/v1/me` carries the theme. Apply it before the first authenticated screen paints:

| Field | Use |
|---|---|
| `brandColorPrimary` | app primary (buttons, active tab) — hex string like `#1A73E8` |
| `brandColorSecondary` | accents, chips, charts |
| `appName` | header title / splash wordmark |
| `logoAllowed` + `logoUrl` | show the firm logo only when `logoAllowed` is true and `logoUrl` is non-null |
| `firm.brandPrimaryHex`, `firm.brandSecondaryHex`, `firm.isPersonalColor` | `isPersonalColor` is present **only** when the firm set its own colours; when absent, fall back to the app default palette |
| `firm.logoUrl`, `firm.logoAllowed` | same rule per firm |
| `firm.isTrial`, `firm.trialExpiresAt`, `firm.daysRemaining` | trial banner ("12 days left in your trial") |

Design tokens to produce: primary, primary-container, on-primary, secondary, surface, surface-variant,
outline, plus success/warning/danger for the status colours in §7.3. Provide a dark theme; the app will
be used in court corridors.

---

## 4. Roles, first-run and session flow

```
Launch
  └─ token in secure store? ── no ──► [S1 Login]
        │ yes
        ▼
    GET /api/v1/me
        ├─ 401 ──► try POST /auth/refresh ── fail ──► [S1 Login]
        └─ 200 ──► choose shell by userType + role.code
                     FIRM_ADMIN  ► Firm Admin shell
                     ADVOCATE/PARALEGAL ► Employee shell
                     CLIENT      ► Client shell   (entry is /auth/client/login, not /auth/login)
```

- **Two login doors.** Staff (`FIRM`, `FIRM_USER`) use `POST /api/v1/auth/login`; clients use
  `POST /api/v1/auth/client/login`. Wrong door → `401 "This account is not a client account"` /
  `"Clients must sign in through the client portal"`. The Client app shows **one** door; the staff app
  shows the staff door. Never mix them in one form.
- **Firm code** (`lawFirmCode`) is required on both doors. Provide the web equivalent of "remember my
  firm" locally (it is not secret) so staff type only username + password daily.
- After a successful login the response `status` tells the app what to do next — handle all four:

| `status` | Fields present | Screen |
|---|---|---|
| `SUCCESS` | `accessToken`, `refreshToken`, `expiresIn` | → Home |
| `MFA_REQUIRED` | `mfaToken` | → [S2] code challenge → `POST /auth/mfa/validate` |
| `MFA_SETUP_REQUIRED` | `mfaToken`, `mfaQrCodeUri`, `mfaManualKey` | → [S3] QR + first code → `POST /auth/mfa/setup/confirm` |
| `PASSWORD_CHANGE_REQUIRED` | `passwordChangeToken` | → [S4] forced rotation → `POST /auth/change-password` |

---

## 5. Navigation

Bottom navigation, 5 items max, role-shaped. Everything else lives under **More**.

**Firm Admin**

| Tab | Screen | Icon hint |
|---|---|---|
| Home | [S7] Firm dashboard | `home` |
| Cases | [S11] Matters list → [S12] detail | `folder` |
| Day | [S14] Calendar (today / upcoming) | `calendar` |
| Inbox | [S10] Notifications | `bell` + unread badge |
| More | [S19] More hub → Clients, Team, Firm profile & branding, Announcement, Settings | `menu` |

**Employee**

| Tab | Screen | Icon hint |
|---|---|---|
| Home | [S8] Employee dashboard | `home` |
| My Cases | [S11] Matters (default filter: mine) | `folder` |
| Day | [S14] Calendar | `calendar` |
| Inbox | [S10] Notifications | `bell` + badge |
| Me | [S6] Profile & settings | `user` |

**Client**

| Tab | Screen | Icon hint |
|---|---|---|
| Home | [S9] Client dashboard (or [S13] fallback) | `home` |
| My Cases | [S13] My matters → detail | `briefcase` |
| Files | [S18] Shared documents | `file-text` |
| Inbox | [S10] Notifications | `bell` + badge |
| Me | [S6] Profile & settings | `user` |

Rules: the tab bar is fixed; the **unread badge polls `GET /api/v1/notifications/unread-count` every
45–60 s** (the backend's own guidance) while the app is foregrounded, and refreshes on resume. Pull-to-
refresh on every list and dashboard.

---

## 6. Screen specification

Each screen: **goal → layout blocks → data (endpoint + fields) → states → actions**.
Field names are the exact JSON keys.

---

### S1 · Login (staff) / S1c · Login (client)

**Goal:** get in fast, with the firm relationship obvious.

**Layout**
1. Logo block — firm logo if `logoAllowed`, else `appName` wordmark.
2. Firm code field (`lawFirmCode`) — uppercase, auto-trim, remembered after first success.
3. Username field (`username`).
4. Password field (`password`) with show/hide.
5. Primary button "Sign in"; text link "Forgot password?".
6. Small footnote: "Contact your firm administrator for access."

**Endpoint** `POST /api/v1/auth/login` (staff) · `POST /api/v1/auth/client/login` (client)

Request:

```json
{ "data": { "lawFirmCode": "TLAW", "username": "tlaw_emp1", "password": "•••••", "totpCode": null } }
```

Response (`data`, all keys except `status` are optional/omitted when null):

```json
{ "status": "SUCCESS", "accessToken": "eyJ…", "refreshToken": "eyJ…", "expiresIn": 86400000 }
```

| Field | Type | Notes |
|---|---|---|
| `status` | enum | `SUCCESS` \| `MFA_REQUIRED` \| `MFA_SETUP_REQUIRED` \| `PASSWORD_CHANGE_REQUIRED` |
| `accessToken` | string | store in secure storage |
| `refreshToken` | string | store in secure storage |
| `expiresIn` | number | ms; **hint only** (see §3.4) |
| `mfaToken` | string | short-lived, for the MFA screens |
| `mfaQrCodeUri` | string | `otpauth://…` — render as QR |
| `mfaManualKey` | string | typeable fallback |
| `passwordChangeToken` | string | first-login rotation |

**States** — inline error under the form using the server `message`; disable the button while in flight;
`401` = "wrong username or password" without revealing which part; a locked account returns
`401 "Account locked. Try again in 29 minute(s)."` — render as a distinct warning banner with a
countdown; do **not** offer retry in a loop.

**Actions** — Sign in → route by `status`; Forgot password → [S5].

---

### S2 · OTP / MFA challenge

**Goal:** second factor without friction.

**Layout** — 6 single-digit boxes (`totpCode`), auto-advance, 30 s countdown ring, "Use a different
account" text button.

**Endpoint** `POST /api/v1/auth/mfa/validate`

```json
{ "data": { "mfaToken": "<from login>", "totpCode": "123456" } }
```

→ same `data` shape as S1, `status: SUCCESS`, tokens present.

**States** — wrong code returns `401` with a generic message (the backend deliberately does not say
"wrong code"); invalidate `mfaToken` on expiry → back to login. Repeated failures bump the same lockout
counter as passwords, so show the lock banner here too.

---

### S3 · First-time MFA setup

**Goal:** enrol an authenticator once, on a phone — the natural device to do it on.

**Layout** — QR of `mfaQrCodeUri` with a "can't scan?" toggle revealing `mfaManualKey`; 6-digit input;
explainer "you'll need this code each time you sign in".

**Endpoint** `POST /api/v1/auth/mfa/setup/confirm`

```json
{ "data": { "mfaToken": "<from login>", "totpCode": "123456" } }
```

→ `data` = tokens (`status: SUCCESS`).

---

### S4 · Forced password change

**Goal:** satisfy the first-login rotation without dropping the user out.

**Layout** — "This is your first sign-in — choose your own password." Three fields; a live policy hint.

**Endpoint** `POST /api/v1/auth/change-password`

```json
{ "data": { "passwordChangeToken": "<from login>", "newPassword": "…", "confirmPassword": "…" } }
```

→ `data` = tokens; continue into Home.

---

### S5 · Forgot / reset password

**Goal:** recovery without the ERP web app.

**Step 1** — `POST /api/v1/auth/forgot-password`

```json
{ "data": { "lawFirmCode": "TLAW", "username": "tlaw_emp1" } }
```

Response `data: null`. **Always reports success** (anti-enumeration) — the copy must say *"If the account
exists, a reset link has been sent"*, never "email not found".

**Step 2** — the link opens the web reset page, or the app handles the deep link:
`POST /api/v1/auth/reset-password`

```json
{ "data": { "token": "<from link>", "newPassword": "…", "confirmPassword": "…" } }
```

This clears any lockout and revokes existing sessions → send the user to S1 with a success toast.

---

### S6 · Profile & settings (all roles)

**Goal:** identity, security, preferences, sign-out.

**Endpoint** `GET /api/v1/me`

```json
{
  "id": "31fbb032-…", "username": "tlaw_emp1", "fullName": "…", "email": "…",
  "mobileNo": "98…", "profilePhotoUrl": null, "userType": "FIRM_USER",
  "firm": {
    "id": "d7ec28f8-…", "name": "TLAW Law Firm", "lawFirmCode": "TLAW",
    "email": "…", "phone": "…", "address": "…", "jurisdiction": "…",
    "logoUrl": null, "logoAllowed": true,
    "brandPrimaryHex": null, "brandSecondaryHex": null, "isPersonalColor": null,
    "status": "ACTIVE", "isTrial": true, "trialExpiresAt": "2026-12-01T00:00:00.000",
    "daysRemaining": 50
  },
  "role": { "id": "…", "name": "ADVOCATE", "code": "ADVOCATE", "isSystem": false },
  "permissions": ["CASE_MANAGEMENT:VIEW", "CALENDAR:VIEW", "…"],
  "modules": [ { "moduleCode": "CASE_MANAGEMENT", "moduleName": "Case Management",
                 "icon": "FolderIcon", "path": "/cases", "enabled": true,
                 "actions": ["VIEW"], "subModules": [] } ],
  "brandColorPrimary": "#1A73E8", "brandColorSecondary": "#0F9D58",
  "appName": "Tarikh", "logoAllowed": true, "logoUrl": null,
  "isActive": true, "lastLoginAt": "2026-10-12T11:53:00.000"
}
```

**Design rules that fall out of this payload**
- `permissions[]` is the client-side feature flag list. Build the tab bar and the "More" hub from it:
  `CASE_MANAGEMENT:VIEW` → Cases, `CALENDAR:VIEW` → Day, `DOCUMENT_MANAGEMENT:VIEW` → Files,
  `CLIENT_MANAGEMENT:VIEW` → Clients, `EMPLOYEE:VIEW` → Team, `FIRM_CONFIG:VIEW` → branding.
  Never show an entry the array does not contain.
- `modules[].enabled` (and `subModules`) means the *firm* disabled that module on its plan — show a
  friendly "not in your plan" state instead of a 403 error screen.
- `role.code` chooses the shell; `userType` is the coarse type.

**Layout blocks** — avatar/initials + `fullName` + `role.name`; firm card (`firm.name`, `lawFirmCode`,
`firm.phone`, trial badge from `daysRemaining`); Security section (Change password → the endpoint below,
MFA status, "Sign out of all devices"); Preferences → [S10]; About (version, `appName`).

**Change password** — `POST /api/v1/me/change-password`

```json
{ "data": { "currentPassword": "…", "newPassword": "…", "confirmPassword": "…" } }
```

Response `data: null`; message says other sessions were signed out → force re-login locally.

**Log out** — `POST /api/v1/auth/logout` (no body). **Warn the user**: it ends every session on every
device. Clear secure storage either way.

---

### S7 · Firm Admin — Home / dashboard

**Goal:** the 10-second pulse of the firm.

**Endpoint** `GET /api/v1/dashboard/firm` (guards: `FIRM_ADMIN` + `DASHBOARD_MANAGEMENT:VIEW`)

`data` blocks and fields:

| Block | Fields |
|---|---|
| `caseStats` | `totalMatters`, `activeMatters`, `dormantMatters`, `closedMatters`, `staleMatters` |
| `todayEvents[]` | `matterTitle`, `courtRoom`, `scheduledTime`, `judgeName`, `attendingAdvocateName` |
| `upcomingHearings[]` | `matterTitle`, `hearingDate`, `courtName`, `daysUntil` |
| `invoiceStats` | `totalInvoices`, `outstanding`, `overdue`, `totalOutstandingAmount`, `overdueAmount` |
| `overdueInvoices[]` | `invoiceNumber`, `clientName`, `amount`, `daysOverdue` |
| `renewalStats` | `dueThisMonth`, `overdue` |
| `upcomingRenewals[]` | `projectName`, `renewalTitle`, `dueDate`, `daysUntil` |
| `teamCaseload[]` | `advocateName`, `openMatters` |
| `recentActivity[]` | `summary`, `userName`, `createdAt` |

**Layout** — greeting header with firm name + date; stat row of 3–4 tappable tiles (`activeMatters`,
`todayEvents.length`, `overdue` invoices, `staleMatters`); "Today's cause list" card list (each row:
`scheduledTime` chip, `matterTitle`, `courtRoom`, `attendingAdvocateName`); "Overdue" section with
`daysOverdue` badges; "Team" horizontal chips with `advocateName` + `openMatters`; "Recent activity"
timeline.

**Empty states** — "No hearings today", "Nothing overdue — good", "No activity yet".

---

### S8 · Employee — Home / dashboard

**Endpoint** `GET /api/v1/dashboard/employee` (guards: `ADVOCATE`/`PARALEGAL` + `DASHBOARD_MANAGEMENT:VIEW`)

| Block | Fields |
|---|---|
| `myCaseStats` | `openMatters`, `upcomingHearings`, `staleMatters` |
| `myTodayEvents[]` | `matterTitle`, `ourCourtCaseRef`, `courtRoom`, `scheduledTime`, `judgeName`, `status` |
| `myUpcomingHearings[]` | `matterTitle`, `ourCourtCaseRef`, `hearingDate`, `courtName`, `daysUntil` |
| `myUpcomingDeadlines[]` | `matterTitle`, `ourCourtCaseRef`, `deadline`, `deadlineType`, `daysRemaining`, `isUrgent` |
| `myStaleMatters[]` | `matterTitle`, `ourCourtCaseRef`, `lastHearingDate`, `daysSinceLastHearing` |
| `recentUpdates[]` | `matterTitle`, `ourCourtCaseRef`, `updateType`, `description`, `createdAt` |

**Layout** — compact stat trio; "Today" list with a time rail; "Deadlines" list where `isUrgent` turns the
row's accent red and pins it above non-urgent ones; "Needs attention" (`myStaleMatters`) with
`daysSinceLastHearing`; "What changed" feed from `recentUpdates`.

Every row shows `ourCourtCaseRef` — it is how lawyers talk about a case, so it is the row's second line,
and it links to the case detail [S12].

---

### S9 · Client — Home / dashboard

**Endpoint** `GET /api/v1/dashboard/client` (guard: `CLIENT` + `DASHBOARD_MANAGEMENT:VIEW` — **403 today**,
see §9.1)

| Block | Fields |
|---|---|
| `myMatterStats` | `totalMatters`, `activeMatters`, `closedMatters` |
| `myMatters[]` | `matterNumber`, `title`, `status`, `courtName`, `ourCourtCaseRef`, `lastUpdate`, `nextHearingDate` |
| `myNextHearing` | `matterTitle`, `ourCourtCaseRef`, `hearingDate`, `courtName`, `daysUntil` |
| `myUpcomingEvents[]` | `matterTitle`, `ourCourtCaseRef`, `eventDate`, `eventType`, `courtRoom` |
| `myInvoiceStats` | `totalInvoices`, `outstanding`, `outstandingAmount` |
| `myOutstandingInvoices[]` | `invoiceNumber`, `amount`, `dueDate`, `daysUntil` |
| `myRecentUpdates[]` | `matterTitle`, `ourCourtCaseRef`, `updateType`, `description`, `createdAt` |

**Layout** — calm and reassuring: a "next hearing" hero card (`myNextHearing` with `daysUntil` =
"in 3 days"); matter cards with a status pill; an "Updates" feed phrased for a client
(`updateType` + `description`); "Outstanding" card with `amount` + `dueDate` and a note that payment is
arranged with the firm (no pay button — there is no billing endpoint).

**If §9.1 is not fixed before build:** assemble this screen from `GET /api/v1/client/matters`
(`ClientMatterResponse`) + `GET /api/v1/notifications`, and omit invoice/stat blocks entirely.

---

### S10 · Notifications (all roles)

**Goal:** the reason people open the app.

**Endpoints**

```
GET  /api/v1/notifications?unreadOnly=false&page=0&size=20   → PagedResponse<NotificationResponse>
GET  /api/v1/notifications/unread-count                      → { "count": 4 }
POST /api/v1/notifications/{notificationId}/read             → data: null
POST /api/v1/notifications/read-all                          → { "updated": 12 }
GET  /api/v1/notifications/preferences                        → array of NotificationPreferenceView
PUT  /api/v1/notifications/preferences                        → body { "data": { "type": …, "emailEnabled": true } }
POST /api/v1/notifications/broadcast                          → FIRM_ADMIN only
```

`NotificationResponse` fields: `id`, `type`, `category`, `title`, `body`, `referenceType`,
`referenceId`, `read`, `readAt`, `createdAt`.

`NotificationPreferenceView` fields: `type`, `emailEnabled`, `locked` (`locked: true` = the backend
refuses changes — render the toggle disabled with a reason).

**Layout** — segmented filter "All / Unread" (`unreadOnly`); grouped by day; each row: type icon, `title`
(bold when `read == false`), `body` (2-line clamp), relative time from `createdAt`, unread dot.
Swipe a row right to mark read (`POST …/{id}/read`). A "Mark all read" action in the app bar.
Tap → deep link by `referenceType` + `referenceId` (map: matter/case → [S12], hearing/event → [S14],
document → [S18], invoice → [S9] block, announcement → a read-only detail sheet).

**Deep-link resolver** — the app should tolerate unknown `referenceType` values by opening the
notification detail sheet instead of failing.

**Type → icon/colour map** (from `NotificationType`, with the categories in brackets):

| `type` | Category | Suggested treatment |
|---|---|---|
| `HEARING_REMINDER` | ALERT | calendar icon, warning accent — highest priority |
| `APPEAL_DEADLINE` | ALERT | gavel/clock icon, danger accent |
| `CASE_ASSIGNED` | SYSTEM | folder icon, neutral |
| `INVOICE_STATUS` | SYSTEM | receipt icon, neutral |
| `APPEAL_LAPSED` | SYSTEM | danger |
| `TRIAL_EXPIRING` | ALERT | warning |
| `TRIAL_EXPIRED` | SYSTEM | danger |
| `ANNOUNCEMENT` | BROADCAST | megaphone, pinned styling |

**Push notifications:** the backend has **no device-token/APNs/FCM endpoint today** and its own doc says
to poll the unread count every 45–60 s. v1 = foreground polling + local notifications or a badge only;
§9.4 covers what the backend must expose for real push.

---

### S11 · Cases / matters list (Firm Admin + Employee)

**Endpoint**

```
GET /api/v1/firm/matters?matterType=&status=&clientUserId=&search=&page=0&size=20
```

- `matterType` ∈ `CIVIL` | `CRIMINAL`
- `status` ∈ `ACTIVE` | `DORMANT` | `CLOSED`
- `clientUserId` (UUID) — filter to one client
- `search` — free text
- Returns Spring `Page<MatterResponse>` (§3.3)

`MatterResponse` fields: `id`, `matterNumber`, `matterType`, `title`, `status`, `currentCourtCaseId`,
`assignedPartnerId`, `clientUserId`, `clientName`, `originatingCourtLevel`, `description`,
`courtCases[]`, `parties[]`, `createdAt`, `updatedAt`.

Also useful:

```
GET /api/v1/firm/matters/stale?days=90&page=0&size=20   → Page<StaleMatterResponse>
GET /api/v1/firm/matters/timeline?from=&to=&page=0&size=20 → Page<TimelineEventResponse>  (firm-wide feed)
```

**Layout** — search field pinned under the app bar; filter chips (status + type); list rows:
`title` (1-line), `matterNumber` + `clientName`, status pill, `originatingCourtLevel` as a small tag,
`updatedAt` as relative time. Employee default = "my matters" (client-side filter on
`assignedPartnerId`/assignments until a dedicated endpoint exists).

**Empty state** — "No cases match these filters" with a "clear filters" action.

---

### S12 · Case detail

**Endpoint** `GET /api/v1/firm/matters/{matterNumber}` → `MatterResponse` (full, with children)

**Layout** — header: `title`, `matterNumber`, status pill, `matterType` tag.
Sections:
- **Court cases** (`courtCases[]`, `CourtCaseResponse`) — each with `ourCourtCaseRef`, court name, stage.
- **Parties** (`parties[]`, `MatterPartyResponse`).
- **Timeline** — `GET /api/v1/firm/matters/{matterNumber}/timeline` → array of `TimelineEventResponse`
  (`id`, `matterNumber`, `matterTitle`, `ourCourtCaseRef`, `eventType`, `title`, `description`, `createdAt`, `createdBy`), rendered as a vertical timeline.
- **Documents** — `GET /api/v1/firm/matters/{matterNumber}/documents` (firm admin only today).
- **Events** — `GET /api/v1/firm/court-cases/{ourCourtCaseRef}/events` → array of `CourtEventResponse`.

⚠️ The matter-number path variable is the **string** `matterNumber`, not a UUID.

---

### S13 · Client — My matters

**Endpoint** `GET /api/v1/client/matters` → plain array of `ClientMatterResponse`

Fields: `id`, `matterNumber`, `title`, `matterType`, `status`, `originatingCourtLevel`,
`currentCourtCaseRef`, `courtName`, `stage`, `nextHearingAt`, `createdAt`, `updatedAt`.
Detail: `GET /api/v1/client/matters/{matterNumber}`.

**Layout** — card list, one card per matter: `title`, `matterNumber`, status pill, `courtName`,
`stage` (humanise the enum, §7.3), "Next hearing" line from `nextHearingAt`.
Detail screen: same facts plus a plain-language "what stage this is" line (use the stage dictionary in
§7.3) — this is the single most valuable thing for a client.

---

### S14 · Calendar / Day (Firm Admin + Employee)

**Endpoints** (all `@RequiresModule("CALENDAR")` + `CALENDAR:VIEW`)

```
GET /api/v1/firm/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD&advocateId=
GET /api/v1/firm/calendar/today?advocateId=
GET /api/v1/firm/calendar/upcoming?days=7&advocateId=
```

- `from`/`to` default to last 30 → next 90 days; accept `YYYY-MM-DD` or full ISO.
- `days` must be 1–365.
- Returns a plain array of `CalendarEventResponse`.

`CalendarEventResponse`: `id`, `courtCaseId`, `ourCourtCaseRef`, `matterNumber`, `matterTitle`,
`eventType` (`TARIK` | `PESHI`), `scheduledDate`, `scheduledTime`, `endTime`, `courtRoom`, `status`,
`attendingAdvocateId`.

Event detail: `GET /api/v1/firm/court-events/{eventId}` → `CourtEventResponse` with extra fields
`sequenceNo`, `outcome`, `outcomeType`, `nextEventType`, `nextEventId`, `judgeName`, `notes`,
`conflictWarning`.

**Layout** — month strip at the top with dot markers, agenda below for the selected day; a
"Today | Upcoming 7 days" segmented control. Row: `scheduledTime` on the left rail, `matterTitle`,
`courtRoom`, `eventType` badge (`TARIK` = first listing, `PESHI` = follow-up date), `status` chip.
If `conflictWarning` is non-null on a detail screen, show a warning banner — the backend computed it.

**Status colours** — `SCHEDULED` neutral/primary, `HELD` success, `ADJOURNED` warning, `CANCELED`
muted/strikethrough.

---

### S15 · Clients (Firm Admin)

**Endpoint** `GET /api/v1/firm/clients?page=0&size=20` → `PagedResponse<ClientResponse>`
(`@RequiresModule("CLIENT_MANAGEMENT")`, `hasRole('FIRM_ADMIN')`)

`ClientResponse`: `id`, `username`, `email`, `mobileNo`, `fullName`, `userType`, `isActive`,
`portalAccessEnabled`, `createdAt`.

Detail: `GET /api/v1/firm/clients/{clientId}`. Portal toggle (phase 2):
`PATCH /api/v1/firm/clients/{clientId}/portal-access`.

**Layout** — searchable list with avatar initials, `fullName`, `email`/`mobileNo`, a
"portal on/off" chip from `portalAccessEnabled`, inactive rows dimmed.
Detail: contact actions (call/email/message), matters of that client
(`GET /api/v1/firm/matters?clientUserId={id}`).

---

### S16 · Team (Firm Admin)

**Endpoint** `GET /api/v1/firm/employees?page=0&size=20` → `PagedResponse<EmployeeResponse>`
(`@RequiresModule("EMPLOYEE")`, `hasRole('FIRM_ADMIN')`)

`EmployeeResponse`: `id`, `username`, `email`, `mobileNo`, `fullName`, `userType`, `isActive`, `roleId`,
`roleName`, `roleCode`, `employeeCode`, `designation`, `barCouncilNo`, `specialization`, `joiningDate`,
`emergencyContactName`, `emergencyContactPhone`, `notes`, `createdAt`.

Detail: `GET /api/v1/firm/employees/{employeeId}`.

**Layout** — grouped by `roleCode` with a count header ("Advocates · 6"); row: initials, `fullName`,
`designation`, `barCouncilNo` tag; inactive dimmed. Detail: contact + professional facts, "open matters"
from the firm dashboard's `teamCaseload` where `advocateName` matches.

---

### S17 · Firm profile & branding (Firm Admin)

**Endpoints** — `GET /api/v1/firm/profile`, `GET/PUT /api/v1/firm/brand/theme`,
`GET/POST /api/v1/firm/brand/logo`, `PATCH /api/v1/firm/brand/logo/allowed`.
Branding values themselves: `GET/PUT /api/v1/firm/config` (**the PUT takes a flat map, not `data`-wrapped**).

`FirmProfileResponse`: `id`, `lawFirmCode`, `name`, `firmType`, `status`, `email`, `phone`, `address`,
`jurisdiction`, `logoUrl`, `logoAllowed`, `brandPrimaryHex`, `brandSecondaryHex`, `isPersonalColor`,
`isTrial`, `trialDays`, `trialExpiresAt`, `createdAt`.

**Layout** — read-only profile card set (name, code, contact, jurisdiction, status, trial countdown);
"Brand" section with two colour swatches + a live preview of the app bar using the chosen hexes;
logo card with an allow-toggle. Phase 2 for editing; v1 can be view-only with a hint to use the web app.

---

### S18 · Documents (view + download)

**Firm:** `GET /api/v1/firm/documents?status=&visibility=&search=&page=0&size=20` →
`PagedResponse<DocumentResponse>`
**Client:** `GET /api/v1/client/documents?matterNumber=&projectCode=&search=&page=0&size=20`
**Download URL (both):** `GET …/documents/{documentId}/download-url`

`DocumentResponse`: `id` (Long), `uuid`, `fileName`, `contentType`, `extension`, `sizeBytes`, `status`,
`visibility`, `documentUrl`, `matterNumber`, `projectCode`, `courtCaseId`, `uploadedByUserId`,
`createdAt`, `archivedAt`.

`DownloadUrlResponse` (record): `documentUuid`, `downloadUrl`, `fileName`, `expiresAt` (Instant).

**Design rules**
- The download URL is **short-lived** — mint it on tap, then hand it to the OS downloader/opener; never
  cache or pre-fetch it.
- Show `extension`/`contentType` icons; `sizeBytes` humanised; `visibility` as a lock/open chip;
  `status` (`ACTIVE`/`ARCHIVED`) as a muted tag.
- In-app preview is worth it for `contentType` = PDF and images; otherwise "open with system".
- Employees get `403` on this module today (§9.2) — hide the Files tab unless
  `DOCUMENT_MANAGEMENT:VIEW` is in `me.permissions`.

---

### S19 · More hub (Firm Admin) & Client projects

**Firm Admin "More"** — grouped list driven by `permissions[]`: Clients, Team, Firm profile & branding,
Send announcement ([S20]), Notification preferences, Change password, About, Sign out.

**Client projects** — `GET /api/v1/client/projects` → array of `ClientProjectResponse`
(`id`, `projectCode`, `name`, `description`, `status`, `startDate`, `targetEndDate`,
`upcomingRenewals[]`, `createdAt`);
detail `GET /api/v1/client/projects/{projectCode}`;
renewals `GET /api/v1/client/projects/{projectCode}/renewals` → `RenewalInstanceResponse[]`
(`id`, `dueDate`, `status`, `completedAt`, `completedByName`, `notes`).
Layout: project cards with a status pill and an "upcoming renewals" count; detail shows the renewal
timeline.

---

### S20 · Announcement broadcast (Firm Admin, phase 2)

`POST /api/v1/notifications/broadcast` (`hasRole('FIRM_ADMIN')`)

```json
{ "data": { "title": "Office closed Friday", "body": "Court matters are rescheduled.", "audience": "ALL" } }
```

`audience` is `ALL` or a role code (e.g. `ADVOCATE`).
Compose screen: title, body, audience picker, preview as a notification row, send → confirm dialog.

---

## 7. Reference dictionaries

### 7.1 Enums you must map to human copy

| Enum | Values |
|---|---|
| `MatterType` | `CIVIL`, `CRIMINAL` |
| `MatterStatus` | `ACTIVE`, `DORMANT`, `CLOSED` |
| `CourtLevel` | `DISTRICT`, `HIGH`, `SUPREME`, `SPECIALIZED` |
| `CourtEventType` | `TARIK`, `PESHI` |
| `CourtEventStatus` | `SCHEDULED`, `HELD`, `ADJOURNED`, `CANCELED` |
| `NotificationCategory` | `SYSTEM`, `ALERT`, `BROADCAST` |
| `DocumentStatus` (filter) | `ACTIVE`, `ARCHIVED` (as exposed by the filter param) |
| `UserType` | `SUPER_ADMIN`, `FIRM`, `FIRM_USER`, `CLIENT` |
| `FirmStatus` | e.g. `TRIAL`, `ACTIVE`, `SUSPENDED`, `EXPIRED` (a suspended/expired firm cannot log in at all) |

### 7.2 Court-case stages (`CourtCaseStage`) — for the client-facing copy

`FILED`, `SUMMONS_ISSUED`, `RESPONSE_PENDING`, `MEDIATION`, `HEARING_STAGE`, `JUDGMENT_AWAITED`,
`JUDGMENT_DELIVERED`, `APPEALED`, `EXECUTION`, `SENTENCING`, `CLOSED`, `FIR_REGISTERED`,
`UNDER_INVESTIGATION`, `CHARGE_SHEET_FILED`, `APPEAL_FILED`, `ADMITTED`, `NOTICE_ISSUED`,
`FURTHER_APPEALED`, `REMANDED`, `WRIT_FILED`.

Write a one-line plain-language gloss for each (e.g. `SUMMONS_ISSUED` → "the court has issued a summons
to the other side"). Show the gloss to clients, the raw label to staff.

### 7.3 Status colour semantics

| Meaning | Values | Colour |
|---|---|---|
| Alive / in progress | `ACTIVE`, `SCHEDULED`, `HEARING_STAGE`, `UNDER_INVESTIGATION` | primary |
| Needs attention | `DORMANT`, `ADJOURNED`, `APPEAL_DEADLINE`, `TRIAL_EXPIRING`, `stale`, `overdue` | warning |
| Done | `CLOSED`, `HELD`, `JUDGMENT_DELIVERED`, `paid` | success/muted |
| Dead / danger | `CANCELED`, `APPEAL_LAPSED`, `TRIAL_EXPIRED`, `daysOverdue > 0` | danger |

---

## 8. UX rules that must survive review

1. **Court-friendly:** huge time and reference text, high contrast, one-hand reachability for the Day
   and Cases lists. Assume a user standing in a corridor.
2. **Offline-tolerant:** cache the last successful dashboard, matters list and today's events; show them
   with a "last updated <time>" chip and an offline banner. Never show a blank screen when a cache exists.
3. **Stale-while-revalidate:** paint cache, then refresh (pull-to-refresh, focus, push tap).
4. **Dates:** "Today", "Tomorrow", "in 3 days", then `DD MMM`. Always keep the raw `hearingDate` on the
   detail screen. Never show a bare ISO string.
5. **Empty ≠ error.** Distinguish "nothing here" (illustration + one line) from "you can't see this"
   (403 → hide the surface) from "something broke" (retry).
6. **Errors are shown, not swallowed:** surface `message` from `ApiResponse` — it is already human copy.
7. **Accessibility:** 4.5:1 contrast for text, ≥48 dp touch targets, screen-reader labels for
   `ourCourtCaseRef`/`matterNumber`, no colour-only status (pair with text/icon).
8. **Security:** tokens in secure storage; no PII in logs; screenshot blocking on the documents viewer is
   a nice-to-have; **MFA setup screens must not be screenshot-able** if the platform allows.
9. **Never cache a `downloadUrl`** — it expires.

---

## 9. Backend gaps to close before/with the build

These are verified from the code and the live dev database, not assumptions. They block specific screens.

**9.1 Client dashboard + client documents return 403 today.** `CLIENT` holds **0 permissions**, and
`PermissionEvaluator.require()` exempts only Super Admin. So `GET /api/v1/dashboard/client` (needs
`DASHBOARD_MANAGEMENT:VIEW`) and `GET /api/v1/client/documents` (needs `DOCUMENT_MANAGEMENT:VIEW`) both
fail. Fix by granting those two VIEW permissions to the CLIENT role, or by exempting client scope in the
same way `GET /api/v1/client/matters` already does (`ReadScopeGuard.isClientScope()`). **Needed for:**
the client Home and Files tabs.

**9.2 Employees have no `DOCUMENT_MANAGEMENT` permission** (`ADVOCATE` clone = `CALENDAR`,
`CASE_MANAGEMENT`, `DASHBOARD_MANAGEMENT`, `NOTIFICATION_MANAGEMENT` only), so the firm document list
403s for them. Either grant `DOCUMENT_MANAGEMENT:VIEW` or accept a Files-less employee app.

**9.3 No client-facing invoice endpoint.** `InvoiceController` is super-admin only (`GET/POST/PUT/DELETE`,
`/{id}/pdf`, `/{id}/send`). Clients and firm admins only see invoice *counts and totals* inside their
dashboards. There is no list, no detail and no PDF for them. **Needed for:** any "Invoices" screen.

**9.4 No push infrastructure.** No device-token registration endpoint, no APNs/FCM sender. v1 polls
(`/notifications/unread-count` every 45–60 s, per the backend's own guidance). To ship real push, the
backend needs: register/unregister device token, a send path on notification creation, and a token
lifecycle (expiry/rotation).

**9.5 `expiresIn` is hardcoded** to `86400000` in `AuthMapper` even when the firm config sets a
1-minute access-token lifetime — decode the JWT `exp` instead (§3.4). Trivial backend fix, worth doing.

**9.6 Notifications are RBAC-gated too.** Employees hold `NOTIFICATION_MANAGEMENT:*`; the CLIENT role
holds nothing, so the Inbox must be verified for clients before it is designed as a tab.

**9.7 No "my matters" shortcut for employees.** The employee dashboard has "my" data, but the matters
list is firm-wide with no `assignedToMe` filter — v1 does it client-side.

---

## 10. Out of scope for v1 (and why)

| Not in v1 | Reason |
|---|---|
| Creating/editing matters, court cases, parties, events | Form-heavy, error-prone on mobile; the user asked for view + notifications |
| Document upload / visibility changes / delete | Multipart + sharing decisions belong on a bigger screen |
| Creating clients/employees, role changes, portal toggles | Admin work; phase 2 candidates |
| Billing edits, invoice send/PDF | No client/firm endpoints exist (§9.3) |
| Reports, audit logs, RBAC, modules, scraper admin | Desktop-only surfaces |
| Super-admin (`SUPER_ADMIN`) anything | Platform administration is not a phone job |
| Full-text search across the firm | Only per-list `search` params exist |
| Chat / messaging | No backend |

---

## 11. Copy-paste brief for an AI design tool

> Design a mobile app for "Tarikh", a law-firm ERP. Three personas share one app: **Firm Admin**,
> **Employee (advocate/paralegal)** and **Client**. Five bottom tabs, role-shaped:
> Admin = Home · Cases · Day · Inbox · More; Employee = Home · My Cases · Day · Inbox · Me;
> Client = Home · My Cases · Files · Inbox · Me.
> Tone: calm, professional, dense-but-legible, trustworthy — closer to a banking app than a consumer
> app. Neutral surfaces, one **tenant-configurable primary colour** (hex arrives at runtime, so every
> colour must live in tokens), 8-pt spacing, 16 sp body, large time/reference typography.
> Every list has: pull-to-refresh, skeleton loading, an illustrated empty state, and an offline state
> showing cached data with a "last updated" chip.
> Screens and their contents are specified in §6 (S1–S20); field names in §6 and §7 are the real API
> keys and must be used verbatim in the design spec so the implementation maps 1:1.
> Status colours follow §7.3; every status is text + colour, never colour alone.
> Deliver: navigation map, one screen per S-number, component inventory (stat tile, case card, event row,
> notification row, status pill, timeline item, empty/offline/error states), light + dark themes, and
> token list.

---

*Source of truth: `src/main/java/com/lawfirm/erp/**` on branch `devG`, verified 2026-10-12. If a field
name here disagrees with the code, the code wins — and this file should be corrected.*
