# State
_Last updated: 2026-08-23_

## Active work
- Full E2E test session DONE → report at `docs/test-report-2026-08-23.md`
- CRITICAL authz gaps FIXED (§3.12), MEDIUM bugs FIXED (§3.13), hearing-reminder emails BUILT + verified live (§3.14) — 177 tests pass
- Go-live prep next: matter-list scoping for advocate, project DELETE endpoint, secrets → env/SystemConfig, MFA bypass removal
- App running on :6969 (dev); test data: E2EFIRM/SECFIRMA/SECFIRMB firms + users (see report §6); test PESHI 2026-08-24 15:00 + reminder log rows in dev DB

## Recent decisions (keep ~6, drop the oldest)
- Strix removed entirely (skills, CLI, runs) — not needed; manual/curl testing instead
- Test-mode security items (MFA 123456, committed JWT/AES/registration secrets) INTENTIONAL for now — user will move to env/SystemConfig before go-live (TODOs in report §5)
- Project mgmt module + RBAC permission layer have NO enforcement — top go-live blockers
- All modules follow Controller → Service (interface) → ServiceImpl → Mapper pattern
- @Transactional must be on public methods only, never private

## Recent decisions (keep ~6, drop the oldest)
- Strix security skills (9): NEVER auto-run, only on explicit user request; stored in `.agents/skills/security/` (nested = not auto-discoverable, intentional); read SKILL.md from disk when asked
- All modules follow Controller → Service (interface) → ServiceImpl → Mapper pattern
- Response building extracted from services into mapper classes per module
- Swagger summaries/descriptions centralized as constants in `common/constant/`
- Utility/engine classes kept as-is (AppealDeadlineEngine, HearingIngestionService, etc.)
- @Transactional must be on public methods only, never private

## Known issues / follow-ups
- Controllers still have some inline Swagger strings — constants created but not all controllers updated yet

## Last session
Built Project Management module: design brainstorming → spec → full implementation (40+ files). Generated docs (MD + PDF + Postman). Fixed 2 DB issues (property path mismatch, CHECK constraint). 138 tests passing.

## Deep history index (pointers only, not content)
- Case Management Dashboard + Global Dashboard → memory/2026-08-18.md (inferred)
- Dashboards documentation → memory/2026-08-19.md
- Complete module refactoring (all modules) → memory/2026-08-20.md
- N+1 query optimization + Project Management module → memory/2026-08-21.md
- Strix security skills install + manual-only policy → memory/2026-08-22.md
_Last updated: 2026-08-25_

## Active work
- Full E2E test session DONE → report at `docs/test-report-2026-08-23.md`
- CRITICAL authz gaps FIXED (§3.12), MEDIUM bugs FIXED (§3.13), hearing-reminder emails BUILT + verified live (§3.14) — 178 tests pass
- Go-live prep next: matter-list scoping for advocate, project DELETE endpoint, secrets → env/SystemConfig, MFA bypass removal
- App running on :6969 (dev); test data: E2EFIRM/SECFIRMA/SECFIRMB firms + users (see report §6)
