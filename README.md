# ResolveX — IT Service Management / Incident Management Platform

A production-oriented incident management platform for three roles: **Employee**,
**Support Agent** and **Admin**.

This repository is being built **incrementally**. What is described under
"Current state" is what actually exists and runs today; everything else is
planned and explicitly not implemented yet.

---

## Stack

| Layer     | Technology                                                              |
| --------- | ----------------------------------------------------------------------- |
| Frontend  | React 18, TypeScript, Vite 6, React Router 6, Axios                      |
| Backend   | Java 21, Spring Boot 3.3, Maven, Spring Security, Spring Data JPA, JJWT  |
| Database  | H2 for local development and tests; PostgreSQL + Flyway behind a profile |
| Design    | Supplied design language reference — one token module, `frontend/src/lib/tokens.ts` |

## Layout

```
ITSM/
├── backend/            Spring Boot API (Maven)
│   └── src/main/java/com/rewin/itsm/
│       ├── common/     BaseEntity, ApiError, exceptions, global handler
│       ├── config/     SecurityConfig, JpaConfig, DevDataSeeder
│       ├── domain/     enums + entities (user, team, incident, sla)
│       ├── dto/        request and response records (auth, incident, admin)
│       ├── health/     GET /api/health
│       ├── repository/ Spring Data JPA repositories
│       ├── security/   JWT service, filter, principal, 401/403 handlers
│       ├── service/    AuthService, IncidentService, SlaService, AdminService
│       └── web/        REST controllers
├── frontend/           React + TypeScript app (Vite)
│   └── src/
│       ├── api/        Axios instance and typed API calls
│       ├── app/        App root, router, role home
│       ├── auth/       Auth context, provider, useAuth, ProtectedRoute
│       ├── components/ layout shell + presentational ui kit
│       ├── lib/        tokens.ts (design truth) and shared types
│       ├── pages/      Route-level screens, grouped by role
│       └── styles/     Base layer CSS only
├── docker-compose.yml  PostgreSQL 16 for local development
└── README.md
```

---

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+ and npm
- PostgreSQL 16, or Docker for the bundled `docker-compose.yml`

---

## Running the project

### 1. Backend

```bash
cd backend
mvn spring-boot:run
```

Serves `http://localhost:8080`. That is the whole setup — **no Docker, no
database server, no credentials**. Spring Boot starts H2 in process and
Hibernate maintains the schema.

The data lives in `backend/data/itsm.mv.db`, so the incidents you create while
working are still there after a restart. Delete that folder for a clean slate,
or set `DB_URL=jdbc:h2:mem:itsm;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` for a database
that is empty every time.

Browse the data at `http://localhost:8080/h2-console` (JDBC URL
`jdbc:h2:file:./data/itsm`, user `sa`, no password).

Verify:

```bash
curl http://localhost:8080/api/health
```

### 2. Running against PostgreSQL (optional)

Local development does not need this, and nothing in the application requires
it. It exists for a deployed environment that wants PostgreSQL:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

That profile points at `DB_URL` (default `jdbc:postgresql://localhost:5432/itsm`),
runs the Flyway migrations, and sets Hibernate to `validate`. It was verified end
to end against PostgreSQL 16; see **Database** below.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Serves `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8080`,
so the frontend never hardcodes a backend origin — the same relative paths work
behind a reverse proxy in production. Override with `VITE_API_PROXY_TARGET`
(see `frontend/.env.example`).

---

## Build and test

```bash
cd backend  && mvn clean verify     # compiles, runs tests, packages the jar
cd frontend && npm run build        # tsc -b (strict typecheck) then vite build
cd frontend && npm run typecheck    # typecheck alone
```

