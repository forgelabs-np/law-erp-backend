# Tarikh / LAW-ERP — G-Stack Product Review

**Date:** 2026-10-12 · **Branch:** `devG` · **gstack:** v1.91.93.0 (`.agents/skills/Gstack`)
**Baseline:** 671 tests green (`./mvnw -o test`) · 549 main Java files · 92 test files · PG 17.6

---

## How this was produced (and its limits)

gstack's skills are Claude Code workflows: a `gstack-skill-start` preamble, telemetry, plan-file
writes, and AskUserQuestion gates. This host ran their **frameworks**, not that interactive harness.
Lenses applied:

| Lens | gstack skill | What was taken |
|---|---|---|
| CEO | `/plan-ceo-review` | Step 0 nuclear scope challenge (0A premise, 0B reuse, 0C dream state, 0E mode), prime directives |
| CTO | `/plan-eng-review` | Scope challenge, engineering review, priority hierarchy |
| QA | `/qa` | Baseline → Phase 7 severity triage, coverage debt |
| CSO | `/cso` | Actor/asset/boundary census, severity/confidence/evidence rubric |

Evidence used: the repo itself, a **live probe of the configured PostgreSQL** (2026-10-12), the
existing `docs/qa-report-2026-09-21.md`, `MEMORY/STATE.md`, and `checklist-prod.md` produced earlier.
Nothing here is implemented — this is review only.

---

## Scorecard

| Lens | Verdict | One line |
|---|---|---|
| **CEO** | CONDITIONAL GO | Ship the Nepal wedge now; the revenue engine (time → billing) is missing |
| **CTO** | **NO-GO** | Prod can boot against the dev database with committed secrets |
| **QA** | CONDITIONAL GO | 671 green, but no CI and open medium findings |
| **CSO** | CONDITIONAL GO | No critical, but repo-committed secrets and a plaintext MFA secret |
| **Overall** | **NO-GO today** | Close §5 P0s, then GO |

---

## 1. CEO lens — `/plan-ceo-review`

### 0A. Premise challenge

**The real problem:** Nepali law firms run on paper, Excel and memory. Nepal's court system publishes
hearings on websites in Devanagari with Bikram Samvat dates, and nothing mainstream (Clio, MyCase)
handles BS calendars, Devanagari numerals, or Nepal's court hierarchy.

**Does the product solve it?** Partly, and the part it solves is the *moat*. `scraper` (court scraping,
BS↔AD conversion, 77 districts, case-number matching) plus matter/court-case modelling is genuinely
hard to copy and is why a firm picks Tarikh over a generic tool. That wedge is real and defensible.

**Do-nothing cost:** firms stay on spreadsheets; the scraper's value decays unless hearing matching is
trustworthy, and a half-matching scraper erodes trust faster than no scraper.

