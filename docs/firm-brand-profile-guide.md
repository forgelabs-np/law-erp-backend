# Firm branding + profile — frontend contract

Two concerns, one owner surface (firm admin only):

1. **Change your own password** — already exists at `POST /api/v1/me/change-password`. Prove the current password, pick a new one (8–50 chars, must not be the current password), and every other session is signed out. This is the portal's "change password" action.
2. **Firm brand** — new at `GET/PUT /api/v1/firm/brand/theme` (primary/secondary hex) and `GET/POST/PATCH /api/v1/firm/brand/logo` (logo allowed toggle + file upload). These are the firm's own look: its hex colors and an optional logo. See below.

The portal reads both the user's own identity and the firm's brand in one shot on app load via `GET /api/v1/me`; its `data.firm` sub-object now carries the logo + brand fields too.

---

## 1. Change own password (already exists)

```http
POST /api/v1/me/change-password
{
  "data": {
    "currentPassword": "OldPass123!",
    "newPassword": "NewPass123!",
    "confirmPassword": "NewPass123!"
  }
}
```

- Must include `currentPassword`. Wrong current password → `400 "Current password is incorrect"`.
- New password must be 8–50 chars and differ from the current one.
- On success: `200`, every other session is invalidated (the response body says so; the client should clear stored tokens).
- The forced first-login rotation is a different path (`/auth/change-password` with a one-time token) — do not use `/me/change-password` for that.

---

## 2. Firm theme (primary/secondary colors)

```http
GET /api/v1/firm/brand/theme
```
```json
{ "success": true, "responseCode": 200, "message": "...", "data": {
  "id": "uuid",
  "lawFirmCode": "YLAW",
  "name": "YLaw & Partners",
  "...": "...",
  "brandPrimaryHex": "#1A237E",
  "brandSecondaryHex": "#E3F2FD",
  "isPersonalColor": false,
  "...": "..."
}}
```

```http
PUT /api/v1/firm/brand/theme
{ "data": {
  "brandPrimaryHex": "#0D47A1",
  "brandSecondaryHex": "#E3F2FD"
}}
```

- Hex is **strict**: only `#rrggbb` or `rrggbb`, 6 hex chars, normalized to lowercase `#rrggbb` on save.
- Invalid → `400 BusinessRuleException` (message names the value).
- Each field is optional; omit one and only that one is updated.
- When the firm has not set either color, `brandPrimaryHex`/`brandSecondaryHex` come back `null` and `isPersonalColor` is `false` — the portal should render the app's own default theme in that case.

**Portal rendering rule:**

- If `isPersonalColor` is truthy, render `brandPrimaryHex` and `brandSecondaryHex`.
- Otherwise (missing or `false`), render the app's default theme and ignore the firm hex fields.

---

## 3. Firm logo

### 3a. Logo allowed toggle (default: **off**)

```http
GET /api/v1/firm/brand/logo
```
```json
{ "success": true, "responseCode": 200, "message": "...", "data": {
  "logoUrl": "https://minio/...?...",
  "logoAllowed": false
}}
```

```http
PATCH /api/v1/firm/brand/logo/allowed
{ "data": { "logoAllowed": true } }
```

- Default for a newly customized firm is **`logoAllowed = false`** — the firm must opt in before any logo upload is accepted.
- When `logoAllowed === false`, `POST /api/v1/firm/brand/logo` is rejected with `403 "Logo uploads are not allowed for this firm..."`.
- When set to `true`, the firm admin can upload a logo.

### 3b. Upload a logo

```http
POST /api/v1/firm/brand/logo
Content-Type: multipart/form-data

[ part name: file, file part ]
```

```json
{ "success": true, "responseCode": 200, "message": "...", "data": {
  "logoUrl": "https://minio/...?...",
  "logoAllowed": true
}}
```

- Requires `file` part (non-empty). Empty/missing → `400 "A logo file is required"`.
- Only images allowed: `png`, `jpg`, `jpeg`, `webp`. Other type → `400 "Logo must be an image (...)"`.
- Size cap: **200 KiB**. Oversized → `400 "Logo must be at most 200 KiB (204800 bytes). Got ... bytes."`.
- If the firm already had a logo, the prior stored object is removed before the new one is written.
- The returned `logoUrl` is a presigned GET link. Treat it like a document download URL: it expires (~15 minutes), so a long-lived page should not stash it forever — fall back to re-fetching when it stops working.

---

## 4. What the portal reads on load (MeResponse.firm)

On app load the frontend calls `GET /api/v1/me` and gets, among other things:

```json
{ "data": { "firm": {
  "logoUrl": "https://minio/...?...",
  "logoAllowed": true,
  "brandPrimaryHex": "#1A237E",
  "brandSecondaryHex": "#E3F2FD",
  "isPersonalColor": true,
  "name": "YLaw & Partners",
  "...": "..."
}}}
```

- `isPersonalColor` is **omitted entirely** when the firm has not set either brand color (not sent as `false`), so treat a missing value the same as `false`.
- `logoAllowed` is always present; `brandPrimaryHex`/`brandSecondaryHex` are `null` when unset.

So the portal does **not** need a separate call to render the firm's branding — it's part of the identity payload. The theme/logo APIs above are for the firm admin to change those values.

---

## 5. Errors to handle

| Action | Status | `message` | What to show |
|---|---|---|---|
| Change own password — wrong current password | `400` | `Current password is incorrect` | inline on the current-password field |
| Change own password — new == current | `400` | `Please choose a password you have not used before` | inline on the new-password field |
| Any hex value invalid | `400` | `Invalid brand color '…'. Expected #rrggbb or rrggbb.` | inline on the color field |
| Logo upload — not allowed | `403` | `Logo uploads are not allowed for this firm. Enable them first via the theme/logo settings.` | block the upload UI / toast |
| Logo upload — wrong type | `400` | `Logo must be an image (png, jpg, jpeg or webp). Got: …` | inline / retry |
| Logo upload — too big | `400` | `Logo must be at most 200 KiB (204800 bytes). Got … bytes.` | inline / retry |
| Firm admin-only endpoint, not admin | `403` | per-PreAuthorize denial | hide the panel |

---

## 6. Notes

- Logo upload stores the file in object storage under `firms/{firmId}/logo.{ext}` and persists only the resulting presigned URL in `firm.logoUrl`. Do not edit `logoUrl` yourself to arbitrary URLs — only the firm-admin upload path can populate it from the store.
- Logo presigned links expire; do not treat `logoUrl` as a permanent asset URL. Re-request on failure.
- Brand colors: primary/secondary are stored on the firm as hex, and mirrored into the `me.firm` payload via `isPersonalColor`. If `isPersonalColor` is `false`, the firm has not chosen colors — show the app default.