Backend tests run against in-memory H2 under the `test` profile, so CI needs no
PostgreSQL and the suite leaves nothing behind. 103 tests cover login for all three roles, rejected
credentials, anonymous and garbled-token access, admin-only endpoints, employee
ownership rules, agent triage through resolution and closure, illegal status
transitions, resolve-without-summary, close-before-resolve, reopen clearing the
resolution, the full activity timeline, queue filters, and the rule that a
filter cannot widen a role scope. A dedicated SLA suite covers per-priority
deadlines, all four statuses including a genuine breach, response-clock rules,
role-based SLA visibility and policy administration. A comments-and-audit
suite covers per-role commenting, whitespace rejection, internal-note
visibility, every recorded action with its generated description, ordering,
paging metadata, and that the trail exposes no write or delete route. A
scheduler suite drives the sweep against a controllable clock: on-track,
at-risk and breached transitions, one activity per stage across repeated
passes, answered responses and resolved incidents never breaching, closed
incidents skipped, and the sweep persisting what it decided. An admin suite
covers user creation with a hashed password, duplicate emails, profile edits
that leave the credential alone, deactivation blocking login and assignment,
list filtering and paging, team CRUD and membership, policy validation, an
existing deadline surviving a policy edit, the absence of any delete route, and
every admin endpoint refusing an employee and an agent. A notification and
paging suite covers password reset (hashing, non-return, RBAC, validation),
notification delivery and the rule that nobody is told about their own action,
one user being unable to read or touch another's notifications, mark-read and
mark-all-read, repeated events not repeating notifications, and page totals
combined with filters.

---

## Database

| | Local development | Automated tests | `postgres` profile |
| --- | --- | --- | --- |
| Engine | H2, file-backed | H2, in memory | PostgreSQL 16 |
| Schema created by | Hibernate | Hibernate | Flyway migrations |
| `ddl-auto` | `update` | `create-drop` | `validate` |
| Flyway | disabled | disabled | enabled |

**Local development is H2 and nothing else.** No Docker, no database server, no
external credentials — `mvn spring-boot:run` is the whole story. Tests use H2 in
memory, so the suite needs nothing either and leaves nothing behind.

**PostgreSQL lives behind the `postgres` profile** and is not part of the local
workflow. It is kept because the migrations in
`backend/src/main/resources/db/migration` are real, were verified against
PostgreSQL 16, and are what a deployed environment will want.

### Migrations

`V1__initial_schema.sql` is the whole schema, written from the DDL Hibernate
derives from the entity model. Under the `postgres` profile, Flyway owns the
schema and Hibernate only validates it, so a mapping that has drifted from the
migrations fails at startup rather than at the first query.

The rules, when that profile is in use:

- **Never edit a migration that has run anywhere.** Flyway records a checksum;
  changing the file makes it refuse to start against that database.
- **Every schema change is a new file** — `V2__add_something.sql`, then `V3__…`.
- **Never delete a migration.** The history is how an existing database knows
  where it is.

Local H2 development does not run the migrations, so a schema change during
development means updating the entity *and* adding a migration for the profile
that uses them.

### Schema

Eight tables: `users`, `teams`, `sla_policies`, `incidents`, `incident_comments`,
`incident_activities`, `notifications`, `admin_audit_entries`.

Conventions: UUID primary keys generated by the application; `created_at`,
`updated_at` and `version` on every table; instants stored with a timezone, so a
deadline cannot shift with a server's; enums stored as their names.

Under PostgreSQL those are backed by database constraints as well as service
checks — unique email, unique team name, unique incident reference, every foreign
key, positive SLA times with the response no longer than the resolution, a
non-blank comment, a required notification recipient — plus indexes chosen from
the queries that exist, including partial indexes for the SLA sweep and the
unread-notification badge.

---

## Demo accounts (dev profile only)

Seeded on first start, and skipped if they already exist.

| Email                 | Role          | Team           |
| --------------------- | ------------- | -------------- |
| `admin@itsm.local`    | ADMIN         | —              |
| `agent@itsm.local`    | SUPPORT_AGENT | Infrastructure |
| `employee@itsm.local` | EMPLOYEE      | —              |

**Development password for all three: `Password123!`**

The seeder is restricted to the `dev` profile, so it cannot run in staging or
production. Passwords are BCrypt-hashed like any other; the plain
value never reaches the database. Change this password before any shared
deployment.

---

## API

