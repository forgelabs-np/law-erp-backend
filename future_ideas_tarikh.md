# Future Ideas for Tarikh — Feature Gap Reference

Benchmarked against established legal practice management platforms (Clio, MyCase, PracticePanther, Filevine, SmartAdvocate). Each section explains what the feature is, why competitors treat it as core, how it's typically built, and a suggested approach given Tarikh's existing Spring Boot + Postgres + multi-tenant architecture.

---

## 1. Trust / IOLTA Accounting

**What it is:** A segregated accounting system for client funds held "in trust" (retainers, settlement funds) — kept completely separate from the firm's operating account, with strict rules about which transactions are allowed.

**Why it matters:** In most jurisdictions, mishandling trust funds is a bar-discipline issue, not just a bookkeeping error. It's one of the few features that shows up as "must-have" across every competitor (Clio, MyCase, CosmoLex all lead with it). Nepal's regulatory framework may differ from the US IOLTA model, but the underlying need — client money kept separate and fully auditable — is universal.

**How it's typically built:**
- A separate ledger entity (`TrustAccount`, `TrustTransaction`) distinct from the invoice/billing tables
- Every transaction tied to a specific client + matter, never a pooled/anonymous balance
- Reconciliation reports (three-way reconciliation: bank statement vs. ledger vs. per-client balances)
- Hard validation: you should never be able to deposit trust funds into the operating account through the app, and never let a client's trust balance go negative

**Suggested approach for Tarikh:** New module alongside `invoice/` — `trust/` — with its own audit trail (you already have the audit infrastructure, so hook into `AuditAspect`). Research Nepal Bar Council rules specifically before designing the ledger rules, since this shouldn't just copy US IOLTA conventions.

**Complexity:** Medium-high — the accounting logic itself is simple, but getting the compliance rules right requires legal research, not just engineering.

---

## 2. Time Tracking Tied to Billing

**What it is:** Timers or manual time entries per matter, categorized by activity (research, drafting, court appearance), which flow directly into invoice line items.

**Why it matters:** Competitors emphasize this heavily because billable-hour disputes are one of the top sources of client friction — accurate, timestamped entries protect both the firm and the client. Tarikh currently has invoice generation but nothing that captures *how* the invoice line items were determined.

**How it's typically built:**
- A `TimeEntry` entity: user, matter, start/end or duration, activity type, billable rate (which can vary by attorney seniority or matter type), narrative description
- A "start/stop timer" UI concept, plus manual entry for retroactive logging
- A step that converts approved time entries into `InvoiceItem` rows rather than requiring manual re-entry
- Rounding rules (some firms round to nearest 6 or 15 minutes) — configurable per firm

**Suggested approach for Tarikh:** Sits naturally as a submodule of `invoice/`. Reuse your existing firm-scoped rate/permission patterns. This is a good candidate for an early build since it directly increases revenue capture (unbilled time is lost time).

**Complexity:** Medium.

---

## 3. Document Management & Automation

**What it is:** Version-controlled storage for case documents (pleadings, contracts, correspondence), template-based document generation (auto-fill a pleading template with matter/party data), and often e-signature integration.

**Why it matters:** Beyond invoices, lawyers generate dozens of documents per matter. Competitors like Clio and Smokeball differentiate heavily on document automation because it saves the most billable-adjacent time (a paralegal manually retyping party names into templates is pure waste).

**How it's typically built:**
- Object storage (S3-compatible) for raw files, with metadata rows in Postgres (filename, matter link, version, uploader, timestamp)
- Template engine — you already use Thymeleaf for PDF generation via OpenHTMLtoPDF, which can be extended: build a `DocumentTemplate` entity with placeholder tokens (`{{party.plaintiff.name}}`) resolved from matter/party data at generation time
- Version history — never overwrite, always append a new version and keep the chain
- Optional: e-signature via a third-party API (DocuSign, or a lighter regional alternative) — check what's actually usable/affordable in Nepal before committing to a vendor

