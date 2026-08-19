
## Case Management Dashboard (2026-08-18)

Added role-based dashboard and case assignment system:

**New entities:**
- `CaseAssignment` (case_assignments) - many-to-many between Matter and User with role
- `AssignmentRole` enum: PRIMARY_ADVOCATE, CO_ADVOCATE, PARALEGAL, JUNIOR, SUPERVISOR

**New services:**
- `CaseAssignmentService` - assign/revoke/list employees to matters
- `DashboardService` - aggregated stats with role-based filtering

**New controllers:**
- `DashboardController` - GET /api/v1/firm/dashboard
- `CaseAssignmentController` - POST/GET/DELETE /api/v1/firm/matters/{matterNumber}/assignments

**Dashboard features:**
- Total/active/dormant/closed matter counts
- Today's events (advocate-filtered for non-admins)
- Case positioning (current court, stage, last hearing, next event, days since)
- Stale cases (90+ days since last hearing)
- Role-based: FIRM_ADMIN sees all, ADVOCATE/PARALEGAL see only assigned matters

**Tests:** 91 total, 0 failures (1 pre-existing ErpApplicationTests error)
**devG:** 0 case-management errors (pre-existing javax.cache errors unrelated)

## Global Dashboard (2026-08-18)

Added site-wide dashboard in userManagement module:

**Endpoint:** GET /api/v1/modules/dashboard (FIRM_ADMIN only)

**Response sections:**
- UserStats: total/active/inactive, by role (advocates, paralegals, clients, firm admins)
- FirmStats: total/active/suspended firms
- CaseStats: total/active/closed/stale matters, today's events
- ScraperStats: courts tracked, daily/weekly hearings, matches, last scrape time
- RecentActivity: last 10 audit log entries with user names

**Files:**
- GlobalDashboardResponse.java (DTO)
- GlobalDashboardService.java (aggregates from all modules)
- GlobalDashboardController.java (endpoint)

## Dashboards documentation (2026-08-19)

Deliverables in `docs/` for the dashboard feature set:
- `dashboards-module.html` / `dashboards-module.pdf` — API reference + frontend integration guide (what changed, both dashboards, case assignments, department removal, role-based UI rules, curl collection)
- `dashboards-curl-collection.sh` — runnable bash curl collection (auth flow incl. MFA/change-password, case dashboard, matter create, assign/list/revoke, global dashboard)
- `dashboard.postman_collection.json` — Postman v2.1 collection (updated: added assign/revoke/create-matter)

Scope note for future work: the new feature = Case Dashboard (GET /firm/dashboard), Case Assignments (POST/GET/DELETE /firm/matters/{no}/assignments), Global Dashboard (GET /modules/dashboard). Also changed in same session: department removed from employee CRUD; JwtAuthFilter now populates roles (FIRM_ADMIN sees all matters); daysSinceLastHearing nullable; stale flag consistent for no-hearing matters.