| Method | Path                           | Who                    |
| ------ | ------------------------------ | ---------------------- |
| POST   | `/api/auth/login`              | public                 |
| GET    | `/api/auth/me`                 | any authenticated user |
| GET    | `/api/health`                  | public                 |
| GET    | `/api/incidents`               | all roles, scoped, paged |
| POST   | `/api/incidents`               | all roles              |
| GET    | `/api/incidents/{id}`          | all roles, scoped      |
| PUT    | `/api/incidents/{id}`          | agent, admin           |
| PATCH  | `/api/incidents/{id}/status`   | agent, admin           |
| PATCH  | `/api/incidents/{id}/assign`   | agent, admin           |
| PATCH  | `/api/incidents/{id}/resolve`  | agent, admin           |
| PATCH  | `/api/incidents/{id}/close`    | agent, admin           |
| GET    | `/api/incidents/{id}/comments` | all roles, scoped      |
| POST   | `/api/incidents/{id}/comments` | all roles, scoped      |
| GET    | `/api/incidents/{id}/activities`| all roles, scoped     |
| GET    | `/api/users`                   | admin (paged, filterable) |
| GET    | `/api/users/{id}`              | admin                  |
| POST   | `/api/users`                   | admin                  |
| PUT    | `/api/users/{id}`              | admin                  |
| PATCH  | `/api/users/{id}/active`       | admin                  |
| POST   | `/api/users/{id}/reset-password`| admin                 |
| GET    | `/api/agents`                  | agent, admin           |
| GET    | `/api/teams`                   | any authenticated user |
| POST   | `/api/teams`                   | admin                  |
| PUT    | `/api/teams/{id}`              | admin                  |
| PATCH  | `/api/teams/{id}/active`       | admin                  |
| GET    | `/api/teams/{id}/members`      | admin                  |
| GET    | `/api/sla-policies`            | any authenticated user |
| POST   | `/api/sla-policies`            | admin                  |
| PUT    | `/api/sla-policies/{id}`       | admin                  |
| PATCH  | `/api/sla-policies/{id}/active`| admin                  |
| GET    | `/api/reports`                 | admin                  |
| GET    | `/api/admin/audit`             | admin                  |
| GET    | `/api/notifications`           | own notifications only |
| GET    | `/api/notifications/unread-count`| own notifications only |
| PATCH  | `/api/notifications/{id}/read` | own notifications only |
| PATCH  | `/api/notifications/read-all`  | own notifications only |

"Scoped" means the role gate lets the call through but the service filters by
row: an employee sees only incidents they reported, an agent sees their team
queue plus unassigned new incidents, an admin sees everything.

`GET /api/incidents` accepts optional `status`, `priority`, `teamId`,
`slaStatus` and `search` query parameters, plus `page` and `size`. Filters are
applied in the same query as the role scope, so a filter can only narrow what a
role may see, never widen it — and paging happens in the database, not after
loading everything.

**It returns the paged envelope**, not a bare array. That changed in Phase 8 and
is a breaking change for any caller written against the earlier shape.

### Lifecycle

```
NEW ──assign──▶ ASSIGNED ──▶ IN_PROGRESS ──resolve──▶ RESOLVED ──close──▶ CLOSED
                     ▲              │                     │
                     └──────────────┘                     │
                            ▲                             │
                            └───────── reopen ────────────┘
```

### Comments and the audit trail

`GET /api/incidents/{id}/comments` returns the thread oldest first;
`GET /api/incidents/{id}/activities` returns the audit trail newest first. Both
come back in a paged envelope (`content`, `page`, `size`, `totalElements`,
`totalPages`, `last`) and accept `page` and `size`. V1 does not need paging at
its volumes, but the shape is there from the start so turning it on later is not
a breaking change for callers.

A comment is rejected when empty **or whitespace-only**. The author travels as
id, name and role — never the account object, so no password field can be
serialised by accident. Internal notes are excluded from an employee's result
**in the query**, not filtered out afterwards.

Every state change appends an `IncidentActivity`:

| Action | Written when |
| --- | --- |
| `INCIDENT_CREATED` | an incident is reported |
| `INCIDENT_ASSIGNED` | an agent or a team is put on it for the first time |
| `INCIDENT_REASSIGNED` + `AGENT_CHANGED` | a different agent takes it over |
| `TEAM_CHANGED` | it moves between teams |
| `STATUS_CHANGED` | any status move, including the automatic NEW → ASSIGNED |
| `PRIORITY_CHANGED` | priority is edited |
| `INCIDENT_UPDATED` | another field, such as category, is edited |
| `COMMENT_ADDED` | a comment or internal note is posted |
| `INCIDENT_RESOLVED` / `INCIDENT_CLOSED` / `INCIDENT_REOPENED` | lifecycle end states |
| `SLA_BREACHED` / `SLA_COMPLETED` | the SLA status crosses into either, recorded once |

`description` is generated server-side from the real user, team and values —
"Priority changed from HIGH to CRITICAL by Priya Admin" — and rendered as-is by
the UI. Nothing is hardcoded and nothing is reassembled client-side, so the
record and the screen cannot disagree.

