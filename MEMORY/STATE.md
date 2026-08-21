# State
_Last updated: 2026-08-21_

## Active work
- Project Management module complete — code, docs, Postman collection, PDF all done.
- Pending: Run SQL migration to add CREDENTIAL_VIEW/CREDENTIAL_REVEAL to DB constraint, then start app.

## Recent decisions (keep ~6, drop the oldest)
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
