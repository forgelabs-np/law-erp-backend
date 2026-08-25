# State
_Last updated: 2026-08-25_

## Active work
- Query optimization pass DONE — 6 hotspots fixed (N+1, full-table scans, extra DB hits)
- Dashboard trends feature DONE — userTrends, matterTrends, firmTrends with configurable days param
- Invoice Generator module DONE — 8 endpoints, Thymeleaf PDF, email with attachment
- 178 tests pass
- Go-live prep next: matter-list scoping for advocate, project DELETE endpoint, secrets → env/SystemConfig, MFA bypass removal
- App running on :6969 (dev); test data: E2EFIRM/SECFIRMA/SECFIRMB firms + users

## Recent decisions (keep ~6, drop the oldest)
- Dashboard trends: reconstruct from creation timestamps (fast, no snapshot table needed); configurable days param (1-365)
- Query optimization: batch-load patterns preferred over N+1; COUNT queries over full-table loads
- PermissionEvaluator fast-path: use JWT permissions list, skip DB queries when available
- All modules follow Controller → Service (interface) → ServiceImpl → Mapper pattern
- Response building extracted from services into mapper classes per module
- @Transactional must be on public methods only, never private

## Known issues / follow-ups
- Controllers still have some inline Swagger strings — constants created but not all controllers updated yet

## Last session
Query optimization pass (6 hotspots) + Dashboard trends feature + Invoice Generator module (Thymeleaf PDF, email with attachment). 178 tests pass.

## Deep history index (pointers only, not content)
- Case Management Dashboard + Global Dashboard → memory/2026-08-18.md (inferred)
- Dashboards documentation → memory/2026-08-19.md
- Complete module refactoring (all modules) → memory/2026-08-20.md
- N+1 query optimization + Project Management module → memory/2026-08-21.md
- Strix security skills install + manual-only policy → memory/2026-08-22.md
- Query optimization pass + Dashboard trends feature → memory/2026-08-25.md