**The trail is immutable.** `IncidentActivity` has no setters, every column is
`updatable = false`, the repository exposes no delete helper, and no endpoint
writes or removes an activity. An admin token does not change that.

### Notifications

In-app only — nothing emails, pages or pushes. A notification is addressed to
exactly one person at the moment the event happens, and the recipient is a column
on the row rather than something worked out at read time.

| Event | Who is told |
| --- | --- |
| Incident created | The assigned team, or every active agent when it arrived unrouted |
| Incident assigned | The agent it was assigned to |
| Comment added | The reporter and the assigned agent — an internal note never reaches the reporter |
| SLA at risk | The assigned agent |
| SLA breached | The assigned agent and every active admin, naming the stage |
| Resolved / closed | The reporter |

**Nobody is notified about their own action.** An agent who comments does not
need telling that a comment was added.

**Repeated events do not repeat notifications.** The SLA states are re-evaluated
on every read and every sweep, so those are guarded by a "have we already told
this person about this incident" check; the breach guard is per stage, because
response and resolution are two different failures.

Every endpoint takes the recipient from the token — there is no user parameter to
pass or to get wrong. A notification belonging to somebody else answers **404,
not 403**: the caller has no business knowing it exists.

### Administration

Users, teams and SLA policies are managed from `/admin/*`, admin only. Two rules
run through all of it:

**Nothing is deleted.** A user is referenced by the incidents they reported and
every activity they performed; a team by the incidents routed to it. Deactivating
is the equivalent operation — the account stops working and stops being offered
for new assignments, while the history it anchors stays readable. There is no
delete endpoint for a user, a team or a policy, and `DELETE` on any of those
paths answers 405.

**Authorization is enforced twice.** Path rules in `SecurityConfig` and
`@PreAuthorize` on every admin method, with the navigation filtering and route
guards on top. The frontend hiding a button is a convenience; the API refusing
the call is the protection, and there is a test that walks an employee and an
agent token across every admin endpoint expecting 403.

`GET /api/users` is paged and filterable by `search`, `role`, `teamId` and
`active`. A profile edit (`PUT /api/users/{id}`) carries **no password field at
all**, so ordinary editing cannot reset a credential as a side effect. An admin
cannot remove their own admin role or deactivate themselves — that is the one
mistake that locks the last administrator out of the screen that could undo it.

Administrative actions are recorded in a small separate table, surfaced at
`GET /api/admin/audit` and on the Reports page. It is deliberately not
`IncidentActivity`: these actions belong to no incident, and putting them there
would mean every incident timeline had to filter them out.

### SLA

Every incident carries **two clocks**, both stamped from the active policy at
creation and never recalculated from the policy afterwards — editing a policy
cannot retroactively change whether a past incident breached.

| Field                   | Meaning                                              |
| ----------------------- | ---------------------------------------------------- |
| `responseDeadline`      | When a first response is due                          |
| `responseCompletedAt`   | First agent action: assignment, or an agent comment   |
| `resolutionDeadline`    | When resolution is due                                |
| `resolutionCompletedAt` | When it was resolved                                  |

`slaStatus` summarises both clocks and takes the **worst** outcome:

- **BREACHED** — either deadline passed with its action outstanding, *or* met
  only after the deadline. An on-time resolution does not undo a first response
  that never came.
- **COMPLETED** — resolved, with neither deadline missed.
- **AT_RISK** — 25% or less of the open stage's window remains.
- **ON_TRACK** — more than 25% remains.

"25% of the window" is measured against the **policy's configured minutes**, not
against `createdAt → deadline`. The two are identical for an untouched incident
but diverge the moment a deadline is adjusted, and the policy is what the
percentage is actually about.

An incident whose priority has no active policy is created with **no SLA at all**
— every SLA field reads as unknown rather than as met.

**Who sees what.** An employee's copy of an incident contains the SLA status and
the resolution deadline. The response-stage fields are omitted from the DTO
entirely for that role, rather than sent and hidden in the UI. Agents and admins
additionally see the response deadline, whether it was answered, and the time
remaining.

**The background sweep.** A scheduled job re-evaluates every open incident that
carries a deadline, so an incident nobody opens no longer breaches silently. It
holds no SLA rules of its own: it selects rows and calls the same
`SlaService.refreshAndRecord` the read path uses, so opening an incident and
sweeping it cannot reach different verdicts.

