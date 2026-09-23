# Reset & Change Password — Frontend Implementation Guide

Version 1.0 — 2026-09-23 — branch `devG`
Backend refs: `modules/usermanagement/…/UserManagementServiceImpl`, `superadmin/…/SuperAdminServiceImpl`,
`auth/…/AuthServiceImpl`, `modules/me/…/MeServiceImpl`, `common/util/PasswordPolicy`,
`common/dto/RequestBodyBinder`, `templates/email/password-reset-notice.html`
Covers checklist items **1.8** (admin reset), **1.9** (change password), and the forced-rotation leg of **1.12**.

---

## 1. The whole feature on one page

There are three separate screens and they are easy to confuse. Each hits a different endpoint.

```
① ADMIN CONSOLE — "Reset password" on a user row
   POST /modules/users/{userId}/reset-password   (or /super-admin/users/{userId}/reset-password)
        │  server sets a password + mustChangePassword=true + kills that user's sessions
        │  response tells you whether the server had to INVENT the password
        └─> if generated: render it ONCE in the dialog, with a copy button

② USER SIGNS IN with the password from ①  (also e-mailed to them)
   POST /auth/login  →  { status: "PASSWORD_CHANGE_REQUIRED", passwordChangeToken }
        │  NO access token yet — the app is not reachable
        └─> POST /auth/change-password { passwordChangeToken, newPassword, confirmPassword }
              → SUCCESS tokens (or MFA screens), user is in

③ USER PROFILE — signed in, changing their own password voluntarily
   POST /me/change-password { currentPassword, newPassword, confirmPassword }
        └─> succeeds, then EVERY session including this one is dead → send them back to login
```

Rule of thumb: **① is an admin acting on someone else** (`USER_MANAGEMENT:EDIT`), **② is a forced
gate inside the login flow** (no permission needed, the token *is* the authorisation), **③ is
self-service from inside the app**.

---

## 2. Endpoints

| # | Method | Path | Who | Gate |
|---|---|---|---|---|
| ①a | POST | `/api/v1/modules/users/{userId}/reset-password` | Firm admin console | permission `USER_MANAGEMENT:EDIT`; target must be in the caller's firm |
| ①b | POST | `/api/v1/super-admin/users/{userId}/reset-password` | Super Admin console | role `SUPER_ADMIN` |
| ②a | POST | `/api/v1/auth/login` | login screen | public |
| ②b | POST | `/api/v1/auth/change-password` | forced-rotation screen | the short-lived `passwordChangeToken`, no Bearer header |
| ③ | POST | `/api/v1/me/change-password` | profile / settings | Bearer access token |
| — | POST | `/api/v1/auth/forgot-password` + `/api/v1/auth/reset-password` | "forgot password" link | public — *self-service e-mail link, a different feature; see §11* |

①a and ①b are **the same contract**. Build one dialog component and swap the URL.

---

## 3. ① Admin reset — request

Header: `Authorization: Bearer <accessToken>`, `Content-Type: application/json`.

The password is **optional**. Every one of these is valid:

| Body | Result |
|---|---|
| `{"newPassword":"Ylaw@2026x"}` | that password is set · `generated: false` |
| `{"password":"…"}` · `{"pwd":"…"}` · `{"new_password":"…"}` | same — accepted aliases |
| `{"data":{"newPassword":"…"}}` | same — envelope also unwrapped |
| `{}` | **server generates** · `generated: true` |
| *(no body at all)* | **server generates** · `generated: true` |
| `{"data":{}}` | **server generates** · `generated: true` |
| `{"newPassword":""}` | **server generates** ⚠️ see §8 |
| `{"newPassword":"short"}` | `400` `"Password must be 8-50 characters"` |

**Send `{}` when the admin left the field empty. Never send `""`.** Both mean "generate", but `{}`
states that intent on purpose while `""` looks like a bug to the next reader.

```ts
const res = await fetch(`${baseUrl}/api/v1/modules/users/${userId}/reset-password`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify(newPassword ? { newPassword } : {}),
});
```

### Response

```json
{
  "success": true,
  "responseCode": 200,
  "message": "Password reset successfully. User must re-login.",
  "data": {
    "username": "advocate1",
    "generated": true,
    "temporaryPassword": "Kx7#mQp2Rt4w",
    "mustChangePassword": true
  }
}
```

