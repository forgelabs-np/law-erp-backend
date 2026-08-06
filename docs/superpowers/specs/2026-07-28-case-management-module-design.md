# Case Management Module — Design Specification

**Version:** 1.0
**Date:** 2026-07-28
**Status:** Draft

---

## 1. Overview

The Case Management module tracks the full lifecycle of civil and criminal cases handled by the law firm. It replaces the physical diary/tarikh register with a digital system that combines complete case lifecycle tracking with daily hearing (tarikh) management.

### 1.1 Core Capabilities

- Create and manage civil and criminal cases through their full lifecycle stages
- Track parties (plaintiff, defendant, accused, appellant) with automatic duplicate matching against existing client records
- Record and manage court hearing dates (tarikh) with calendar integration
- Role-based visibility: advocates see their cases, firm admin sees all, partners see firm-wide reports, paralegals support filings
- Timeline view showing every event in a case's history
- Auto-suggested linking of parties to existing client profiles

### 1.2 Scope — Phase 1

- Case types: Civil + Criminal only (extensible later)
- Hearing/calendar management
- Party matching/deduplication
- Case timeline
- Full CRUD with RBAC

### 1.3 Out of Scope (Phase 1)

- Document generation (templates, auto-fill)
- Billing integration with cases
- Court integration (e-filing APIs)
- Advanced analytics/dashboards

---

## 2. Entity Model

