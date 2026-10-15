# Object Storage (MinIO / S3) — Configuration Guide

Last updated: 2026-10-03 · branch `devG`
Applies to: `common/storage/**`, `modules/document/**`
Related: `docs/frontend-guide.md` (FE contract), `docs/document-store-module.pdf` (full write-up)

---

## 0. The one-paragraph version

A document is created in **one multipart POST to the API** (`POST /api/v1/firm/documents`): the API
receives the bytes, writes them to the object store and returns the document **ACTIVE** — there is no
separate confirm step and no browser-direct-to-storage upload. All storage access in the codebase goes
through one interface, `StorageService`, and its only real implementation is `MinioStorageService`
(the single class that imports `io.minio`). Whether "object storage" is a MinIO container on your laptop or a managed S3 on
the server is purely a matter of the connection settings in the active Spring profile — **the code is
identical in both**. There is no `isProduction` branch in the storage layer (see §9).

---

## 1. What talks to storage

```
DocumentServiceImpl ──┐
StorageQuotaService   ├──► StorageService (interface) ──► MinioStorageService ──► MinIO / S3
StorageBootstrap      ┘                                          └─ io.minio SDK (only importer)
```

| Piece | File | Role |
|---|---|---|
| `StorageService` | `common/storage/StorageService.java` | The swappable contract: `put`, `presignDownload`, `stat`, `readHead`, `delete`, `ensureBucket`, `isConfigured`. Deliberately knows nothing about documents. |
| `MinioStorageService` | `common/storage/MinioStorageService.java` | The only class that imports `io.minio`. Resolves the client lazily so the app/tests boot with no storage present. |
| `StorageConfig` | `common/storage/StorageConfig.java` | Builds the `MinioClient` bean, **only when `storage.minio.endpoint` is set**. |
| `StorageProperties` | `common/storage/StorageProperties.java` | Connection settings (endpoint, keys, bucket, region). |
| `DocumentServiceImpl` | `modules/document/service/DocumentServiceImpl.java` | Stores the uploaded file and returns the document `ACTIVE` in one call; presigns downloads. |
| `StorageBootstrap` | `common/storage/StorageBootstrap.java` | At startup, creates the bucket if missing. An unreachable store is logged, **not fatal**. |

---

## 2. Upload lifecycle (one request)

```
POST /api/v1/firm/documents      multipart/form-data
  file          the bytes
  matterNumber  ┐ exactly one
  projectCode   ┘
  courtCaseRef  optional
→ 200 { id, uuid, status: "ACTIVE", fileName, sizeBytes, documentUrl, ... }
```

The API, in order: validates the filename and declared content type, enforces the size policy,
checks the firm's quota, builds the object key, **writes the file to storage** (`StorageService.put`),
checks the file signature from the stored bytes, reserves quota, saves the row `ACTIVE` and appends
the case timeline event.

- Every rejection path **discards the object it just wrote**, so a refused upload never leaves an
  orphan in the bucket.
- The size ceiling is the transport limit (`spring.servlet.multipart.max-file-size`, 60 MB) *and* the
  `STORAGE_MAX_FILE_SIZE_BYTES` policy (50 MB default) — the policy is the real one.
- Because the file passes through the app, the previous browser-direct-to-storage flow (upload ticket
  → POST to storage → confirm), the `PENDING_UPLOAD` status and `PendingUploadSweeper` are gone.

---

## 3. Configuration model — three layers

### 3.1 Connection settings → Spring profile + env (NOT the DB)

These are environment-bound (the MinIO client is built at startup), so they live in yml/env:

| Setting | dev (default) | prod |
|---|---|---|
| `storage.minio.endpoint` | `http://localhost:9000` (hardcoded in `application-dev.yml`) | `${MINIO_ENDPOINT:}` — **must be set on the server** |
| `storage.minio.access-key` | `${MINIO_ACCESS_KEY:minioadmin}` | `${MINIO_ACCESS_KEY:}` |
| `storage.minio.secret-key` | `${MINIO_SECRET_KEY:minioadmin}` | `${MINIO_SECRET_KEY:}` |
| `storage.minio.bucket` | `tarikh-documents` | `${MINIO_BUCKET:tarikh-documents}` |
| `storage.minio.region` | `us-east-1` | `${MINIO_REGION:us-east-1}` |

