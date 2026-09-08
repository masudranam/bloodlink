# BloodLink

A privacy-first blood donor network for Bangladesh.

Today these requests run through Facebook groups. Someone posts "B+ needed at
DMCH, urgent", the post is buried within the hour, and the requester's phone
number stays public forever afterwards. Donors get spam-called for months. Nobody
records who actually donated, so the same people are asked again while they are
still ineligible.

BloodLink is the same exchange with three rules that Facebook cannot give it.

---

## The three rules

Everything else in this repository is support for these.

### 1. The privacy rule

**Phone numbers never appear in any list response.** A donor pledges against a
request, the requester accepts that pledge, and only then does each side see the
other's number. Every reveal is written to an audit log.

Search and feed endpoints return DTO projections that have no phone field at
all — not an entity with the field nulled out, which is one careless mapper away
from a leak.

### 2. Eligibility is computed, never stored

A donor is eligible when `lastDonationDate + interval < today`. The interval is
configuration (`bloodlink.eligibility.donation-interval-days`, default 90).

There is no `is_eligible` column and there never will be one. A stored flag is
correct on the day it is written and wrong every day after.

### 3. Compatibility is a pure function

The 8x8 blood group matrix lives in one service with no dependencies — no
database, no clock, no user — and all 64 combinations are asserted exhaustively.
A B+ patient can receive from B+, B-, O+ and O-.

---

## How this project is built: spec first

**No production code is written until its spec is merged.**

1. An issue is opened from `.github/ISSUE_TEMPLATE/feature.md`, linking its spec.
2. The spec in `/specs` is filled in — problem, scope, acceptance criteria, API
   contract, data model changes — and merged with **Status: Draft → Approved**.
3. Only then is the code written, against those criteria.
4. The PR uses `.github/pull_request_template.md` and its Definition of Done.
5. The spec's status moves to **Implemented** in the same PR.

Specs live in [`/specs`](specs), copied from
[`SPEC-000-template.md`](specs/SPEC-000-template.md). The ten planned issues,
their goals and their dependencies are in [`docs/BACKLOG.md`](docs/BACKLOG.md).
Structural decisions are in [`docs/adr`](docs/adr).

### The traceability rule

**Every acceptance criterion must be traceable to the evidence that it holds.
Where that evidence is a test, the test's method name starts with the criterion
id — no exceptions.**

```java
@Test
void ac3_secondPledgeBySameDonor_returns409() { ... }
```

A reviewer greps `ac3_` and sees the criterion is covered:

```bash
# Which tests cover AC-3?
grep -rn "ac3_" backend/src/test/java
```

Criteria are numbered `AC-1`, `AC-2`, ... in the spec and are **never renumbered
after merge** — the test names reference them.

This is also why Checkstyle only inspects `src/main/java`: `ac3_secondPledge...`
violates the standard `MethodName` rule, and the convention wins.

### What gets a test, and what gets verified by hand

Not every criterion is worth a test, and pretending otherwise produces tests that
assert the framework works. Tests are written where the logic is genuinely
non-obvious or where being wrong is dangerous:

- the blood group compatibility matrix,
- the eligibility date arithmetic,
- the request state machine's transition guards,
- and whatever else in the service layer the coverage gate requires.

Controllers, repositories, DTO mapping and configuration are **not** unit tested.
They are exercised by hand, and the pull request carries the evidence: the request
made, and the response and database state that came back. `SPEC-002` shipped with
no tests at all and a page of `psql` output instead, which is the right trade for
a spec that is entirely schema.

So a criterion is satisfied by an `acN_` test **or** by transcribed manual
verification in the pull request, and the PR template asks which. A criterion with
neither is not done.

Two consequences worth stating plainly, because both have already bitten:

- **A criterion can be partly verified.** `SPEC-003` AC-8 asserted that a `DONOR`
  token authenticates with `ROLE_DONOR`; nothing in that issue was role-gated, so
  only the claim was checkable. It was recorded as half-verified and finished in
  `SPEC-004`, rather than ticked.
- **Manual verification finds things tests would not.** Both bugs found so far — a
  malformed body answering `401` instead of `400`, and a rejected blood group not
  naming its field — came from typing `curl` commands, not from the suite.

---

## Running it

### Prerequisites

- **JDK 21** (Temurin). Maven is not needed — `./mvnw` fetches Maven 3.9.16.
- **Node 20.19+** and npm.
- **Docker Desktop**, running. The backend test suite starts a real Postgres
  container; without Docker the tests fail rather than skip.