### 2.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         cases                                        │
├─────────────────────────────────────────────────────────────────────┤
│ id (UUID PK)                                                        │
│ firm_id (UUID FK → firms.id)                                        │
│ case_number (VARCHAR 30) — auto-generated, e.g. CIV-2026-0001      │
│ case_type (VARCHAR 10) — 'CIVIL' | 'CRIMINAL'                      │
│ title (VARCHAR 200) — case description/title                        │
│ case_stage (VARCHAR 30) — lifecycle stage per type                  │
│ status (VARCHAR 15) — ACTIVE | CLOSED | ARCHIVED                    │
│ court_name (VARCHAR 100) — e.g. "Supreme Court, Kathmandu"          │
│ court_case_number (VARCHAR 50) — the court's own case number        │
│ judge_name (VARCHAR 100)                                            │
│ filing_date (DATE)                                                  │
│ filing_number (VARCHAR 50) — court filing reference                 │
│ assigned_to (UUID FK → employee_profiles.id) — primary advocate    │
│ description (TEXT) — case notes/description                         │
│ ─── Civil-specific ───                                              │
│ mediation_date (DATE) — nullable                                   │
│ mediation_outcome (TEXT) — nullable                                 │
│ written_statement_deadline (DATE) — nullable                        │
│ ─── Criminal-specific ───                                           │
│ fir_number (VARCHAR 50) — nullable                                  │
│ fir_date (DATE) — nullable                                          │
│ police_station (VARCHAR 100) — nullable                             │
│ investigation_authority (VARCHAR 100) — nullable                    │
│ arrest_date (DATE) — nullable                                       │
│ charge_sheet_date (DATE) — nullable                                 │
│ bail_status (VARCHAR 20) — GRANTED | DENIED | PENDING | null       │
│ ─── Common metadata ───                                             │
│ created_by (UUID FK)                                                │
│ created_at (TIMESTAMP)                                              │
│ updated_by (UUID FK)                                                │
│ updated_at (TIMESTAMP)                                              │
│ active (BOOLEAN DEFAULT TRUE)                                       │
└──────────────────────┬──────────────────────────────────────────────┘
                       │ 1
                       │
              ┌────────┴────────┐
              │                 │
     ┌────────┴────────┐  ┌────┴────────────┐
     │ case_parties    │  │ hearings         │
     ├─────────────────┤  ├──────────────────┤
     │ id (UUID PK)    │  │ id (UUID PK)     │
     │ case_id (FK)    │  │ case_id (FK)     │
     │ party_type      │  │ title (VARCHAR)  │
     │ (PLAINTIFF,     │  │ date (DATE)      │
     │  DEFENDANT,     │  │ time (TIME)      │
     │  ACCUSED,       │  │ end_time (TIME)  │
     │  APPELLANT,     │  │ court_room (VARCHAR)│
     │  RESPONDENT,    │  │ judge (VARCHAR)  │
     │  APPLICANT)     │  │ hearing_type     │
     │ full_name       │  │ (FIRST_HEARING,  │
     │ client_id (FK)  │  │  PLEA, EVIDENCE, │
     │  → client_profiles│  │  ARGUMENT,      │
     │ contact_info    │  │  JUDGMENT,       │
     │ representation  │  │  STATUS_CONF,    │
     │  (REPRESENTED,  │  │  OTHER)          │
     │  OPPOSING,      │  │ notes (TEXT)     │
     │  SELF)          │  │ outcome (TEXT)   │
     │ is_our_client   │  │ (judge's order)  │
     │ (BOOLEAN)       │  │ status (SCHEDULED│
     │ notes (TEXT)    │  │  HELD, CANCELED, │
     └─────────────────┘  │  ADJOURNED)      │
                          │ created_by (FK)  │
                          │ attendees (TEXT) │
                          └────────┬─────────┘
                                   │
                          ┌────────┴────────┐
                          │ case_timeline    │
                          ├──────────────────┤
                          │ id (UUID PK)     │
                          │ case_id (FK)     │
                          │ event_type       │
                          │ (STAGE_CHANGE,   │
                          │  HEARING,        │
                          │  DOCUMENT,       │
                          │  NOTE,           │
                          │  PARTY_ADDED,    │
                          │  JUDGMENT)       │
                          │ title (VARCHAR)  │
                          │ description (TEXT)│
                          │ created_by (FK)  │
                          │ created_at (TIME)│
                          └──────────────────┘
```

### 2.2 Case Entity

Single table with `case_type` discriminator. Common fields shared by both types. Type-specific fields are nullable and validated in the service layer.

```java
@Entity
@Table(name = "cases", indexes = {
    @Index(name = "idx_cases_firm_type", columnList = "firm_id, case_type"),
    @Index(name = "idx_cases_firm_stage", columnList = "firm_id, case_stage"),
    @Index(name = "idx_cases_assigned_to", columnList = "assigned_to"),
    @Index(name = "idx_cases_filing_date", columnList = "firm_id, filing_date DESC"),
    @Index(name = "idx_cases_case_number", columnList = "firm_id, case_number", unique = true)
})
public class Case extends ActiveAuditableEntity {
    // Common fields
    private UUID firmId;
    private String caseNumber;       // CIV-2026-00001
    private CaseType caseType;       // CIVIL | CRIMINAL
    private String title;
    private CaseStage caseStage;     // validated per type
    private CaseStatus status;       // ACTIVE | CLOSED | ARCHIVED
    private String courtName;
    private String courtCaseNumber;
    private String judgeName;
    private LocalDate filingDate;
    private String filingNumber;
    private UUID assignedTo;         // FK → employee_profiles — primary advocate handling the case
    private String description;

    // Civil-specific
    private LocalDate mediationDate;
    private String mediationOutcome;
    private LocalDate writtenStatementDeadline;

    // Criminal-specific
    private String firNumber;
    private LocalDate firDate;
    private String policeStation;
    private String investigationAuthority;
    private LocalDate arrestDate;
    private LocalDate chargeSheetDate;
    private BailStatus bailStatus;
}
```

### 2.3 Case Stages

**Civil Stages (validated lifecycle):**

```
FILED → UNDER_SUMMONS → RESPONSE_PENDING → MEDIATION → EVIDENCE →
ARGUMENT → JUDGMENT_AWAITED → JUDGMENT_DELIVERED → (APPEAL → CLOSED | EXECUTION → CLOSED)
```

| Stage | Description |
|-------|-------------|
| FILED | Plaint/motion filed at court |
| UNDER_SUMMONS | Summons issued to defendant |
| RESPONSE_PENDING | Awaiting defendant's written statement |
| MEDIATION | Court-ordered or voluntary mediation |
| EVIDENCE | Evidence submission and examination |
| ARGUMENT | Final arguments / argument memo |
| JUDGMENT_AWAITED | Awaiting court's final judgment |
| JUDGMENT_DELIVERED | Judgment received |
| APPEAL | Case under appeal at higher court |
| EXECUTION | Execution proceedings |
| CLOSED | Case concluded |

**Criminal Stages (validated lifecycle):**

```
FIR_REGISTERED → UNDER_INVESTIGATION → CHARGE_SHEET_FILED →
TRIAL → JUDGMENT_AWAITED → JUDGMENT_DELIVERED → SENTENCING →
(APPEAL → CLOSED | CLOSED)
```

| Stage | Description |
|-------|-------------|
| FIR_REGISTERED | FIR filed at police station |
| UNDER_INVESTIGATION | Police investigation ongoing |
| CHARGE_SHEET_FILED | Charge sheet submitted to court |
| PLEA | Plea and bail hearing |
| TRIAL | Trial (witness, evidence, cross-examination) |
| JUDGMENT_AWAITED | Awaiting final judgment |
| JUDGMENT_DELIVERED | Judgment received |
| SENTENCING | Sentence hearing (within 30 days) |
| APPEAL | Under appeal at higher court |
| CLOSED | Case concluded |

**Stage transitions are validated.** Only allowed transitions are permitted. Attempting an invalid stage change returns a 400 BusinessRuleException.

### 2.4 CaseParty Entity

Tracks all parties involved in a case, with a link to existing client records.

```java
@Entity
@Table(name = "case_parties", indexes = {
    @Index(name = "idx_cp_case", columnList = "case_id"),
    @Index(name = "idx_cp_client", columnList = "client_id"),
    @Index(name = "idx_cp_name", columnList = "firm_id, full_name")
})
public class CaseParty extends ActiveAuditableEntity {
    private UUID firmId;
    private UUID caseId;
    private PartyType partyType;     // PLAINTIFF | DEFENDANT | ACCUSED | APPELLANT | RESPONDENT | APPLICANT
    private PartyRepresentation representation;  // REPRESENTED | OPPOSING | SELF

    // Person details
    private String fullName;
    private String mobileNo;
    private String email;
    private String address;

    // Link to existing client (if they are or were a client)
    private UUID clientId;           // FK → client_profiles.id (nullable)
    private boolean isOurClient;     // convenience flag

    // Advocate representing this party (if REPRESENTED)
    private UUID advocateId;         // FK → employee_profiles.id (nullable)

    private String notes;
}
```

### 2.5 Hearing Entity

The core calendar/tarikh record.

```java
@Entity
@Table(name = "hearings", indexes = {
    @Index(name = "idx_hearings_case", columnList = "case_id"),
    @Index(name = "idx_hearings_date", columnList = "firm_id, date DESC"),
    @Index(name = "idx_hearings_advocate_date", columnList = "advocate_id, date DESC"),
    @Index(name = "idx_hearings_status", columnList = "firm_id, status, date DESC")
})
public class Hearing extends ActiveAuditableEntity {
    private UUID firmId;
    private UUID caseId;
    private String title;
    private LocalDate date;
    private LocalTime time;
    private LocalTime endTime;
    private String courtRoom;
    private String judgeName;
    private HearingType hearingType;  // FIRST_HEARING | PLEA | EVIDENCE | ARGUMENT | JUDGMENT | STATUS_CONF | OTHER
    private HearingStatus status;     // SCHEDULED | HELD | CANCELED | ADJOURNED
    private String outcome;           // judge's order / result of hearing
    private String notes;
    private String attendees;         // comma-separated names or IDs
    private UUID createdBy;
    private UUID advocateId;          // assigned advocate for this hearing
}
```

### 2.6 CaseTimelineEvent Entity

Immutable log of significant events in a case. Used for the timeline view and audit trail.

```java
@Entity
@Table(name = "case_timeline", indexes = {
    @Index(name = "idx_timeline_case", columnList = "case_id, created_at DESC"),
    @Index(name = "idx_timeline_type", columnList = "case_id, event_type")
})
public class CaseTimelineEvent extends AuditableEntity {
    private UUID firmId;
    private UUID caseId;
    private TimelineEventType eventType;
    // STAGE_CHANGE | HEARING_SCHEDULED | HEARING_HELD | HEARING_ADJOURNED
    // DOCUMENT_UPLOADED | PARTY_ADDED | JUDGMENT_RECORDED | NOTE_ADDED | CASE_CREATED

    private String title;
    @Column(length = 1000)
    private String description;
    private UUID relatedEntityId;    // FK to the entity that triggered this (hearing_id, etc.)
}
```

---

## 3. Party Matching System

When a user creates a case and enters party details, the system automatically checks for potential duplicates against existing client records and case parties.

### 3.1 Matching Logic

```
User enters: name, phone, email
        │
        ▼
  Search existing client_profiles AND case_parties
  within the same firm where:
    - full_name fuzzy matches (case-insensitive contains)
    - OR mobile_no exact match
    - OR email exact match
        │
        ▼
  Return ranked matches with confidence level:
    - HIGH:   name + phone match, or name + email match
    - MEDIUM: name match only (exact name)
    - LOW:    partial name match only
        │
        ▼
  User can:
    - Link to an existing record (client_id populated)
    - Create as new party record
    - Skip matching entirely
```

### 3.2 API

```
POST /api/v1/firm/parties/match
Request: { firmId, fullName?, mobileNo?, email? }
Response: {
    matches: [
        {
            sourceType: "CLIENT" | "CASE_PARTY",
            sourceId: UUID,
            fullName: String,
            mobileNo: String?,
            email: String?,
            confidence: "HIGH" | "MEDIUM" | "LOW"
        }
    ]
}
```

### 3.3 Auto-suggestion on Case Create

When creating a case, the `POST /api/v1/firm/cases` request accepts party details inline. The service performs matching server-side and returns any potential matches in the response. The frontend can then prompt the user to link or proceed.

```
POST /api/v1/firm/cases
{
    "caseType": "CIVIL",
    "title": "...",
    ...
    "parties": [
        {
            "partyType": "PLAINTIFF",
            "fullName": "Ram Sharma",
            "mobileNo": "9800000001",
            "email": "ram@email.com"
        }
    ]
}

Response:
{
    "case": { ... },
    "partyMatches": [
        {
            "partyIndex": 0,
            "matches": [ ... ]  // potential duplicates found
        }
    ]
}
```

To link explicitly, the frontend calls:

```
PUT /api/v1/firm/cases/{id}/parties/{partyId}/link
{ "clientId": "existing-client-uuid", "isOurClient": true }
```

---

## 4. Calendar Integration

### 4.1 Architecture

The Calendar is not a separate module — it's a **view** over Hearing records. Hearings are the source of truth. The calendar provides:

- Daily/weekly/monthly views filtered by advocate, date range, hearing type
- Today's hearings summary (dashboard widget)
- Conflict detection (same advocate, overlapping time)

### 4.2 Calendar API Endpoints

```
GET /api/v1/firm/calendar?from=2026-08-01&to=2026-08-07&advocateId=uuid
  → List of hearings in date range, sorted by date/time

GET /api/v1/firm/calendar/today
  → All hearings for today (firm-wide)

GET /api/v1/firm/calendar/today?advocateId=uuid
  → Today's hearings for a specific advocate

GET /api/v1/firm/calendar/upcoming?days=7&advocateId=uuid
  → Upcoming hearings in the next N days
```

### 4.3 Conflict Detection

When creating/updating a hearing, the system checks for time conflicts:

```
Same advocate + Same date + Overlapping time range
  → Return 409 Conflict with details of conflicting hearing
```

The check is performed in `HearingService` before save:

```
hearingRepository.findConflicts(advocateId, date, time, endTime, excludeHearingId)
```

### 4.4 Hearing Reminders

Reminders are a separate concept (future phase). For Phase 1, the frontend can poll `GET /api/v1/firm/calendar/today` to show upcoming hearings. Push notifications or email reminders can be added later.

---

## 5. API Endpoints — Complete Reference

### 5.1 Case CRUD

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| POST | /api/v1/firm/cases | Create new case | CASE_MANAGEMENT:CREATE |
| GET | /api/v1/firm/cases | List cases (paginated) | CASE_MANAGEMENT:VIEW |
| GET | /api/v1/firm/cases/{id} | Get case details + timeline | CASE_MANAGEMENT:VIEW |
| PUT | /api/v1/firm/cases/{id} | Update case | CASE_MANAGEMENT:EDIT |
| DELETE | /api/v1/firm/cases/{id} | Delete case | CASE_MANAGEMENT:DELETE |
| PUT | /api/v1/firm/cases/{id}/stage | Update case stage | CASE_MANAGEMENT:UPDATE_STATUS |

### 5.2 Case Listing Filters

`GET /api/v1/firm/cases` supports:

| Parameter | Type | Description |
|-----------|------|-------------|
| caseType | CIVIL/CRIMINAL | Filter by type |
| caseStage | CaseStage | Filter by current stage |
| status | ACTIVE/CLOSED/ARCHIVED | Filter by status |
| assignedTo | UUID | Filter by assigned advocate |
| courtName | String | Filter by court |
| dateFrom | Date | Filing date range start |
| dateTo | Date | Filing date range end |
| search | String | Free text search (case number, title, party name) |
| page | int | Page number (default 0) |
| size | int | Page size (default 20) |

### 5.3 Case Parties

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET | /api/v1/firm/cases/{id}/parties | List parties for a case | CASE_MANAGEMENT:VIEW |
| POST | /api/v1/firm/cases/{id}/parties | Add party to case | CASE_MANAGEMENT:EDIT |
| PUT | /api/v1/firm/cases/{id}/parties/{partyId} | Update party | CASE_MANAGEMENT:EDIT |
| DELETE | /api/v1/firm/cases/{id}/parties/{partyId} | Remove party | CASE_MANAGEMENT:EDIT |
| PUT | /api/v1/firm/cases/{id}/parties/{partyId}/link | Link party to client | CASE_MANAGEMENT:EDIT |

### 5.4 Hearings

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| POST | /api/v1/firm/cases/{id}/hearings | Schedule hearing | CASE_MANAGEMENT:EDIT |
| GET | /api/v1/firm/cases/{id}/hearings | List hearings for a case | CASE_MANAGEMENT:VIEW |
| GET | /api/v1/firm/hearings/{id} | Get hearing details | CASE_MANAGEMENT:VIEW |
| PUT | /api/v1/firm/hearings/{id} | Update hearing | CASE_MANAGEMENT:EDIT |
| DELETE | /api/v1/firm/hearings/{id} | Cancel/delete hearing | CASE_MANAGEMENT:EDIT |

### 5.5 Calendar

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET | /api/v1/firm/calendar | Calendar view in date range | CASE_MANAGEMENT:VIEW |
| GET | /api/v1/firm/calendar/today | Today's hearings | CASE_MANAGEMENT:VIEW |
| GET | /api/v1/firm/calendar/upcoming | Upcoming hearings | CASE_MANAGEMENT:VIEW |

### 5.6 Timeline

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET | /api/v1/firm/cases/{id}/timeline | Full case timeline | CASE_MANAGEMENT:VIEW |

### 5.7 Party Matching

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| POST | /api/v1/firm/parties/match | Match party against DB | CASE_MANAGEMENT:CREATE |

---

## 6. Permissions & RBAC

### 6.1 Permission Definitions

New permissions to be created in the system:

| Code | Action | Scope | Description |
|------|--------|-------|-------------|
| CASE_MANAGEMENT:ACCESS | ACCESS | TENANT | See the Case Management module in sidebar |
| CASE_MANAGEMENT:VIEW | VIEW | TENANT | View case details and listings |
| CASE_MANAGEMENT:CREATE | CREATE | TENANT | Create new cases |
| CASE_MANAGEMENT:EDIT | EDIT | TENANT | Edit case details, parties, hearings |
| CASE_MANAGEMENT:DELETE | DELETE | TENANT | Delete cases |
| CASE_MANAGEMENT:ASSIGN | ASSIGN | TENANT | Assign advocates to cases |
| CASE_MANAGEMENT:UPDATE_STATUS | UPDATE_STATUS | TENANT | Change case stage/status |
| CASE_MANAGEMENT:ARCHIVE | ARCHIVE | TENANT | Archive closed cases |

### 6.2 Role-based Defaults

| Role | Permissions |
|------|-------------|
| FIRM_ADMIN | ACCESS, VIEW, CREATE, EDIT, DELETE, ASSIGN, UPDATE_STATUS, ARCHIVE |
| ADVOCATE | ACCESS, VIEW, CREATE, EDIT, UPDATE_STATUS |
| PARALEGAL | ACCESS, VIEW, CREATE, EDIT |
| CLIENT | VIEW (own cases only — future phase) |

### 6.3 Data Visibility

- **Advocate (primary):** Sees cases where `cases.assigned_to` matches their employee profile
- **Advocate (party rep):** Sees cases where `case_parties.advocateId` matches their profile (they represent a party)
- **Firm Admin:** Sees all cases in the firm
- **Paralegal:** Sees all cases they are assigned to support via `case_assignments` (future phase)
- **Partners:** See all cases for reporting

Data filtering is implemented at the repository level using the firm context (`firm_id`) plus role-based filters.

---

## 7. Integration with Existing Modules

### 7.1 Document Management

When a document is uploaded to a case, the system:
1. Uses the existing Document Management module to store the file
2. Creates a link record in `case_documents` (junction table)
3. Adds a `DOCUMENT_UPLOADED` timeline event

```java
// Future: Junction table (Phase 1: manual linking via existing document tags)
@Entity
@Table(name = "case_documents")
public class CaseDocument {
    private UUID caseId;
    private UUID documentId;  // FK → document_management docs
}
```

### 7.2 Client Management

- `CaseParty.clientId` links to `ClientProfile.id`
- Party matching searches `client_profiles` for potential duplicates
- When viewing a client's profile, the system shows their linked cases

### 7.3 Audit

- All case mutations go through the existing `@Audit` annotation / `AuditService`
- `AuditEntity.CASE` enum value will be added
- `AuditEntity.HEARING` enum value will be added

### 7.4 Employee / Advocate Assignment

- `CaseParty.advocateId` links to `EmployeeProfile.id` where `userType = FIRM_USER`
- This allows tracking which advocate is handling which party's representation

---

## 8. Database Schema

### 8.1 Index Strategy

| Table | Index | Why |
|-------|-------|-----|
| cases | (firm_id, case_type) | Filter cases by type within firm |
| cases | (firm_id, case_stage) | Filter by stage (pipeline view) |
| cases | (firm_id, filing_date DESC) | "Recent cases" listing |
| cases | (firm_id, case_number) UNIQUE | Case number lookup |
| cases | (firm_id, assigned_to) | Primary advocate's caseload |
| case_parties | (case_id) | Load all parties for a case |
| case_parties | (firm_id, full_name) | Party matching search |
| case_parties | (client_id) | Find cases linked to a client |
| hearings | (case_id) | Load hearings for a case |
| hearings | (firm_id, date DESC) | Calendar view |
| hearings | (advocate_id, date DESC) | Advocate's calendar |
| hearings | (firm_id, status, date DESC) | Upcoming hearings filter |
| case_timeline | (case_id, created_at DESC) | Timeline display |

### 8.2 Case Number Generation

Format: `{TYPE}-{YEAR}-{SEQUENCE:05d}`

```
CIV-2026-00001
CRM-2026-00001
```

Sequence is generated per firm, per year, per type. Uses database sequence or a counter table.

```sql
CREATE TABLE case_number_sequences (
    firm_id UUID NOT NULL,
    year INT NOT NULL,
    case_type VARCHAR(10) NOT NULL,
    last_sequence INT NOT NULL DEFAULT 0,
    PRIMARY KEY (firm_id, year, case_type)
);
```

---

## 9. Error Handling

| Scenario | HTTP Status | Error Code |
|----------|-------------|------------|
| Invalid stage transition | 400 | INVALID_STAGE_TRANSITION |
| Party match not found | 404 | PARTY_NOT_FOUND |
| Hearing time conflict | 409 | HEARING_TIME_CONFLICT |
| Case not found | 404 | CASE_NOT_FOUND |
| Party not in firm | 403 | PARTY_NOT_IN_FIRM |
| Cannot delete case with hearings | 400 | CASE_HAS_HEARINGS |
| Invalid case type for stage | 400 | INVALID_STAGE_FOR_TYPE |

---

## 10. Package Structure

```
com.lawfirm.erp.modules.casemanagement
├── CaseManagementApplication.java    (module marker, if needed)
├── entity/
│   ├── Case.java
│   ├── CaseParty.java
│   ├── Hearing.java
│   ├── CaseTimelineEvent.java
│   └── enums/
│       ├── CaseType.java
│       ├── CaseStage.java
│       ├── CaseStatus.java
│       ├── PartyType.java
│       ├── PartyRepresentation.java
│       ├── HearingType.java
│       ├── HearingStatus.java
│       ├── BailStatus.java
│       └── TimelineEventType.java
├── repository/
│   ├── CaseRepository.java
│   ├── CasePartyRepository.java
│   ├── HearingRepository.java
│   └── CaseTimelineRepository.java
├── service/
│   ├── CaseService.java
│   ├── CasePartyService.java
│   ├── HearingService.java
│   ├── CalendarService.java
│   ├── PartyMatchService.java
│   └── CaseNumberGenerator.java
├── controller/
│   ├── CaseController.java
│   ├── CasePartyController.java
│   ├── HearingController.java
│   └── CalendarController.java
└── dto/
    ├── request/
    │   ├── CreateCaseRequest.java
    │   ├── UpdateCaseRequest.java
    │   ├── UpdateCaseStageRequest.java
    │   ├── AddPartyRequest.java
    │   ├── UpdatePartyRequest.java
    │   ├── LinkPartyRequest.java
    │   ├── CreateHearingRequest.java
    │   ├── UpdateHearingRequest.java
    │   └── PartyMatchRequest.java
    └── response/
        ├── CaseResponse.java
        ├── CaseListResponse.java
        ├── PartyResponse.java
        ├── PartyMatchResponse.java
        ├── HearingResponse.java
        ├── CalendarResponse.java
        └── TimelineEventResponse.java
```

---

## 11. Case Creation Flow (Detailed)

```
Frontend                                Backend
   │                                       │
   │  POST /api/v1/firm/cases              │
   │  {                                    │
   │    caseType: "CIVIL",                 │
   │    title: "...",                      │
   │    courtName: "...",                  │
   │    filingDate: "...",                 │
   │    parties: [                         │
   │      { fullName, mobileNo, ... }     │
   │    ]                                  │
   │  }                                    │
   │──────────────────────►                │
   │                                       │
   │                         1. Validate request
   │                         2. Generate case number
   │                         3. Create case record
   │                         4. Create party records
   │                         5. For each party → match
   │                            against existing clients
   │                         6. Create "CASE_CREATED"
   │                            timeline event
   │                         7. @Audit log
   │                                       │
   │  ◄──────────────────────              │
   │  {                                    │
   │    case: { ... },                     │
   │    partyMatches: [                    │
   │      {                                │
   │        partyIndex: 0,                 │
   │        matches: [                     │
   │          { fullName, clientId,       │
   │            confidence: "HIGH" }       │
   │        ]                              │
   │      }                                │
   │    ]                                  │
   │  }                                    │
   │                                       │
   │  [Optional]                           │
   │  PUT /firm/cases/{id}/parties/{id}/link │
   │  { clientId: "..." }                 │
   │──────────────────────►                │
```

---

## 12. Implementation Order

| Step | What | Dependencies |
|------|------|-------------|
| 1 | Enums (CaseType, CaseStage, CaseStatus, PartyType, etc.) | None |
| 2 | Entities (Case, CaseParty, Hearing, CaseTimelineEvent) | Enums, ActiveAuditableEntity |
| 3 | Repositories (with custom queries for matching, calendar, conflicts) | Entities |
| 4 | DTOs (request/response classes) | Entities, Enums |
| 5 | CaseNumberGenerator service | CaseRepository |
| 6 | PartyMatchService | ClientProfileRepository, CasePartyRepository |
| 7 | CaseService (CRUD + stage transitions + timeline) | Repositories, PartyMatchService |
| 8 | HearingService (CRUD + conflict detection) | HearingRepository |
| 9 | CalendarService (queries for calendar views) | HearingRepository |
| 10 | CasePartyService | Repositories |
| 11 | Controllers + Security config | Services |
| 12 | DataInitializer update (permissions for CASE_MANAGEMENT) | PermissionService |
| 13 | Tests | Everything |

---

## 13. Testing Strategy

### Unit Tests (Service Layer)
- CaseService: create, update, delete, stage transitions (valid + invalid), duplicate party detection
- HearingService: schedule, conflict detection, status update
- PartyMatchService: match by name, phone, email; confidence levels; no-match case
- CalendarService: date range queries, advocate filtering
- CaseNumberGenerator: sequence generation, year rollover

### Integration Tests
- Full case creation flow with party matching
- Calendar query with hearings across multiple cases
- Stage transition validation across the full lifecycle
- Permission enforcement (FIRM_ADMIN vs ADVOCATE vs PARALEGAL)

### DataInitializer Tests
- Verify CASE_MANAGEMENT permissions are created
- Verify default roles get correct permissions