| Field | Meaning |
|---|---|
| `generated` | `true` = the server invented the password and you must show it. `false` = the admin typed it. |
| `temporaryPassword` | Populated **only** when `generated` is true. **Show-once** — no endpoint can return it again. |
| `mustChangePassword` | Always `true`. The user is forced to rotate it at next login. |
| `username` | Echo for the confirmation message ("Password reset for `advocate1`"). |

### Errors

`responseCode` mirrors the HTTP status, and `data` is absent/null on every error.

```json
{ "success": false, "responseCode": 403, "message": "User does not belong to your firm" }
```

| Status | `message` | When | What to show |
|---|---|---|---|
| 400 | `Password must be 8-50 characters` | supplied password violates the policy | inline on the password field |
| 400 | `Malformed request body: …` | broken JSON | generic error toast (a bug, not user error) |
| 401 | `Invalid username or password` | admin's session expired | global logout → login |
| 403 | `User does not belong to your firm` | crossing firms | toast; refresh the list |
| 403 | `You do not have permission to perform this action` | missing `USER_MANAGEMENT:EDIT` | hide the button instead |
| 404 | `User not found` | stale row (user deleted) | toast + refresh the list |

---

## 4. ② Forced rotation (the login gate)

### 4a. Login

```http
POST /api/v1/auth/login
{ "data": { "lawFirmCode": "YLAW", "username": "advocate1", "password": "<the temp password>" } }
```

Branch on `data.status` — this enum drives the entire auth router:

| `status` | Payload present | Next screen |
|---|---|---|
| `SUCCESS` | `accessToken`, `refreshToken`, `expiresIn` | the app |
| `PASSWORD_CHANGE_REQUIRED` | `passwordChangeToken` | **the rotate screen (§4b)** |
| `MFA_SETUP_REQUIRED` | `mfaToken`, `mfaQrCodeUri`, `mfaManualKey` | QR setup |
| `MFA_REQUIRED` | `mfaToken` | 6-digit code |

```ts
if (data.status === 'PASSWORD_CHANGE_REQUIRED') {
  sessionStorage.setItem('passwordChangeToken', data.passwordChangeToken);
  navigate('/change-password', { replace: true });
}
```

A user who has been reset can **never** skip this: every login keeps returning
`PASSWORD_CHANGE_REQUIRED` until they rotate. There is no access token to leak in the meantime.

### 4b. Rotate

```http
POST /api/v1/auth/change-password
{ "data": { "passwordChangeToken": "…", "newPassword": "…", "confirmPassword": "…" } }
```

No `Authorization` header — the token in the body is the credential. It expires in **10 minutes**
and is single-use.

The response is a **`LoginResponse`**, so it is not necessarily a token pair:

```json
{ "success": true, "responseCode": 200, "message": "Password changed",
  "data": { "status": "SUCCESS", "accessToken": "…", "refreshToken": "…", "expiresIn": 86400000 } }
```

Handle it exactly like a login result: `SUCCESS` → store tokens and enter the app;
`MFA_SETUP_REQUIRED` / `MFA_REQUIRED` → continue into the MFA screens with the returned `mfaToken`.
**A rotation by an MFA-enabled user does not hand back tokens.**

### 4c. Errors here are masked — read this before writing the screen

`/auth/change-password` throws `BadCredentialsException` for three different causes, and the global
handler turns all of them into the **same 401 `"Invalid username or password"`**:

| Real cause | Response |
|---|---|
| `newPassword` ≠ `confirmPassword` | `401 Invalid username or password` |
| token expired / already used / malformed | `401 Invalid username or password` |
| `newPassword` length outside 8–50 | `400 Password must be 8-50 characters` |

So: **validate the match client-side** (never let a mismatch reach the server — you cannot explain
the resulting 401), and treat any **401 on this screen as "start over"** → clear the stored
`passwordChangeToken`, show "Your reset link expired — please sign in again", and route to login.
Do not try to distinguish mismatch from expiry from the response.

---

## 5. ③ Profile — change your own password

```http
POST /api/v1/me/change-password
Authorization: Bearer <accessToken>

{ "data": { "currentPassword": "…", "newPassword": "…", "confirmPassword": "…" } }
```

```json
{ "success": true, "responseCode": 200,
  "message": "Password changed. Other sessions have been signed out.", "data": null }
```

Errors — unlike §4c these are all **400 with a usable message**, so put them on the field:

| `message` | Cause |
|---|---|
| `Current password is incorrect` | `currentPassword` wrong |
| `Please choose a password you have not used before` | new == current |
| `Passwords do not match` | confirm mismatch |
| `Password must be 8-50 characters` | length |

