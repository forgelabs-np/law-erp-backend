# Document Store — Frontend Implementation Guide

Version 1.0 — 2026-09-28 — branch `devG`
Backend refs: `modules/document/**`, `common/storage/**`, `superadmin/controller/StorageQuotaController`
Full implementation write-up: `docs/document-store-module.pdf` (13 sections, including the data model and every design trade-off)

---

## 1. What was built, on one page

A firm can upload files against **a case (Matter) or a project (Project)** — exactly one of the two.
The bytes **never travel through the API**: the client uploads straight to object storage
(MinIO/S3) using a short-lived signed policy, then tells the API it is done.

```
① ASK           POST /api/v1/firm/documents/upload-ticket        { matterNumber | projectCode, filename, contentType, sizeBytes }
                └─> creates a PENDING_UPLOAD row + returns { documentId, uploadUrl, fields, expiresAt }
                    (also refuses up front if the file is too big, the type is not allowed, or the firm is out of space)

② POST TO STORAGE   multipart/form-data → uploadUrl
                    every entry of `fields` verbatim, then the file, field name `file`, LAST
                └─> the storage server itself enforces the size ceiling and the content type

③ CONFIRM       POST /api/v1/firm/documents/{documentId}/confirm    (body optional)
                └─> API re-reads the object, checks real size + file signature, reserves the firm's
                    storage and flips the document to ACTIVE. Only now is it a document.
```

**Nothing is readable until step ③ succeeds.** An abandoned upload leaves a `PENDING_UPLOAD` row that
a background sweeper removes once the 30-minute upload window has passed (the sweeper runs every
15 minutes) — along with any bytes that did land.

Everything else is ordinary CRUD-shaped: list, download-link, share, archive, storage usage.

---

## 2. Endpoint index

Base URL is whatever you already use for the API. Two body conventions apply — see §3.

### Firm side (`/api/v1/firm`) — for staff and firm admins

| # | Method | Path | Permission | Purpose |
|---|---|---|---|---|
| 1 | POST | `/documents/upload-ticket` | `DOCUMENT_MANAGEMENT:UPLOAD` | Step 1 of the upload |
| 2 | POST | `/documents/{documentId}/confirm` | `DOCUMENT_MANAGEMENT:UPLOAD` | Step 3 of the upload |
| 3 | GET | `/documents` | `DOCUMENT_MANAGEMENT:VIEW` | Firm-wide library |
| 4 | GET | `/matters/{matterNumber}/documents` | `DOCUMENT_MANAGEMENT:VIEW` | Documents on one case |
| 5 | GET | `/projects/{projectCode}/documents` | `DOCUMENT_MANAGEMENT:VIEW` | Documents on one project |
| 6 | GET | `/documents/{documentId}/download-url` | `DOCUMENT_MANAGEMENT:VIEW` | Short-lived download link |
| 7 | PATCH | `/documents/{documentId}/visibility` | `DOCUMENT_MANAGEMENT:SHARE` | PRIVATE ⇄ SHARED |
| 8 | DELETE | `/documents/{documentId}` | `DOCUMENT_MANAGEMENT:EDIT` | Archive (soft delete) |
| 9 | GET | `/documents/storage-usage` | `DOCUMENT_MANAGEMENT:VIEW` | This firm's quota + usage |

`{documentId}` is the **numeric** `id` from the upload ticket or a list response. Documents also carry a
`uuid` — that is the identifier the audit trail records, **not** a path parameter. Use `id` everywhere.

### Client portal (`/api/v1/client/documents`)

| # | Method | Path | Permission | Purpose |
|---|---|---|---|---|
| 10 | GET | `/api/v1/client/documents` | `DOCUMENT_MANAGEMENT:VIEW` | List my documents |
| 11 | GET | `/api/v1/client/documents/{documentId}/download-url` | `DOCUMENT_MANAGEMENT:VIEW` | Download one of mine |

### Super Admin (`/api/v1/super-admin/firms`) — `SUPER_ADMIN` role, not a permission

| # | Method | Path | Purpose |
|---|---|---|---|
| 12 | PUT | `/{firmId}/storage-quota` | Allocate storage to a firm |
| 13 | GET | `/{firmId}/storage-usage` | Read a firm's usage |

> **Download is gated on `:VIEW`, not `:DOWNLOAD`.** The `DOWNLOAD` action exists in the permission
> catalogue but is deliberately unused — `READ_ONLY` (paralegal) and `OWN` (client) roles are seeded
> with `ACCESS + VIEW` only, so gating downloads on `:DOWNLOAD` would lock them out of reading any file.
> **Do not gate the download button on `DOWNLOAD`.**

---

## 3. Response and request envelopes

Every response, success or failure:

```json
{
  "success": true,
  "responseCode": 200,
  "message": "Documents fetched successfully",
  "data": { }
}
```

`responseCode` mirrors the HTTP status. On an error `success` is `false`, `responseCode` is the status,
`message` is human-readable, and **`data` is absent** — never render an error path from `data`.

There are **two body conventions**, and mixing them up is the most common integration mistake:

| Style | Endpoints | Body |
|---|---|---|
| **Enveloped** (the `data` key is required) | 1, 7, 12 | `{ "data": { …fields… } }` |
| **Enveloped but optional** | 2 | `{ "data": { "etag": "…" } }`, `{}`, or omit the body entirely |