> The endpoint **must be reachable from the user's browser**, not just from the app — presigned URLs
> are generated against it and the browser connects to it directly. An in-cluster hostname like
> `http://minio:9000` will break uploads at runtime.

### 3.2 Policy settings → the `STORAGE` group in `system_config` (DB)

Tunable at runtime, editable from the Super Admin settings UI (no rebuild/restart):

| Key | Default | Meaning |
|---|---|---|
| `STORAGE_MAX_FILE_SIZE_BYTES` | 52428800 (50 MiB) | Max size of one file |
| `STORAGE_UPLOAD_EXPIRY_SECONDS` | 1800 | Upload ticket lifetime |
| `STORAGE_DOWNLOAD_EXPIRY_SECONDS` | 900 | Download link lifetime |
| `STORAGE_DEFAULT_QUOTA_BYTES` | 5368709120 (5 GiB) | Quota given to a firm on first use (0 = unlimited) |
| `DOCUMENT_MAX_FILENAME_LENGTH` | 120 | Truncation limit for stored filenames |

These are declared once in `ConfigKeyRegistry` (`GROUP_STORAGE`), seeded at boot by
`SystemConfigService.seedGlobalDefaults()` (insert-if-missing), and read through runtime helpers
(`storageMaxFileSizeBytes()`, etc.). The yml values remain only as the pre-seed fallback.

### 3.3 Precedence

```
DB (system_config, if the row exists)  >  yml/env value of the active profile
```

`APP_PRODUCTION` (`application.yml:app.production`) follows the same pattern but is **unrelated to
storage** — see §9.

---

## 4. Production / server setup

Set these env vars on the server (the `prod` profile reads them):

```bash
MINIO_ENDPOINT=https://storage.example.com   # MUST be browser-reachable
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=tarikh-documents
MINIO_REGION=us-east-1
DEFAULT_STORAGE_QUOTA_BYTES=5368709120
```

- The app runs as profile `prod` (`spring.profiles.active` today is set in `application.yml`;
  on the server, override it, e.g. `-Dspring.profiles.active=prod`).
- The **bucket is created automatically** at startup by `StorageBootstrap` → `ensureBucket()`, so
  there is no init step. The bucket is private; nothing is served without a signed URL.
- If storage is unreachable at boot the app still starts; document endpoints return
  **`502 Document storage is temporarily unavailable. Please try again.`**
- ⚠️ **Verify the empty-default path.** `application-prod.yml` defaults `MINIO_ENDPOINT` to empty and
  comments that the app stays bootable. In code, `StorageConfig` creates the `MinioClient` bean
  whenever the `storage.minio.endpoint` property is *present* — and an empty value still counts as
  present. Confirm a boot with `MINIO_ENDPOINT` unset before relying on it; if it fails, make the
  condition require a non-blank value.

> ⚠️ **MinIO's own distribution was removed on 2026-09-11** (see §5). Plan the server on a
> **maintained S3-compatible backend** — SeaweedFS, Garage, RustFS, or a managed S3 — since the
> MinIO images/binaries are no longer published.

---

## 5. Local setup (as configured on 2026-10-03)

Locally the app uses the `dev` profile, which points at `http://localhost:9000`. That endpoint is
served by a MinIO container from the repo's `docker-compose.yml`.

### 5.1 Prerequisite: Docker Desktop running

MinIO is a container; the Docker engine must be up.

```bash
docker info    # if this errors, start Docker Desktop
```

### 5.2 Start MinIO

```bash
docker compose up -d
docker compose ps       # expect: tarikh-minio ... Up (healthy)  0.0.0.0:9000-9001->9000-9001/tcp
                        # (9001 is mapped but unused — there is no web console, see §5.5)
```

