# RBAC Delegation Chain — Full Design Spec

Status: agreed design, ready to implement. Read start to finish before writing code —
later sections depend on decisions made in earlier ones.

## 0. Invariant this design enforces

No employee ever holds a permission their Firm Admin doesn't hold. No Firm Admin ever
holds a permission Super Admin hasn't allowed via the `FIRM_ADMIN` system template.
This must hold **at every point in time**, not just immediately after an edit —
including for firms onboarded after a template change, and after cascading edits.

## 1. What already works today — do not rebuild

- **Two-layer role model**: system templates (`firm_id IS NULL`, `is_system=true`) —
  `SUPER_ADMIN`, `FIRM_ADMIN`, `ADVOCATE`, `PARALEGAL`, `CLIENT` — and per-firm clones
  (`is_system=false`, `parent_role_id → template`) created at onboarding for every
  template except `SUPER_ADMIN`.
- **Permission model**: flat codes `MODULE:ACTION`, junction table `role_permissions`.
  `scope` column exists (`TENANT`/`GLOBAL`/`OWN`/`ASSIGNED`) but only `TENANT` is
  actually seeded/used anywhere — treat the others as unused for this design; do not
  build enforcement for them as part of this work.
- **Super Admin authorization**: a hardcoded bypass (`isSuperAdmin()` → allow) in
  `PermissionEvaluator`, not permission-row-based. `SUPER_ADMIN` role/permission rows
  are never read for SA's own authorization. This design does not change that — SA
  stays a bypass. The `SUPER_ADMIN` template itself stays permanently immutable
  (editing it is pure ceremony since it's never read, and it's a lockout risk).
- **Firm Admin ceiling today**: a firm's `FIRM_ADMIN` clone is ceilinged by its parent
  `FIRM_ADMIN` template. Since templates are currently immutable, this ceiling is
  frozen at whatever the boot seed matrix says — this design's main job is making
  that ceiling actually SA-adjustable.
- **Firm Admin → employee ceiling today**: editing any non-FIRM_ADMIN firm role
  (`PUT /api/v1/firm/roles/{roleId}/permissions`) is ceilinged by the firm's own
  `FIRM_ADMIN` clone's *currently enabled* permissions, minus `GLOBAL`. Working
  correctly. Do not change this endpoint's behavior.