Endpoints 1 and 7 declare `@NotNull @Valid` on the envelope, so a bare object is a **400**:

```json
// WRONG for POST /documents/upload-ticket
{ "filename": "petition.pdf", "contentType": "application/pdf", "sizeBytes": 1000 }

// RIGHT
{ "data": { "matterNumber": "CASE-2026-001", "filename": "petition.pdf",
            "contentType": "application/pdf", "sizeBytes": 1000 } }
```

GET and DELETE endpoints take no body. All list endpoints take **query parameters only**.

---

## 4. Step ① — request an upload ticket

`POST /api/v1/firm/documents/upload-ticket`

```json
{
  "data": {
    "matterNumber": "CASE-2026-001",
    "filename": "petition.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 184320
  }
}
```

| Field | Required | Notes |
|---|---|---|
| `matterNumber` | one of the two | Case document. Must be a case in the caller's firm. |
| `projectCode` | one of the two | Project document. |
| `courtCaseRef` | no | Tags the document with one court instance of the matter (`ourCourtCaseRef`). Only valid on a matter document, and the court case must belong to that matter. |
| `filename` | **yes** | Must have an allowed extension and must not contain a path. Max 255 chars. |
| `contentType` | **yes** | Must be on the whitelist (§7). A `;charset=…` suffix is tolerated and stripped. |
| `sizeBytes` | **yes** | Exact byte count, `> 0`. |

**Supply exactly one of `matterNumber` / `projectCode`.** Both, or neither → `400 Provide either a case
(matterNumber) or a project (projectCode) — exactly one`.

### Response

```json
{
  "success": true,
  "responseCode": 200,
  "message": "Upload ticket issued successfully",
  "data": {
    "documentId": 41,
    "fileName": "petition.pdf",
    "uploadUrl": "http://localhost:9000/tarikh-documents",
    "fields": {
      "x-amz-algorithm": "AWS4-HMAC-SHA256",
      "x-amz-credential": "minioadmin/20260928/us-east-1/s3/aws4_request",
      "x-amz-date": "20260928T101500Z",
      "policy": "eyJleHBpcmF0aW9uIjoi…",
      "x-amz-signature": "9f2c…",
      "key": "firms/8f3c…/cases/CASE-2026-001/2b91…/petition.pdf",
      "Content-Type": "application/pdf"
    },
    "expiresAt": "2026-09-28T10:45:00Z"
  }
}
```

Notes:

- `uploadUrl` points at **object storage, not the API**. It is a *different origin* — see §10.
- `fields` also carries a `x-amz-security-token` entry if the deployment uses temporary credentials.
  Treat `fields` as opaque and send **every** key.
- `expiresAt` is the ticket deadline (default **30 minutes**). The same window bounds the confirm step:
  confirming after it fails with `The upload window expired. Please upload the file again.`

---

## 5. Step ② — POST the bytes to storage

This is a **plain HTML form POST**, not a `fetch` with a JSON body, and not a PUT.

```ts
export async function uploadToStorage(
  uploadUrl: string,
  fields: Record<string, string>,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<void> {
  const form = new FormData();

  // 1. EVERY returned field, verbatim and unchanged. The signature covers them:
  //    a trimmed key or a re-encoded value becomes a 403 from storage.
  for (const [key, value] of Object.entries(fields)) {
    form.append(key, value);
  }

  // 2. The file LAST, under the field name `file`. The bucket is stored as
  //    "browser form upload", which defines this field name.
  form.append('file', file);

  // 3. Do NOT set a Content-Type header. The browser must generate the multipart
  //    boundary itself; `Content-Type` is already present inside `fields`.
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', uploadUrl, true);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300
      ? resolve()
      : reject(new Error(`Storage rejected the upload (${xhr.status})`)));
    xhr.onerror = () => reject(new Error('Network error reaching storage'));
    xhr.send(form);
  });
}
```

`XMLHttpRequest` is used deliberately: `fetch` cannot report upload progress.

### Why the policy makes this safe

The signed policy pins three things, so a tampered request is refused by storage rather than by us:

| Pinned | Effect |
|---|---|
| `key` (exact object path) | The client cannot choose where the file lands, or overwrite another document. |
| `Content-Type` (exact) | The declared type cannot be swapped after the ticket is issued. |
| `content-length-range` `1…50 MiB` | The size ceiling is enforced by storage. |

Storage's own success response is **204 / 200 with an XML body `<PostResponse>…</PostResponse>`**. You do
not need to parse it — move straight to step ③. Do not show "uploaded" to the user until ③ succeeds.

---

## 6. Step ③ — confirm

`POST /api/v1/firm/documents/{documentId}/confirm` — body optional.

```json
{ "data": { "etag": "\"9f2cd0e1b1a4…\"" } }
```

`etag` is **optional and cosmetic** — the server reads the real ETag back from storage and only uses
your value if it is a plausible one. Simplest correct call is no body at all; if you have the ETag from
storage's XML response, send it quoted or unquoted (both are normalised).

The server, in order:

