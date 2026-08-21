# Project Management Module — Official Documentation

**Module Code:** `PROJECT_MANAGEMENT`
**Version:** 1.0.0
**Date:** 2026-08-21
**Status:** Production Ready

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Data Model](#3-data-model)
4. [API Reference](#4-api-reference)
5. [RBAC & Permissions](#5-rbac--permissions)
6. [Security — Credential Encryption](#6-security--credential-encryption)
7. [Renewal Engine](#7-renewal-engine)
8. [Client Portal](#8-client-portal)
9. [Restrictions & Business Rules](#9-restrictions--business-rules)
10. [Frontend Integration Guide](#10-frontend-integration-guide)
11. [Seed Data](#11-seed-data)
12. [Testing](#12-testing)

---

## 1. Overview

The Project Management module enables law firms to manage their clients' recurring legal work — portal credentials, renewal deadlines, and team assignments. Each project belongs to one client and can have multiple credentials, renewals, and team members.

### Key Features

- **Project Profiles** — Create and manage projects linked to clients
- **Credential Vault** — Store portal credentials with AES-256-GCM encryption
- **Renewal Tracking** — Recurring schedules (yearly, quarterly, monthly) + one-time deadlines
- **Team Management** — Owner + member assignments per project
- **Client Portal** — Limited view for clients to see their projects and renewal deadlines
- **Dashboard** — Overdue alerts, upcoming deadlines, project statistics

---

## 2. Architecture

### Module Structure

```
src/main/java/com/lawfirm/erp/modules/projectmanagement/
├── controller/          # REST controllers (6)
├── service/             # Business logic (6 interfaces + 7 impls)
├── repository/          # Data access (6 repos)
├── entity/              # JPA entities (6)
├── dto/request/         # Inbound DTOs (9)
├── dto/response/        # Outbound DTOs (9)
├── enums/               # Type-safe enums (5)
└── mapper/              # Entity → DTO mapping (1)
```

### Pattern

All services follow the codebase standard:

```
Controller → Service (interface) → ServiceImpl → Mapper → Repository
```

### Dependencies

- `ConfigEncryptionUtil` — AES-256-GCM encryption for credentials
- `AuditService` — All mutations are audit-logged
- `CurrentUserResolver` — Resolves the authenticated user
- `FirmContextHolder` — Multi-tenant firm context
- `UserRepository` — User resolution for member names

---

## 3. Data Model

### Entity Relationship Diagram

```
┌─────────────┐       ┌──────────────────┐       ┌─────────────────┐
│   Project    │──1:N──│   Credential     │       │  RenewalType    │
│  (UUID PK)   │       │  (Long PK)       │       │  (Long PK)      │
│              │       └──────────────────┘       └────────┬────────┘
│              │──1:N──┌──────────────────┐                │
│              │       │    Renewal       │──1:N───────────┘
│              │       │  (Long PK)       │
│              │       └────────┬─────────┘
│              │                │
│              │       ┌────────▼─────────┐
│              │       │ RenewalInstance  │
│              │       │  (Long PK)       │
│              │       └──────────────────┘
│              │
│              │──1:N──┌──────────────────┐
└──────────────┘       │  ProjectMember   │
                       │  (Long PK)       │
                       └──────────────────┘
```

### 3.1 Project (Aggregate Root)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK | Exposed in URLs and client portal |
| `firm_id` | UUID | FK → firms, NOT NULL | Multi-tenant scope |
| `client_user_id` | UUID | FK → users, NULLABLE | Link to existing client |
| `client_name` | VARCHAR(200) | NOT NULL | Denormalized client name |
| `project_code` | VARCHAR(30) | UNIQUE per firm | Format: `FIRMCODE-PRJ-YYYY-NNNNN` |
| `name` | VARCHAR(200) | NOT NULL | Project name |
| `description` | TEXT | | Project description |
| `status` | ENUM | NOT NULL | ACTIVE, ON_HOLD, COMPLETED, CANCELLED |
| `start_date` | DATE | | Project start date |
| `target_end_date` | DATE | NULLABLE | End date (null = ongoing) |
| `owner_id` | UUID | FK → users, NOT NULL | Project manager |
| `created_at` | TIMESTAMP | | Audit |
| `updated_at` | TIMESTAMP | | Audit |
| `created_by` | UUID | | Audit |
| `is_active` | BOOLEAN | DEFAULT TRUE | Soft delete |

**Indexes:** `(firm_id, status)`, `(firm_id, project_code) UNIQUE`, `(client_user_id)`

### 3.2 Credential

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO | |
| `project_id` | UUID | FK → projects, NOT NULL | |
| `site_name` | VARCHAR(150) | NOT NULL | e.g. "USPTO Portal" |
| `site_type` | VARCHAR(50) | | Free-form: "Trademark Portal" |
| `site_url` | VARCHAR(500) | | Redirect URL |
| `username_or_email` | VARCHAR(200) | NOT NULL | |
| `encrypted_password` | TEXT | NOT NULL | AES-256-GCM encrypted |
| `contact_person` | VARCHAR(150) | | Who gave us credentials |
| `contact_phone` | VARCHAR(15) | | |
| `contact_email` | VARCHAR(200) | | |
| `notes` | TEXT | | |
| `is_active` | BOOLEAN | DEFAULT TRUE | Soft delete |

### 3.3 RenewalType

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO | |
| `firm_id` | UUID | FK → firms, NULLABLE | NULL = system default |
| `name` | VARCHAR(100) | NOT NULL | e.g. "Trademark Renewal" |
| `description` | VARCHAR(250) | | |
| `is_system` | BOOLEAN | DEFAULT FALSE | System types can't be deleted |
| `is_active` | BOOLEAN | DEFAULT TRUE | |

### 3.4 Renewal (Recurrence Template)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO | |
| `project_id` | UUID | FK → projects, NOT NULL | |
| `renewal_type_id` | BIGINT | FK → renewal_types, NOT NULL | |
| `title` | VARCHAR(200) | NOT NULL | |
| `description` | TEXT | | |
| `recurrence` | ENUM | NOT NULL | ONE_TIME, YEARLY, QUARTERLY, MONTHLY |
| `start_date` | DATE | NOT NULL | First occurrence |
| `end_date` | DATE | NULLABLE | Stop generating after this |
| `assigned_to_id` | UUID | FK → users | Who handles this |
| `status` | ENUM | NOT NULL | ACTIVE, COMPLETED, CANCELLED |
| `is_active` | BOOLEAN | DEFAULT TRUE | |

### 3.5 RenewalInstance (Each Deadline)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO | |
| `renewal_id` | BIGINT | FK → renewals, NOT NULL | |
| `due_date` | DATE | NOT NULL | |
| `status` | ENUM | NOT NULL | PENDING, IN_PROGRESS, COMPLETED, OVERDUE, SKIPPED |
| `completed_at` | TIMESTAMP | NULLABLE | |
| `completed_by_id` | UUID | FK → users | |
| `notes` | TEXT | | |

### 3.6 ProjectMember

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO | |
| `project_id` | UUID | FK → projects, NOT NULL | |
| `user_id` | UUID | FK → users, NOT NULL | |
| `role_in_project` | ENUM | NOT NULL | OWNER, MEMBER, VIEWER |

**Unique constraint:** `(project_id, user_id)`

---

## 4. API Reference

**Base URL:** `/api/v1`

All endpoints require JWT authentication via `Authorization: Bearer <token>` header.

### 4.1 Projects

#### Create Project
```
POST /api/v1/projects
```

**Request Body:**
```json
{
  "name": "Nepal Telecom Trademark Renewal",
  "clientName": "Nepal Telecom Ltd.",
  "clientUserId": "uuid-of-existing-client",
  "description": "Annual trademark renewal for Nepal Telecom",
  "startDate": "2026-01-15",
  "targetEndDate": "2028-12-31",
  "ownerId": "uuid-of-project-manager"
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "message": "Project created",
  "data": {
    "id": "uuid",
    "projectCode": "APX-PRJ-2026-00001",
    "name": "Nepal Telecom Trademark Renewal",
    "clientName": "Nepal Telecom Ltd.",
    "status": "ACTIVE",
    "ownerName": "Ram Shrestha",
    "members": [...],
    "credentialCount": 0,
    "renewalCount": 0,
    "overdueInstances": 0
  }
}
```

#### List Projects
```
GET /api/v1/projects?status=ACTIVE&page=0&size=20
```

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | String | No | Filter by status |
| `search` | String | No | Search by name |
| `page` | int | No | Page number (default: 0) |
| `size` | int | No | Page size (default: 20) |

#### Get Project
```
GET /api/v1/projects/{projectCode}
```

#### Update Project
```
PUT /api/v1/projects/{projectCode}
```

#### Change Status
```
PATCH /api/v1/projects/{projectCode}/status?status=ON_HOLD
```

#### Add Team Member
```
POST /api/v1/projects/{projectCode}/members
```

**Request Body:**
```json
{
  "userId": "uuid-of-user",
  "role": "MEMBER"
}
```

#### Remove Team Member
```
DELETE /api/v1/projects/{projectCode}/members/{userId}
```

#### List Team Members
```
GET /api/v1/projects/{projectCode}/members
```

### 4.2 Credentials

#### Add Credential
```
POST /api/v1/projects/{projectCode}/credentials
```

**Request Body:**
```json
{
  "siteName": "USPTO Portal",
  "siteType": "Trademark Portal",
  "siteUrl": "https://www.uspto.gov",
  "usernameOrEmail": "nepaltelecom@legal.com",
  "password": "actual-password-here",
  "contactPerson": "Sita Sharma",
  "contactPhone": "+977-9841234567",
  "contactEmail": "sita@uspto.gov",
  "notes": "Portal access for trademark filing"
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "message": "Credential added",
  "data": {
    "id": 1,
    "siteName": "USPTO Portal",
    "siteType": "Trademark Portal",
    "siteUrl": "https://www.uspto.gov",
    "usernameOrEmail": "nepaltelecom@legal.com",
    "password": "••••••••",
    "contactPerson": "Sita Sharma",
    "createdAt": "2026-08-21T10:30:00"
  }
}
```

#### List Credentials
```
GET /api/v1/projects/{projectCode}/credentials
```

**Note:** Passwords are always masked as `••••••••` in list responses.

#### Reveal Password
```
POST /api/v1/projects/{projectCode}/credentials/{id}/reveal
```

**Response:**
```json
{
  "success": true,
  "message": "Password revealed (audit-logged)",
  "data": {
    "password": "actual-decrypted-password"
  }
}
```

**⚠️ Security:** This endpoint is audit-logged. The reveal event (who, when, which credential) is recorded.

### 4.3 Renewals

#### Create Renewal
```
POST /api/v1/projects/{projectCode}/renewals
```

**Request Body:**
```json
{
  "renewalTypeId": 1,
  "title": "Nepal Telecom Trademark Yearly",
  "description": "Annual trademark renewal filing",
  "recurrence": "YEARLY",
  "startDate": "2026-03-15",
  "endDate": "2029-03-15",
  "assignedToId": "uuid-of-handling-user"
}
```

**Response:** Auto-generates `RenewalInstance` rows based on recurrence.

#### List Renewals
```
GET /api/v1/projects/{projectCode}/renewals
```

#### Get Renewal + Instances
```
GET /api/v1/projects/{projectCode}/renewals/{id}
```

#### Update Instance Status
```
PATCH /api/v1/projects/{projectCode}/renewals/{renewalId}/instances/{instanceId}
```

**Request Body:**
```json
{
  "status": "COMPLETED",
  "notes": "Filed on time, confirmation received"
}
```

### 4.4 Renewal Types

#### List Types
```
GET /api/v1/projects/renewal-types
```

#### Create Custom Type
```
POST /api/v1/projects/renewal-types
```

**Request Body:**
```json
{
  "name": "Environmental Compliance",
  "description": "Annual environmental compliance filing"
}
```

#### Delete Custom Type
```
DELETE /api/v1/projects/renewal-types/{id}
```

**Note:** System types (`isSystem=true`) cannot be deleted.

### 4.5 Client Portal

#### List My Projects
```
GET /api/v1/client/projects
```

**Note:** Returns only projects where `clientUserId = current user`. No credentials or internal notes.

#### View Project
```
GET /api/v1/client/projects/{projectCode}
```

#### View Renewal Deadlines
```
GET /api/v1/client/projects/{projectCode}/renewals
```

### 4.6 Dashboard

#### Get Dashboard Stats
```
GET /api/v1/projects/dashboard
```

**Response:**
```json
{
  "totalProjects": 15,
  "activeProjects": 12,
  "overdueInstances": 3,
  "upcomingInstances": 8,
  "totalCredentials": 24,
  "totalRenewals": 45,
  "overdueItems": [
    {
      "projectCode": "APX-PRJ-2026-00001",
      "projectName": "Nepal Telecom Trademark",
      "renewalTitle": "Annual Trademark Filing",
      "renewalTypeName": "Trademark Renewal",
      "dueDate": "2026-08-01",
      "daysOverdue": 20
    }
  ],
  "upcomingItems": [...]
}
```

---

## 5. RBAC & Permissions

### Module: `PROJECT_MANAGEMENT`

| Permission | Scope | SUPER_ADMIN | FIRM_ADMIN | ADVOCATE | PARALEGAL | CLIENT |
|------------|-------|-------------|------------|----------|-----------|--------|
| `VIEW` | TENANT | ✅ | ✅ | ✅ | ✅ | ✅ (own) |
| `CREATE` | TENANT | ✅ | ✅ | ❌ | ❌ | ❌ |
| `EDIT` | TENANT | ✅ | ✅ | ✅ (owned) | ❌ | ❌ |
| `DELETE` | TENANT | ✅ | ✅ | ❌ | ❌ | ❌ |
| `CREDENTIAL_VIEW` | ASSIGNED | ✅ | ✅ | ✅ (member) | ✅ (member) | ❌ |
| `CREDENTIAL_REVEAL` | ASSIGNED | ✅ | ✅ | ✅ (owner) | ❌ | ❌ |

### Access Rules

- **Project creation:** Only SUPER_ADMIN and FIRM_ADMIN
- **Project editing:** SUPER_ADMIN, FIRM_ADMIN, and project OWNER
- **Credential access:** Only team members (owner + members)
- **Credential reveal:** Only project OWNER (audit-logged)
- **Client portal:** Only the linked client user can view their projects
- **Non-admin users:** See only projects they're members of

---

## 6. Security — Credential Encryption

### Encryption Details

- **Algorithm:** AES-256-GCM (Galois/Counter Mode)
- **Key:** 256-bit, stored in `config.encryption.key` (Base64-encoded)
- **IV:** 12-byte random IV per encryption
- **Tag:** 128-bit authentication tag

### How It Works

1. **On Save:** Password is encrypted using `ConfigEncryptionUtil.encrypt()` and stored as `encrypted_password`
2. **On List/Detail:** Password is returned as `••••••••` (never decrypted in bulk)
3. **On Reveal:** Password is decrypted using `ConfigEncryptionUtil.decrypt()` — only when explicitly requested
4. **Audit:** Every reveal event is logged with: user ID, credential ID, site name, timestamp

### Reuse

The same `ConfigEncryptionUtil` is used for:
- SMTP email config passwords
- Project management credential passwords

---

## 7. Renewal Engine

### Recurrence Types

| Type | Behavior |
|------|----------|
| `ONE_TIME` | Single deadline on `start_date` |
| `YEARLY` | One instance per year |
| `QUARTERLY` | One instance per quarter (3 months) |
| `MONTHLY` | One instance per month |

### Instance Generation

When a renewal is created with `recurrence != ONE_TIME`:

1. **With `endDate`:** Generate instances from `startDate` to `endDate`
2. **Without `endDate`:** Generate instances for 3 years ahead from `startDate`

Example: `YEARLY` starting `2026-03-15` with no `endDate` → generates instances for:
- 2026-03-15
- 2027-03-15
- 2028-03-15

### Daily Overdue Check

A `@Scheduled` job runs daily at **08:00 AM**:

```
Marks PENDING instances with dueDate < today as OVERDUE
```

This is implemented in `RenewalScheduler.java`.

### Instance Status Flow

```
PENDING → IN_PROGRESS → COMPLETED
    ↓
  OVERDUE (auto-detected)
    ↓
  SKIPPED (manual)
```

---

## 8. Client Portal

### Access

- Uses existing `CLIENT` user type
- Client must be linked to a project via `clientUserId`
- Client can only see projects where `clientUserId = current user`

### What Clients See

| Field | Visible | Notes |
|-------|---------|-------|
| Project name, status, dates | ✅ | |
| Project description | ✅ | |
| Renewal deadlines | ✅ | PENDING + OVERDUE only |
| Renewal status | ✅ | |
| Credentials | ❌ | Never exposed to client |
| Team members | ❌ | Internal only |
| Internal notes | ❌ | |

### API Endpoints

```
GET /api/v1/client/projects
GET /api/v1/client/projects/{projectCode}
GET /api/v1/client/projects/{projectCode}/renewals
```

---

## 9. Restrictions & Business Rules

### Project Rules

1. **One client per project** — `clientUserId` is a single UUID, not a list
2. **Project code is immutable** — Generated at creation, never changes
3. **Owner cannot be removed** — The project owner cannot be removed from members
4. **Owner is auto-added** — When a project is created, the owner is automatically added as a `OWNER` member
5. **Status transitions** — Any status can transition to any other status (no restricted transitions for V1)

### Credential Rules

1. **Passwords are never stored in plaintext** — Always encrypted with AES-256-GCM
2. **Passwords are never returned in list/detail** — Always masked as `••••••••`
3. **Password reveal is audit-logged** — Every reveal event is recorded
4. **Soft delete** — Credentials are deactivated, not hard-deleted

### Renewal Rules

1. **Recurrence auto-generates instances** — YEARLY generates 3 years ahead by default
2. **One-time renewals get exactly 1 instance**
3. **Overdue is auto-detected** — Daily scheduler marks past-due PENDING instances as OVERDUE
4. **System renewal types cannot be deleted** — Only firm-scoped custom types can be removed

### Member Rules

1. **Unique membership** — A user can only be a member of a project once (unique constraint)
2. **Owner protection** — The project owner cannot be removed
3. **Firm-scoped** — Only users belonging to the same firm can be added as members

---

## 10. Frontend Integration Guide

### Project List Page

```
GET /api/v1/projects?page=0&size=20&status=ACTIVE
```

- Display: project code, name, client name, status, owner, credential count, renewal count, overdue badge
- Filter: by status (dropdown)
- Sort: by createdAt DESC (default)

### Project Detail Page

```
GET /api/v1/projects/{projectCode}
```

**Tabs:**
1. **Overview** — Name, description, status, dates, owner
2. **Credentials** — List with masked passwords, "Reveal" button, "Add" form
3. **Renewals** — List with instances, status badges, "Add" form
4. **Team** — Member list with roles, "Add Member" / "Remove" actions

### Credential Card

```jsx
<CredentialCard
  siteName={credential.siteName}
  siteType={credential.siteType}
  siteUrl={credential.siteUrl}      // "Open Portal" link
  username={credential.usernameOrEmail}
  password={credential.password}     // "••••••••" until revealed
  onReveal={() => POST /credentials/{id}/reveal}
  contact={credential.contactPerson}
/>
```

### Renewal Timeline

```jsx
<RenewalTimeline
  renewals={renewals.map(r => ({
    title: r.title,
    type: r.renewalTypeName,
    instances: r.instances.map(i => ({
      dueDate: i.dueDate,
      status: i.status,           // PENDING, OVERDUE, COMPLETED
      daysOverdue: i.status === 'OVERDUE' ? calculateDays(i.dueDate) : null
    }))
  }))}
/>
```

### Dashboard Cards

```jsx
<DashboardStats
  totalProjects={data.totalProjects}
  activeProjects={data.activeProjects}
  overdueCount={data.overdueInstances}
  upcomingCount={data.upcomingInstances}
/>

<OverdueAlerts items={data.overdueItems} />
<UpcomingDeadlines items={data.upcomingItems} />
```

### Client Portal

```jsx
// Only for CLIENT role users
<ClientProjectList
  endpoint="/api/v1/client/projects"
  // Shows: name, status, dates, upcoming renewals
  // Hides: credentials, team, internal notes
/>
```

### Form Validation

| Field | Required | Validation |
|-------|----------|------------|
| Project name | Yes | Max 200 chars |
| Client name | Yes | Max 200 chars |
| Client user ID | No | Must exist in firm |
| Start date | No | Date |
| Target end date | No | Date, must be after start |
| Site name (credential) | Yes | Max 150 chars |
| Username (credential) | Yes | Max 200 chars |
| Password (credential) | Yes | Min 1 char (encrypted on save) |
| Renewal title | Yes | Max 200 chars |
| Renewal type ID | Yes | Must exist |
| Recurrence | Yes | Enum: ONE_TIME, YEARLY, QUARTERLY, MONTHLY |
| Start date (renewal) | Yes | Date |

---

## 11. Seed Data

### Auto-Seeded on Application Start

#### Module & Permissions

```
Module: PROJECT_MANAGEMENT
Permissions:
  - PROJECT_MANAGEMENT:ACCESS
  - PROJECT_MANAGEMENT:VIEW
  - PROJECT_MANAGEMENT:CREATE
  - PROJECT_MANAGEMENT:EDIT
  - PROJECT_MANAGEMENT:DELETE
  - PROJECT_MANAGEMENT:CREDENTIAL_VIEW
  - PROJECT_MANAGEMENT:CREDENTIAL_REVEAL
```

#### Default Renewal Types (System)

| Name | Description |
|------|-------------|
| Trademark Renewal | Annual trademark renewal and maintenance |
| Patent Renewal | Patent maintenance and renewal fees |
| License Renewal | Business license and permit renewals |
| Annual Compliance | Annual statutory compliance filings |
| Tax Filing | Tax return and filing deadlines |
| Secretarial Compliance | Company secretarial compliance filings |
| Other | General renewal or deadline |

---

## 12. Testing

### Unit Tests

```bash
# Run all tests
./mvnw test

# Run only project management tests
./mvnw test -Dtest='ProjectServiceTest,RenewalServiceTest'
```

### Test Coverage

| Class | Tests | Coverage |
|-------|-------|----------|
| `ProjectServiceTest` | 5 | Create, validation, members, owner protection |
| `RenewalServiceTest` | 4 | One-time, yearly, quarterly recurrence, not-found |
| **Total** | **9** | |

### Pre-existing Tests

All 132 original tests continue to pass (138 total with new module tests).

---

**Document generated:** 2026-08-21
**Module version:** 1.0.0
**API version:** v1
