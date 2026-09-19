# Dynamic Dashboards — Design

> Date: 2026-09-19 · Status: awaiting review · Scope: backend dashboard engine + Super Admin layout CRUD

## 1. Summary

Replace the four hand-written dashboards (`/modules/dashboard`, `/firm/dashboard`,
`/firm/lawyer/dashboard`, `/firm/projects/dashboard`) with one dashboard engine, four
endpoints (one per `UserType`), and a curated **metric registry**.

The Super Admin composes dashboards per user type from a server-filtered list of metrics —
choosing chart shape, grouping, order and size. The database stores *arrangement only*;
data extraction stays in code, where it is scoped, typed, testable and greppable.

Primary driver: **no data crosses user types.** Layout configurability is layered on top.

## 2. Why This Approach

Three shapes were considered.

**(a) Four independent dashboard services** — the obvious read of "make one API per user
type". Rejected: it reproduces the current disease. Today three services each re-derive
"does this user see all matters or only assigned ones?" in their own way; four more would
make that seven answers to keep in sync, and the one that drifts is the leak we are trying
to close. Endpoints may be four; the scoping rule must exist once.

**(b) One endpoint returning everything; the frontend hides what a user shouldn't see** —
smallest backend, and the classic version of this leak: the server would be shipping other
people's data into the browser and trusting the UI to hide it. Rejected outright.

**(c) Super Admin writes the query ("query management")** — the user's original instinct.
Rejected, and this is the most important decision in the document:

- **It defeats the goal.** Safety reduces to the Super Admin remembering
  `WHERE client_id = :userId` in every query. A free-text query cannot be scoped
  automatically, and no test can prove a stored string is scoped. One omitted clause and
  `CLIENT` reads the firm's whole caseload.
- **It is an unrestricted credential.** The query runs as the application's DB user: it can
  read `users`, `audit_logs` and credential tables across every tenant.
- **Nothing validates it.** Rename a column and compiler, IDE and test suite all stay green
  while real users get a broken dashboard.
- **It costs a product to make safe** — sandbox, timeouts, result caps, approval flow,
  query audit — i.e. precisely the query-management platform we are trying to avoid.

The facts a legal-ERP dashboard can show are a finite, known list (cases by status,
hearings today, invoice aging, staff activity…). So the configurable part is the **layout**;
the data comes from declared metrics. Adding a metric later is one small class plus tests.

## 3. Goals / Non-Goals

**Goals**

- One scoping rule, enforced in one place, used by every dashboard.
- Four endpoints, each guarded by the user type it serves.
- Super Admin can compose dashboards per user type (which widgets, chart shape, grouping,
  order, size) without a deploy.
- Existing dashboard content survives the migration; old URLs either redirect or are retired.
- A leak regression test per endpoint.

**Non-Goals**

- Free-text or admin-authored SQL.
- Per-firm custom layouts and per-user tailoring (phase 3, same tables).
- Real-time/streaming widgets.
- Replacing the `/me` sidebar or `MeResponse.modules`.

## 4. The Scoping Rule

A `DashboardScope` is built once per request from the authenticated user:

```
userId, firmId, userType, roleCode, permissionCodes, moduleAccess, now
```

Every metric receives only this scope. It never receives a raw firm id or a free-form
filter, so scoping cannot be forgotten at a call site.

Each metric declares a `scopeLevel`:

| Level | Meaning | Legal for |
|---|---|---|
| `USER` | rows owned by / assigned to `scope.userId` | all types |
| `FIRM` | rows where `firmId = scope.firmId` | FIRM, FIRM_USER |
| `PLATFORM` | cross-tenant aggregates, counts only — never tenant-identifying detail | SUPER_ADMIN only |

Rules:

1. `SUPER_ADMIN` gets `PLATFORM` metrics and never tenant detail. A `FIRM` metric is not
   registered for it.
2. `FIRM_ADMIN` sees whole-firm data; `FIRM_USER` sees only rows assigned to them
   (`USER` metrics) — matching today's advocate/paralegal filtering.
3. `CLIENT` sees only `USER` metrics and only its own matters, hearings, events and invoices.
4. A metric whose level is not legal for a type is never offered to the Super Admin when
   composing that type's dashboard, and is skipped at render time if it somehow appears
   (defence in depth).
5. `scope.firmId == null` for a non-super-admin is a hard error, not an empty dashboard.

## 5. Architecture