1. Loads the row and requires `status = PENDING_UPLOAD` and an unexpired window.
2. Requires the caller to still be allowed to act on the case/project.
3. `stat`s the object — missing → `400 The file was not found in storage — the upload may have failed`.
4. Rejects if the real size exceeds the 50 MiB ceiling (and **deletes the object**).
5. Rejects if the real size **differs from the `sizeBytes` you declared** (and **deletes the object**).
6. Reads the first 4 KiB and checks the **file signature** against the declared type (and **deletes the
   object** if it does not match).
7. Reserves the firm's storage under a row lock; if it does not fit, the object is deleted and the
   request fails.
8. Marks the document `ACTIVE` and appends a `DOCUMENT_UPLOADED` entry to the case timeline
   (case documents only — projects have no timeline).

### Response

```json
{
  "success": true,
  "responseCode": 200,
  "message": "Document uploaded successfully",
  "data": {
    "id": 41,
    "uuid": "2b91c4d2-6f0a-4a1f-9c33-2f8c5d0b7e11",
    "fileName": "petition.pdf",
    "contentType": "application/pdf",
    "extension": "pdf",
    "sizeBytes": 184320,
    "status": "ACTIVE",
    "visibility": "PRIVATE",
    "matterNumber": "CASE-2026-001",
    "projectCode": null,
    "courtCaseId": null,
    "uploadedByUserId": "c17b…",
    "createdAt": "2026-09-28T10:16:02",
    "archivedAt": null
  }
}
```

That object is exactly the list-item shape — store it and you can render the row without a refetch.

### Why confirm can fail after the bytes landed

Every one of steps 4–7 removes the object before failing, so a failed confirm never leaves an orphaned
file behind. If confirm fails, the row stays `PENDING_UPLOAD` and is swept later — **the client must
restart from step ①**, not retry the confirm.

---

## 7. What may be uploaded

The **declared `contentType`** decides the whitelist. The stored extension is derived from the content
type, never from the filename (§11, gotcha 3).

