# SPEC-003: JWT authentication with DONOR and REQUESTER roles

**Status:** Implemented
**Issue:** #4 (backlog item 3)
**Depends on:** SPEC-002

---

## Problem

The privacy rule only means something if the system knows who is asking. "Phone numbers never
appear in any list response" is unenforceable against anonymous traffic, and "only the requester
who accepted this pledge may see the donor's number" needs an authenticated identity attached
to every request.

The two roles are genuinely different actors with different rights, not a permission flag. A
donor publishes availability and pledges; a requester publishes need and accepts. Conflating
them would let anyone manufacture a request to harvest contact details, which is precisely the
Facebook-group failure mode this project exists to fix.

## Scope

### In

- Self-service registration as either a `DONOR` or a `REQUESTER`.
- Login by phone and password, returning a signed JWT.
- BCrypt password hashing, with the plaintext never stored, logged or echoed.
- A stateless Spring Security filter chain: no session, no cookie, bearer token only.
- Role authorities (`ROLE_DONOR`, `ROLE_REQUESTER`) derived from the token, ready for the
  endpoints SPEC-004 and SPEC-006 will gate.
- A single authenticated endpoint, `GET /api/auth/me`, so a client can learn who it is and so
  the filter chain is demonstrably working.
- Phone number normalisation, so one person cannot hold two accounts by typing their number two
  different ways.
- Interactive API docs at `/swagger-ui.html`, with an Authorize button that takes the token from
  a login response. Approved during implementation; SPEC-010 keeps the polish.

### Out

- **Refresh tokens, logout, token revocation.** The access token expires and the user logs in
  again. A revocation list is server state, and there is nothing here worth the complexity yet.
- **Password reset and email verification.** Both need a delivery channel, and SMS and email are
  out of scope for the whole project.
- **Rate limiting and brute-force lockout.** Real, deliberately deferred; noted as a risk below.
- **OAuth, social login, two-factor.**
- **Donor profile creation.** Registering creates an `app_user` and nothing else. The donor
  profile is SPEC-004.
- **Role-specific endpoints.** This spec proves a role reaches the server as an authority; the
  first endpoint actually restricted to one role arrives with SPEC-004.
- **Auto-login on registration.** Register returns 201 and no token; the client then calls login.

## Acceptance Criteria

- **AC-1:** `POST /api/auth/register` with a valid body creates one `app_user` row and returns
  `201` with `{ id, fullName, role }`. The stored `password_hash` is a BCrypt hash — 60
  characters, `$2a$` or `$2b$` prefix — and is not equal to the submitted password.

- **AC-2:** Registering a phone that already exists returns `409` with a `ProblemDetail` body,
  and creates no second row. `01712345678` and `+8801712345678` are the same phone for this
  purpose: both normalise to `+8801712345678`.

- **AC-3:** `POST /api/auth/register` returns `400` with a `ProblemDetail` naming the offending
  fields when the body is invalid: missing `fullName`, `phone`, `password` or `role`; a `role`
  outside `DONOR`/`REQUESTER`; a phone that is not a Bangladeshi mobile number; a password
  shorter than 8 or longer than 72 characters.

- **AC-4:** `POST /api/auth/login` with correct credentials returns `200` with
  `{ accessToken, tokenType: "Bearer", expiresIn, role }`, where `expiresIn` is the token
  lifetime in seconds.

- **AC-5:** Login with a wrong password and login with an unregistered phone both return `401`
  with a byte-identical body. Neither response, nor its timing-independent content, reveals
  whether the account exists.

- **AC-6:** `GET /api/auth/me` returns `401` with no `Authorization` header, and `200` with
  `{ id, fullName, role }` when given a valid bearer token.

- **AC-7:** `GET /api/auth/me` returns `401` for a token that is expired, signed with the wrong
  key, or has had a character of its payload altered.

- **AC-8:** The token's `sub` claim is the `app_user` id and it carries a `role` claim. A request
  bearing a `DONOR` token is authenticated with authority `ROLE_DONOR`, and a `REQUESTER` token
  with `ROLE_REQUESTER`.

- **AC-9:** No response defined by this spec contains a phone number, including the registration
  response, the login response and `/api/auth/me`. The caller supplied their own number; the
  server never sends one back.

- **AC-10:** No response sets a session cookie. `Set-Cookie` is absent from every response, and
  two consecutive requests with no token share no server-side state.

## API Contract

All bodies are `application/json`. All failures are RFC 7807 `ProblemDetail`.

### `POST /api/auth/register`

**Auth:** none

Request:

```json
{
  "fullName": "Masud Rana",
  "phone": "01712345678",
  "password": "correct-horse-battery",
  "role": "DONOR"
}
```

Response `201`:

```json
{
  "id": 1,
  "fullName": "Masud Rana",
  "role": "DONOR"
}
```

| Status | When | Body |
| ------ | ---- | ---- |
| 400 | Missing or malformed field, bad role, bad phone, password outside 8..72 characters | `ProblemDetail` with a `errors` map of field to message |
| 409 | Phone already registered | `ProblemDetail`, detail `Phone already registered` |

