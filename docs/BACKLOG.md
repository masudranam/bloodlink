# RoktoLink backlog

Ten issues, in dependency order. Each one has a spec in `/specs`; no production
code is written for an issue before its spec is merged with filled-in acceptance
criteria. See the README for the workflow.

| # | GitHub | Title | Goal (one line) | Spec | Depends on |
| - | ------ | ----- | --------------- | ---- | ---------- |
| 1 | [#2](https://github.com/masudranam/roktolink/issues/2) | Repo skeleton, Docker Compose, CI green on empty test | A backend that starts, a frontend that builds, Postgres in Compose, and a pipeline that already enforces the gates. | [SPEC-001](../specs/SPEC-001-repo-skeleton-local-infrastructure-and-green-ci.md) | — |
| 2 | [#3](https://github.com/masudranam/roktolink/issues/3) | Domain model, Flyway migrations, seed data | Real tables owned by Flyway, seeded with Dhaka thanas and hospitals that have coordinates. | [SPEC-002](../specs/SPEC-002-domain-model-flyway-migrations-and-dhaka-seed-data.md) | 1 |
| 3 | [#4](https://github.com/masudranam/roktolink/issues/4) | JWT auth, roles DONOR and REQUESTER, BCrypt | Every request carries a known identity with one of two roles. | [SPEC-003](../specs/SPEC-003-jwt-authentication-with-donor-and-requester-roles.md) | 2 |
| 4 | [#5](https://github.com/masudranam/roktolink/issues/5) | Donor profile CRUD + computed eligibility | A donor record whose `isEligible` and `nextEligibleDate` are derived on read, never stored. | [SPEC-004](../specs/SPEC-004-donor-profile-crud-with-computed-eligibility.md) | 3 |
| 5 | [#6](https://github.com/masudranam/roktolink/issues/6) | BloodCompatibilityService + 8x8 matrix | One dependency-free service answering "can this group receive from that group", proven for all 64 pairs. | [SPEC-005](../specs/SPEC-005-blood-compatibility-service-and-the-8x8-matrix.md) | 1 |
| 6 | [#7](https://github.com/masudranam/roktolink/issues/7) | Blood request lifecycle | A request that moves OPEN → PLEDGED → FULFILLED, can be CANCELLED or EXPIRED, and refuses every other move. | [SPEC-006](../specs/SPEC-006-blood-request-lifecycle-and-transition-guards.md) | 3 |
| 7 | [#11](https://github.com/masudranam/roktolink/issues/11) | Donor search: compatible, eligible, within N km | The core query — Haversine in native SQL, ranked by distance, paginated, projected without a phone field. | [SPEC-007](../specs/SPEC-007-donor-search-compatible-eligible-within-n-km.md) | 4, 5, 6 |
| 8 | [#8](https://github.com/masudranam/roktolink/issues/8) | Pledge → accept → contact reveal + audit log | The privacy rule: numbers exchanged only after acceptance, every reveal recorded. | [SPEC-008](../specs/SPEC-008-pledge-accept-contact-reveal-and-audit-log.md) | 7 |
| 9 | [#9](https://github.com/masudranam/roktolink/issues/9) | React client | Auth, request feed, donor search and the pledge flow behind protected routes. | [SPEC-009](../specs/SPEC-009-react-client-auth-feed-search-and-pledge-flow.md) | 8 |
| 10 | [#10](https://github.com/masudranam/roktolink/issues/10) | Docs, logging, expiry job, polish | OpenAPI published, structured logs that leak nothing, and a scheduled job that expires stale requests. | [SPEC-010](../specs/SPEC-010-openapi-docs-structured-logging-and-stale-request-expiry.md) | 9 |

> **Numbering.** The two columns differ because the repository's first pull
> request took number 1, and GitHub draws issues and pull requests from the same
> sequence. Backlog numbers are what we say out loud ("issue 7"); the GitHub
> column is what `Closes #N` must use. Backlog item 7 is `#11` rather than `#8`
> because its first creation attempt timed out and it was created last.


## Dependency graph

```
1 ──┬── 2 ── 3 ──┬── 4 ──┐
    │            │       │
    │            └── 6 ──┼── 7 ── 8 ── 9 ── 10
    │                    │
    └── 5 ───────────────┘
```

Issue 5 is the only one that can be built in parallel with the 2 → 3 → 4 chain:
`BloodCompatibilityService` has no dependencies, not even on the database.

## Status

| # | Spec status | Notes |
| - | ----------- | ----- |
| 1 | Draft | Skeleton and harness are in place; acceptance criteria still to be written. |
| 2 | Implemented | Schema, entities and seed data delivered; verified by hand, no automated tests by design. |
| 3 | Implemented | Register, login and /me with BCrypt and a stateless JWT chain; Swagger UI added. |
| 4 | Implemented | Donor profile CRUD with isEligible and nextEligibleDate computed on every read. |
| 5 | Implemented | Dependency-free service, 8x8 table asserted exhaustively and cross-checked against antigen rules. |
| 6 | Draft | |
| 7 | Draft | |
| 8 | Draft | |
| 9 | Draft | |
| 10 | Draft | |

## Explicitly not on this backlog

SMS and email notification, push notification, file upload, an admin dashboard,
in-app chat, Google Places or any real map service, and multi-language support.
Distance is Haversine over stored lat/lng in native SQL, and nothing else.