| `contentType` | Extensions accepted in the filename |
|---|---|
| `application/pdf` | `.pdf` |
| `application/msword` | `.doc` |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `.docx` |
| `application/vnd.ms-excel` | `.xls` |
| `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `.xlsx` |
| `application/vnd.ms-powerpoint` | `.ppt` |
| `application/vnd.openxmlformats-officedocument.presentationml.presentation` | `.pptx` |
| `text/plain` | `.txt` |
| `text/csv` | `.csv` |
| `image/jpeg` | `.jpg`, `.jpeg` |
| `image/png` | `.png` |
| `image/tiff` | `.tiff` |
| `application/zip` | `.zip` |

Anything else — including `.gif`, `.svg`, `.html`, `.js`, `.exe` — is refused, and `.docx`/`.xlsx`/`.pptx`
are validated as ZIP containers because that is what they are.

**Limits**

| Limit | Default | Where it comes from |
|---|---|---|
| Max file size | **50 MiB** | `storage.minio.max-file-size-bytes` |
| Upload window (ticket + confirm) | **30 min** | `storage.minio.upload-expiry-seconds` |
| Download link validity | **15 min** | `storage.minio.download-expiry-seconds` |

Build the file picker's `accept` from the extension column above, and validate `file.size` client-side
before step ① so the user gets an instant error instead of a round trip.

---

## 8. Permissions — who can do what

Seeded by `DataInitializer`. `FULL` = all actions in the module; `READ_ONLY` / `OWN` = `ACCESS + VIEW` only.

| Action | Permission | SUPER_ADMIN | FIRM_ADMIN | ADVOCATE | PARALEGAL | CLIENT |
|---|---|---|---|---|---|---|
| Upload (ticket + confirm) | `DOCUMENT_MANAGEMENT:UPLOAD` | ❌ | ✅ | ✅ | ❌ | ❌ |
| List / view | `DOCUMENT_MANAGEMENT:VIEW` | ❌ | ✅ | ✅ | ✅ | ✅ |
| Download link | `DOCUMENT_MANAGEMENT:VIEW` | ❌ | ✅ | ✅ | ✅ | ✅ |
| Share (visibility) | `DOCUMENT_MANAGEMENT:SHARE` | ❌ | ✅ | ✅ | ❌ | ❌ |
| Archive | `DOCUMENT_MANAGEMENT:EDIT` | ❌ | ✅ | ✅ | ❌ | ❌ |
| Storage usage | `DOCUMENT_MANAGEMENT:VIEW` | ❌ | ✅ | ✅ | ✅ | ❌ † |

† Clients use `/api/v1/client/documents`, not the firm endpoints.

> **Super Admin is refused on every document endpoint (403).** Documents are firm records and the
> platform has no firm context, so this is enforced in the service (`requireFirmId`), not just by the
> seed matrix — `PermissionEvaluator` exempts Super Admin from permission checks, so a grant removal
> alone would not have blocked them.

**A permission is necessary but not sufficient.** `:VIEW` gets you into the endpoint; a **scope guard**
then decides *which rows* you see. Hide the UI for the actions you lack, but never assume the server will
let you see everything you could have asked for:

| Caller | Sees |
|---|---|
| Firm admin | Every document in the firm |
| Super Admin | **Nothing — 403 on every endpoint** (see above) |
| Advocate, paralegal | Only documents on **cases they are assigned to** and **projects they are a member of** |
| Client | Only **SHARED + ACTIVE** documents on **their own** cases and projects |
| Unassigned staff | An **empty page** (200, `content: []`) — not a 403 |

A client asking for another client's case or project by number gets **403**, not 404 — the record exists
in the firm, and only the caller's scope is wrong. Case documents are the exception: a **differently
scoped firm's** document id is a **404**, deliberately, so IDs cannot be probed across tenants.

Custom roles created later may grant these actions differently — read the permission list from your
existing permissions endpoint rather than hardcoding this table.

---

## 9. Listing

All four list endpoints share the same query parameters and the same response shape.

| Query param | Type | Default | Notes |
|---|---|---|---|
| `status` | `PENDING_UPLOAD` \| `ACTIVE` \| `ARCHIVED` | all | **Pass `ACTIVE` for a normal library.** |
| `visibility` | `PRIVATE` \| `SHARED` | all | Ignored on the client endpoint. |
| `search` | string | — | Case-insensitive substring of the **filename only**. Does not search matter numbers, project codes, or content. |
| `page` | int | `0` | **0-based.** |
| `size` | int | `20` | Page size. |

Results are always sorted by `createdAt` **descending** (newest first). There is no sort parameter.

```json
{
  "success": true,
  "responseCode": 200,
  "message": "Documents fetched successfully",
  "data": {
    "content": [
      { "id": 41, "fileName": "petition.pdf", "status": "ACTIVE",
        "documentUrl": "http://localhost:9000/tarikh-documents/firms/…?X-Amz-Algorithm=…", "…": "…" }
    ],
    "page": 0, "size": 20, "totalElements": 137, "totalPages": 7,
    "first": true, "last": false, "empty": false
  }
}
```

Endpoint-specific behaviour:

- **`GET /documents`** (library) — firm-wide, narrowed by the caller's scope (§8).
- **`GET /matters/{matterNumber}/documents`** — `404 Matter not found: …` if the case is not in your firm.
  A **client** opening a case that is not theirs is a different failure: **403
  `You do not have access to this matter`**, because the case exists in the firm and only the caller's
  scope is wrong. Never build a link that lets a client reach another client's case number; the guard is
  there, but the UX should not depend on it.
- **`GET /projects/{projectCode}/documents`** — same shape; a client must own the project, otherwise
  **403 `You do not have access to this project`**. For **staff** this is deliberately not a membership
  test: the listing is already narrowed to their memberships, so a non-member gets an empty page.

> **`PENDING_UPLOAD` rows appear in an unfiltered list.** They are real rows from a started-but-unconfirmed
> upload. Either send `status=ACTIVE`, or render them as an in-progress row that hides the download,
> share and archive actions. Do not offer actions on a pending row — every one of them fails.

**`documentUrl`** — every list item carries a ready-to-use presigned download link, so a row can be
opened without a second request. It is populated **only for `ACTIVE` documents**; `PENDING_UPLOAD` and
`ARCHIVED` rows have `null`. Two consequences of it being a presigned bearer credential:

- It **expires with the page** (~15 min). If a user may sit on the list longer than that, call
  §10's `download-url` at click time instead of trusting a stale `documentUrl`.
- A download that uses `documentUrl` does **not** write a `DOCUMENT_DOWNLOADED` audit row. When an
  audited download matters, use the `download-url` endpoint.

---

## 10. Downloading

Two ways to get a link: the list above already embeds `documentUrl` per row, or fetch a fresh one
on demand (audited) from:

`GET /api/v1/firm/documents/{documentId}/download-url`
`GET /api/v1/client/documents/{documentId}/download-url`

```json
{
  "success": true,
  "responseCode": 200,
  "message": "Download link generated successfully",
  "data": {
    "documentUuid": "2b91c4d2-6f0a-4f1f-9c33-2f8c5d0b7e11",
    "downloadUrl": "http://localhost:9000/tarikh-documents/firms/…?X-Amz-Algorithm=…&response-content-disposition=attachment%3B%20filename%3D%22petition.pdf%22",
    "fileName": "petition.pdf",
    "expiresAt": "2026-09-28T10:31:02Z"
  }
}
```

- The link is valid for **15 minutes**, defaults to a `Content-Disposition: attachment` download with the
  original filename, and **requires no `Authorization` header** — the signature *is* the credential.
- **Never log, persist, or put this URL in analytics, error reports, or a shareable state object.** It is a
  bearer credential for a private file. Treat it like a password.
- Each request mints a fresh link *and writes a `DOCUMENT_DOWNLOADED` audit row*. Do not call it
  speculatively (e.g. to probe availability, or in a `useEffect` that runs per render) — it fills the audit
  trail with phantom downloads.
- For viewing, `window.open(downloadUrl, '_blank')` is fine; the browser will download rather than render
  because of the attachment disposition. Inline preview is not supported in Phase 1.

Errors:

| Status | `message` | Cause |
|---|---|---|
| 400 | `This document is not available for download` | document is `PENDING_UPLOAD` or `ARCHIVED` |
| 403 | `This document has not been shared with you` | client asking for a `PRIVATE` document |
| 403 | `You are not assigned to this case` | staff member not on the document's case |
| 403 | `You are not a member of this project` | staff member not on the document's project |
| 403 | `You do not have access to this document` | client asking for a document on someone else's project |
| 404 | `Document not found: 41` | not in your firm |

---

## 11. Sharing

`PATCH /api/v1/firm/documents/{documentId}/visibility`

```json
{ "data": { "visibility": "SHARED" } }
```

`PRIVATE` (the default for every upload) keeps a document internal. `SHARED` is the **only** thing that
makes a document visible in the client portal. There is no per-document grant table: sharing is a single
flag plus the client's own case/project membership.

- Archiving an already-archived document is a no-op; archiving a live one succeeds.
- `400 An archived document cannot be shared` — re-share before archiving, not after.
- Writes an audit row either way.

---

## 12. Archive (soft delete)

`DELETE /api/v1/firm/documents/{documentId}`

`DELETE` here means **archive**, not destroy:

- The row moves to `ARCHIVED` with `archivedAt` set.
- The **stored object is kept** — legal documents are retained on purpose.
- The bytes are **released from the firm's storage quota**, so archiving is the way a firm frees space.
- The document disappears from the client portal and, if you filter `status=ACTIVE`, from the library.
- Idempotent: archiving twice returns the same document with a 200.
- **There is no un-archive and no hard delete endpoint in Phase 1.**

Design the UI around that: label the button **Archive**, not Delete, and say what happens.

---

## 13. Storage quota

### Firm view — `GET /api/v1/firm/documents/storage-usage`

```json
{
  "success": true, "responseCode": 200, "message": "Storage usage fetched successfully",
  "data": { "usedBytes": 2147483648, "quotaBytes": 5368709120,
            "availableBytes": 3221225472, "usedPercent": 40.0, "unlimited": false }
}
```

| Field | Meaning |
|---|---|
| `usedBytes` | Sum of the bytes of the firm's `ACTIVE` documents. Archived documents do not count. |
| `quotaBytes` | The firm's allocation. **`0` means unlimited.** |
| `availableBytes` | `quotaBytes - usedBytes`, floored at 0. **`0` when `unlimited` is true** — do not read it as "full". |
| `usedPercent` | `0–100`, already capped. **`0` when `unlimited` is true.** |
| `unlimited` | Render a plain "2.0 GB used", not a bar, when this is true. |

**Always branch on `unlimited` before rendering a progress bar.** A naive bar shows 0% for an unlimited
firm and an "0 bytes left" warning.

Every new firm starts at **5 GiB** the first time it stores anything (`storage.minio.default-quota-bytes`),
so quota is never null and you can always render something.

### Super Admin — allocate and inspect

`PUT /api/v1/super-admin/firms/{firmId}/storage-quota` — body **enveloped**:

```json
{ "data": { "quotaBytes": 10737418240 } }
```

10 GiB above. `0` means unlimited. Both endpoints return the same `StorageUsage` shape as §13 above.

- **`quotaBytes` cannot be negative**, and it is **bytes** — convert from the GiB/MB the admin types.
- Applies to **new uploads only**. Lowering a firm's allocation never deletes anything; the firm simply
  cannot upload until it archives enough.
- These two endpoints are `@PreAuthorize("hasRole('SUPER_ADMIN')")` — a firm admin gets **403** and
  **cannot raise their own limit**, because the allocation deliberately does not live in firm config.

After an allocation change, any cached usage view in the Super Admin console must be invalidated — the
same firm's usage is now measured against a new denominator.

### What the firm sees when it runs out

Step ① fails with **`400 This file does not fit in your firm's remaining storage allocation`**, and step ③
re-checks the same thing under a lock if two uploads race. Surface the remaining space next to the error,
with a shortcut to the archived documents.

