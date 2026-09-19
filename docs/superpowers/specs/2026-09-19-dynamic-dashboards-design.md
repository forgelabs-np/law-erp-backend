# Dashboard Redesign — 4 Clean Endpoints

> Date: 2026-09-19 · Status: revised · Scope: 4 typed dashboard endpoints under usermanagement module

## 1. Summary

Replace the four hand-written dashboards (`/modules/dashboard`, `/firm/dashboard`, `/firm/lawyer/dashboard`, `/firm/projects/dashboard`) with four clean, typed endpoints — one per user type. Each endpoint returns a purpose-built response with the exact data that user type needs.

**No dynamic engine, no metric registry, no layout tables.** Just 4 services with typed responses, shared scoping logic, and purpose-built frontend components.

## 2. Why Not the Dynamic Engine

The original spec proposed a metric registry + layout CRUD + generic renderer. Rejected because:

- The 4 dashboards show **fundamentally different data** — almost zero overlap between SUPER_ADMIN (platform aggregates) and CLIENT (personal matters).
- Layout configurability is a feature nobody asked for — the developer sets the layout once.
- 26 metric classes + generic renderer + layout tables = complexity that doesn't serve the use case.
- Purpose-built frontend components look better than a generic widget grid.

## 3. Architecture

```
DashboardController (one controller, 4 endpoints)
    │
    ├─→ GET /api/v1/dashboard/super-admin  → SuperAdminDashboardResponse
    │     @PreAuthorize("hasRole('SUPER_ADMIN')")
    │
    ├─→ GET /api/v1/dashboard/firm         → FirmDashboardResponse
    │     @PreAuthorize("hasRole('FIRM_ADMIN')")
    │
    ├─→ GET /api/v1/dashboard/employee     → EmployeeDashboardResponse
    │     @PreAuthorize("hasAnyRole('ADVOCATE','PARALEGAL')")
    │
    └─→ GET /api/v1/dashboard/client       → ClientDashboardResponse
          @PreAuthorize("hasRole('CLIENT')")
```

Each endpoint builds a `DashboardScope` from the JWT, then calls a service that queries repositories directly.

### DashboardScope (shared)

```java
DashboardScope {
    UUID userId
    UUID firmId          // null for SUPER_ADMIN
    UserType userType    // SUPER_ADMIN, FIRM_ADMIN, FIRM_USER, CLIENT
    String roleCode      // ADVOCATE, PARALEGAL, etc.
    LocalDateTime now    // clock injection for testing
}
```

Built once per request by `DashboardScopeFactory`. Every service receives it — no service can accidentally get an unscoped firmId.

## 4. Per-Endpoint Data

### SUPER_ADMIN Dashboard

**Guard:** `hasRole('SUPER_ADMIN')`

```jsonc
{
  "firmStats": {
    "totalFirms": 45,
    "activeFirms": 38,
    "suspendedFirms": 5,
    "trialFirms": 2
  },
  "userStats": {
    "totalUsers": 312,
    "byRole": { "FIRM_ADMIN": 45, "ADVOCATE": 180, "PARALEGAL": 60, "CLIENT": 27 },
    "activeUsers": 290
  },
  "caseStats": {
    "totalMatters": 1250,
    "activeMatters": 890,
    "closedMatters": 360
  },
  "scraperStats": {
    "courtsTracked": 77,
    "totalHearings": 15000,
    "totalMatches": 8500,
    "lastScrapeTime": "2026-09-19T06:00:00"
  },
  "trialAlerts": {
    "expiringThisWeek": 3,
    "expired": 1
  },
  "recentActivity": [
    { "summary": "Firm ABC created", "action": "CREATE", "userName": "admin", "createdAt": "..." }
  ],
  "matterTrends": [
    { "date": "2026-09-01", "total": 1200, "active": 870, "closed": 330 }
  ]
}
```

**Data sources:** `FirmRepository`, `UserRepository`, `MatterRepository`, `CourtRepository`, `DailyHearingRepository`, `HearingMatchRepository`, `AuditLogRepository`

### FIRM_ADMIN Dashboard