### `POST /api/auth/login`

**Auth:** none

Request:

```json
{
  "phone": "01712345678",
  "password": "correct-horse-battery"
}
```

Response `200`:

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 43200,
  "role": "DONOR"
}
```

| Status | When | Body |
| ------ | ---- | ---- |
| 400 | Missing `phone` or `password` | `ProblemDetail` |
| 401 | Unknown phone **or** wrong password — one identical response for both | `ProblemDetail`, detail `Invalid phone or password` |

### `GET /api/auth/me`

**Auth:** any authenticated user

Response `200`:

```json
{
  "id": 1,
  "fullName": "Masud Rana",
  "role": "DONOR"
}
```

| Status | When | Body |
| ------ | ---- | ---- |
| 401 | No token, expired token, bad signature, tampered payload | `ProblemDetail`, detail `Authentication required` |

### Filter chain rules

| Path | Access |
| ---- | ------ |
| `POST /api/auth/register`, `POST /api/auth/login` | permit all |
| `GET /actuator/health` | permit all |
| `GET /v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` | permit all |
| `/error` | permit all |
| everything else under `/api/**` | authenticated |

`/error` has to be permitted. It is the container's error dispatch, and a request that reaches it
while the chain denies it comes back as a misleading 401 instead of its real status — which is
exactly what happened with an unparseable role before it was fixed.

**Privacy note:** no endpoint in this spec returns a phone number in any response, success or
failure. `app_user.phone` is written at registration and read only as a login identifier. The
first endpoint permitted to return a phone is the contact reveal in SPEC-008, and only after a
pledge is accepted.

## Data Model Changes

No new tables. `app_user` already carries `password_hash` and `role` from `V1__core_schema.sql`.

One additive migration makes the phone format a schema invariant rather than a promise the
application layer keeps:

### `V3__app_user_phone_format.sql`

| Table | Change | Notes |
| ----- | ------ | ----- |
| `app_user` | add `constraint ck_app_user_phone_format check (phone ~ '^\+8801[3-9][0-9]{8}$')` | Canonical E.164 for a Bangladeshi mobile. Applied to an empty table; there are no rows to migrate. |

Storage is always canonical `+8801XXXXXXXXX`. The API accepts `01XXXXXXXXX`, `+8801XXXXXXXXX`
and `8801XXXXXXXXX`, and normalises before it touches the database.

### Configuration

```yaml
roktolink:
  security:
    jwt:
      secret: ${ROKTOLINK_JWT_SECRET:<dev-only placeholder, at least 32 bytes>}
      ttl: PT12H
```

Twelve hours because there is no refresh token: a shorter life would mean logging in again
mid-task, and a longer one widens the window on a stolen token.

### Java

```
com.roktolink.auth            AuthController, RegisterRequest, LoginRequest, ...
com.roktolink.auth.service    AuthService — registration, login, normalisation
com.roktolink.auth.jwt        JwtIssuer, JwtAuthenticationFilter, SecurityConfig
com.roktolink.user            AppUserRepository (added to the existing package)
```

## Out of Scope & Risks

- **Blocker — the coverage gate and the "no tests" rule collide here.** `mvn verify` fails the
  build when a package matching `com.roktolink.*.service` has less than 80% line coverage, and
  `com.roktolink.auth.service` will match. Under the standing rule that tests are written only
  for the compatibility matrix, the eligibility date maths and the state machine guards, this
  spec produces no tests and therefore a red build. This must be settled before implementation
  starts; the options are listed in the pull request.
- **Risk — no brute-force protection.** Nothing stops an attacker trying passwords against
  `/api/auth/login` as fast as the network allows. Out of scope by choice, and the honest
  mitigation is that this is a portfolio project rather than a deployed service.
- **Risk — a symmetric signing key in configuration.** The JWT is signed with an HMAC secret. A
  development placeholder ships in `application.yml`; anything real must set
  `ROKTOLINK_JWT_SECRET`. A leaked secret mints valid tokens for every account until it is
  rotated, and rotation invalidates every live token at once.
- **Risk — a token cannot be revoked before it expires.** Deleting an account leaves its token
  valid for up to twelve hours. Accepted, and worth revisiting if pledges ever move money or
  medical data.
- **Risk — the phone check constraint is a snapshot.** If an operator gets a new prefix outside
  `013`–`019`, valid numbers are rejected until a migration widens the pattern.
- **Out of scope — password strength beyond a length floor.** No dictionary checks, no
  composition rules.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-03 | Draft | Filled in: scope, ten acceptance criteria, three endpoints, one additive migration. |
| 2026-09-03 | Approved | Approved on review of PR #14. Coverage blocker settled by testing the auth service; springdoc approved. |
| 2026-09-03 | Implemented | Three endpoints, V3 migration, springdoc. All ten criteria verified by hand; AC-8's authority mapping is verified by configuration and claims only, pending a role-gated endpoint in SPEC-004. |