---

## 14. Client portal

`GET /api/v1/client/documents` — query params `matterNumber`, `projectCode`, `search`, `page`, `size`.

The client endpoints are **read-only by construction**:

- There is **no upload and no archive endpoint** for a client. Do not build the affordances.
- The query **ignores `status` and `visibility` you send** and always returns `SHARED` + `ACTIVE`
  documents only — a private or archived document is never returned, so there is nothing to hide client-side.
- Scope is the client's own cases and projects, resolved from `Matter.clientUserId` *or* a party row marked
  as our client, plus `Project.clientUserId`.
- Both endpoints require `DOCUMENT_MANAGEMENT:VIEW`; the `CLIENT` role is seeded with it at `OWN` scope.
- Pass `matterNumber` / `projectCode` to narrow to one case or project; combine with `search` for a filename
  filter within it.

**A client uploading their own document is not supported in Phase 1.** If that is needed, it is a new
endpoint with its own review — the current design has no notion of a client-authored document.

---

## 15. Error reference

| Status | `message` | Cause | What to show |
|---|---|---|---|
| 400 | `Provide either a case (matterNumber) or a project (projectCode) — exactly one` | both or neither supplied | inline on the form |
| 400 | `A file may be at most 50.0 MB` | declared size over the ceiling | inline on the file field |
| 400 | `Unsupported content type '…'. Allowed types: …` | type not on the whitelist | inline; fix your file picker |
| 400 | `Files of type '.exe' are not accepted. Allowed: …` | bad extension | inline |
| 400 | `The file must not contain a path` / `The file must have an extension` / `The filename is longer than 255 characters` | malformed filename | inline |
| 400 | `This file does not fit in your firm's remaining storage allocation` | quota reached | show remaining space + archive shortcut |
| 400 | `The upload window expired. Please upload the file again.` | >30 min between ① and ③ | restart the upload from ① |
| 400 | `The file was not found in storage — the upload may have failed` | step ② did not land | retry from ① |
| 400 | `The uploaded file (…) does not match the declared size (… bytes)` | ② sent a different file | restart from ① |
| 400 | `The file content does not match its declared type ('application/pdf')` | renamed / spoofed file | "This file isn't a real PDF" |
| 400 | `This upload is no longer awaiting confirmation` | confirm called twice | treat as success if you already have the document |
| 400 | `This document is not available for download` | document is pending or archived | refresh the list |
| 400 | `An archived document cannot be shared` | share after archive | refresh the list |
| 400 | `The uploaded file is larger than the 50.0 MB limit` | oversized write slipped past the policy | restart from ① |
| 400 | `quotaBytes cannot be negative` / `quotaBytes is required` | Super Admin form | inline |
| 401 | `Invalid username or password` | session gone | global logout → login |
| 403 | `Missing required permission: DOCUMENT_MANAGEMENT:UPLOAD` | the caller's role lacks the action | **hide the button instead** |
| 403 | `You do not have permission to perform this action` | `@PreAuthorize` denial — the **Super Admin** endpoints only | hide the console entry |
| 403 | `No authenticated user` | token present but not resolved | global logout → login |
| 403 | `Client accounts cannot upload or change documents` | client account calling a firm endpoint | not reachable from the portal UI |
| 403 | `This document has not been shared with you` | client asking for a `PRIVATE` document | it should not have been in their list |
| 403 | `You are not assigned to this case` / `You are not a member of this project` | staff outside the caseload | an unattached link; refresh the list |
| 403 | `You do not have access to this document` / `You do not have access to this project` | client outside their own records | refresh; do not retry |
| 403 | `This request is not associated with a firm` | token has no firm context | global logout → login |
| 404 | `Matter not found: …` / `Project not found: …` | wrong firm, or stale | toast + refresh |
| 404 | `Document not found: 41` | **also returned for another firm's document** — deliberate, so IDs cannot be probed | toast + refresh the list |
| 404 | `Court case not found on this matter: …` | `courtCaseRef` is not on that matter | clear the field |
| 405 | `HTTP method not supported for this endpoint` | wrong verb | a bug in your client |
| 502 | `Document storage is temporarily unavailable. Please try again.` | object storage is down or misconfigured | retryable toast; do **not** discard the user's file |
| 500 | `An unexpected error occurred` | bug | generic error toast |