**Guard:** `hasRole('FIRM_ADMIN')`

```jsonc
{
  "caseStats": {
    "totalMatters": 85,
    "activeMatters": 62,
    "dormantMatters": 8,
    "closedMatters": 15,
    "staleMatters": 5
  },
  "todayEvents": [
    {
      "eventId": "...",
      "matterTitle": "Smith vs Jones",
      "courtRoom": "Room 3",
      "scheduledTime": "10:00",
      "judgeName": "Hon. Justice Ram",
      "attendingAdvocateName": "Adv. Sharma"
    }
  ],
  "upcomingHearings": [
    {
      "matterTitle": "Smith vs Jones",
      "hearingDate": "2026-09-22",
      "courtName": "Supreme Court",
      "daysUntil": 3
    }
  ],
  "invoiceStats": {
    "totalInvoices": 120,
    "outstanding": 35,
    "overdue": 8,
    "totalOutstandingAmount": 2500000,
    "overdueAmount": 450000
  },
  "overdueInvoices": [
    {
      "invoiceNumber": "INV-2026-045",
      "clientName": "ABC Corp",
      "amount": 150000,
      "daysOverdue": 12
    }
  ],
  "renewalStats": {
    "dueThisMonth": 5,
    "overdue": 2
  },
  "upcomingRenewals": [
    {
      "projectName": "XYZ Trademark",
      "renewalTitle": "Annual Renewal",
      "dueDate": "2026-09-30",
      "daysUntil": 11
    }
  ],
  "teamCaseload": [
    { "advocateName": "Adv. Sharma", "openMatters": 15 },
    { "advocateName": "Adv. Patel", "openMatters": 12 }
  ],
  "recentActivity": [
    { "summary": "Matter created", "userName": "Adv. Sharma", "createdAt": "..." }
  ]
}
```

**Data sources:** `MatterRepository`, `CourtEventRepository`, `CourtCaseRepository`, `InvoiceRepository`, `RenewalRepository`, `RenewalInstanceRepository`, `AuditLogRepository`, `CaseAssignmentService`

### EMPLOYEE Dashboard (ADVOCATE / PARALEGAL)

**Guard:** `hasAnyRole('ADVOCATE','PARALEGAL')`

```jsonc
{
  "myCaseStats": {
    "openMatters": 15,
    "upcomingHearings": 4,
    "staleMatters": 2
  },
  "myTodayEvents": [
    {
      "eventId": "...",
      "matterTitle": "Smith vs Jones",
      "ourtCaseRef": "SC-2026-001",
      "courtRoom": "Room 3",
      "scheduledTime": "10:00",
      "judgeName": "Hon. Justice Ram",
      "status": "SCHEDULED"
    }
  ],
  "myUpcomingHearings": [
    {
      "matterTitle": "Smith vs Jones",
      "ourtCaseRef": "SC-2026-001",
      "hearingDate": "2026-09-22",
      "courtName": "Supreme Court",
      "daysUntil": 3
    }
  ],
  "myUpcomingDeadlines": [
    {
      "matterTitle": "Smith vs Jones",
      "ourtCaseRef": "SC-2026-001",
      "deadline": "2026-09-25",
      "deadlineType": "WRITTEN_STATEMENT",
      "daysRemaining": 6,
      "isUrgent": false
    }
  ],
  "myStaleMatters": [
    {
      "matterTitle": "Old Case",
      "ourtCaseRef": "HC-2025-100",
      "lastHearingDate": "2026-06-15",
      "daysSinceLastHearing": 96
    }
  ],
  "recentUpdates": [
    {
      "matterTitle": "Smith vs Jones",
      "ourtCaseRef": "SC-2026-001",
      "updateType": "HEARING_SCHEDULED",
      "description": "Next hearing set for Sep 22",
      "createdAt": "..."
    }
  ]
}
```

**Data sources:** `MatterRepository` (assigned only), `CourtCaseRepository`, `CourtEventRepository`, `CaseAssignmentService`, `HearingMatchRepository`

### CLIENT Dashboard

**Guard:** `hasRole('CLIENT')`