```
DashboardController          GET /api/v1/dashboard/super-admin | firm | employee | client
        │  @PreAuthorize per type
        ▼
DashboardScopeFactory   →   DashboardScope
        │
        ▼
DashboardService        →   resolves the active DashboardDefinition for the type
        │                   (falls back to the code default layout when none is saved)
        ▼
MetricRegistry          →   code → DashboardMetric
        │                   filtered by type legality, module access, permission
        ▼
DashboardMetric.value(scope, params)  →  WidgetPayload (shape + data)
```

Two definitions of "what this type sees":

- **Code default** — `DashboardDefaults` declares the out-of-the-box metric set per user
  type. Guarantees a fresh database and a new firm are never blank.
- **Saved definition** — `dashboard_definitions` + `dashboard_widgets`. Overrides the code
  default for that type when present and active.

### Metric contract

```java
public interface DashboardMetric {
    String code();                              // CASE_STATUS_BREAKDOWN
    String label();                             // "Cases by status"
    ScopeLevel scopeLevel();                    // USER | FIRM | PLATFORM
    Set<String> userTypes();                     // which types may ever see this
    Set<WidgetShape> shapes();                   // NUMBER | PIE | BAR | LINE | TABLE
    Set<String> dimensions();                    // grouping options: status, court, stage
    String requiredModule();                     // nullable
    String requiredPermission();                 // nullable
    WidgetPayload value(DashboardScope scope, WidgetParams params);
}
```

`WidgetParams` carries only what the metric declared it supports: `groupBy`, `periodDays`,
`limit`. Unknown keys are rejected, so a saved widget cannot smuggle an unscoped filter in.
`WidgetPayload` is `{ shape, value, series[], rows[], columns[], meta{ unit } }`:

- `shape = NUMBER` → `value` (a single number, optionally with a delta).
- `shape = PIE | BAR | LINE` → `series[]`, each `{ label, value }`; `LINE` points also carry
  `x` (ISO date or bucket label).
- `shape = TABLE` → `rows[]` (string-keyed maps) plus `columns[]` declaring each key's
  `{ key, label, type }`, so the generic renderer needs no per-metric knowledge.

Shapes are constrained by the metric: a count can be `NUMBER`/`PIE`/`BAR`; a time series may
also be `LINE`; `TABLE` only when the metric returns rows.

### Error handling

One failing metric must not blank the dashboard. Failures are logged and the widget is
omitted, with `degraded: true` on the response so the UI can say "some widgets unavailable"
— the same isolation the notification retry sweep uses.

## 6. Data Model

```
dashboard_definitions
    id             UUID pk
    name           VARCHAR(100)  not null
    user_type      VARCHAR(20)   not null   -- UserType enum
    firm_id        UUID          null       -- null = platform default for this type; reserved for phase 3
    is_default     BOOLEAN       not null default false
    active         BOOLEAN       not null default true
    created_at / updated_at / created_by / updated_by
    unique (user_type, firm_id)            -- one active definition per type per scope

dashboard_widgets
    id             UUID pk
    definition_id  UUID not null  fk → dashboard_definitions(id) on delete cascade
    metric_code    VARCHAR(60)  not null
    chart_type     VARCHAR(20)  not null   -- NUMBER | PIE | BAR | LINE | TABLE
    group_by       VARCHAR(40)  null       -- must be one of the metric's dimensions
    period_days    INTEGER      null
    limit_rows     INTEGER      null
    size           VARCHAR(10)  not null default 'MEDIUM'   -- SMALL | MEDIUM | LARGE | FULL
    position       INTEGER      not null default 0
    active         BOOLEAN      not null default true
    unique (definition_id, position)
```

Notes:

- `firm_id` exists from day one so phase 3 needs no migration, but nothing writes it in
  phases 1–2.
- `metric_code` is deliberately *not* a foreign key: metrics live in code, so an unknown code
  is skipped at render time rather than blocking a schema change.
- Deleting a metric from code is therefore a silent layout shrink, which is the correct
  failure mode for a dashboard.

## 7. Metric Catalogue (initial set)

Mapped from what the four existing dashboards already compute.