- **SA per-firm override**: `PUT /super-admin/firms/{firmId}/roles/{roleId}/permissions`
  bypasses ceiling entirely for a single role in a single firm. Confirm (Phase 0) that
  it already accepts non-`FIRM_ADMIN` roles (e.g. a firm's `PARALEGAL` clone) — if so,
  this endpoint needs no changes and remains SA's "surgical override" tool alongside
  the new template-edit tool for "platform-wide" changes.
- **Custom roles**: Firm Admin can already create firm-scoped custom roles
  (`POST /api/v1/firm/roles`) and assign employees to them
  (`bulk-role-change`). This is the correct, existing mechanism for
  "this specific employee needs different access than their role" — see §7.
- **Invalidation**: `permissionVersion` claim checked every request; edits bump it +
  clear cache → immediate effect, forced re-login for affected users. Reused as-is.
- **Audit trail**: `ROLE_CREATED/UPDATED/DELETED`, `ROLE_PERMISSION_CHANGED`,
  `USER_ROLE_CHANGED`. Extended, not replaced, by this design.

## 2. What this design adds

1. System templates (`FIRM_ADMIN`, `ADVOCATE`, `PARALEGAL`, `CLIENT` — **not**
   `SUPER_ADMIN`) become SA-editable.
2. Template edits propagate to firm clones as a **diff** (add/remove delta), not a
   hard reset — preserves firm-level customizations unrelated to the SA's edit.
3. Cascade enforcement runs in **both directions**:
   - Narrowing `FIRM_ADMIN` template/clone → strip over-ceiling permissions from
     employee clones **and** from employee templates (§4 — this is the fix that
     closes the fresh-onboarding gap; do not skip it).
   - Firm Admin narrowing their own clone → strip over-ceiling permissions from that
     firm's employee clones only (single-firm, synchronous).
4. Chain validation at edit time in both directions: employee-template/clone
   permission set must always be a subset of the current `FIRM_ADMIN`
   template/clone set. Reject with the specific offending permission codes named in
   the error — never silently clip.
5. SA gets read visibility into any firm's roles, and can create firm-scoped custom
   roles on a firm's behalf.
6. Custom roles get a `parent_role_id` at creation (closes an existing bug where the
   ceiling check silently skips custom roles that lack one).

## 3. Data model changes — two small ones, no new tables for roles/permissions

- `Role.last_sa_edit_at` (nullable timestamp) — set on any SA template edit. The
  additive/idempotent boot seeder MUST check this and skip re-injecting default
  matrix values into any template where it's non-null. Without this, a restart
  silently resurrects permissions SA removed and re-breaks the ceiling invariant.
- `Role.parent_role_id` — already exists for template→clone; extend its use to
  custom roles too (set at creation time from a base template the creator picks).
  Backfill existing custom roles once, against a chosen base template.
- New table for job tracking (§6): `sync_jobs (id, template_id, status, counts,
  error_summary, correlation_id, created_at, completed_at)`.
- **Before building `sync_jobs`**: check whether the codebase already has any
  async-job or scheduled-task infrastructure (thread pool config, existing job
  table, queue). If so, reuse that pattern instead of introducing a second one.

## 4. Sync semantics (authoritative — supersedes any earlier partial version)

| Template change | Effect |
|---|---|
| **Add** permission to employee template (`ADVOCATE`/`PARALEGAL`/`CLIENT`) | Add to each firm's clone **only if** within that firm's current `FIRM_ADMIN` clone ceiling; otherwise skip (record as "skipped by ceiling" in job result, not an error) |
| **Remove** permission from employee template | Remove from every firm's clone unconditionally — including permissions a firm admin independently added themselves that happen to share the same code (explicit SA decision wins; surfaced in preview + audit; see provenance caveat below) |
| **Widen** `FIRM_ADMIN` template | Auto-widen every firm's `FIRM_ADMIN` clone (template is their ceiling). Does **not** auto-grant anything to employees — they only gain a widened permission via their own employee-template's delta, separately |
| **Narrow** `FIRM_ADMIN` template | Narrow every firm's `FIRM_ADMIN` clone, then cascade in **two places**: (a) recompute each firm's employee-role ceilings and strip anything now over-ceiling from employee **clones**; (b) strip the same now-over-ceiling permissions from the employee **templates** themselves, in the same transaction as the `FIRM_ADMIN` template edit. (b) is required — without it, a firm onboarded *after* this edit clones fresh from an employee template that still holds a permission the `FIRM_ADMIN` template no longer grants, silently reintroducing the exact violation this design exists to prevent. |
| Chain validation (either direction) | Employee template/clone set must always be ⊆ current `FIRM_ADMIN` template/clone set. Violation → reject, naming the specific offending permission codes. |

**Documented tradeoff, not a bug**: because no per-permission provenance is tracked
(deliberately — avoids new schema), the system cannot distinguish a clone holding a
code because it inherited it from the template vs. because a Firm Admin
independently, legitimately added that same code themselves. An unconditional
template removal wipes both indistinguishably. Acceptable given the scope of this
design; revisit only if it causes a real incident.

## 5. API surface

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/admin/roles/templates` | List system templates + current permissions |
| `GET /api/v1/super-admin/firms/{firmId}/roles` | A firm's roles + permissions (closes SA discoverability gap) |
| `POST /api/v1/admin/roles/templates/{id}/permissions/preview` | Dry-run: computed delta + per-firm impact counts (applied / skipped-by-ceiling / removed / cascade-stripped). Zero writes. |
| `PUT /api/v1/admin/roles/templates/{id}/permissions` | Validates chain (§4), computes delta, persists template, sets `last_sa_edit_at`, enqueues sync job. Returns `jobId`. |
| `GET /api/v1/admin/sync-jobs/{jobId}` | Job status: firms complete/total, per-firm errors |
| `POST /api/v1/super-admin/firms/{firmId}/roles` | SA creates a firm-scoped custom role (same service as Firm Admin path, ceiling skipped for SA, `parent_role_id` required) |
| `PUT /super-admin/firms/{firmId}/roles/{roleId}/permissions` | **Existing, unchanged.** SA's per-firm surgical override — no ceiling. Confirm in Phase 0 that it already accepts non-`FIRM_ADMIN` roles. |
| `PUT /api/v1/firm/roles/{roleId}/permissions` | **Existing, unchanged.** Firm Admin editing within their own ceiling. |

**Ceiling-semantics clarification**: the parent-template ceiling governs template
*chain validation* only (§4). It does not additionally constrain SA's existing
per-firm override endpoint — that stays override-semantics (no ceiling), or
diverged-but-legal firm customizations would become uneditable by SA.

## 6. Sync job execution

- **Async**, not inline in the `PUT` request. A single template edit can touch every
  firm's clone of that template — doing that synchronously means request latency
  scales with customer count, which is the wrong direction as the platform grows.
- `@Async` is sufficient at current scale; move to a real queue only if job volume or
  reliability needs outgrow it.
- Per-firm transactional batches. Delta-apply must be idempotent (ensure-added-if-
  ceiling-allows / ensure-removed, computed against fresh per-firm state each time)
  so retries after partial failure are safe.
- `permissionVersion` bump batched per role (one UPDATE per affected role, not one
  per user); cache clears follow.
- One firm's failure must not block others; record per-firm errors on the job for
  retry, don't fail the whole job.
- The `FIRM_ADMIN`-narrows cascade (both clone-strip and template-strip, §4) runs
  inside the same job, per firm, immediately after that firm's `FIRM_ADMIN` clone is
  synced — keep each firm's unit of work together rather than two separate full
  passes over all firms.

## 7. Audit + UX

- Top-level `TEMPLATE_PERMISSION_CHANGED` audit row for the SA's edit, plus one
  `ROLE_PERMISSION_CHANGED (scope=TEMPLATE_SYNC)` per affected clone/template, all
  linked by the job's `correlation_id` — "why did Firm X's PARALEGAL change" must
  always trace back to the specific SA action that caused it.
- Nothing about a cascade is ever silent:
  - Preview endpoint shows it before commit.
  - Job status/result records exactly what was stripped, where.
  - Firm Admin self-narrowing (single-firm, synchronous) returns the cascade effect
    directly in the response body — e.g. "this also removed X from N employees
    holding role Y."

## 8. Explicitly out of scope for this design

- Per-user permission grants/overrides (decided: role-level only — see the
  John/Ron case below).
- Firm-invented new permission *codes* (only recombination of existing codes into
  custom roles is in scope).
- Deny/negative permissions.
- `UserRole` legacy table cleanup (separate, low-risk cleanup item, unrelated to
  this design; do anytime).
- "Respect local edits, never auto-sync customized clones" mode (Option C from
  earlier brainstorm) — diff-based sync (Option B) was chosen instead; revisit only
  if diff-based sync proves insufficient in practice.

## 9. The John/Ron case (why no per-user overrides)

Two employees with the same role needing different access (e.g. one Paralegal needs
`CASE_MANAGEMENT:EDIT`, another doesn't) is **not** a per-user-override problem.
Solution: Firm Admin creates a new firm-scoped custom role (e.g. "Senior Paralegal")
via the existing `POST /api/v1/firm/roles`, and reassigns that one employee to it via
existing `bulk-role-change`. This keeps every permission check a single lookup
(`User.role → role_permissions`), keeps the ceiling/audit/invalidation machinery
uniform, and avoids the audit and drift problems of per-user exception grants.
This case requires **no new code** from this design — the mechanism already exists.

## 10. Build order

- **Phase 0** (verification + trivial fixes, no risk):
  - Confirm SA's existing per-firm override endpoint accepts non-`FIRM_ADMIN` roles.
  - Add `parent_role_id` to custom-role creation; backfill existing custom roles.
  - Check for existing async-job infra before designing `sync_jobs` from scratch.
- **Phase 1** (read-only, unblocks everything else):
  - `GET /api/v1/admin/roles/templates`
  - `GET /api/v1/super-admin/firms/{firmId}/roles`
- **Phase 2** (template editing, no cross-firm side effects yet):
  - `PUT .../templates/{id}/permissions` — delta computation, chain validation (both
    directions per §4), `last_sa_edit_at`, seeder freeze check.
  - `POST .../templates/{id}/permissions/preview`
- **Phase 3** (the fan-out):
  - `sync_jobs` table + async job (or integrate with existing job infra from Phase 0).
  - `GET /api/v1/admin/sync-jobs/{jobId}`.
  - Both-direction cascade logic from §4, including the `FIRM_ADMIN`-narrows →
    employee-*template* strip (not just clone strip).
  - Audit correlation (§7).
- **Phase 4** (single-firm, synchronous — smaller, can be done independently of
  Phase 3 if useful to ship sooner):
  - Cascade on Firm Admin narrowing their own clone; cascade effect in response body.
- **Phase 5**:
  - `POST /api/v1/super-admin/firms/{firmId}/roles` — SA-side custom role creation.

Each phase should be shippable and testable independently — later phases don't
require earlier ones to be perfect, but do require them to exist (e.g. Phase 3's
cascade needs Phase 2's chain-validation logic already in place and correct).
