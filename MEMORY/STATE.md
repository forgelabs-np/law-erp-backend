# State
_Last updated: 2026-08-20_

## Active work
- Refactoring complete. All 26 services follow Controller → Service (interface) → ServiceImpl → Mapper pattern.

## Recent decisions (keep ~6, drop the oldest)
- All modules follow Controller → Service (interface) → ServiceImpl → Mapper pattern
- Response building extracted from services into mapper classes per module
- Swagger summaries/descriptions centralized as constants in `common/constant/`
- Utility/engine classes kept as-is (AppealDeadlineEngine, HearingIngestionService, etc.)
- @Transactional must be on public methods only, never private

## Known issues / follow-ups
- Controllers still have some inline Swagger strings — constants created but not all controllers updated yet

## Last session
Full codebase refactoring complete. Fixed @Transactional on private method bug in AuthServiceImpl. All 132 tests pass. All 26 services across 10 modules follow the clean architecture pattern.

## Deep history index (pointers only, not content)
- Case Management Dashboard + Global Dashboard → memory/2026-08-18.md (inferred)
- Dashboards documentation → memory/2026-08-19.md
- Complete module refactoring (all modules) → memory/2026-08-20.md
