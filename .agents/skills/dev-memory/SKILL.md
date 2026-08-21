---
name: dev-memory
description: Restores project context cheaply at the start of every coding session (reads one small rolling summary, not the full history), logs completed work to memory/ as the session goes, and writes full feature docs to docs/ when the user says "generate." Use at the start of any session on this project, on "what did we do last time" / "continue where we left off," on "generate" / "generate docs" / "document this," and on session-end cues like "that's it for today."
---

# Dev Memory & Docs

Carries context across sessions without re-reading the whole project history every time a new chat starts.

## When this fires

- Start of a session → load context
- "what did we do last time" / "continue where we left off" → load context
- "generate" / "generate docs" / "document this" → write a doc for the feature just finished
- "that's it for today" / "wrapping up" → leave STATE.md accurate before ending

## The idea

Two folders, two jobs:

- **`memory/`** — rolling working memory. `STATE.md` holds *current* state and is edited in place, so it never grows. `YYYY-MM-DD.md` holds the detailed log for that day and is only ever appended to.
- **`docs/`** — permanent documentation, one file per feature, written on request.

This is what keeps token cost flat as the project gets older: **only `STATE.md` gets read automatically.** Daily logs accumulate forever but get opened only when something specific needs them, via a targeted search — never a full scan of `memory/`.

## Folder layout

```
memory/
  STATE.md            <- only file read automatically. Edited in place, stays small.
  2026-08-19.md       <- one per day. Append-only. Read on-demand only.
  2026-08-20.md
docs/
  _template.md        <- structure every generated doc follows
  dynamic-qr.md       <- one per shipped feature, written on "generate"
```

If `memory/` or `docs/` don't exist yet, create them — and recreate `docs/_template.md` from the section list in step 4 below if it's missing.

## 1. Session start — every time

1. Read `memory/STATE.md`. That's the whole default load.
2. If today's `memory/YYYY-MM-DD.md` already exists, read that too — it means work already happened today. Still nothing else.
3. Give a 1-3 line recap of where things stand, then start on the new task. This is a pointer check, not a status report.
4. Only go further than steps 1-2 if the task genuinely needs it — a specific past date or feature comes up, or STATE.md's one-liner isn't enough to answer a "why did we do X" question. Then search `memory/` and `docs/` for the keyword and open only what matches. Never open every daily file to "catch up."

## 2. While working — log to memory/

When a discrete unit of work finishes — a feature shipped, a bug fixed, an approach chosen or rejected, anything annoying to have to re-derive later — append it to today's `memory/YYYY-MM-DD.md` (create it with a `# YYYY-MM-DD` header on the first entry of the day). Log as you go, not in one reconstruction pass at the end, so the log stays accurate even if the session cuts off early.

Rules:
- Bullets, not prose.
- Every entry earns its "why," not just the "what" — "fixed the scan-path check" is half an entry.
- Skip the noise. Routine reads, no-op edits, and dead ends with nothing found don't need a line.
- Group same-day entries under a subheading per feature if more than one thing got touched.

## 3. Keep STATE.md current

After logging to today's file, update `memory/STATE.md` in place — edited, never appended to. It answers "what's going on right now," never "what has ever happened."

## 4. On "generate" — write docs/

Only on request, never automatically.

1. Pull context for the feature from today's (and, if it spanned days, the relevant) daily log entries — don't re-read the whole project to do this.
2. Follow `docs/_template.md`: Summary, Why This Approach, Backend Implementation, Frontend Implementation (skip if backend-only), API/Contract Changes, Testing & Edge Cases, Follow-ups.
3. "Why This Approach" is the section that matters most — what was considered and rejected, and why, not just what shipped.
4. Write `docs/<feature-slug>.md`, kebab-case.
5. Add one pointer line under STATE.md's "Deep history index."
6. If it's not obvious which feature "generate" refers to, ask before writing.

## 4b. On module completion — generate docs + Postman + PDF

When a full module is completed (all entities, services, controllers, tests passing), automatically generate:

1. **Comprehensive Markdown doc** at `docs/<module-name>-module.md` covering:
   - Overview & architecture
   - Data model (all entities with column tables)
   - Full API reference (all endpoints with request/response examples)
   - RBAC & permissions matrix
   - Security details (encryption, audit logging)
   - Business rules & restrictions
   - Frontend integration guide (code snippets, form validation)
   - Seed data reference
   - Testing instructions

2. **Postman collection JSON** at `docs/postman-<module-name>.json`:
   - All endpoints organized by resource
   - Environment variables (`baseUrl`, `authToken`, entity IDs)
   - Request bodies with realistic examples
   - Basic tests for key responses

3. **PDF export** at `docs/<module-name>-module.pdf`:
   - Use `npx --yes md-to-pdf` to convert the Markdown doc
   - A4 format with 20mm margins

This ensures every module ships with official documentation, importable API collection, and a shareable PDF.

## Hard rules

- Session start reads `STATE.md` (+ today's file, if present). Nothing else, by default.
- Never re-summarize full project history "just in case" — load only what the task in front of you needs.
- Search for a specific past decision instead of opening files one by one to look for it.
- `STATE.md` is edited, not appended to. Daily files are appended to, not rewritten.
- Terse bullets with a "why," everywhere in `memory/`.
- No secrets, tokens, or customer/PII data in either folder — assume both get committed.