> ⚠️ **The success message is misleading.** Changing your own password bumps
> `permissionVersion`, which invalidates **every** token for the account — including the caller's
> own current session and its refresh token. The very next API call from this tab returns 401.
>
> After a `200`: clear the stored tokens immediately, show "Password changed — please sign in
> again", and route to login. Do not attempt to keep the session alive; the refresh token is dead too.

---

## 6. UI flows

### ① Admin console — the reset dialog

The console's current dialog is a **confirmation only** and posts no body. That is supported, but
the design leaves the admin holding a credential they can only read from the response. Both bugs
filed for this (checklist 1.8) traced back to that dialog.

Recommended dialog:

```
┌─ Reset password — Test Advocate (advocate1) ─────────────────┐
│                                                              │
│  ○ Let the system generate a password                        │
│      A strong temporary password is created and shown to you  │
│      once. The user also receives it by e-mail.               │
│                                                              │
│  ● Set a specific password                                   │
│      [ ...................... ]  (8–50 characters)            │
│      ☐ show  ·  strength hint: minimum 8, no other rules      │
│                                                              │
│  The user's current sessions end immediately and they must    │
│  choose their own password on next sign-in.                   │
│                                                              │
│                                    [ Cancel ]  [ Reset ]      │
└──────────────────────────────────────────────────────────────┘
```

On success:

- **`generated: true`** → replace the dialog body with the password, monospace, selectable, a
  **Copy** button, and an unmistakable *"This is shown only once. It has also been e-mailed to the
  user."* The `Reset` button disappears; only `Done` remains. Do not close on success and do not
  show it in a toast that auto-dismisses.
- **`generated: false`** → you already know the password; nothing to display. Just confirm.

Rules for the trigger button:
- Show it only when the caller has `USER_MANAGEMENT:EDIT` (never a Super-Admin-only override).
- Reset is **bulk-unsafe** by nature — never offer it from the bulk action bar.
- Consider hiding it on the admin's own row: it is allowed server-side, but it logs them out on
  every device and forces their own rotation.

### ② The rotate screen (post-login)

- Two fields, `newPassword` + `confirmPassword`, min 8 / max 50, plus a match check on blur.
- Show the username and do not offer a "back" — the only exits are success or re-login.
- No password-manager confusion: mark it as a *change*, not a *new sign-in* form.

### ③ Profile

- Three fields. `currentPassword` can be omitted only if you replace the whole flow with a
  re-authentication prompt — the backend requires it.
- After success: global logout (see the warning in §5).

---

## 7. Types to copy

```ts
export interface ApiResponse<T> {
  success: boolean;
  responseCode: number;   // mirrors the HTTP status; 200 on success
  message: string;
  data: T;
}

export interface PasswordResetResult {
  username: string;
  generated: boolean;
  temporaryPassword: string | null;   // only when generated
  mustChangePassword: boolean;
}

export type AuthStatus =
  | 'SUCCESS'
  | 'PASSWORD_CHANGE_REQUIRED'
  | 'MFA_SETUP_REQUIRED'
  | 'MFA_REQUIRED';

export interface LoginResponse {
  status: AuthStatus;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  passwordChangeToken?: string;   // PASSWORD_CHANGE_REQUIRED
  mfaToken?: string;              // MFA_*
  mfaQrCodeUri?: string;          // MFA_SETUP_REQUIRED
  mfaManualKey?: string;          // MFA_SETUP_REQUIRED
}
```

Note the two body conventions in this API: **the auth endpoints require the `{"data": …}`
envelope**, while **the two admin reset endpoints accept either** the envelope or a bare object.
Don't "normalise" them into one helper without checking each call site.

---

## 8. Gotchas

1. **`""` is treated as "the admin didn't choose one."** `chosen == null || chosen.isBlank()` is the
   generate condition, so `{"newPassword":""}` and `{"newPassword":"   "}` silently generate rather
   than 400. Always send `{}` for the empty case.
2. **`temporaryPassword` is show-once and only on the generated path.** It is `null` when the admin
   supplied one. There is no "show me the temp password again" endpoint — if the dialog drops it,
   the only remaining copy is the user's inbox.
3. **Never trim the password field** for a *set* operation. The backend stores exactly what it
   receives and checks only `length`; a client-side trim changes the stored credential relative to
   what was typed.