| Setting | Default | Meaning |
| --- | --- | --- |
| `sla.scheduler.enabled` (`SLA_SCHEDULER_ENABLED`) | `true` | Off disables the job entirely; the test profile sets `false` |
| `sla.scheduler.interval-ms` (`SLA_SCHEDULER_INTERVAL_MS`) | `60000` | Delay between the end of one pass and the start of the next |
| `sla.scheduler.initial-delay-ms` (`SLA_SCHEDULER_INITIAL_DELAY_MS`) | `15000` | Grace period after startup |

Each stage is announced **once**, guarded by `responseBreachedAt` and
`resolutionBreachedAt` on the incident. "The deadline passed and nothing was
done" stays true forever, so it cannot itself tell us whether we have already
said so — the marker can, and keeping the instant rather than a flag also
records when the breach was noticed. A priority change clears both markers,
because the new deadlines are a new commitment.

The activity names the stage — "Response SLA breached" or "Resolution SLA
breached" — since "SLA breached" alone does not say whether nobody answered or
nobody finished. Entering AT_RISK is recorded too, guarded by the stored status.

The job logs only when something changed: at one line per minute, a running
commentary saying nothing happened is how logs stop being read. A pass that
moves something logs `SLA sweep completed: checked=12, updated=2, breached=1`.

**One instance is assumed.** Two would both sweep; the markers stop that
duplicating activities, but the work would be done twice. A shared lock belongs
with a real distributed scheduler — which is also the seam where this timer
would be replaced by an external worker, since replacing it means rewriting the
loop, not the rules.

**Policy selection.** Exactly one policy is active per priority, and activating
one **stands the others down** rather than refusing. Refusing would leave an
admin unable to swap a policy without first remembering to disable the old one,
and a priority briefly left with no active policy creates incidents carrying no
SLA at all — the worse failure. The stand-down is atomic and cannot leave that
gap, and each one is written to the admin audit so the side effect is visible.
Times must be positive and a response time may not exceed its resolution time.

**Editing a policy never moves an existing deadline.** Incidents keep what they
were stamped with at creation; only incidents reported afterwards see the
change. There is a test that edits a policy and asserts the deadline of an
incident created under it is unchanged.

Resolving and closing are **not** reachable through `PATCH /status`. Each has
its own endpoint because each has its own requirement: a resolution needs a
summary, and only a RESOLVED incident can be closed. `PATCH /status` returns a
400 pointing at the right endpoint if asked for either. Reopening a resolved
incident clears `resolvedAt` and the resolution summary, so those fields keep
meaning what they say.

### Authorization model

Two layers, because one is not enough:

1. **Path and method rules** in `SecurityConfig`, plus `@PreAuthorize` on
   controller methods. These answer "may this role call this endpoint".
2. **Row checks** in `IncidentService` (`assertCanView` / `assertCanAct`).
   These answer "may this user touch this incident", which no URL pattern can
   express. An employee holding a perfectly valid token still gets 403 on
   someone else's incident.

Employees can comment on their own incidents but cannot modify them. An internal
agent note is never returned to the reporting employee, and an employee cannot
create one even by sending `"internal": true`.

---

## Current state

### Phase 1 — foundation (done)

Root structure, both apps building and running independently, environment-driven
config, PostgreSQL datasource, package structure, entities and enums,
`GET /api/health`, design tokens.

### Phase 2 — database and authentication (done)

- **Entities**: `User`, `Team`, `Incident`, `IncidentComment`,
  `IncidentActivity`, `SlaPolicy`, with real foreign keys, unique constraints,
  indexes, optimistic locking and auditing timestamps.
- **Role** is an enum column on `User` (`EMPLOYEE`, `SUPPORT_AGENT`, `ADMIN`)
  rather than a table plus join table: V1 has three fixed roles and one role per
  user, so a join table would add a query per request and buy nothing.
- **Auth**: `POST /api/auth/login` returns token, user id, name, email, role and
  team. BCrypt hashing, HS256 JWT, stateless sessions, 401 for anonymous and 403
  for authenticated-but-forbidden, both in the standard `ApiError` shape.
- The JWT filter **reloads the user from the database on every request** rather
  than trusting the token claims, so deactivating a user or changing their role
  takes effect immediately instead of at token expiry.
- **Repositories, services, DTOs and controllers** for incidents, users, teams,
  SLA policies and reports. No entity is serialised directly; no response record
  has a password field, so a hash cannot leak by accident.
- **Incident lifecycle** with an explicit legal-transition table; an illegal move
  (NEW straight to CLOSED, for example) is a 400, not a silent write. Every
  change appends to the incident's activity history.
