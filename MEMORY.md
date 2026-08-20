## Code Architecture Refactoring — Auth & SuperAdmin Modules (2026-08-20)

Refactored auth and superadmin modules to clean architecture pattern:

**Pattern: Controller → Service (interface) → ServiceImpl → Mapper**

**Auth Module Changes:**
- `AuthService.java` — now an interface
- `AuthServiceImpl.java` — implementation with clean separation of concerns
- `AuthMapper.java` — extracts all LoginResponse building (success, MFA setup, MFA required, password change required)
- `AuthController.java` — cleaned up, uses `AuthConstants` for Swagger
- `AuthConstants.java` — centralized Swagger summary/description strings

**SuperAdmin Module Changes:**
- `SuperAdminService.java` — now an interface
- `SuperAdminServiceImpl.java` — implementation using `AuthMapper` for response building
- `SuperAdminController.java` — uses `SuperAdminConstants` for Swagger
- `SuperAdminConstants.java` — centralized Swagger summary/description strings

**Key improvements:**
- Response building extracted from services into mapper classes
- All services follow interface + impl pattern
- Swagger summaries/descriptions are constants (no inline strings in controllers)
- Removed dead/commented-out code from controllers
- Reduced verbose comments to minimal essential ones
- Helper methods extracted (`validateAccountStatus`, `handleFailedLogin`, `handleMfaFlow`)

**Tests:** 132 total, 0 failures, 0 errors

## Me, Scraper & UserManagement Refactoring (2026-08-20)

Extended the same pattern to three more modules:

**Me Module:**
- `MeService.java` — interface
- `MeServiceImpl.java` — implementation using MeMapper
- `MeMapper.java` — extracts FirmInfo/RoleInfo building
- `MeConstants.java` — Swagger constants

**Scraper Module:**
- `ScraperService.java` — interface
- `ScraperServiceImpl.java` — implementation using ScraperMapper
- `ScraperMapper.java` — extracts hearing response building and sorting
- `ScraperConstants.java` — Swagger constants

**UserManagement Module:**
- `UserManagementService.java` — interface
- `UserManagementServiceImpl.java` — implementation using UserManagementMapper
- `GlobalDashboardService.java` — interface
- `GlobalDashboardServiceImpl.java` — implementation
- `UserManagementMapper.java` — extracts toSummary, toActivityEntry, groupByModule
- `UserManagementConstants.java` — Swagger constants

## RBAC, Audit & Email Refactoring (2026-08-20)

**RBAC Module:**
- `RbacResponseMapper.java` — extracts Permission/Role response building
- `ModuleService.java`, `PermissionService.java`, `RoleManagementService.java`, `RolePermissionService.java` — interfaces
- `*Impl.java` — implementations using RbacResponseMapper
- `RbacConstants.java` — Swagger constants for all 3 RBAC controllers

**Audit Module:**
- `AuditService.java` — interface
- `AuditServiceImpl.java` — implementation
- `AuditConstants.java` — Swagger constants

**Email Module:**
- `EmailService.java` — interface
- `EmailServiceImpl.java` — implementation

**Tests:** 132 total, 0 failures, 0 errors

## Firm & CaseManagement Refactoring (2026-08-20)

**Firm Module (8 services):**
- `FirmMapper.java` — extracts all response building
- `ClientService`, `EmployeeService`, `FirmService`, `FirmAdminService`, `FirmRoleService`, `FirmProfileService`, `FirmModuleService`, `FirmEmailConfigService` — interfaces
- All 8 impls updated to implement their interfaces
- `FirmConstants.java` — Swagger constants for all 7 controllers

**CaseManagement Module (6 services):**
- `CalendarService`, `CaseAssignmentService`, `CourtCaseService`, `CourtEventService`, `DashboardService`, `MatterService` — interfaces
- All 6 impls updated to implement their interfaces
- `CaseManagementConstants.java` — Swagger constants for all 6 controllers
- Utility classes (AppealDeadlineEngine, CourtCaseRefGenerator, MatterNumberGenerator, PartyMatchService) kept as-is

**All modules now follow: Controller → Service (interface) → ServiceImpl → Mapper pattern**

**Tests:** 132 total, 0 failures, 0 errors

---

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