**If `success === false`, render `message`.** Do not re-derive a message from `responseCode` — many distinct
causes share a 400, and `message` is the only precise signal.

---

## 16. Types to copy

```ts
export interface ApiResponse<T> {
  success: boolean;
  responseCode: number;   // mirrors the HTTP status; 200 on success
  message: string;
  data: T;                // absent on errors
}

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

export type DocumentStatus = 'PENDING_UPLOAD' | 'ACTIVE' | 'ARCHIVED';
export type DocumentVisibility = 'PRIVATE' | 'SHARED';

/** The document identifier for every path is `id`. `uuid` is what the audit trail records. */
export interface DocumentResponse {
  id: number;
  uuid: string;
  fileName: string;
  contentType: string;
  extension: string;
  sizeBytes: number;
  status: DocumentStatus;
  visibility: DocumentVisibility;
  matterNumber: string | null;    // set for a case document
  projectCode: string | null;     // set for a project document
  courtCaseId: string | null;
  uploadedByUserId: string | null;
  documentUrl: string | null;     // presigned, ACTIVE-only; a bearer credential — see §10
  createdAt: string;              // LocalDateTime, no timezone offset
  archivedAt: string | null;
}

export interface UploadTicketRequest {
  matterNumber?: string;
  projectCode?: string;
  courtCaseRef?: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
}

export interface UploadTicketResponse {
  documentId: number;
  fileName: string;
  uploadUrl: string;                 // object storage — a different origin
  fields: Record<string, string>;    // send every entry verbatim
  expiresAt: string;                 // ISO-8601 instant
}

export interface DownloadUrlResponse {
  documentUuid: string;
  downloadUrl: string;   // a bearer credential: never log or persist it
  fileName: string;
  expiresAt: string;
}

export interface StorageUsage {
  usedBytes: number;
  quotaBytes: number;      // 0 = unlimited
  availableBytes: number;  // 0 when unlimited
  usedPercent: number;     // 0 when unlimited
  unlimited: boolean;
}
```

`createdAt` / `archivedAt` are `LocalDateTime` (no offset); `expiresAt` is an `Instant` (with `Z`).
Parse them differently — a single date helper will show one of them in the wrong zone.

---

## 17. Helpers you will want

```ts
/** Human-readable size for list rows, error messages and the quota bar. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024, i = 0;
  while (value >= 1024 && i < units.length - 1) { value /= 1024; i++; }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[i]}`;
}

/** Convert the GiB an admin types into the bytes the API wants. */
export const gib = (n: number) => Math.round(n * 1024 ** 3);

