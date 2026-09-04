# SPEC-006: Blood request lifecycle and transition guards

**Status:** Draft
**Issue:** #7 (backlog item 6)
**Depends on:** SPEC-003

---

## Problem

A Facebook post has no state. It is equally live an hour after it is written and a month after
the patient was discharged, which is why donors stop trusting any of them and why requesters
keep getting calls about a need that was met yesterday.

A request here is a small state machine - OPEN, PLEDGED, FULFILLED, plus CANCELLED and EXPIRED -
and its value comes entirely from the transitions it refuses. A fulfilled request must not
reopen for pledges; a cancelled one must not be fulfilled; an expired one must not accept a
donor who is about to cross Dhaka for nothing. The guards are the feature; the happy path is
the easy part.

## Scope

### In

- A requester creating a blood request against a seeded hospital.
- The five statuses and the seven transitions between them, in a
  `BloodRequestStateMachine` with no dependencies — the same discipline as
  `BloodCompatibilityService`, and testable over all 25 ordered pairs.
- A feed: any authenticated user listing open requests, paginated, newest first.
- Reading one request.
- The two transitions a requester drives directly: cancel and fulfil.
- Ownership guards: only the requester who raised a request may cancel or fulfil it.

### Out

- **Driving `OPEN → PLEDGED` over HTTP.** That transition belongs to a donor pledging, which is
  SPEC-008. The state machine implements and tests the guard now; SPEC-008 calls it. There is
  deliberately no endpoint that lets a client set an arbitrary status.
- **Driving `→ EXPIRED` over HTTP.** A request expires because time passed, not because someone
  asked. The scheduled job that performs it is SPEC-010; the guard exists here.
- **Editing a request after creation.** No `PUT /api/requests/{id}`. A request whose details were
  wrong is cancelled and raised again, so the feed never silently changes under a donor who is
  already deciding.
- **Reopening a terminal request.** `FULFILLED`, `CANCELLED` and `EXPIRED` have no outgoing
  transitions. A new need is a new request, with its own audit trail.
- **Notifying anyone of anything.** No SMS, no email, no push, anywhere in this project.
- **Pledges, contact reveal, the audit log.** SPEC-008.

## Acceptance Criteria

- **AC-1:** `POST /api/requests` with a `REQUESTER` token and a valid body creates one row with
  status `OPEN` and returns `201` with the request.

- **AC-2:** `POST /api/requests` returns `403` for a `DONOR` token and `401` for no token. A donor
  cannot raise a request.

- **AC-3:** `POST /api/requests` returns `400` naming the field for: an unknown `hospitalId`, a
  `patientBloodGroup` outside the eight symbols, `unitsNeeded` below 1 or above 10, and a
  `neededBy` date in the past.

- **AC-4:** `GET /api/requests` returns only `OPEN` requests by default, newest first, paginated,
  to any authenticated user of either role. `?status=FULFILLED` returns only fulfilled ones.

- **AC-5:** `GET /api/requests/{id}` returns `200` for a request that exists and `404` for one
  that does not.

- **AC-6:** `POST /api/requests/{id}/cancel` by the requester who raised it moves `OPEN` to
  `CANCELLED` and returns `200` with the updated status.

- **AC-7:** Cancel and fulfil return `403` for a different requester and `403` for a donor. A
  requester cannot touch another requester's request.

- **AC-8:** `POST /api/requests/{id}/fulfil` on an `OPEN` request returns `409`. A request can
  only be fulfilled from `PLEDGED` — nobody has offered blood yet, so there is nothing to
  confirm. This is the headline guard.

- **AC-9:** Every transition out of a terminal status returns `409`: cancelling a `CANCELLED`
  request, fulfilling a `FULFILLED` one, cancelling a `FULFILLED` one, and cancelling an
  `EXPIRED` one. The response names the current status and the attempted one.

- **AC-10:** The state machine permits exactly **7 of the 25 ordered pairs**, asserted
  exhaustively:

  | from \ to | OPEN | PLEDGED | FULFILLED | CANCELLED | EXPIRED |
  | --------- | ---- | ------- | --------- | --------- | ------- |
  | **OPEN**      | no  | yes | no  | yes | yes |
  | **PLEDGED**   | yes | no  | yes | yes | yes |
  | **FULFILLED** | no  | no  | no  | no  | no  |
  | **CANCELLED** | no  | no  | no  | no  | no  |
  | **EXPIRED**   | no  | no  | no  | no  | no  |

- **AC-11:** No status transitions to itself. All five self-transitions are refused, so a
  double-submitted cancel is a `409` rather than a silent success that hides a bug.

- **AC-12:** `PLEDGED → OPEN` is legal. A request whose only pledge is withdrawn returns to the
  feed rather than being stranded. SPEC-008 drives it; the guard is here.

- **AC-13:** No response defined by this spec contains a phone number — not the feed, not a
  single request, not a failure. A request carries its requester's name so a donor knows who is
  asking, and nothing more.

## API Contract

All bodies are `application/json`. All failures are RFC 7807 `ProblemDetail`.

### `POST /api/requests`

**Auth:** REQUESTER

```json
{
  "patientBloodGroup": "B+",
  "hospitalId": 1,
  "unitsNeeded": 2,
  "neededBy": "2026-09-10",
  "note": "Post-operative, ward 4"
}
```

