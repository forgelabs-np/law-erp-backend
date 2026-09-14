# Project Management Module — Design Spec

**Date:** 2026-08-21
**Status:** Approved — ready for implementation

---

## 1. Overview

A standalone Project Management module for law firms to manage their clients' recurring legal work: portal credentials, renewal deadlines, and team assignments. Each project belongs to one client. Clients get a limited portal view.

## 2. Entity Model

### 2.1 Project (aggregate root)

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | Exposed in URLs, client portal |
| firm_id | UUID FK → firms | NOT NULL |
| client_user_id | UUID FK → users | NULLABLE — nullable for "new client" flow |
| client_name | VARCHAR(200) | NOT NULL — denormalized for self-containment |
| project_code | VARCHAR(30) | UNIQUE per firm — `FIRMCODE-PRJ-YYYY-NNNNN` |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT | |
| status | ENUM | ACTIVE, ON_HOLD, COMPLETED, CANCELLED |
| start_date | DATE | |
| target_end_date | DATE | NULLABLE — ongoing projects |
| owner_id | UUID FK → users | NOT NULL — project manager |
| created_at / updated_at / created_by / active | — | Audit fields |

Indexes: `(firm_id, status)`, `(firm_id, project_code) UNIQUE`, `(client_user_id)`

### 2.2 Credential

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (PK, auto) | |
| project_id | UUID FK → projects | NOT NULL |
| site_name | VARCHAR(150) | NOT NULL — "USPTO Portal" |
| site_type | VARCHAR(50) | Free-form — "Trademark Portal", "Tax Filing", etc. |
| site_url | VARCHAR(500) | Redirect URL |
| username_or_email | VARCHAR(200) | NOT NULL |
| encrypted_password | TEXT | NOT NULL — AES-256-GCM |
| contact_person | VARCHAR(150) | Who gave us these credentials |
| contact_phone | VARCHAR(15) | |
| contact_email | VARCHAR(200) | |
| notes | TEXT | |
| created_at / updated_at / created_by / active | — | Audit fields |

Indexes: `(project_id)`

### 2.3 RenewalType (shared lookup)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (PK, auto) | |
| firm_id | UUID FK → firms | NULLABLE — NULL = system-wide default |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(250) | |
| is_system | BOOLEAN | System-seeded types can't be deleted |
| created_at / updated_at / active | — | Audit fields |

**Seeded defaults:** Trademark Renewal, Patent Renewal, License Renewal, Annual Compliance, Tax Filing, Secretarial Compliance, Other

### 2.4 Renewal (recurrence template)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (PK, auto) | |
| project_id | UUID FK → projects | NOT NULL |
| renewal_type_id | BIGINT FK → project_renewal_types | NOT NULL |
| title | VARCHAR(200) | NOT NULL |
| description | TEXT | |
| recurrence | ENUM | ONE_TIME, YEARLY, QUARTERLY, MONTHLY |
| start_date | DATE | NOT NULL |
| end_date | DATE | NULLABLE — stop generating after this |
| assigned_to_id | UUID FK → users | Who handles this renewal |
| status | ENUM | ACTIVE, COMPLETED, CANCELLED |
| created_at / updated_at / created_by / active | — | Audit fields |

Indexes: `(project_id)`, `(assigned_to_id)`

### 2.5 RenewalInstance (each deadline occurrence)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (PK, auto) | |
| renewal_id | BIGINT FK → project_renewals | NOT NULL |
| due_date | DATE | NOT NULL |
| status | ENUM | PENDING, IN_PROGRESS, COMPLETED, OVERDUE, SKIPPED |
| completed_at | TIMESTAMP | NULLABLE |
| completed_by_id | UUID FK → users | NULLABLE |
| notes | TEXT | |
| created_at / updated_at / active | — | Audit fields |

Indexes: `(renewal_id)`, `(due_date, status)` — for daily overdue scan

### 2.6 ProjectMember

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (PK, auto) | |
| project_id | UUID FK → projects | NOT NULL |
| user_id | UUID FK → users | NOT NULL |
| role_in_project | ENUM | OWNER, MEMBER, VIEWER |
| created_at / created_by | — | Audit fields |

Unique constraint: `(project_id, user_id)`

## 3. API Endpoints

### 3.1 Projects
```
POST   /api/v1/projects                                    — Create project
GET    /api/v1/projects                                    — List (paginated, filtered)
GET    /api/v1/projects/{projectCode}                      — Get details
PUT    /api/v1/projects/{projectCode}                      — Update
PATCH  /api/v1/projects/{projectCode}/status               — Change status
POST   /api/v1/projects/{projectCode}/members              — Add member
DELETE /api/v1/projects/{projectCode}/members/{userId}     — Remove member
GET    /api/v1/projects/{projectCode}/members              — List members
```

### 3.2 Credentials
```
POST   /api/v1/projects/{projectCode}/credentials                   — Add (encrypted on save)
GET    /api/v1/projects/{projectCode}/credentials                   — List (password masked)
GET    /api/v1/projects/{projectCode}/credentials/{id}              — Get detail
PUT    /api/v1/projects/{projectCode}/credentials/{id}              — Update
DELETE /api/v1/projects/{projectCode}/credentials/{id}              — Soft delete
POST   /api/v1/projects/{projectCode}/credentials/{id}/reveal       — Decrypt password (audit-logged)
```

### 3.3 Renewals
```
POST   /api/v1/projects/{projectCode}/renewals                               — Create (auto-generates instances)
GET    /api/v1/projects/{projectCode}/renewals                               — List
GET    /api/v1/projects/{projectCode}/renewals/{id}                          — Get + instances
PUT    /api/v1/projects/{projectCode}/renewals/{id}                          — Update template
PATCH  /api/v1/projects/{projectCode}/renewals/{id}/status                   — Activate/complete/cancel
PATCH  /api/v1/projects/{projectCode}/renewals/{rid}/instances/{iid}         — Update instance status
```