| Code | Label | Level | Types | Shapes | Dimensions |
|---|---|---|---|---|---|
| `PLATFORM_FIRM_COUNTS` | Firms (total / active / suspended / trial) | PLATFORM | SUPER_ADMIN | NUMBER, PIE | status |
| `PLATFORM_USER_COUNTS` | Users by role | PLATFORM | SUPER_ADMIN | NUMBER, PIE | role |
| `PLATFORM_CASE_TOTALS` | Cases across the platform | PLATFORM | SUPER_ADMIN | NUMBER | — |
| `PLATFORM_SCRAPER_HEALTH` | Court scraper (courts, hearings, matches, last run) | PLATFORM | SUPER_ADMIN | NUMBER, TABLE | — |
| `PLATFORM_TRIALS_EXPIRING` | Trials expiring soon | PLATFORM | SUPER_ADMIN | NUMBER, TABLE | — |
| `PLATFORM_RECENT_ACTIVITY` | Recent audit activity | PLATFORM | SUPER_ADMIN | TABLE | — |
| `FIRM_CASE_STATUS` | Cases by status (total / active / closed) | FIRM | FIRM, FIRM_USER | NUMBER, PIE, BAR | status |
| `FIRM_CASE_STAGE` | Matters by stage | FIRM | FIRM, FIRM_USER | BAR, PIE | stage |
| `FIRM_STALE_MATTERS` | Matters with no hearing in 90 days | FIRM | FIRM, FIRM_USER | NUMBER, TABLE | — |
| `FIRM_NEW_MATTERS_TREND` | New matters per month | FIRM | FIRM | LINE, BAR | month |
| `FIRM_TEAM_CASELOAD` | Open matters per advocate | FIRM | FIRM | BAR, TABLE | user |
| `FIRM_HEARINGS_TODAY` | Hearings today | FIRM | FIRM, FIRM_USER | NUMBER, TABLE | — |
| `FIRM_HEARINGS_UPCOMING` | Upcoming hearings (next N days) | FIRM | FIRM, FIRM_USER | NUMBER, TABLE | court |
| `FIRM_INVOICE_AGING` | Outstanding invoices by age bucket | FIRM | FIRM | BAR, TABLE | ageBucket |
| `FIRM_RENEWALS_DUE` | Renewals / deadlines due | FIRM | FIRM, FIRM_USER | NUMBER, TABLE | renewalType |
| `FIRM_ACTIVITY_RECENT` | Firm audit activity | FIRM | FIRM | TABLE | — |
| `MY_OPEN_MATTERS` | My open matters | USER | FIRM_USER | NUMBER, TABLE | — |
| `MY_HEARINGS_TODAY` | My hearings today | USER | FIRM_USER | NUMBER, TABLE | — |
| `MY_HEARINGS_UPCOMING` | My upcoming hearings | USER | FIRM_USER | NUMBER, TABLE | court |
| `MY_EVENTS_TODAY` | My court events today | USER | FIRM_USER | NUMBER, TABLE | — |
| `MY_RENEWALS_DUE` | Renewals assigned to me | USER | FIRM_USER | NUMBER, TABLE | — |
| `MY_MATTER_STATUS` | My matters by status | USER | FIRM_USER, CLIENT | PIE, BAR | status |
| `MY_NEXT_HEARING` | My next hearing | USER | CLIENT | NUMBER, TABLE | — |
| `MY_UPCOMING_EVENTS` | Upcoming events on my matters | USER | CLIENT | TABLE | — |
| `MY_INVOICES_DUE` | My outstanding invoices | USER | CLIENT | NUMBER, TABLE | — |
| `MY_MATTER_UPDATES` | Recent updates on my matters | USER | CLIENT | TABLE | — |

Each metric is implemented against existing repositories; where a query already exists in
any of the four current dashboards it moves into the metric rather than being rewritten.

The catalogue is limited to entities that exist today: `matters`, `court_cases`,
`court_events`, `hearings`, invoices, firms, users, audit logs, renewals. There is **no**
task table and **no** document table yet (`DOCUMENT_MANAGEMENT` is a seeded module label
only), so no metric may depend on them — task/document widgets arrive with those modules.

## 8. API Contract

### Read (four endpoints, all `GET`, all wrapped in `ApiResponse`)

| Path | Guard | Response |
|---|---|---|
| `/api/v1/dashboard/super-admin` | `hasRole('SUPER_ADMIN')` | `DashboardResponse` |
| `/api/v1/dashboard/firm` | `hasRole('FIRM_ADMIN')` | `DashboardResponse` |
| `/api/v1/dashboard/employee` | `hasAnyRole('ADVOCATE','PARALEGAL')` | `DashboardResponse` |
| `/api/v1/dashboard/client` | `hasRole('CLIENT')` | `DashboardResponse` |

