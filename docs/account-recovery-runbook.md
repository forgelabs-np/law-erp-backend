# Account recovery runbook — locked, blocked, deactivated, suspended

**Question this answers:** *"An account is locked — how do I get it back in? Do I have to touch the
database? Is there another way?"*

**Short answer:** for a **password lockout**, no — there are five ways out that do not need the database
(one of them is a two-minute wait). The database is the last resort, and it is only genuinely required for
a **locked, token-less, sole super admin**, or for the two states below that no API can clear.

Everything here was read from the code and verified against the live dev database on 2026-10-12.

---

## 1. Four different things all read as "the account is locked"

| State | Stored in | What the user sees | Time-based? | Can an API clear it? |
|---|---|---|---|---|
| **① Password lockout** | `users.login_attempts`, `users.locked_until` | `401 "Account locked. Try again in 29 minute(s)."` (internal login) · `400 "Account locked. Please try again later."` (super-admin login) | **yes** — expires on its own | ✅ five ways (§2) |
| **② Blocked** | `users.is_blocked = true` | `"Account is blocked. Please contact system administrator."` | no | ❌ **no API and no code path sets or clears it** (`setIsBlocked` appears nowhere in the codebase; only the entity field and a `false` on user creation) |
| **③ Deactivated** | `users.is_active = false` | `"Account is disabled. Please contact system administrator."` | no | ⚠️ partially — see §4 |
| **④ Firm suspended/expired** | `firms.status` | `"Your firm account has been suspended. Please contact support."` — **every** account in that firm | no | ✅ super-admin API |

Rule of thumb: **if the message names minutes, it is ① and it fixes itself.** If it names a system
administrator, it is ②/③/④ and someone must act.

---

## 2. Password lockout (①) — five ways back in, database not required

Seeded policy: `LOGIN_MAX_ATTEMPTS = 5`, `LOGIN_LOCK_MINUTES = 30` (both are live runtime config keys —
changing them takes effect on the next request). The lock is checked **before** the password is verified,
so a locked account cannot burn more attempts, and a later successful login resets both counters to zero.

### Way 1 — Wait
`locked_until = now + LOGIN_LOCK_MINUTES` (30 min by default). When it passes, the next login with the
correct password succeeds and clears `login_attempts`. Zero effort, no admin, no database.

### Way 2 — Unlock endpoint (added 2026-10-12; verified live)

```bash
# Super admin — any user, any firm
curl -X POST "$BASE/api/v1/super-admin/users/{userId}/unlock" -H "Authorization: Bearer <SA_TOKEN>"
#   → {"success":true,"message":"Lockout lifted. The user can sign in again."}

# Firm admin — only users of its own firm
curl -X POST "$BASE/api/v1/modules/users/{userId}/unlock" -H "Authorization: Bearer <FIRM_ADMIN_TOKEN>"
```

Clears `login_attempts` and `locked_until`, leaves password / MFA / blocked / active untouched, writes a
`USER_UNBLOCKED` audit row. Live result: `attempts 5 → 0`, `locked_until → NULL`, and the released
employee signed in immediately.

### Way 3 — An admin password reset (pre-existing, also clears the lock)
```bash
POST /api/v1/super-admin/users/{userId}/reset-password     # super admin
POST /api/v1/modules/users/{userId}/reset-password         # firm admin, own firm
```
Side effect: the password is replaced and the user must rotate it on first login. Use it when you want to
unlock *and* hand over new credentials.

### Way 4 — Self-service reset (user does it themselves)
`POST /api/v1/auth/forgot-password` → emailed link → `POST /api/v1/auth/reset-password`.
The reset **clears the lockout and revokes existing sessions**. The account only has to be active and not
blocked — a lockout does **not** block recovery. (Careful: `forgot-password` always reports success, so
you cannot tell from the response whether the mail went out.)

### Way 5 — You already hold a valid token
A lockout gates **login only**. Any access/refresh token already issued keeps working until it expires, and
`POST /api/v1/me/change-password` clears your own lock. Practically: a locked super admin who still has a
live token can unlock itself with Way 2 (verified live: 200, counters reset).