**Suggested approach for Tarikh:** You already have the PDF pipeline for invoices — generalize it into a document module rather than building a second, separate templating system.

**Complexity:** Medium for storage + templating; higher if you add e-signature and version diffing.

---

## 4. Client Portal (Self-Service)

**What it is:** A restricted-access view where clients log in to see their matter's status, upload documents, and message the firm — without seeing internal firm data.

**Why it matters:** Reduces the volume of "what's happening with my case?" phone calls and emails, and is now a baseline client-experience expectation. You already have a `Client Portal` line in your feature list, but it's currently framed as project-only (project management module) rather than covering matters/cases broadly.

**How it's typically built:**
- A separate, tightly-scoped authentication role (`CLIENT`) with row-level restriction to only their own matter(s)
- A cut-down UI (or a separate small frontend) that surfaces status, upcoming hearing dates, and documents shared with them explicitly (never everything in the case file — staff should mark documents "client-visible" deliberately)
- Secure messaging thread per matter

**Suggested approach for Tarikh:** Extend the existing multi-tenant + RBAC system with a client-scoped role rather than building fully separate auth. The main design risk is accidentally leaking privileged/internal notes to a client-facing endpoint — worth a dedicated security test (see the tenant-isolation test suggestion from before, but for client-vs-staff visibility instead of firm-vs-firm).

**Complexity:** Medium — mostly about careful scoping of what's exposed, not new technology.

---

## 5. CRM / Client Intake

**What it is:** Capturing a prospective client before they're an actual client — intake forms, lead tracking, conflict check (see #7), and conversion into a formal matter once the firm accepts the case.

**Why it matters:** Firms lose track of inbound inquiries without this. Clio Grow and PracticePanther's intake tools exist specifically because "a lead came in via phone/email and nobody followed up" is a common, costly failure mode.

**How it's typically built:**
- A `Lead`/`Intake` entity that's intentionally lighter-weight than `Matter` (fewer required fields, no case number yet)
- A public-facing intake form (could be a simple embeddable web form hitting a REST endpoint) that creates a `Lead` automatically
- A "convert to matter" action once the firm decides to take the case — this is also the natural point to run a conflict check

**Suggested approach for Tarikh:** Lower priority than the accounting/time-tracking pieces unless client acquisition volume is currently a pain point for your target firms.

**Complexity:** Low-medium.

---

## 6. Online Payment Processing

**What it is:** Letting clients pay invoices directly (card/bank transfer) from a link or portal, rather than the firm generating a PDF and waiting for a manual bank transfer.

**Why it matters:** Speeds up collections significantly — competitors integrate with payment processors like LawPay specifically because faster payment cycles improve firm cash flow.