**CEO-1 (High) — the revenue engine is missing.** The product generates invoices but never captures
*how* they were earned. `modules/invoice/` has `InvoiceItem` and PDF generation, and there is no
`TimeEntry` entity anywhere. In legal software, unbilled time is lost revenue; without time capture the
invoice module is a PDF generator. This is the single highest-ROI build: `TimeEntry` (user, matter,
duration, activity, rate, narrative) → approved entries become `InvoiceItem` rows. Reuses the firm
scoping and permissions that already exist. (`future_ideas_tarikh.md` §2 already ranks it #1.)

**CEO-2 (High) — trust/client-money accounting is a compliance cliff.** A legal platform that firms
put real client money into must segregate client funds. Today there is no `trust` module. Firms will
invoice through Tarikh but keep client money in the bank. Nepal Bar rules need research before design —
this is a research task, not just engineering. Sequence it after launch.

**CEO-3 (Medium) — the Nepal wedge is unproven at scale and fragile.** The scraper depends on court
websites that can change markup without notice, and `ScraperProperties.enabled` gates the jobs. One
court-site redesign silently stops ingestion. There is no user-visible freshness signal ("hearings last
synced X ago") in the module list. Add one.

**CEO-4 (Medium) — the client portal is project-only, not matter-wide.** `future_ideas_tarikh.md` §4
flags this: clients can see projects but the portal does not expose their *cases*. Matter status and
next hearing date are the two things clients actually ask about. Extending the portal to matters is the
cheapest retention feature available.

**CEO-5 (Medium) — the upselling mechanism is cosmetic.** QA finding **F-6** (still open) says
module/plan gating is dead code. A multi-tenant SaaS that cannot enforce which modules a firm paid for
cannot price by tier. Either make gating real or stop advertising plan tiers.

**CEO-6 (Medium) — scope is wide for a pre-launch product.** Kanban, renewals, project credentials,
and a global dashboard are built while time tracking, trust accounting and payments are not. Breadth
in low-margin areas with no revenue loop is the classic pre-PMF failure mode.

### 0C. Dream state mapping

```
  CURRENT STATE                      THIS PLAN (ship)               12-MONTH IDEAL
  Nepal-specific wedge built         Harden + launch to first        The operating system for
  (scraper, BS dates, matters)  ---> paying firms; close P0s   ---> Nepali law firms: hearings
  Revenue loop missing               and open QA findings            auto-tracked, time captured,
  (no time capture, no trust)                                        invoices + trust money +
  Breadth > depth                                                    payments, client self-serve
```

### 0E. Mode selection

**Mode: HOLD SCOPE for launch, then SELECTIVE EXPANSION.** Do not add features before going live;
harden what exists. After launch, the only expansions worth attention are CEO-1 (time tracking) and
`future_ideas_tarikh.md` §7 (conflict-of-interest check — high ethical/liability value, low effort,
reuses the existing party-matching service).

### What to cut / defer

- Defer: `future_ideas_tarikh.md` §10 mobile app, §5 CRM/intake, §8 Kanban — all additive, none blocking.
- Do not invest further in project management breadth until the matter/billing loop is closed.

---

## 2. CTO lens — `/plan-eng-review`

### Architecture (as built)

```
  Frontend SPA (separate repo) ──HTTP──> Spring Boot 4.0.6 (Java 21, modular monolith)
                       │
                       ├── auth/  (JWT + MFA TOTP, refresh-token store keyed by jti)
                       ├── rbac/  (roles, permissions, modules, role-permission)
                       ├── firm/  ├── superadmin/  ├── tenant/  ├── customer/
                       ├── modules/ audit, casemangement, document, email,
                       │            invoice, me, notification, projectmanagement,
                       │            scraper, usermanagement
                       └── common/ (SystemConfigService, ConfigEncryptionUtil, exceptions)
                                  │
                       PostgreSQL (ddl-auto=update, hand-run SQL migrations)
                                  └── MinIO / S3-compatible (document store)
```

Layering is consistent (`Controller → Service interface → Impl → Mapper`) and the comment strip plus the
2026-10-08 fetch-graph pass show real care. 671 tests over 92 files is a healthy ratio for this size.

### Prime-directive compliance

| Directive | Status |
|---|---|
| Zero silent failures | **Partial** — `GlobalExceptionHandler` historically masked `BadCredentialsException`; `ConfigEncryptionUtil.decrypt` logs an ERROR that `EncryptedStringConverter` swallows |
| Error class + handler + test | Good — specific login reasons are tested, `MigrationConstraintDriftTest` guards enum drift |
| Happy / nil / empty / upstream paths | Good — nil/empty paths traced in the recent fetch-graph work |
| Dashboards, alerts, runbooks are launch scope | **Missing** — see ENG-3 |
| Rollback / flags / partial deploy | **Missing** — see ENG-4 |

### Findings

**ENG-1 (P0). Prod can start against the dev database with committed secrets.**
`application.yml` ships `spring.profiles.active: dev`, and `application-dev.yml` hard-codes the Supabase
URL, `postgres.fpqmxvcdglizgdymrelw`, the DB password, the JWT secret, the Gmail app password and the
registration secret. If prod starts without `SPRING_PROFILES_ACTIVE=prod`, it binds to the dev database.
`application-prod.yml` also sets `ddl-auto: ${DDL_AUTO}` with **no default**, so an unset `DDL_AUTO`
fails startup. Fix: profile must be mandatory, secrets must come only from env, and `DDL_AUTO` needs an
explicit value.

**ENG-2 (P0). No migration tool.** No Flyway or Liquibase; schema is `ddl-auto=update` plus hand-run
`.sql` files in `db/migration/`. This has already caused production-class outages twice
(`matter_timeline_event_type_check` on 2026-10-05, `mfa_secret` width on 2026-10-06) because Hibernate
update never ALTERs an existing CHECK. `MigrationConstraintDriftTest` is a good guard but not a
replacement for a real migration runner.

**ENG-3 (High). No production observability.** No Actuator, no Micrometer, no Prometheus; the only
reference is `show-actuator: false`. `Dockerfile.prod` has no `HEALTHCHECK`. In production the only
signal that something is wrong is a log line and an audit row. At minimum: `spring-boot-starter-actuator`
with `/health` + `/metrics`, a container healthcheck, and an alert on failed email/notification sends.

**ENG-4 (High). No CI.** No `.github/workflows/`, no `.gitlab-ci.yml`. 671 tests exist and run only when
someone types `./mvnw -o test` on one machine. Every regression the suite would catch can reach `devG`.
Add a pipeline that runs the suite on push and blocks merge on red.

**ENG-5 (High). Schedulers are not multi-instance safe.** `AppealDeadlineEngine`, `HearingReminderScheduler`,
`RenewalScheduler`, `TrialExpiryScheduler`, `ScraperScheduler` and `NotificationRetryScheduler` use plain
`@Scheduled` with no ShedLock/Quartz. The moment prod runs two instances (or a rolling deploy overlaps),
reminder emails and trial-expiry notices double-send. Gate them with ShedLock or run a single scheduler node.

**ENG-6 (Medium). Dead config keys lie to admins.** `STORAGE_UPLOAD_EXPIRY_SECONDS`,
`EMAIL_SIGNATURE` and `TIMEZONE` are declared in `ConfigKeyRegistry`, appear in the Settings UI, and are
read by nothing. `PASSWORD_RESET_URL` is read via `getOrDefault` but is not in the registry, so it can
never be set. Admins will change these and nothing will happen.

**ENG-7 (Medium). MFA enable/disable semantics surprise operators.** Login challenges on the per-user
`user.mfaEnabled` flag, so `MFA_ENABLED=N` does not stop challenges for existing admins. Verified live
against the dev DB. Document it, or make the global key authoritative.

**ENG-8 (Medium). Legacy plaintext MFA secret.** Live probe: `superadmin.mfa_secret` is a 32-char
plaintext Base32 value while `tlaw`'s is 80-char ciphertext. Every boot logs `AES decryption failed …
key has changed` because `EncryptedStringConverter` cannot decrypt it (and silently returns it).
Re-save the row so it re-encrypts.

**ENG-9 (Medium). Document upload accepts bytes through the app.** `Content-Length` up to the 60 MiB
Tomcat ceiling passes through the service on a single multipart call. That is a deliberate 2026-10-03
trade-off, but it puts every upload in JVM heap and makes the API a bandwidth bottleneck. Revisit if
firms upload large scans.

**ENG-10 (Low). `MasterData` cache is in-JVM (Ehcache).** Fine on one node; stale across nodes after a
scale-out. Move to a shared cache if horizontal scaling is on the roadmap.

---

## 3. QA lens — `/qa`

### Baseline

`./mvnw -o test` → **671 tests, 0 failures, 0 errors** (2026-10-12). Live probe of the configured DB
confirmed the config rows, no duplicate keys, and the `data`/profile assumptions in `checklist-prod.md`.
A full-journey E2E (`FullLifecycleE2ETest`) runs the SA → firm admin → employee → client path over real
HTTP against real PostgreSQL.

### Findings by severity (`docs/qa-report-2026-09-21.md` + this review)

**High**
- **QA-1.** MFA `123456` dev bypass is **live on the configured DB** right now (`APP_PRODUCTION=N`,
  no `MFA_DEV_BYPASS` row, dev yml arms it). Verified: submitting `123456` returns a real access token;
  setting `APP_PRODUCTION=Y` rejects it. Release-blocking if that DB is ever promoted.
- **QA-2.** No CI means "671 green" is a claim about one machine, not a guarantee about `devG`.

**Medium**
- **QA-3.** `F-6` module/plan gating is dead code (no enforcement).
- **QA-4.** `F-10` role change forces an unexplained logout the UI must handle.
- **QA-5.** Scheduler double-send (see ENG-5) is a user-visible correctness bug, not just a design smell.

**Low**
- **QA-6.** `F-11` `@PreAuthorize` runs after `@Valid`, so unauthorized callers get 400 not 403 (payload-shape disclosure).
- **QA-7.** `F-12` a new firm is inert: `FIRM_ADMIN` starts with zero permissions and zero modules.
- **QA-8.** `F-13` custom roles cannot use `hasRole('FIRM_ADMIN')` endpoints even with matching permissions.
- **QA-9.** `F-14` calendar/case reads require `CASE_MANAGEMENT:VIEW`, so an employee without it cannot see the calendar they are assigned to.
- **QA-10.** `F-15` follow-ups still owed: defence-in-depth in the authority builder, and cleanup of existing firm rows that carry a `SUPER_ADMIN`-coded role.

### Coverage debt

- No test asserts the **production profile** actually boots (that is exactly how ENG-1 stays hidden).
- No test asserts schedulers are single-fire.
- `STORAGE_UPLOAD_EXPIRY_SECONDS` / `EMAIL_SIGNATURE` / `TIMEZONE` have no test because they have no behaviour.
- Manual `docs/ui-test-checklist.md` sign-off is still outstanding.

---

## 4. CSO lens — `/cso` (evidence: static, severity / confidence / evidence)

**Scope note:** no runtime scanner or reproduction profile was run (`/cso` needs a qualified runtime
catalog + Docker, which is not available here). This is a **supported static** pass, not a full audit.

| ID | Sev | Confidence | Evidence | Finding |
|---|---|---|---|---|
| SEC-1 | High | Supported | `application-dev.yml` | Live DB credentials, JWT signing secret, Gmail app password and the registration secret are committed in git. Rotate all four and move them to env only. |
| SEC-2 | High | Supported | live DB probe | A legacy plaintext `mfa_secret` was observed on the platform super admin. It self-heals to ciphertext on the next save, but because `config.encryption.key` is committed, a DB read plus the repo still yields a valid TOTP seed (proven live: `docs/dev-profile-test-report.md` NEW-5). |
| SEC-3 | Medium | Supported | `application.yml:56` | `config.encryption.key` has a committed default (`rFBpF36+…`). If prod does not set `CONFIG_ENCRYPTION_KEY`, encrypted SMTP and registration secrets are protected by a public key. |
| SEC-4 | Medium | Supported | `SuperAdminConfigController` | `GET /super-admin/config` returns **decrypted** `SMTP_PASSWORD` and `REGISTRATION_SECRET`. By design, but it must stay behind `GLOBAL_CONFIG:VIEW` and never be logged. |
| SEC-5 | Medium | Supported | live DB probe + `TotpUtil` | The `123456` bypass is armed on the configured DB. Prod mode is the only thing stopping it, so `APP_PRODUCTION` becomes a single point of security failure. |
| SEC-6 | Low | Supported | `MFA_REQUIRED_ROLES` registry entry | `MFA_REQUIRED_ROLES` is unvalidated TEXT; a typo silently drops a role from enforcement. |
| SEC-7 | Low | Supported | `AuthServiceImpl.forgotPassword` | Password-reset link TTL is a hard-coded `int validMinutes = 15`, unlike every other runtime knob. |

No tenant-boundary escape, injection, or privilege-escalation finding was supported in this static pass.
The 2026-09-21 pass found and fixed F-15 (a genuine firm→SUPER_ADMIN escalation); its two follow-ups
(QA-10) remain open.

---

## 5. Verdict and what to do

**Overall: NO-GO for a production cutover today. GO once the P0s below are closed and re-verified.**

| Priority | Action | Lens | Reference |
|---|---|---|---|
| 1 | Make `SPRING_PROFILES_ACTIVE=prod` mandatory; remove secrets from `application-dev.yml`; set `DDL_AUTO` explicitly; rotate the four leaked secrets | CTO/CSO | ENG-1, SEC-1 |
| 2 | Verify on the prod DB: `APP_PRODUCTION='Y'`, no `MFA_DEV_BYPASS` row | QA/CSO | QA-1, SEC-5 |
| 3 | Set `CONFIG_ENCRYPTION_KEY` in prod env — the committed key currently decrypts stored MFA secrets | CSO | SEC-2, SEC-3, NEW-5 |
| 4 | Add a CI pipeline running `./mvnw -o test` on push and blocking red | QA | QA-2, ENG-4 |
| 5 | Add Actuator `/health` + `/metrics` and a container `HEALTHCHECK` | CTO | ENG-3 |
| 6 | Adopt Flyway (or an equivalent migration runner) for `db/migration/` | CTO | ENG-2 |
| 7 | Guard schedulers with ShedLock before any multi-instance deploy | CTO | ENG-5 |
| 8 | Remove or wire up the four dead config keys | CTO | ENG-6 |
| 9 | Build `TimeEntry` → `InvoiceItem` (the revenue loop) | CEO | CEO-1 |
| 10 | Make module/plan gating real, or drop plan tiers from the pitch | CEO | CEO-5, QA-3 |

**Straight answer:** the Nepal-specific engineering (`scraper`, BS dates, matter model) is the product's
real asset and it is genuinely good. The blockers are not features, they are *operational*: the app can
boot against the dev database with secrets in git, there is no CI, no health endpoint, no migration
runner, and a dev MFA bypass that is one config row away from being live. Close those five and this is
shippable. Then build time tracking, because right now the product cannot measure what it bills.

---

*Review only. Nothing in this document was implemented. Baseline: 671 tests green, 2026-10-12.*
