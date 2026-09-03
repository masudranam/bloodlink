# RoktoLink

A privacy-first blood donor network for Bangladesh.

Today these requests run through Facebook groups. Someone posts "B+ needed at
DMCH, urgent", the post is buried within the hour, and the requester's phone
number stays public forever afterwards. Donors get spam-called for months. Nobody
records who actually donated, so the same people are asked again while they are
still ineligible.

RoktoLink is the same exchange with three rules that Facebook cannot give it.

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
configuration (`roktolink.eligibility.donation-interval-days`, default 90).

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

**Non-negotiable: every acceptance criterion gets at least one test whose method
name starts with the criterion id.**

```java
@Test
void ac3_secondPledgeBySameDonor_returns409() { ... }
```

A reviewer greps `ac3_` and sees the criterion is covered. Nothing else in the
repository connects a written requirement to a passing test, so this convention
carries the whole idea:

```bash
# Which tests cover AC-3?
grep -rn "ac3_" backend/src/test/java
```

Criteria are numbered `AC-1`, `AC-2`, ... in the spec and are **never renumbered
after merge** — the test names reference them.

This is also why Checkstyle only inspects `src/main/java`: `ac3_secondPledge...`
violates the standard `MethodName` rule, and the convention wins.

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

Postgres 16 on **`localhost:5433`**, database/user/password all `roktolink`.

The host port is 5433 rather than 5432 deliberately: a locally installed
PostgreSQL usually owns 5432, and on Windows both it and the container can bind
that port, which sends the application to the wrong server with a confusing
`password authentication failed` instead of a connection error. Override with
`ROKTOLINK_DB_PORT` if 5433 is taken as well, and set `ROKTOLINK_DB_URL` to
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
| `ROKTOLINK_DB_URL` | `jdbc:postgresql://localhost:5433/roktolink` | JDBC url |
| `ROKTOLINK_DB_USER`, `ROKTOLINK_DB_PASSWORD` | `roktolink` | database credentials |
| `ROKTOLINK_DB_PORT` | `5433` | host port Docker Compose publishes |
| `ROKTOLINK_JWT_SECRET` | a placeholder committed to this repo | HMAC key that signs access tokens, at least 32 bytes |
| `ROKTOLINK_PORT` | `8080` | port the API listens on |

The JWT secret shipped in `application.yml` is a development placeholder and is
not a secret in any meaningful sense — it is in the repository. Anything running
anywhere real must set `ROKTOLINK_JWT_SECRET`; startup fails if the value is
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

The coverage rule is scoped to `com.roktolink.*.service` rather than the whole
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

## Layout

```
backend/     Spring Boot 3.5 · Java 21 · Flyway · JPA · Testcontainers
frontend/    React 18 · Vite · TypeScript · React Query · plain CSS
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