**How it's typically built:**
- Integration with a local Nepali payment gateway (eSewa, Khalti, or a bank's API) rather than assuming Stripe/LawPay availability
- A payment status field on `Invoice` that updates via webhook when payment clears
- Critically: payment processing integrations must never touch the trust-account ledger without going through the same compliance rules as #1

**Suggested approach for Tarikh:** Research which Nepali payment gateways offer developer APIs suitable for B2B invoice collection (this differs meaningfully from consumer checkout flows) before designing the integration.

**Complexity:** Medium — mostly integration and compliance work, not novel engineering.

---

## 7. Conflict-of-Interest Checking

**What it is:** Before onboarding a new client or matter, searching existing parties/clients/matters to flag any prior relationship that could create a legal or ethical conflict (e.g., the firm previously represented the opposing party).

**Why it matters:** This is an ethical obligation in most legal systems, not just a nice-to-have. You already have a "Party Matching Service" for scraper data — the same fuzzy-matching concept applies here, just against your own client/party database instead of scraped court data.

**How it's typically built:**
- A search across all historical parties (plaintiffs, defendants, witnesses, related entities) by name, with fuzzy matching to catch spelling variants
- A mandatory "conflict check performed" step gating matter creation, with the result logged (for audit purposes — if a conflict is later alleged, the firm needs proof they checked)

**Suggested approach for Tarikh:** You likely already have most of the underlying data model (parties, matters) — this is largely a matter of building a dedicated search/flagging workflow on top of what exists, and reusing your existing party-matching logic.

**Complexity:** Low-medium, and higher value than its complexity suggests given the ethical/liability angle.

---

## 8. Kanban / Visual Workflow Boards

**What it is:** A drag-and-drop board view of matters or tasks by stage (e.g., Intake → Discovery → Trial Prep → Closed), as an alternative to list/table views.

**Why it matters:** Several competitors (Rocket Matter notably) highlight this because it gives an at-a-glance sense of firm-wide case load and bottlenecks that a table view doesn't communicate as quickly.

**How it's typically built:**
- No new backend data model needed if your `MatterStatus`/`ProjectStatus` fields already exist — this is primarily a frontend rendering concern (drag-and-drop reordering, columns per status)
- Backend just needs an endpoint to update status on drag, ideally with audit logging of stage transitions

**Suggested approach for Tarikh:** Cheapest feature on this list to add if your status fields are already well-modeled — almost pure frontend work.

**Complexity:** Low.

---

## 9. Reporting & BI Dashboards

**What it is:** Aggregated analytics beyond a basic project dashboard — revenue per attorney, matter profitability (time spent vs. billed vs. collected), invoice aging, caseload distribution.

**Why it matters:** Firm leadership needs this to make staffing and growth decisions; "well-organized data offers a clear picture of a firm's operations, providing insight into its strengths and weaknesses."

**How it's typically built:**
- Depends on time tracking (#2) and trust accounting (#1) existing first, since most useful reports are derived from that data
- Aggregation queries (materialized views or scheduled rollup jobs are common at scale, to avoid slow live aggregation on large datasets)
- A charting frontend layer — this pairs naturally with whatever dashboard framework you're already using for the existing Global/Project dashboards

**Suggested approach for Tarikh:** Sequence this *after* #2 (time tracking) and #1 (trust accounting) — building reports before the underlying data exists means retrofitting later.

**Complexity:** Medium, but complexity scales with how much of the underlying data model is already there when you get to it.

---

## 10. Mobile App

**What it is:** A native or cross-platform mobile client, since attorneys are frequently in court or traveling and need to check hearing schedules or log time on the go.

**Why it matters:** Baseline expectation among competitors, though the ROI depends heavily on your specific user base's habits — worth validating demand with actual users before committing engineering time.

**How it's typically built:**
- Since Tarikh already exposes a REST API (Swagger-documented), a mobile app is a separate client consuming the same API — no backend rework needed beyond making sure endpoints are mobile-friendly (pagination, payload size)
- React Native or Flutter are the common cross-platform choices to cover both iOS and Android from one codebase

**Suggested approach for Tarikh:** Lowest priority of the list — your API-first architecture means this can be deferred indefinitely without blocking anything else; it's additive whenever there's bandwidth.

**Complexity:** Medium-high, but isolated — doesn't entangle with other backend work.

---

## Suggested rough sequencing

1. **Time tracking** (#2) — directly increases revenue capture, moderate effort
2. **Conflict-of-interest checking** (#7) — high value relative to effort, reuses existing party-matching logic
3. **Kanban view** (#8) — cheap win if status fields already exist
4. **Trust accounting** (#1) — higher effort but likely necessary for firms to trust the platform with real client funds
5. **Document management & automation** (#3) — generalizes your existing PDF pipeline
6. **Client portal expansion** (#4) — extend existing project-portal concept to matters generally
7. **Reporting/BI** (#9) — sequence after #1 and #2 so there's real data to report on
8. **Online payments** (#6) — requires research into Nepal-specific payment gateway APIs
9. **CRM/intake** (#5) — lower priority unless lead volume is a current pain point
10. **Mobile app** (#10) — defer until core web feature set is mature

*This is a reference/planning document, not a committed roadmap — priorities should shift based on actual user feedback from firms using Tarikh.*