### 1. Start the database

```bash
docker compose up -d
```

Postgres 16 on **`localhost:5433`**, database/user/password all `bloodlink`.

The host port is 5433 rather than 5432 deliberately: a locally installed
PostgreSQL usually owns 5432, and on Windows both it and the container can bind
that port, which sends the application to the wrong server with a confusing
`password authentication failed` instead of a connection error. Override with
`BLOODLINK_DB_PORT` if 5433 is taken as well, and set `BLOODLINK_DB_URL` to
match.

```bash
docker compose ps          # check health
docker compose logs -f     # follow
docker compose down        # stop, keep data
docker compose down -v     # stop and delete the volume
```

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

- API: <http://localhost:8080>
- Health: <http://localhost:8080/actuator/health>
- **API docs: <http://localhost:8080/swagger-ui.html>**

Swagger UI is how you exercise the API by hand. Call `POST /api/auth/login`, copy
the `accessToken` out of the response, press **Authorize**, paste it, and every
protected endpoint is callable from the browser with the token attached.

Flyway migrates on startup and Hibernate runs with `ddl-auto=validate`, so a
mismatch between an entity and a migration fails the boot rather than altering a
table.

### 3. Run the frontend

```bash
cd frontend
npm install     # first time only
npm run dev
```

<http://localhost:5173>, with `/api` proxied to the backend on 8080.

### Environment variables

| Variable | Default | What it does |
| -------- | ------- | ------------ |
| `BLOODLINK_DB_URL` | `jdbc:postgresql://localhost:5433/bloodlink` | JDBC url |
| `BLOODLINK_DB_USER`, `BLOODLINK_DB_PASSWORD` | `bloodlink` | database credentials |
| `BLOODLINK_DB_PORT` | `5433` | host port Docker Compose publishes |
| `BLOODLINK_JWT_SECRET` | a placeholder committed to this repo | HMAC key that signs access tokens, at least 32 bytes |
| `BLOODLINK_PORT` | `8080` | port the API listens on |
| `BLOODLINK_EXPIRY_ENABLED` | `true` | `false` means the expiry job bean is never created, so nothing is scheduled |
| `BLOODLINK_EXPIRY_CRON` | `0 5 * * * *` | six-field Spring cron for the expiry job |
| `BLOODLINK_LOG_LEVEL` | `INFO` | log level for `com.bloodlink` |

The JWT secret shipped in `application.yml` is a development placeholder and is
not a secret in any meaningful sense — it is in the repository. Anything running
anywhere real must set `BLOODLINK_JWT_SECRET`; startup fails if the value is
shorter than 32 bytes.

### Windows note

Use `mvnw.cmd` in PowerShell, `./mvnw` in Git Bash. PowerShell 5.1 has no `&&`,
so chain with `;` instead.

---

## Tests and quality gates

```bash
cd backend
./mvnw verify
```

That one command runs all three gates, in this order:

| Phase | Gate | Fails the build when |
| ----- | ---- | -------------------- |
| `validate` | Checkstyle (`backend/config/checkstyle.xml`) | any violation in `src/main/java` |
| `test` | JUnit 5 + Testcontainers | any test fails |
| `verify` | Jacoco | service-layer line coverage < 80% |

The coverage rule is scoped to `com.bloodlink.*.service` rather than the whole
project, so it measures the code that holds the logic instead of being diluted by
DTOs and entities. It is a no-op until the first service package exists.

Coverage report after a run: `backend/target/site/jacoco/index.html`.

Frontend:

```bash
cd frontend
npm run lint       # ESLint, flat config
npm run typecheck  # tsc project references
npm run build      # tsc -b && vite build
```

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs exactly these on
every pull request: `./mvnw verify` on Temurin 21 with Testcontainers, then
`npm ci && npm run lint && npm run build` on Node 20.

---

## The API

Interactive docs while the app is running: **<http://localhost:8080/swagger-ui.html>**
(the raw document is at `/v3/api-docs`). Sign in with `POST /api/auth/login`, then
paste the `accessToken` into **Authorize**.