```jsonc
{
  "myMatterStats": {
    "totalMatters": 3,
    "activeMatters": 2,
    "closedMatters": 1
  },
  "myMatters": [
    {
      "matterId": "...",
      "matterNumber": "MTR-2026-010",
      "title": "Smith vs Jones",
      "status": "ACTIVE",
      "courtName": "Supreme Court",
      "ourtCaseRef": "SC-2026-001",
      "lastUpdate": "2026-09-18",
      "nextHearingDate": "2026-09-22"
    }
  ],
  "myNextHearing": {
    "matterTitle": "Smith vs Jones",
    "ourtCaseRef": "SC-2026-001",
    "hearingDate": "2026-09-22",
    "courtName": "Supreme Court",
    "daysUntil": 3
  },
  "myUpcomingEvents": [
    {
      "matterTitle": "Smith vs Jones",
      "ourtCaseRef": "SC-2026-001",
      "eventDate": "2026-09-22",
      "eventType": "HEARING",
      "courtRoom": "Room 3"
    }
  ],
  "myInvoiceStats": {
    "totalInvoices": 3,
    "outstanding": 1,
    "outstandingAmount": 50000
  },
  "myOutstandingInvoices": [
    {
      "invoiceNumber": "INV-2026-045",
      "amount": 50000,
      "dueDate": "2026-09-30",
      "daysUntil": 11
    }
  ],
  "myRecentUpdates": [
    {
      "matterTitle": "Smith vs Jones",
      "ourtCaseRef": "SC-2026-001",
      "updateType": "STATUS_CHANGE",
      "description": "Case status updated to ACTIVE",
      "createdAt": "..."
    }
  ]
}
```

**Data sources:** `MatterRepository` (filtered by client), `CourtCaseRepository`, `CourtEventRepository`, `InvoiceRepository`, `AuditLogRepository`

## 5. File Structure

```
modules/usermanagement/
├── controller/
│   └── DashboardController.java          ← 4 endpoints
├── dashboard/
│   ├── DashboardScope.java               ← shared scope object
│   ├── DashboardScopeFactory.java        ← builds scope from JWT
│   ├── SuperAdminDashboardService.java   ← interface
│   ├── SuperAdminDashboardServiceImpl.java
│   ├── FirmDashboardService.java
│   ├── FirmDashboardServiceImpl.java
│   ├── EmployeeDashboardService.java
│   ├── EmployeeDashboardServiceImpl.java
│   ├── ClientDashboardService.java
│   └── ClientDashboardServiceImpl.java
├── dto/response/
│   ├── SuperAdminDashboardResponse.java
│   ├── FirmDashboardResponse.java
│   ├── EmployeeDashboardResponse.java
│   └── ClientDashboardResponse.java
```

## 6. RBAC

| Endpoint | Permission | Scoping |
|---|---|---|
| `GET /dashboard/super-admin` | `DASHBOARD_MANAGEMENT:VIEW` | No firmId filter — cross-tenant aggregates |
| `GET /dashboard/firm` | `DASHBOARD_MANAGEMENT:VIEW` | firmId from JWT — whole firm |
| `GET /dashboard/employee` | `DASHBOARD_MANAGEMENT:VIEW` | firmId + userId — assigned matters only |
| `GET /dashboard/client` | `DASHBOARD_MANAGEMENT:VIEW` | firmId + userId — own matters only |

All endpoints reuse the same `DASHBOARD_MANAGEMENT:VIEW` permission. The data scoping happens in the service layer via `DashboardScope`.

## 7. Migration

1. New endpoints go live alongside old ones
2. Frontend moves to new endpoints
3. Old endpoints removed: `/modules/dashboard`, `/firm/dashboard`, `/firm/lawyer/dashboard`, `/firm/projects/dashboard`

## 8. Testing

- **Leak test per endpoint:** seed two firms, two advocates, two clients; assert every returned row belongs to the correct firm/user
- **Empty states:** firm with no matters, client with nothing shared — return zeros, not errors
- **Performance:** each endpoint's query count is asserted (no N+1)