### 5.3 The image is a stopgap — read this

MinIO **deleted its Docker Hub repositories on 2026-09-11**. As a result:

| Source | Status |
|---|---|
| `minio/minio` (Docker Hub) | ❌ 404 — `object not found` |
| `quay.io/minio/minio` | ❌ 401 — requires authentication |
| `dl.min.io/server/minio/...` (binaries) | ❌ 410 Gone |
| `github.com/minio/minio` | ❌ removed |

`docker-compose.yml` therefore uses the **Bitnami legacy image**,
`bitnamilegacy/minio:2025.7.23-debian-12-r5`, which still bundles the genuine MinIO server
(`RELEASE.2025-07-23`). It is a **local-development-only stopgap**: same `MINIO_ROOT_USER/PASSWORD`
env vars and same ports as the old image, but it is frozen (last pushed 2025-08-19) and will itself
disappear eventually. **Do not deploy it to the server.**

### 5.4 Create the bucket (first time / if the app booted while storage was down)

The app creates the bucket at startup, but if it started while MinIO was down the bucket won't exist.
Create it manually:

```bash
docker exec tarikh-minio sh -c \
  'export MC_HOST_local=http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@localhost:9000 && \
   mc mb --ignore-existing local/tarikh-documents && mc ls local'
```

**No app restart is needed afterwards** — nothing caches bucket state, each request re-checks.

### 5.5 Verify

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9000/minio/health/live   # 200
docker exec tarikh-minio sh -c \
  'export MC_HOST_local=http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@localhost:9000 && mc ls local'
```

> **There is no web console.** This MinIO build (`RELEASE.2025-07-23`) no longer ships the embedded
> browser UI — the container starts the **API on `:9000` only**, so <http://localhost:9001> returns
> nothing even though the port is mapped. Use `mc` (§5.6) or the app's own API instead.

### 5.6 Inspecting the store with `mc`

`mc` is bundled in the container. Convenience alias, then normal commands:

```bash
docker exec -it tarikh-minio sh
export MC_HOST_local=http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@localhost:9000
mc ls local                       # buckets
mc ls --recursive local/tarikh-documents   # objects
mc cp local/tarikh-documents/some/key.pdf ./key.pdf
```

---

## 6. Where the bytes are stored

### 6.1 Local (Docker)

The store is **bind-mounted into the repo**, so its directory tree is visible on disk:

| Layer | Location |
|---|---|
| On the Windows host | `<repo>/data/minio` (git-ignored) |
| Inside the container | `/bitnami/minio/data` |
| Compose line | `- ./data/minio:/bitnami/minio/data` |

Contents: `.minio.sys/`, `.root_user`, `.root_password`, and one directory per bucket
(`tarikh-documents/`).

Inspect from the host:

```bash
ls -la data/minio/
docker exec tarikh-minio sh -c 'ls -la /bitnami/minio/data/'
```

> ⚠️ **Objects are not plain files.** MinIO stores each object in its own internal format: an object
> appears as a *directory* named after its key containing `xl.meta` (plus `part.1`, … for larger
> objects). Small objects are inlined in `xl.meta`. So the bucket/object **structure** is visible in
> Explorer, but you **cannot double-click a stored document to read it**.
>
> To get the actual bytes use `mc cp` (§5.6), a presigned download URL, or the app.

> ⚠️ `docker compose down -v` or deleting `data/minio` **deletes every stored document**. Use plain
> `docker compose down` to keep the data.

Historical note: this used to be a Docker **named volume** (`law-erp-backend_minio-data`) that lived
only inside Docker Desktop's WSL2 disk
(`C:\Users\<you>\AppData\Local\Docker\wsl\disk\docker_data.vhdx`), which is why nothing showed up in
Explorer. The old volume still exists but is now unused.

### 6.2 Production

Objects live in the configured bucket (`tarikh-documents` by default) on the S3/MinIO endpoint.

### 6.3 Object key layout (same in both)

Built by `DocumentStoragePath`:

```
firms/{firmId}/cases/{matterNumber}/{objectId}/{filename}
firms/{firmId}/projects/{projectCode}/{objectId}/{filename}
```

- `firmId` is the first segment because a firm *code* is mutable and would orphan objects beneath it.
- `objectId` is a generated UUID folder so readable filenames never have to be made unique.
- `filename` is sanitised and truncated to `DOCUMENT_MAX_FILENAME_LENGTH`.

---

## 7. Storage quota

- Usage lives in the `firm_storage_usage` table; quota is **Super-Admin-only**
  (`PUT/GET /api/v1/super-admin/firms/{firmId}/storage-quota|storage-usage`) so a firm admin cannot
  raise their own limit.
- `0 = unlimited`. A firm's first write is given 5 GiB.
- Archiving a document **releases** its bytes from the quota (the object is kept).
- Firm view: `GET /api/v1/firm/documents/storage-usage`.

---

## 8. Dev vs Prod at a glance

| | Local (dev) | Production (prod) |
|---|---|---|
| Profile | `dev` | `prod` |
| Endpoint source | hardcoded `http://localhost:9000` | `MINIO_ENDPOINT` env |
| Server | `tarikh-minio` container | external / managed S3-compatible |
| Bucket | `tarikh-documents` | `MINIO_BUCKET` |
| Credentials | `minioadmin` / `minioadmin` | env secrets |
| Data location | Docker volume `law-erp-backend_minio-data` | bucket on the endpoint |
| Image | `bitnamilegacy/minio:2025.7.23-debian-12-r5` (stopgap) | maintained S3 backend |
| Code path | identical — `StorageService` → `MinioStorageService` | identical |