```jsonc
{
  "userType": "CLIENT",
  "degraded": false,
  "generatedAt": "...",
  "widgets": [
    {
      "metricCode": "MY_MATTER_STATUS",
      "label": "My matters by status",
      "shape": "PIE",
      "size": "MEDIUM",
      "position": 0,
      "groupBy": "status",
      "payload": { "series": [ { "label": "Active", "value": 3 } ] }
    }
  ]
}
```

### Super Admin layout CRUD

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/super-admin/dashboards` | list definitions (`?userType=`) |
| `POST` | `/api/v1/super-admin/dashboards` | create definition for a user type |
| `PUT` | `/api/v1/super-admin/dashboards/{id}` | rename / activate |
| `DELETE` | `/api/v1/super-admin/dashboards/{id}` | delete (fall back to code default) |
| `GET` | `/api/v1/super-admin/dashboards/metrics?userType=CLIENT` | **server-filtered** metric catalogue: code, label, shapes, dimensions |
| `PUT` | `/api/v1/super-admin/dashboards/{id}/widgets` | replace the widget list in one call (order, size, chart, groupBy) |

The catalogue endpoint is the guard rail: it returns only metrics legal for the requested
user type, and `PUT .../widgets` re-validates every `metricCode`/`chart_type`/`group_by`
against that same legality server-side. Every mutation is audit-logged.

## 9. Integration With Existing RBAC

A metric is shown only when all of these hold: the caller's user type is in
`metric.userTypes()`; `requiredModule()` (if any) is enabled for the firm, using
`ModuleAccessResolver` so sub-modules inherit their parent; and `requiredPermission()` is in
the caller's permission codes. This reuses `PermissionEvaluator` and the module plumbing
rather than adding a parallel access system.

## 10. Testing & Edge Cases

- **Leak contract test, per endpoint** — seed two firms, two advocates, two clients; assert
  every returned row/figure belongs to `scope.userId` for `USER` metrics and `scope.firmId`
  for `FIRM` metrics, and that no `PLATFORM` metric appears on a tenant endpoint.
- **Registry invariants** — every metric's `scopeLevel` must be legal for each type in its
  `userTypes()` (a `PLATFORM` metric can never declare `CLIENT`); every `dimension` a widget
  stores must be declared by its metric; every `chart_type` must be in the metric's `shapes()`.
- **Legality** — `GET .../metrics?userType=CLIENT` never lists a `FIRM`/`PLATFORM` metric.
- **Degradation** — a metric throwing does not fail the endpoint; `degraded: true` and the
  other widgets still render.
- **Fallback** — no saved definition ⇒ code default layout; unknown `metric_code` in a saved
  widget is skipped without error.
- **Empty states** — firm with no matters, client with nothing shared: widgets return zero
  values, not errors.
- **Multi-tenant** — `FIRM_ADMIN` of firm A never sees firm B; a missing `firmId` for a
  tenant type is rejected.
- **Performance** — each endpoint's query count is asserted (no N+1 in widget rendering).

## 11. Migration Of Existing Dashboards

1. Metrics are extracted from the four existing dashboards (global, case/firm, lawyer,
   project); behaviour must match for the equivalent type before anything is deleted.
2. The new endpoints go live alongside the old ones.
3. The frontend moves to the generic renderer.
4. Old endpoints are removed in the same release as the frontend switch:
   `/modules/dashboard`, `/firm/dashboard`, `/firm/lawyer/dashboard`,
   `/firm/projects/dashboard`.

Leaving the old endpoints alive is what would keep the older, inconsistent scoping rules in
production — so this step is part of the goal, not cleanup.

## 12. Phasing

Phase 1 is what this spec plans to implement; phases 2 and 3 get their own spec + plan once
phase 1 is in use.

- **Phase 1 — engine + metrics + 4 endpoints.** Scoped `DashboardScope`, metric registry,
  code default layouts, contract tests. No new tables yet.
- **Phase 2 — layout CRUD.** `dashboard_definitions` + `dashboard_widgets`, the Super Admin
  endpoints, the server-filtered catalogue, audit logging.
- **Phase 3 — optional.** Per-firm layouts (`firm_id` column already present), per-user
  tailoring, per-scope metric caching if query load requires it.

## 13. Follow-ups

- Currency/date formatting for `FIRM_INVOICE_AGING` and hearing times: firm `TIMEZONE`
  config key is declared but nothing reads it yet — this feature is the natural first reader.
- Consider a `DashboardMetric` assertion that no metric implementation calls a repository
  without going through the scope (ArchUnit-style rule), to make the leak impossible by
  construction rather than by review.