- **SLA**: the active policy for the incident's priority is resolved at creation,
  the deadline is stamped on the row, and status is recomputed on read
  (`ON_TRACK`, `AT_RISK` under 25% of the window left, `BREACHED`, `COMPLETED`).
- **Frontend**: `/login`, auth context, protected routes, role-based navigation,
  employee dashboard and report form, agent queue with assign and advance
  actions, admin overview plus users, teams and SLA policy screens.
- **Validation** on every request DTO, with field-level errors returned to the
  form.

### Phase 3 — core incident management (done)

- **Incident creation** by an employee with title, description, category,
  subcategory and priority. `INC-000001`-style reference, `status = NEW`,
  reporter, timestamps and the SLA deadline are all set server-side.
- **Queue** for agents and admins with every required column (incident id,
  title, priority, status, reporter, team, agent, created, SLA status) and
  **server-side filters**: status, priority, team and free-text search over
  reference, title, description and reporter name. Search is debounced.
- **Assignment**: select a team, an agent, or both. Assigning an agent moves a
  NEW incident to ASSIGNED automatically and inherits the agent's team when no
  team was chosen.
- **Update**: priority and category, each recorded as its own activity entry. A
  priority change re-resolves the SLA policy, because the old policy should not
  keep governing an incident that is no longer that urgent.
- **Resolution**: requires a summary, sets `resolvedAt`, completes the SLA.
- **Closure**: only from RESOLVED, sets `closedAt`, optional closing note.
- **Incident detail page** shared by all three roles: information panel, SLA
  panel with remaining time, description, resolution, comment thread and the
  activity timeline. Agent and admin additionally get the actions panel; the
  status control only offers transitions the API will accept.
- **Activity history** for create, assign, priority change, category change,
  status change, comment, resolve, close and reopen — each with timestamp,
  actor, before/after values and an optional note.
- **Comments** for anyone who may see the incident, with internal agent notes
  hidden from the reporting employee.

### Phase 4 — SLA management (done)

- **Two SLA clocks per incident** — response and resolution — with deadlines,
  completion timestamps and a single summarising `slaStatus`.
- **Four default policies** seeded for LOW, MEDIUM, HIGH and CRITICAL
  (4h/72h, 1h/24h, 30m/8h, 15m/4h respectively).
- **Response is stopped** by assigning an agent or by an agent/admin comment. A
  comment from the reporter does not count.
- **Priority changes re-stamp both deadlines** from the original creation time,
  not from now — the incident has been open that whole time.
- **Reopening a resolved incident** restarts the resolution clock.
- **Admin SLA policy page**: create, edit, activate, deactivate and view, with a
  warning banner when a priority has no active policy.
- Policies are listed by **severity**, sorted in Java; the priority column stores
  the enum name, so ordering in SQL would sort alphabetically.

### Phase 5 — comments, activity history and audit trail (done)

- **Comments**: `GET` and `POST /api/incidents/{id}/comments`, paged envelope,
  author id/name/role on every response, whitespace-only rejected, internal
  notes invisible to the reporting employee.
- **Audit trail**: `GET /api/incidents/{id}/activities`, newest first, immutable
  by construction, fourteen recorded actions with server-generated descriptions.
- **`SLA_BREACHED` and `SLA_COMPLETED` are now actually written** — recorded
  once, when the status crosses, rather than merely being declared in the enum.
- **Unmatched routes return 404, and a wrong verb returns 405.** Both previously
  fell through to the catch-all and came back as 500.
- **Incident detail page**: information panel, SLA panel, comment thread with
  role badges and success feedback, and a timeline that renders the stored
  descriptions chronologically with tone-coloured markers.

### Phase 6 — SLA breach sweep scheduler (done)

- **`SlaSchedulerService`** (the timer) and **`SlaSweepService`** (one
  transactional pass), configurable and disableable, logging only on change.
- **Per-stage breach markers** so a sweep running every minute announces each
  stage once, verified by a test that runs twenty passes with no reads between.
- **`SLA_AT_RISK` activity**, so the timeline shows the warning as well as the
  breach.
- **A `Clock` bean** behind all SLA time. Tests move time instead of sleeping,
  so a nine-hour deadline is crossed in a millisecond.
- **Agent queue** gained response and resolution deadline columns with live
  countdowns, and a breached row now carries a red edge and tint.