---

## 3. The super admin specifically

The platform allows exactly **one** super admin — a second registration is refused ("Super admin already
exists"), so a locked-and-token-less SA cannot be rescued by creating another admin. In order of preference:

| # | Action | Works when |
|---|---|---|
| 1 | **Wait `LOGIN_LOCK_MINUTES`** (30 min) and sign in | always |
| 2 | **Self-service reset.** The SA account lives in the internal firm `SYSTEM` (status `ACTIVE`) with a real email — so `lawFirmCode: "SYSTEM"`, `username: "superadmin"` on `/api/v1/auth/forgot-password` issues a link, and `/api/v1/auth/reset-password` clears the lock | mail delivery works |
| 3 | **Use an unexpired existing SA token** to call `/api/v1/super-admin/users/{userId}/unlock` on its own id | you have one (app installed, token not expired) |
| 4 | **Database** (below) | last resort |

### The database statement (last resort)

```sql
-- ① password lockout — this is the entire fix
UPDATE users
SET login_attempts = 0,
    locked_until   = NULL
WHERE username = 'superadmin';       -- or WHERE id = '<uuid>'
```

Nothing else has to be touched: permissions come from the JWT, so a lock lift needs no version bump, role
change or cache clear — the next login simply succeeds. Verified on the dev database: the statement is
valid and a lock-out/lock-in cycle clears exactly as described.

Handy lookups:

```sql
SELECT username, user_type, is_active, is_blocked, login_attempts, locked_until
FROM users
WHERE username IN ('superadmin','tlaw','tlaw_emp1');

SELECT config_key, config_value FROM system_config
WHERE config_key IN ('LOGIN_MAX_ATTEMPTS','LOGIN_LOCK_MINUTES','MFA_ENABLED');
```

> **Recommended before go-live:** either keep the SA's email reachable + SMTP working (so Way 2 is real),
> or write down the SQL above as the break-glass step in the ops runbook. Today a locked, token-less sole
> SA has no self-service path *through the app* that does not depend on email.

---

## 4. Blocked (②) and deactivated (③)

### Blocked — `is_blocked = true`
- Login is refused at the account-state check. It is never set by any application code path — no endpoint
  blocks a user — so in practice it is only reachable by a manual database edit.
- There is **no unblock API**. Clear it in the database:
  ```sql
  UPDATE users SET is_blocked = false WHERE username = '<username>';
  ```
  `POST …/unlock` deliberately does **not** touch this flag (so a lock lift can never silently re-enable an
  account an admin blocked on purpose).

### Deactivated — `is_active = false`
| User type | Reactivate via API | Endpoint |
|---|---|---|
| Employee | ✅ firm admin | `PATCH /api/v1/firm/employees/{employeeId}/toggle` |
| Firm admin user | ✅ super admin | `PATCH /api/v1/super-admin/firms/admins/{adminId}/toggle` |
| Client | ❌ **no endpoint** | only `POST /api/v1/modules/users/bulk-deactivate` exists — no activate counterpart → database: `UPDATE users SET is_active = true WHERE …` |

Deactivation happens through firm-admin user management (`POST /api/v1/modules/users/bulk-deactivate`,
`DELETE /api/v1/modules/users/{userId}` —
both soft, they set `is_active = false`).

### Firm suspended or expired — `firms.status`
`PUT /api/v1/super-admin/firms/{firmId}/suspend` and `PUT /api/v1/super-admin/firms/{firmId}/activate`.
While suspended/expired, every login in that firm is refused with the firm-level message, whatever the
individual account state says. Fix the firm, not the user.

---

## 5. Quick decision tree

```
Locked out?
├─ message names minutes ──────────── ① wait, or unlock endpoint, or reset ──► back in
├─ "Account is blocked" ──────────── ② UPDATE users SET is_blocked = false   (no API)
├─ "Account is disabled" ─────────── ③ toggle endpoint for employee/firm-admin;
│                                       DB for a client
├─ "firm account has been suspended"  ④ activate the firm (super admin)
└─ sole super admin, no token, no mail ──► wait 30 min, else the ① SQL above
```