4. **The only policy is 8–50 characters.** No uppercase/digit/symbol requirement. Do not invent a
   strength rule the server will not enforce.
5. **The e-mail is fire-and-forget.** Sends are `@Async` and failures are swallowed by design
   (`EMAIL_FAILED:` in the server log, never in the API response). Treat the mail as a
   *convenience*, never as the delivery mechanism — always render `temporaryPassword`.
6. **The reset kills the target's sessions, not yours.** `permissionVersion` is bumped for the
   target only, so an admin is not logged out by resetting someone else. (Resetting *yourself* is
   the exception — see §6①.)
7. **`GET`/`PUT` on these paths returns 405**, not 404: `/reset-password` is POST-only.
8. **The subject line is firm-specific** — `Password reset for Y Law`. Useful for support scripts;
   do not hardcode it in user-facing copy.

---

## 9. What the user receives

Triggered by ① on both the firm-admin and Super Admin paths, sent to the target user's e-mail:

- **Subject:** `Password reset for <firm name>`
- **Body:** the firm name, "Your password was reset", the recipient's name, the **temporary
  password**, then: *"When you sign in you will be asked to choose a password of your own. If you
  did not ask for this change, contact your firm administrator immediately."*
- **Sign in** button → the URL configured as `LOGIN_URL` (see below), **not** a link to the admin
  console.

The **Sign in** link is a global system setting, not a code constant, and it is deliberately
**global-only** so a firm admin cannot repoint password-reset mails. For local frontend work it
must point at the dev app:

```bash
curl -X PUT "$BASE/api/v1/super-admin/config" \
  -H "Authorization: Bearer $SA_TOKEN" -H "Content-Type: application/json" \
  -d '{"LOGIN_URL":"http://localhost:5173/"}'
```

Or the Super Admin console → System Config → APP → **Login URL**. It takes effect immediately; no
restart. (The boot seed is insert-if-missing, so an existing row keeps whatever it was set to.)

---

## 10. Manual test script

Mirrors checklist 1.8 / 1.9. Run the backend with mail credentials configured — otherwise the
user never learns a generated password and you are testing only the dialog.

1. Admin console → Users → **Reset password** on an employee → *Let the system generate* → **the
   dialog must display a 12-character password.** Copy it.
2. Sign in as that employee with the copied password → **must be stopped at the rotate screen**,
   with no access to the app.
3. Confirm the e-mail arrived and that its **Sign in** link opens the configured URL.
4. Enter a 7-character password → blocked with `Password must be 8-50 characters`.
5. Enter mismatched confirmation → blocked client-side (you must not see the generic 401).
6. Complete the rotation → land in the app, and the app must now be usable.
7. Sign out; sign back in with the rotated password → straight in, `status: SUCCESS`.
8. Sign back in with the **temporary** password → refused.
9. Second employees' session: while employee B is signed in on another device, reset B's password
   → B's very next request is 401 → B is bounced to login.
10. Repeat the reset dialog choosing *Set a specific password*, type `Ylaw@2026x` → the dialog must
    **not** show a generated password, and the employee must be able to sign in with
    `Ylaw@2026x`.
11. Profile → change own password → confirm you are **logged out** afterwards, then sign in with
    the new one.
12. Super Admin console → reset a firm admin through ①b → the same e-mail must arrive.

---

## 11. Out of scope / not this feature

- **Forgot password** (`POST /auth/forgot-password` → `POST /auth/reset-password`): a *one-time
  e-mailed link*, no admin involved and no temporary password. Always reports success so it cannot
  be used to discover accounts — do not show "user not found".
- **MFA reset** (`/modules/users/{userId}/reset-mfa`, `/super-admin/mfa/reset`): separate endpoints,
  separate dialogs.
- **Bulk deactivate / bulk role change**: unrelated, though they share the "body may be bare or
  enveloped" quirk.

---

## 12. Known backend gaps the frontend may hit

| Gap | Impact |
|---|---|
| `POST /auth/change-password` masks mismatch, expiry and reuse into one 401 | mismatched confirmation is indistinguishable from an expired token — validate the match client-side (§4c) |
| `POST /me/change-password` says "other sessions" but kills the caller's too | do a global logout after success (§5) |
| No endpoint to read back a generated temporary password | display it in the dialog or it is unrecoverable (§6①) |
| E-mail failures are invisible to the API caller | never rely on the mail (§8.5) |
| The reset dialog's "no body" path is still accepted | fine to keep, but it silently generates — prefer an explicit choice |