| Method | Path | Who | What |
| ------ | ---- | --- | ---- |
| POST | `/api/auth/register` | public | create an account as `DONOR` or `REQUESTER` |
| POST | `/api/auth/login` | public | exchange phone and password for a 12-hour JWT |
| GET | `/api/auth/me` | any | who the token belongs to |
| GET | `/api/thanas` | any | the 40 seeded thanas, for a form |
| GET | `/api/hospitals` | any | the seeded hospitals, for a form |
| POST | `/api/donors/me` | donor | create a donor profile |
| GET | `/api/donors/me` | donor | read it, with `isEligible` computed on the spot |
| PUT | `/api/donors/me` | donor | replace it |
| DELETE | `/api/donors/me` | donor | delete it |
| POST | `/api/requests` | requester | raise a request; always starts `OPEN` |
| GET | `/api/requests` | any | the feed, filtered by status, paged |
| GET | `/api/requests/{id}` | any | one request |
| POST | `/api/requests/{id}/cancel` | requester (owner) | `OPEN`/`PLEDGED` → `CANCELLED` |
| POST | `/api/requests/{id}/fulfil` | requester (owner) | `PLEDGED` → `FULFILLED` |
| GET | `/api/requests/{id}/donors` | requester (owner) | **the core query** — compatible, eligible, available, within `radiusKm`, nearest first |
| POST | `/api/requests/{id}/pledges` | donor | offer blood; an `OPEN` request becomes `PLEDGED` |
| GET | `/api/requests/{id}/pledges` | requester (owner) | the offers on it |
| GET | `/api/donors/me/pledges` | donor | your own offers |
| POST | `/api/pledges/{id}/accept` | requester (owner) | pick this donor |
| POST | `/api/pledges/{id}/decline` | requester (owner) | pick somebody else |
| POST | `/api/pledges/{id}/withdraw` | donor (owner) | pull out |
| GET | `/api/pledges/{id}/contact` | the two parties | **the only endpoint that returns a phone number**, and only for an `ACCEPTED` pledge. Audited. |
| GET | `/api/me/reveals` | any | who has seen your number, newest first |
| GET | `/actuator/health` | public | liveness |

Every failure is an RFC 7807 `ProblemDetail` with a `type` you can branch on,
and validation failures carry an `errors` map of field to message.

## Operability

**Correlation ids.** Every response carries `X-Correlation-Id`, and every log
line produced while handling that request carries the same id. Send your own and
it is echoed back rather than replaced, so a client can join its logs to the
server's.

```
2026-09-08 23:52:01.334  INFO [a3f19c7d21b8] [http-nio-8080-exec-4] c.b.p.service.PledgeService : event=pledge_made pledgeId=9 requestId=27 donorId=20 patientGroup=B+ donorGroup=O-
```

**Nothing logs a phone number.** Not at `DEBUG`, not in an exception message.
That is why there is no scrubbing layer and no redaction setting: there is
nothing to scrub. A contact reveal is logged by id — pledge, viewer, revealed
user, role — because the log records that a reveal happened while the database
records what was revealed.

**Stale requests expire themselves.** A request still `OPEN` or `PLEDGED` whose
`neededBy` has passed becomes `EXPIRED` on a schedule, so nobody has to remember
to tidy up. The comparison is strict — a request needed *today* is still live —
and the job calls the same state machine every other status change goes through,
so it can never expire something already fulfilled.

---

## Layout

```
backend/     Spring Boot 3.5 · Java 21 · Flyway · JPA · Testcontainers
  auth/        registration, login, the JWT filter chain, OpenAPI
  donor/       profiles, the 8x8 compatibility matrix, the eligibility calculator
  request/     the request lifecycle, its state machine, the expiry job
  search/      the one native query: compatible AND eligible AND within N km
  pledge/      pledges, the contact reveal, the append-only reveal log
  reference/   seeded thanas and hospitals
  logging/     the correlation id filter
frontend/    React 18 · Vite · TypeScript · React Query · plain CSS
  api/         hand-written types, the only fetch wrapper, query keys
  auth/        the session and the 401 handler
  routing/     ~60 lines over the History API
  screens/     one file per route
specs/       SPEC-000 template + one spec per issue
docs/adr/    Architecture decision records
docs/        BACKLOG.md — the ten issues and their dependencies
.github/     CI, PR template with the Definition of Done, issue template
```

## Stack

Java 21, Spring Boot 3.5.16, Maven (wrapper), PostgreSQL 16, Flyway, Spring
Security with a stateless JWT chain, springdoc OpenAPI, JUnit 5, Testcontainers,
Jacoco, Checkstyle. React 18, Vite 7, TypeScript 5.9, React
Query, plain CSS — no UI kit. Docker Compose locally, GitHub Actions for CI.

## Deliberately out of scope

SMS and email notification, push notification, file upload, admin dashboards,
in-app chat, Google Places or any real map service, and multi-language support.

Distance is Haversine over stored lat/lng in native SQL. There is no external geo
service and there will not be one.
