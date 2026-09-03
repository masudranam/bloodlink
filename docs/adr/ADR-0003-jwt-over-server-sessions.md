# ADR-0003: A signed JWT rather than a server session

**Status:** Accepted
**Date:** 2026-09-03

## Context

SPEC-003 needs every request to carry an identity, because the privacy rule is
meaningless against anonymous traffic. Spring Security offers two well-trodden
answers: a server-side session with a `JSESSIONID` cookie, or a signed bearer
token the client stores and sends.

The consumer is a React single-page app served from a different origin in
development (Vite on 5173, the API on 8080) and potentially a different host in
production. There is no server-rendered page in this project, and there never
will be.

## Decision

Authentication is a signed JWT, HS256, sent as `Authorization: Bearer <token>`.
The filter chain is `SessionCreationPolicy.STATELESS`, CSRF protection is off,
and no response sets a cookie.

The token carries the `app_user` id as `sub` and a single `role` claim, which
Spring maps to `ROLE_DONOR` or `ROLE_REQUESTER`. It carries no name and no phone
number: anyone holding a token can read its payload, so it holds nothing worth
reading.

Signing uses a symmetric secret from `roktolink.security.jwt.secret`, and the
token lives twelve hours. There is no refresh token.

## Consequences

- **A cross-origin SPA needs no cookie choreography.** No `SameSite` reasoning, no
  CSRF token round-trip, no session affinity if the API is ever run twice.
- **CSRF stops being a threat class.** The browser attaches nothing
  automatically, so a forged cross-site request carries no credential. That is
  why disabling CSRF here is safe, and it would not be with a cookie.
- **A token cannot be withdrawn before it expires.** Deleting an account leaves
  its token valid for up to twelve hours. This is the real cost, accepted because
  a pledge is a phone number exchange and not a payment. If that changes, the fix
  is a revocation list, and it puts server state back.
- **The secret is one point of failure.** A leaked signing key mints tokens for
  every account until rotated, and rotating it invalidates every live token at
  once. Asymmetric keys would let the verifier hold only a public key; that is
  worth revisiting if anything other than this service ever validates a token.
- **Twelve hours is a compromise.** Shorter means logging in mid-task with no
  refresh flow; longer widens the window on a stolen token.

## Alternatives considered

- **Server-side sessions with a cookie.** Revocable immediately, which is a real
  advantage, and the natural choice for a server-rendered app. Rejected because
  every request would then need CSRF protection and cross-origin cookie handling
  for a client that is entirely JavaScript, and because sessions are state the
  API would have to keep.
- **Refresh tokens alongside short-lived access tokens.** The standard answer to
  the revocation problem. Rejected as out of scope for SPEC-003: it doubles the
  endpoints and introduces token rotation, storage and reuse detection, for a
  project whose sessions are minutes long in practice.
- **Asymmetric signing, RS256.** Better if the token were verified elsewhere.
  Rejected as premature: one service issues and one service verifies, so a shared
  secret is the smaller moving part.