- **Admin dashboard** gained Open, SLA at risk, SLA breached, Resolved and
  Closed, with the two SLA tiles tinted only when non-zero.

### Phase 7 — admin management (done)

- **Users**: paged, filterable list (search, role, team, status), create, edit,
  activate/deactivate with confirmation, and a detail view with assigned and
  resolved counts. A deactivated user cannot log in and is no longer offered for
  assignment — and assigning to one is refused by the API.
- **Teams**: list with member counts, create, edit, activate/deactivate, and a
  member list per team.
- **SLA policies**: create, edit, activate/deactivate with a confirmation that
  spells out the consequence, plus created/updated columns.
- **Dashboard**: open, in progress, at risk, breached, resolved, closed, total
  and active users, teams — and breakdowns by status, priority and team, drawn
  as plain bars rather than by adding a chart library.
- **Reports page** with those breakdowns and the administrative audit trail.
- **Admin navigation**: Dashboard, All Incidents, Users, Teams, SLA Policies,
  Reports — filtered by role, guarded by route, enforced by the API.

### Phase 8 — completing the MVP (done)

- **Password reset**: `POST /api/users/{id}/reset-password`, admin only, hashed
  before storage, absent from every response and every log line. The admin audit
  records that a reset happened, never what it was set to.
- **Pagination** on the incident queue, My Incidents and the user list, with
  page numbers, a "Showing 1–10 of 42" line, and Previous/Next disabled at the
  ends. A filter change returns to page one.
- **SLA status filter**, and **per-stage SLA verdicts** on both the list rows and
  the detail page — computed by `SlaService`, never by the UI.
- **SLA wording**: "Resolution SLA breached 35m ago", "Response SLA due in 18m".
  An overdue deadline never reads "0m remaining".
- **Notification centre**: bell with unread badge, panel with read state, mark
  one or all read, click through to the incident, empty state.
- **Reports**: incident summary, SLA summary, priority breakdown, team workload
  and agent workload — plain CSS bars and tables, no chart library.
- **Dashboard** quick actions for creating a user or team, managing policies and
  opening reports.
- **Accessibility**: SLA and notification states carry a glyph as well as a
  tone, dialogs close on Escape, the bell announces its unread count, and every
  filter control has a label.
- **Responsive layer** (`styles/responsive.css`): below 820px the navigation rail
  becomes a horizontal strip and two-column layouts collapse. At 375px the app
  previously gave 254px of a 375px screen to the rail; it no longer does.

### Phase 9 — PostgreSQL and Flyway (done)

- **PostgreSQL is the runtime database.** The H2 runtime profile is gone; H2 is
  now a test-scoped dependency and backs the test suite only.
- **Flyway owns the schema**, with `V1__initial_schema.sql` covering all eight
  application tables, written from the exported entity DDL.
- **Hibernate runs in `validate`**, so a mapping that has drifted from the
  migrations fails at startup rather than at the first query.
- **Constraints and indexes moved into the database**: check constraints on every
  enum column and on SLA times, a non-blank comment constraint, and partial
  indexes for the SLA sweep and the unread-notification badge.
- Verified against a live PostgreSQL 16: Flyway migrated an empty database,
  Hibernate validated, and every flow — auth, incidents, assignment, comments,
  SLA breach through the scheduler, admin management, password reset,
  notifications and reports — was exercised end to end.

### Phase 10 — local H2 restored, and a UI pass (done)

**Local development is H2 again.** PostgreSQL and Flyway moved behind the
opt-in `postgres` profile; nothing about running the application locally touches
Docker.

The interface pass:

- **An application header** across every screen, carrying the page title, the
  notification bell and an account menu. The bell used to live at the bottom of
  the sidebar, which is not where anyone looks for it.
- **Grouped navigation** with section captions, so the admin rail reads as
  Overview / Administration rather than as one long list.
- **One toast system.** Success and failure used to be per-page banners on their
  own timers; now every outcome is announced the same way, from a live region
  that assistive technology hears.
- **Employee and agent dashboards** built on a role-scoped `/api/incidents/summary`,
  so the cards and the table beneath them are counted from the same set.
- **Skeletons** in place of "Loading…", matching the shape of what is arriving.
- **Login** gained branding, a show/hide password control and a clearer error.
- **Reporting an incident** gained required markers, character counters and a
  confirmation that names the incident: "Incident INC-000123 created successfully."
