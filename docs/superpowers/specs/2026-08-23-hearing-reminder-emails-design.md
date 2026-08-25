# Hearing Reminder Emails — Design

**Date:** 2026-08-23
**Status:** Approved by user (2026-08-23) — PESHI only, idempotent log table included
**Context:** Go-live requirement from E2E test report (§5 item 6): "No email exists for 'hearing tomorrow' to client + assigned advocate. Required per product spec."

## Goal

Every morning, send a T-1 reminder email about tomorrow's hearings to:
1. The **assigned advocate** (event.attendingAdvocateId)
2. Every **linked client** (matter parties with `isOurClient=true` + `clientId`)

Delivered via each firm's own SMTP config (firm config → global DB config → default sender fallback), using the existing `EmailService.sendHtmlEmail` machinery (Thymeleaf, @Async, EMAIL_SENT/EMAIL_FAILED audit).

## Decisions (user-approved)

- **Event type:** PESHI only (actual hearings). Tarik is a diary date, no client email.
- **Idempotency:** `hearing_reminder_log` table, unique `(court_event_id, recipient_type, recipient_email, scheduled_date)` — re-runs and restarts never duplicate emails, and the log doubles as testable evidence.
- **Timing:** `@Scheduled(cron = "0 0 8 * * *")` daily (same as RenewalScheduler), server-local time.
- Events must be `status = SCHEDULED` and `scheduledDate = tomorrow`.

## Components

1. **`HearingReminderScheduler`** (modules/casemanagement/service) — thin `@Scheduled` wrapper around the service method.
2. **`HearingReminderService` / `HearingReminderServiceImpl`** — `sendRemindersForDate(LocalDate date)`:
   - Find PESHI/SCHEDULED events for the date
   - Batch-load court cases → matters → our-client parties → client users, and advocate users (no N+1)
   - Per recipient: insert SENT claim row (unique constraint = skip on conflict) → dispatch async email with the log row id
   - Per-event try/catch so one failure never stops the batch
3. **`HearingReminderLog`** entity + repository — JPA entity only; schema is Hibernate `ddl-auto=update` (no Flyway dependency in the project). Columns: id, courtEventId, recipientType (CLIENT/ADVOCATE), recipientEmail, scheduledDate, status (SENT/FAILED), createdAt; unique index on the four-key tuple.
4. **`EmailService.sendHearingReminder(firmId, logId, toEmail, fullName, recipientType, details…)`** — @Async; renders `email/hearing-reminder.html`; on send failure flips the log row to FAILED (in addition to the existing EMAIL_FAILED audit row).
5. **Template `email/hearing-reminder.html`** — same style as welcome-client.html; shows firmName, fullName, matter number/title, court ref, court name, date/time, courtroom, judge; CTA link differs by recipient (client → portal URL, advocate → login URL); primaryColor/footer from SystemConfig.

## Audit & error handling

- `AuditLog.userId` is NOT NULL; scheduler has no authenticated user → audit rows record the **recipient's** user id with userTypeChar "S" (summary states it was a system-generated reminder).
- No SMTP config → existing fallback path → EMAIL_FAILED audit + log row FAILED; job continues.
- Missing recipient email (advocate unassigned / no our-client party) → debug log, no log row, no email.

## Testing

- Unit: `HearingReminderServiceImplTest` — recipient resolution, claim-before-send, skip on duplicate claim, CANCELED excluded, batch resilience.
- Live (dev DB): schedule a PESHI for tomorrow with advocate + our-client party, invoke the job once, verify log rows + EMAIL_SENT/EMAIL_FAILED evidence, then clean up. Real delivery requires real SMTP creds (known dev limitation).