### 3.4 Renewal Types
```
GET    /api/v1/projects/renewal-types              — List (system + firm custom)
POST   /api/v1/projects/renewal-types              — Create custom
PUT    /api/v1/projects/renewal-types/{id}         — Update custom
DELETE /api/v1/projects/renewal-types/{id}         — Delete (non-system only)
```

### 3.5 Client Portal
```
GET    /api/v1/client/projects                     — List my projects
GET    /api/v1/client/projects/{projectCode}       — View project (no credentials)
GET    /api/v1/client/projects/{projectCode}/renewals — View deadlines
```

### 3.6 Dashboard
```
GET    /api/v1/projects/dashboard                  — Stats: counts, overdue, upcoming
```

## 4. RBAC

New module: `PROJECT_MANAGEMENT`

| Permission | Scope | Who |
|------------|-------|-----|
| `PROJECT_MANAGEMENT:VIEW` | TENANT | All firm users (filtered to member projects) |
| `PROJECT_MANAGEMENT:CREATE` | TENANT | FIRM_ADMIN, SUPER_ADMIN |
| `PROJECT_MANAGEMENT:EDIT` | TENANT | FIRM_ADMIN, SUPER_ADMIN, project OWNER |
| `PROJECT_MANAGEMENT:DELETE` | TENANT | FIRM_ADMIN, SUPER_ADMIN |
| `PROJECT_MANAGEMENT:CREDENTIAL_VIEW` | ASSIGNED | Owner + team members |
| `PROJECT_MANAGEMENT:CREDENTIAL_REVEAL` | ASSIGNED | Owner only (audit-logged) |

## 5. Encryption

- Reuses `ConfigEncryptionUtil` (AES-256-GCM) — same key as SMTP config
- Password encrypted on entity save, decrypted only on `/reveal` endpoint
- `/reveal` is audit-logged: who revealed, when, which credential
- List responses always return masked `••••••••`

## 6. Renewal Engine

- On create with `recurrence != ONE_TIME`: auto-generate `RenewalInstance` rows
  - YEARLY: one per year from start_date → end_date (or 3 years ahead)
  - QUARTERLY: one per quarter
  - MONTHLY: one per month
- `@Scheduled` job runs daily at 08:00: marks PENDING instances with `due_date < today` as OVERDUE
- Overdue count surfaces in project dashboard

## 7. Client Portal

- Uses existing `CLIENT` user type
- `client_user_id` on Project links to the client's User record
- Client sees: project info, renewal deadlines + status
- Client does NOT see: credentials, internal notes, team member details
- Access validated: client can only view projects where `client_user_id = current user`

## 8. File Structure

```
src/main/java/com/lawfirm/erp/modules/projectmanagement/
├── controller/
│   ├── ProjectController.java
│   ├── CredentialController.java
│   ├── RenewalController.java
│   ├── RenewalTypeController.java
│   ├── ClientPortalController.java
│   └── ProjectDashboardController.java
├── service/
│   ├── ProjectService.java (interface)
│   ├── ProjectServiceImpl.java
│   ├── CredentialService.java (interface)
│   ├── CredentialServiceImpl.java
│   ├── RenewalService.java (interface)
│   ├── RenewalServiceImpl.java
│   ├── RenewalTypeService.java (interface)
│   ├── RenewalTypeServiceImpl.java
│   ├── ClientPortalService.java (interface)
│   ├── ClientPortalServiceImpl.java
│   └── RenewalScheduler.java
├── repository/
│   ├── ProjectRepository.java
│   ├── CredentialRepository.java
│   ├── RenewalTypeRepository.java
│   ├── RenewalRepository.java
│   ├── RenewalInstanceRepository.java
│   └── ProjectMemberRepository.java
├── entity/
│   ├── Project.java
│   ├── Credential.java
│   ├── RenewalType.java
│   ├── Renewal.java
│   ├── RenewalInstance.java
│   └── ProjectMember.java
├── dto/
│   ├── request/
│   │   ├── CreateProjectRequest.java
│   │   ├── UpdateProjectRequest.java
│   │   ├── AddCredentialRequest.java
│   │   ├── UpdateCredentialRequest.java
│   │   ├── CreateRenewalRequest.java
│   │   ├── UpdateRenewalRequest.java
│   │   ├── UpdateInstanceStatusRequest.java
│   │   ├── AddMemberRequest.java
│   │   └── CreateRenewalTypeRequest.java
│   └── response/
│       ├── ProjectResponse.java
│       ├── ProjectSummaryResponse.java
│       ├── CredentialResponse.java
│       ├── RenewalResponse.java
│       ├── RenewalInstanceResponse.java
│       ├── RenewalTypeResponse.java
│       ├── ProjectMemberResponse.java
│       ├── ProjectDashboardResponse.java
│       └── ClientProjectResponse.java
├── enums/
│   ├── ProjectStatus.java
│   ├── CredentialSiteType.java
│   ├── RenewalRecurrence.java
│   ├── RenewalStatus.java
│   ├── RenewalInstanceStatus.java
│   └── ProjectMemberRole.java
└── mapper/
    └── ProjectMapper.java
```

## 9. Seed Data

- `PROJECT_MANAGEMENT` module registered in `DataInitializer`
- 6 permissions auto-assigned to SUPER_ADMIN
- 7 default RenewalType records (is_system=true, firm_id=NULL)