`note` is optional, at most 500 characters.

Response `201`:

```json
{
  "id": 1,
  "patientBloodGroup": "B+",
  "hospital": { "id": 1, "name": "Dhaka Medical College Hospital", "thana": "Chawkbazar" },
  "unitsNeeded": 2,
  "neededBy": "2026-09-10",
  "status": "OPEN",
  "note": "Post-operative, ward 4",
  "requester": { "id": 3, "fullName": "Masud Rana" },
  "createdAt": "2026-09-04T04:00:00Z"
}
```

| Status | When |
| ------ | ---- |
| 400 | Unknown hospital, bad blood group, `unitsNeeded` outside 1..10, `neededBy` in the past |
| 401 | No token |
| 403 | A `DONOR` token |

### `GET /api/requests`

**Auth:** any authenticated user

Query: `status` (default `OPEN`), `page` (default 0), `size` (default 20, max 100).

Response `200`: a page object with `content`, `page`, `size`, `totalElements`, `totalPages`.
Newest first.

### `GET /api/requests/{id}`

**Auth:** any authenticated user. `200`, or `404`.

### `POST /api/requests/{id}/cancel` and `POST /api/requests/{id}/fulfil`

**Auth:** REQUESTER, and the one who raised the request.

Response `200`: the updated request.

| Status | When |
| ------ | ---- |
| 403 | A donor, or a requester who does not own this request |
| 404 | No such request |
| 409 | The transition is not legal from the current status |

The `409` body names both statuses:

```json
{
  "type": "https://bloodlink.dev/problems/illegal-transition",
  "title": "Conflict",
  "status": 409,
  "detail": "A request cannot move from FULFILLED to CANCELLED",
  "instance": "/api/requests/1/cancel"
}
```

**Privacy note:** the feed is a list response, so the privacy rule applies at its strictest.
`BloodRequestResponse` has no phone field to leak — not a nulled one. The requester's name is
included because a donor deciding whether to cross Dhaka deserves to know who is asking; their
number is not, and reaches a donor only through the accepted-pledge reveal in SPEC-008.

## Data Model Changes

### `V4__blood_request.sql`

| Column | Type | Constraints |
| ------ | ---- | ----------- |
| `id` | `bigint` | PK, identity |
| `requester_id` | `bigint` | not null, FK → `app_user(id)` on delete cascade |
| `patient_blood_group` | `varchar(3)` | not null, check in the eight symbols |
| `hospital_id` | `bigint` | not null, FK → `hospital(id)` on delete restrict |
| `units_needed` | `smallint` | not null, check between 1 and 10 |
| `needed_by` | `date` | not null |
| `status` | `varchar(16)` | not null, check in the five statuses, default `OPEN` |
| `note` | `varchar(500)` | nullable |
| `created_at` | `timestamptz` | not null, default `now()` |
| `updated_at` | `timestamptz` | not null, default `now()` |

Index `ix_blood_request_status_needed_by (status, needed_by)` — serves both the feed, which
filters on status, and SPEC-010's expiry job, which looks for open requests whose date has
passed.

### Why status is stored when eligibility is not

These look like the same decision and are not, so it is worth writing down.

Eligibility is a **pure function** of a date and a configured interval. Storing it means storing
an answer that rots as the clock moves, and the value can always be recomputed exactly.

A request's status is **not** a function of anything derivable. `CANCELLED` happened because a
person cancelled; `FULFILLED` because a person confirmed. The current state is the record of
those events and cannot be reconstructed from other columns. Storing it is not a cache — it is
the fact itself.

The one place they touch is `EXPIRED`, which is a function of `needed_by` and today. It is still
stored, because a request that expires should stay expired even if someone later edits the date,
and because SPEC-008 must be able to refuse a pledge against it atomically. The scheduled job in
SPEC-010 performs that transition rather than the read path inferring it.

### Java

```
com.bloodlink.request            BloodRequest, BloodRequestStatus, controller, DTOs
com.bloodlink.request.service    BloodRequestService, BloodRequestStateMachine
```

`BloodRequestStateMachine` has no dependencies — no repository, no clock, no user — for the same
reason `BloodCompatibilityService` has none: a table of legal moves is worth asserting
exhaustively, and that is only cheap when the thing under test needs nothing to exist.

## Out of Scope & Risks

- **Risk — a fulfilled request is self-reported.** Nothing verifies that blood was actually
  given. The same limitation as `lastDonationDate` in SPEC-004, and the same answer: RoktoLink
  reduces harm rather than eliminating it.
- **Risk — `unitsNeeded` is informational.** Nothing enforces that the number of accepted pledges
  matches it. Whether a request should auto-fulfil once enough donors are accepted is a real
  question for SPEC-008, and deliberately not answered here.
- **Risk — no rate limit on request creation.** A requester can flood the feed. Out of scope, as
  it was for login in SPEC-003.
- **Out of scope — geography in the feed.** The feed is not filtered or sorted by distance;
  ranking by distance is donor search in SPEC-007. A donor browsing the feed sees hospital names.
- **Out of scope — soft delete.** Cancelling is the soft delete, and it is a status with a
  reason, not a hidden flag.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-04 | Draft | Filled in: thirteen acceptance criteria, five endpoints, one migration, the 5x5 transition table. |