/** Extensions the picker should offer — mirrors the server whitelist (§7). */
export const ACCEPTED_EXTENSIONS =
  '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.jpg,.jpeg,.png,.tiff,.zip';

export const MAX_FILE_BYTES = 50 * 1024 * 1024;

/** Best-effort browser-side MIME guess for a picked File. */
export function contentTypeFor(file: File): string {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  const map: Record<string, string> = {
    pdf: 'application/pdf', doc: 'application/msword', docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ppt: 'application/vnd.ms-powerpoint', pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    txt: 'text/plain', csv: 'text/csv', jpg: 'image/jpeg', jpeg: 'image/jpeg',
    png: 'image/png', tiff: 'image/tiff', zip: 'application/zip',
  };
  return map[ext] ?? (file.type || 'application/octet-stream');
}
```

> `contentTypeFor` keys off the **extension**, not `file.type`. Browsers report `""` or
> `application/octet-stream` for `.docx` often enough that `file.type` alone will fail the whitelist.
> `file.type` is only a fallback.

---

## 18. Why the upload is two steps (and why you cannot skip one)

1. **The bytes never touch the API.** A 50 MiB file would otherwise occupy a request thread, count against
   the app's own memory and bandwidth, and need a size limit far above every other endpoint.
2. **The size ceiling is enforced by storage.** The `content-length-range` condition means an oversized
   body is rejected before it is written, not after — the API's own re-check on confirm is a backstop, not
   the primary gate.
3. **The record exists before the bytes do.** The object key is derived from the case/project *and* a
   generated identifier, so an upload can never overwrite another document, and a failed upload is a row
   the sweeper can clean up rather than an anonymous orphan.
4. **Confirm is where trust is withdrawn.** Everything the client said in step ① is re-verified against
   what actually landed. Skipping confirm means an invisible, unattributed, unquota'd file.

**Consequence for the UI:** model the upload as three states, and only treat it as done after ③.

```
ticket (fast)  →  bytes (slow, has progress)  →  confirm (fast)  →  done
```

The ticket step is also where users hit every validation error, so call it as soon as a file is picked —
then you can reject a bad file before spending any upload bandwidth.

---

## 19. UI checklist

- [ ] File picker `accept` restricted to §7; size checked client-side before step ①.
- [ ] Progress bar bound to step ②; the "Uploading…" state ends only after step ③ returns.
- [ ] On any failure between ① and ③, show **Retry**, and have it restart from ① (a new ticket).
- [ ] Default list query sends `status=ACTIVE`.
- [ ] Pending rows, if shown, expose no download / share / archive action.
- [ ] Upload button hidden without `DOCUMENT_MANAGEMENT:UPLOAD`; share without `:SHARE`; archive without `:EDIT`.
- [ ] Empty page (unassigned staff) rendered as "No documents on your cases yet", not as an error.
- [ ] Quota bar branches on `unlimited` before computing a percentage.
- [ ] Archive labelled "Archive", with copy explaining the file is retained.
- [ ] Download URLs never logged or persisted; each download action is a deliberate user action.
- [ ] Super Admin quota input converts GiB → bytes and uses the enveloped body.
- [ ] Client portal renders no upload / archive affordance at all.

---

## 20. Manual test script

Run the backend with object storage up (`docker compose up -d` — MinIO on `:9000`, console on `:9001`)
and sign in as a **firm admin** unless stated otherwise.

1. **Happy path (case).** Open a case → Upload → pick a real PDF → progress reaches 100% → the row appears
   as `ACTIVE` within a moment. Confirm the case timeline shows "Document uploaded: …".
2. **Happy path (project).** Same on a project. Confirm **no** timeline entry is added (projects have none).
3. **Storage key.** In the MinIO console the object is at
   `firms/{firmId}/cases/{matterNumber}/{uuid}/{filename}` (or `.../projects/{projectCode}/...`) — readable
   and traceable back to the case.
4. **Disguised file.** Rename a `.exe` to `report.pdf`, declare `application/pdf` → step ③ fails with
   `The file content does not match its declared type`, and **nothing is left in the bucket**.
5. **Wrong size.** Start an upload, then send a different (larger) file in step ② → confirm fails with a
   size-mismatch message.
6. **Oversized.** Pick a file over 50 MiB → refused at step ①.
7. **Bad type.** Try `.gif` or `.svg` → refused with the allowed list.
8. **Abandon.** Get a ticket, upload the bytes, never confirm → the row stays `PENDING_UPLOAD` for the
   remainder of its 30-minute window, then the sweeper (every 15 min, first run 5 min after startup)
   removes the row and the object.
9. **Download.** Click download → the file saves with its original name. Open the audit log: exactly one
   `DOCUMENT_DOWNLOADED` row per click, and its detail contains the filename, **never a URL**.
10. **Download an archived document** → refused with `This document is not available for download`.
11. **Sharing.** Share a case document → sign in as that case's client → the document appears in the portal.
    Set it back to `PRIVATE` → it disappears.
12. **Client cannot upload.** In the client portal there is no upload entry point, and
    `POST /api/v1/firm/documents/upload-ticket` returns **403** for that token.
13. **Scope.** As an advocate assigned to case A only: `GET /api/v1/firm/documents` returns only case A's
    documents; ask for case B's documents by number → **404**. As an advocate assigned to nothing → an
    empty page, **not** a 403.
14. **Cross-firm.** With two firms seeded, request firm A's document id using firm B's token → **404**
    (not 403).
15. **Paralegal.** Sign in as a paralegal: listing and download work; the upload, share and archive
    affordances are absent, and calling them directly returns **403**.
16. **Quota display.** `GET /api/v1/firm/documents/storage-usage` shows the used figure grow after an
    upload and shrink after an archive.
17. **Quota enforcement.** As Super Admin set a firm's allocation below its current usage → its next
    upload is refused at step ① with the "does not fit" message; existing documents remain intact.
18. **Firm admin cannot allocate.** `PUT /api/v1/super-admin/firms/{firmId}/storage-quota` with a firm
    admin token → **403**.
19. **Unlimited.** Set a firm's quota to `0` → the usage view reports `unlimited: true` and all uploads
    succeed.
20. **Archive releases space.** Archive a large document → `usedBytes` drops and the file is still in the
    bucket (retention is intentional).
21. **Confirm twice** → the second call returns `This upload is no longer awaiting confirmation`.

Automated coverage for items 4, 5, 8, 11–15, 17–20 already exists in
`src/test/java/com/lawfirm/erp/qa/DocumentQaTest.java` (21 end-to-end authorization tests).

---

## 21. Gotchas

1. **Both upload writes use the `data` envelope; `GET`/`DELETE` take no body.** A bare
   `{"filename": "…"}` on the ticket endpoint is a 400 with a message about `data`.
2. **`Content-Type` is a form *field*, not a header.** Set it as a header and the signature no longer
   matches the policy → storage returns 403.
3. **The stored extension comes from the content type.** `scan.PDF` declared as `image/png` is stored as
   `.png`. The `extension` field in the response is authoritative; the original name is preserved
   separately in `fileName`.
4. **Never trim or re-encode a value from `fields`.** The policy signature covers them byte for byte.
5. **`uploadUrl` is a different origin from the API.** If step ② fails with an opaque CORS/network error,
   check the storage server's CORS `AllowedOrigin` — the API's own CORS config does not apply to it.
6. **`search` matches filenames only.** A user searching "CASE-2026-001" will get nothing; use the
   per-case endpoint for that.
7. **`page` is 0-based** and there is no sort parameter — always newest first.
8. **An unfiltered list includes `PENDING_UPLOAD` and `ARCHIVED` rows.** Send `status=ACTIVE` by default.
9. **Download links expire in 15 minutes** and are single-purpose. Do not cache one in component state
   beyond the click that requested it.
10. **Every `download-url` call writes an audit row.** Do not call it to check whether a file exists.
11. **`availableBytes` and `usedPercent` are `0` for unlimited firms**, not "full". Branch on `unlimited`.
12. **`DELETE` archives; it does not delete.** There is no un-archive and no hard delete in Phase 1 — say so
    in the UI.
13. **`responseCode` is the HTTP status, not a domain code.** There is no `responseCode: 0` in error
    responses.
14. **The numeric `id` is the path parameter, the `uuid` is for the audit trail.** Sending a UUID as
    `{documentId}` is a 400 (type mismatch), not a 404.
15. **`createdAt` has no timezone; `expiresAt` does.** Use different parsing.
16. **An error message is the only reliable signal.** The same 400 covers a dozen causes — display
    `message` rather than inventing one from the status.
17. **Permissions are cached server-side for about 5 minutes.** After a role's document permissions are
    changed, a signed-in user may still be allowed *or* still be blocked for a few minutes. Do not read a
    403 as "the admin's change did not save", and have the user sign out and back in if it matters.

---

## 22. Not built (Phase 2 candidates)

Do not design UI that assumes any of these:

| Missing | Impact |
|---|---|
| **Virus scanning** | There is no AV gate. Type and signature checks only. |
| **Versioning / replace** | A document is immutable once `ACTIVE` — new content means a new upload. |
| **Un-archive / restore** | Archiving is one-way from the UI's perspective. |
| **Hard delete / purge** | Bytes are retained deliberately; purging needs a separate authorised operation. |
| **Per-firm encryption keys (KMS)** | Encryption is whatever the storage backend provides. |
| **Inline preview / thumbnails** | Downloads are `attachment`-disposition; no rendered preview. |
| **Folders / tags** | Organisation is by case or project only. |
| **Client uploads** | The client portal is strictly read-only. |
| **Full-text search** | `search` is a filename substring, not content search. |
| **Per-document ACLs** | Access is role + scope + a single `SHARED` flag. |
| **Bulk upload / multi-file ticket** | One file per ticket, one ticket per document. |
| **Compression** | Bytes are stored as sent. |
| **Restricting an upload to a user** | Anyone with `:UPLOAD` and case access can upload. |

Two things are **unverified against live infrastructure** and worth a word with the backend team before
you rely on them: the migration `V2026_09_28__document_store_constraints.sql` has not been run against a
production-shaped PostgreSQL, and the "storage really rejects an oversized POST" behaviour has not been
confirmed on the deployed MinIO build. Neither changes this contract — they only affect how quickly a
bad upload is refused.