- **Priority now carries a glyph** as well as a colour, as SLA state already did,
  so neither depends on colour alone.
- Verified with no horizontal overflow at 375, 390, 768, 1024, 1440 and 1920px.

### Not implemented yet

**Self-service password change** — an admin can reset anyone's password, but
nobody can change their own, and there is no email-based reset. Refresh tokens.
File attachments. Email or push notification. Saved queue views.

### Known limitations

- **The JWT is stored in `localStorage`.** It survives a reload and needs no
  refresh-token plumbing, which is the right MVP trade, but it is readable by
  any script on the origin and therefore XSS-exposed. The hardening step is an
  httpOnly cookie plus a CSRF token.
- **`JWT_SECRET` has a development default** in `application.yml` and must be
  overridden by a real environment variable everywhere else. The app refuses to
  start if the secret is shorter than 32 bytes.
- **Reports are computed in memory** over the full incident table. Fine at MVP
  volume, wrong at scale; it becomes grouped SQL when the table is big enough to
  notice.
- **Nothing notifies anyone.** The sweep records a breach; it does not email,
  page or push. Noticing still depends on somebody looking at the queue.
- **The sweep assumes a single instance** and holds no lock, so a second
  replica would repeat the work (without duplicating activities).
- **The sweep scans every open incident each pass.** Fine at MVP volume; it
  wants an index on the deadline columns and a narrower query before it is not.
- **SLA clocks run on wall time.** Business hours, weekends and holidays are not
  modelled, so a Friday-evening HIGH incident burns its window overnight.
- **`SLA_BREACHED` is declared as an activity type but never written**, because
  nothing observes a breach as it happens — only a reader notices it.
- **A closed incident is final.** There is no reopen-from-closed path in V1.
- **Comments cannot be edited or deleted.** That is deliberate for an audit
  trail, but it means a typo is permanent; a correction is a second comment.
- **The notification badge is polled once a minute**, so a count can be up to a
  minute stale. Opening the panel always fetches fresh.
- **A new incident with no team notifies every active agent.** Correct at MVP
  scale, noisy with a large support organisation; routing rules would fix it.
- **The comment field is named `comment`,** not `body`. It was renamed in
  Phase 5 to match the specification, which is a breaking API change for any
  caller written against the earlier shape.
- **No rate limiting on login.** Brute-force protection is not in V1.
- **Incident references use `max(reference_seq) + 1`**, which can collide under
  concurrent creation. A database sequence is the fix.

---

## Roadmap

| Phase | Scope                                                                    |
| ----- | ------------------------------------------------------------------------ |
| 1     | Foundation, entities, health endpoint, design tokens — **done**           |
| 2     | Auth, RBAC, incidents, SLA basics, role dashboards — **done**             |
| 3     | Incident lifecycle, queue filters, detail page, activity — **done**       |
| 4     | SLA policies, dual clocks, status rules, admin management — **done**      |
| 5     | Comments, activity history, audit trail — **done**                        |
| 6     | SLA breach sweep scheduler — **done**                                     |
| 7     | Admin management of users, teams and SLA policies — **done**              |
| 8     | Password reset, pagination, notifications, reports — **done**             |
| 9     | PostgreSQL and Flyway — **done**                                          |
| 10    | Local H2 restored, UI and UX pass — **done**                              |
| 11    | Business-hours SLA clocks, email notification, saved views                |
| 12    | Hardening: httpOnly cookie auth, rate limiting, audit export              |

Infrastructure (AWS, EventBridge, Lambda, Redis, S3, Prometheus, Grafana,
Jenkins, Kubernetes) is intentionally out of scope until the core application is
functional.

---

## Design system

`frontend/src/lib/tokens.ts` is the **only** source of design values: brand
`#2563EB`, ground `#F6F7F9`, the dark navigation rail `#0F172A`, the six status
tones, Manrope + JetBrains Mono, and the radius scale (14px card / 9px control /
20px pill).

The design reference documents that the product it described drifted by maintaining
two token sources — a token module and a Tailwind theme — so edits in the
obvious place stopped taking effect. This project therefore has one source and
no Tailwind. Rules carried over:

- Status colour comes from a **tone name**, never a hand-picked hex.
- `#475569` is the contrast floor; anything lighter is metadata, never body copy.
- Only the primary button is filled; destructive actions are white with a tinted border.
- Every empty state gives a message **and** a way forward.
- A figure that must come from the API renders as `--` until it does. A zero
  would be a claim.