**The only difference is configuration.** There is no branch in the code that swaps the storage
backend by environment.

---

## 9. `APP_PRODUCTION` is NOT the storage switch

There is a production flag — `SystemConfigService.productionFlag()` (GLOBAL key `APP_PRODUCTION`,
`Y`/`N`, seeded from `app.production` in yml; also surfaced as `TotpUtil.isProduction()`). **Nothing
in `common/storage/**` reads it.** Today its only consumer is the MFA dev-bypass:

```java
// TotpUtil: "123456" always passes unless production
if (!isProduction() && DEV_BYPASS_CODE.equals(code.trim())) { ... }
```

If you want a single runtime flag to *also* switch the storage target, that is a new feature
(a routing `StorageService` + a local-disk implementation + upload/download endpoints), not existing
behaviour. As configured, the dev/prod storage split is done by **Spring profile**, which is the
right tool for an environment-bound connection anyway.

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `400 This document is not available for download` | The document is `ARCHIVED`. Archived documents are deliberately not downloadable; re-upload if needed. |
| `400 The file content does not match its declared type` | The bytes did not match the content type (e.g. an executable renamed `.pdf`). Nothing is left in storage. |
| `400 A file may be at most …` | The upload exceeds `STORAGE_MAX_FILE_SIZE_BYTES`. |
| `413 Payload Too Large` / multipart rejected | The file exceeds the transport limit; raise `spring.servlet.multipart.max-file-size` (keep it above the policy). |
| `502 Document storage is temporarily unavailable` | The app cannot reach the configured endpoint. Start MinIO (or set the correct `MINIO_ENDPOINT`). |
| `docker compose up` → `pull access denied for minio/minio` | Expected since 2026-09-11. The compose file now uses the Bitnami legacy image; pull again. |
| List shows a row but `documentUrl` is `null` | The document is `ARCHIVED` (embedded download URLs are only added for `ACTIVE` rows). |

---

## 11. Open items / next steps

1. **Pick a maintained S3-compatible backend for the server.** MinIO distribution is gone; don't
   carry the Bitnami legacy image to production.
2. **Verify the empty-`MINIO_ENDPOINT` boot path** (§4).
3. Optional: a runtime `isProduction`-driven local-disk backend so local dev needs no container at
   all (see §9).
