# SPEC-008: Pledge, accept, contact reveal and audit log

**Status:** Implemented
**Issue:** #8 (backlog item 8)
**Depends on:** SPEC-007

---

## Problem

This is the privacy rule itself. In a Facebook group the requester's number is public from the
moment the post goes up, stays public forever, and reaches everyone who ever scrolled past -
which is why donors are still being cold-called months later about a request that closed.

Here contact details are the last thing exchanged, not the first. A donor pledges against a
request; the requester accepts a specific pledge; only then does each side see the other's
number, and only those two. Every reveal is written to an audit log, so the question "who has
seen my number, and why" has a real answer rather than a reassurance.

The double-pledge case matters too: the same donor pledging twice against one request inflates
the count and lets a requester believe their need is met when it is not.

## Scope

### In

- A donor pledges against a live request. The pledge starts `PENDING`, and an `OPEN` request
  becomes `PLEDGED`.
- A `pledge` table with a unique constraint on `(request_id, donor_id)`, so the double pledge is
  refused by the database and not only by a service that remembered to check.
- `PledgeStateMachine`, dependency-free, in the shape of `BloodRequestStateMachine`: 4 legal
  moves out of 16 ordered pairs, asserted exhaustively.
- The requester accepts or declines a specific pledge. The donor may withdraw their own.
- **The reveal.** `GET /api/pledges/{id}/contact` returns the other party's phone number, only
  for an `ACCEPTED` pledge, and only to the two people in it. It is the only endpoint in the
  entire project that returns a phone number.
- An append-only `contact_reveal` log. One row per successful reveal, recording who looked, whose
  number they saw, in which role, and when.
- `GET /api/me/reveals`: the log read back to the person whose number it is. A log nobody can
  read is a reassurance, which is the thing this project exists to replace.
- Returning a request to `OPEN` when its last active pledge goes away, which is SPEC-006 AC-12
  reached over HTTP for the first time.

### Out

- **Notifying anybody.** No SMS, no email, no push — out of scope for the whole project. A donor
  discovers their pledge was accepted by looking, and the accept response is what the requester
  sees.
- **Choosing for the requester.** Nothing ranks or recommends pledges. Search (SPEC-007) ranks
  donors; a pledge is a person volunteering, and the requester reads a list.
- **Revoking a reveal.** Once a number has been seen it has been seen. The log records that
  irreversibly; there is no endpoint that un-reveals, and deleting audit rows is not a feature.
- **Units and partial fulfilment.** A request for 3 units accepting one pledge is not two-thirds
  fulfilled. `unitsNeeded` stays informational, as in SPEC-006; matching pledges to units is not
  in this project.
- **A donor pledging against their own request.** Impossible by construction — roles are
  exclusive, a `DONOR` token cannot raise a request and a `REQUESTER` token cannot pledge.

## Acceptance Criteria

> Each criterion is independently testable and stated as an observable outcome.
> Test method names begin with the id (`ac1_`, `ac2_`, ...).

- **AC-1:** A donor pledges against an `OPEN` request: `201`, pledge status `PENDING`, and the
  request's status becomes `PLEDGED` in the same operation.
- **AC-2:** The same donor pledging a second time against the same request gets `409` and the
  request still has exactly one pledge. The refusal holds even if the service check is bypassed:
  `(request_id, donor_id)` is unique in the schema.
- **AC-3:** A donor whose blood group is not one the patient can receive gets `409` naming both
  groups. The answer comes from `BloodCompatibilityService`, the same table search uses.
- **AC-4:** A donor who is not eligible today gets `409` naming their next eligible date. A donor
  who last gave on `today - 91` may pledge; one who last gave on `today - 90` may not.
- **AC-5:** A donor with no profile at all gets `409`: there is no blood group to check.
- **AC-6:** A `REQUESTER` token pledging gets `403`; no token gets `401`.
- **AC-7:** Pledging against a request in a terminal status gets `409` naming the status.
- **AC-8:** The requester who raised a request lists its pledges: `200`, a page, newest first. A
  different requester gets `403`, and a donor gets `403`.
- **AC-9:** A donor lists their own pledges at `GET /api/donors/me/pledges`: `200`, only their
  own, across every request they have pledged against.
- **AC-10:** The requester accepts a `PENDING` pledge: `200`, status `ACCEPTED`. The request
  stays `PLEDGED` — accepting is not fulfilling, and SPEC-006 still owns that move.
- **AC-11:** Accepting a pledge that is already `ACCEPTED`, or is `DECLINED` or `WITHDRAWN`, gets
  `409` naming the current status and the attempted one.
- **AC-12:** Only the requester who raised the request may accept or decline: anybody else gets
  `403`. Only the donor who made the pledge may withdraw it: anybody else, including the
  requester, gets `403`.
- **AC-13:** Declining the only active pledge on a `PLEDGED` request returns that request to
  `OPEN`, so it reappears in the feed.
- **AC-14:** Withdrawing does the same — but with a second active pledge still standing, the
  request stays `PLEDGED`. "Active" means `PENDING` or `ACCEPTED`.
- **AC-15:** **The reveal.** While the pledge is `PENDING`, `GET /api/pledges/{id}/contact`
  returns `409` to both the donor and the requester, and its body contains no phone number. Once
  `ACCEPTED`, the requester receives the donor's number and the donor receives the requester's
  number.
- **AC-16:** A third party — another requester, another donor — calling the contact endpoint gets
  `403`, and no audit row is written for the attempt.
- **AC-17:** Every successful reveal appends exactly one `contact_reveal` row, recording the
  viewer, the person whose number was shown, the viewer's role and the timestamp. Calling twice
  writes two rows: a reveal is an event, not a state, and the second look is as real as the
  first.
- **AC-18:** `GET /api/me/reveals` returns the reveals of the caller's own number, newest first,
  each naming who looked, in what role, against which request, and when. It contains no phone
  number — not even the caller's own.
- **AC-19:** Across every endpoint in this spec, `GET /api/pledges/{id}/contact` is the only one
  whose response can contain a phone number. Asserted structurally: no other response type in the
  spec declares a phone field, so a phone cannot appear in one by accident.
- **AC-20:** The pledge transition table holds for all 16 ordered pairs, exactly 4 are legal, and
  no status transitions to itself.

## API Contract

### `POST /api/requests/{id}/pledges`

**Auth:** `DONOR`

No request body. A pledge is "I will give"; there is nothing to parameterise.

Response `201`:

```json
{
  "id": 4,
  "requestId": 3,
  "patientBloodGroup": "B+",
  "hospital": { "id": 1, "name": "Dhaka Medical College Hospital", "thana": "Chawkbazar" },
  "donor": {
    "donorId": 5,
    "fullName": "Rahim Uddin",
    "bloodGroup": "B-",
    "thana": { "id": 8, "name": "Chawkbazar", "district": "Dhaka" }
  },
  "status": "PENDING",
  "pledgedAt": "2026-09-08T16:40:12Z",
  "decidedAt": null
}
```

Failure modes:

| Status | When | Problem type |
| ------ | ---- | ------------ |
| 401 | No token | — |
| 403 | Not a `DONOR` | — |
| 404 | No such request | `request-not-found` |
| 409 | The donor has no profile | `donor-profile-required` |
| 409 | The donor's group is incompatible with the patient's | `incompatible-blood-group` |
| 409 | The donor is not eligible today | `donor-not-eligible` |
| 409 | The donor has already pledged against this request | `already-pledged` |
| 409 | The request is in a terminal status | `request-not-active` |

An unavailable donor (`available = false`) **may** pledge. Availability governs whether they are
offered up in search; pledging is them volunteering anyway, and refusing that would be the system
overruling a person about their own willingness.

### `GET /api/requests/{id}/pledges`

**Auth:** `REQUESTER`, and only for a request they raised. Paged like the feed
(`page`, `size` 1..100). Returns `PageResponse<PledgeResponse>`, newest first.

### `GET /api/donors/me/pledges`

**Auth:** `DONOR`. The caller's own pledges across all requests, newest first, same shape.

### `POST /api/pledges/{id}/accept` and `POST /api/pledges/{id}/decline`

**Auth:** `REQUESTER`, and only the one who raised the pledge's request.

Response `200`: the updated `PledgeResponse`.

| Status | When | Problem type |
| ------ | ---- | ------------ |
| 403 | Somebody else's request | `not-the-requester` |
| 404 | No such pledge | `pledge-not-found` |
| 409 | Not a legal move from the pledge's current status | `illegal-pledge-transition` |

### `POST /api/pledges/{id}/withdraw`

**Auth:** `DONOR`, and only the one who made the pledge. `403` (`not-the-pledging-donor`) for
anybody else, including the requester. Legal from `PENDING` and from `ACCEPTED`: a donor who
cannot come must be able to say so, and the alternative is a requester waiting for someone who
will not arrive.

### `GET /api/pledges/{id}/contact`

**Auth:** the donor of the pledge, or the requester who raised its request. Nobody else.

**This is the only endpoint in BloodLink that returns a phone number.**

Response `200`:

```json
{
  "pledgeId": 4,
  "requestId": 3,
  "counterparty": {
    "fullName": "Rahim Uddin",
    "role": "DONOR",
    "phone": "+8801712000003"
  },
  "revealedAt": "2026-09-08T16:41:03Z"
}
```

Symmetrical: the requester sees the donor, the donor sees the requester. Each call appends one
`contact_reveal` row before the response is returned; if the audit write fails, the reveal fails
with it and the transaction rolls back. An unaudited reveal is worse than a failed one.

| Status | When | Problem type |
| ------ | ---- | ------------ |
| 403 | Neither party to this pledge | `not-a-party-to-this-pledge` |
| 404 | No such pledge | `pledge-not-found` |
| 409 | The pledge is not `ACCEPTED` | `pledge-not-accepted` |

The pledge status is what gates the reveal, not the request status. A request cancelled after an
acceptance leaves two people who may still need to reach each other.

### `GET /api/me/reveals`

**Auth:** any authenticated user. Reveals **of the caller's own number**, newest first, paged.

```json
{
  "content": [
    {
      "id": 9,
      "requestId": 3,
      "pledgeId": 4,
      "viewer": { "fullName": "Karim Ahmed", "role": "REQUESTER" },
      "revealedAt": "2026-09-08T16:41:03Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

**Privacy note.** Absent from every response in this spec except the contact endpoint:

- **`phone`**, everywhere. Pledge lists, the accept and decline responses, the withdraw response
  and the reveal log all omit it. A requester reading ten pledges has ten names and no numbers.
- **The caller's own number is absent from `/api/me/reveals`.** They know it; echoing it back
  would put a phone number into a paged list response for no reason, which is exactly the shape
  this project refuses.
- **`viewer.phone` is absent from the reveal log.** Knowing who looked at your number does not
  entitle you to theirs.

## Data Model Changes

`V5__pledge_and_contact_reveal.sql` — additive, two new tables.

| Table | Change | Notes |
| ----- | ------ | ----- |
| `pledge` | add | `request_id`, `donor_id`, `status`, timestamps. **Unique `(request_id, donor_id)`** — the double pledge is a schema-level refusal. `check status in ('PENDING','ACCEPTED','DECLINED','WITHDRAWN')`. FK cascade to `blood_request` and to `donor_profile`. Index `(request_id, status)` for the "any active pledges left?" question that drives the request back to `OPEN`. |
| `contact_reveal` | add | `pledge_id`, `viewer_user_id`, `revealed_user_id`, `viewer_role`, `revealed_at`. **No foreign keys, by design** (see below). `check viewer_user_id <> revealed_user_id`. Index `(revealed_user_id, revealed_at desc)` serving `/api/me/reveals`. |

**Why the audit table has no foreign keys.** An audit row records that something happened. A
foreign key would let a cascade delete it when a profile is deleted, which is the one thing it
must never do — and `on delete restrict` instead would mean a donor who has revealed contact can
no longer delete their profile, i.e. the log would hold people hostage. It holds ids without
constraining them, so it outlives its subjects in both directions. ADR-0006 records this.

Deliberately **not** stored: any denormalised "contact revealed" boolean on `pledge`. Whether a
reveal has happened is a question about the log, and the log is the only place that knows. A flag
would be a second, staler answer to the same question — the `is_eligible` mistake in a new
costume.

## Out of Scope & Risks

- **Risk: the phone number leaks through a nearby endpoint.** The obvious version is adding
  `phone` to `PledgeResponse` "so the requester does not have to click again", which would put a
  number into a paged list. AC-19 asserts structurally that no response type in this spec except
  the contact one declares a phone field.
- **Risk: a reveal happens without an audit row.** If the audit insert is best-effort, or is
  moved after the response is composed, the log silently stops being complete. The reveal and the
  audit row are one transaction, and AC-17 counts rows rather than trusting the code path.
- **Risk: the double pledge is only refused in the service.** Two concurrent pledges would both
  pass a service-level check and both insert. The unique constraint is the real guard; AC-2 says
  so explicitly.
- **Risk: a request is stranded in `PLEDGED`.** Every pledge is declined or withdrawn, no pledge
  is left, and the request sits invisible to donors browsing an `OPEN` feed. AC-13 and AC-14
  cover both routes out.
- **Risk: eligibility is checked at pledge time and then trusted.** It is a computed predicate,
  so it can lapse between pledging and donating. That is accepted: the pledge records an
  intention at a moment, and eligibility is recomputed wherever it is asked for. Nothing caches
  it.
- **Out of scope: rate limiting the contact endpoint.** Nothing stops a party calling it fifty
  times; it writes fifty audit rows and reveals nothing new. Worth doing if this were public, and
  a distraction here.

## Decisions needing an ADR

- **ADR-0006: the contact reveal log is append-only and carries no foreign keys.**
- **ADR-0007: the reveal is a separate audited endpoint**, rather than the phone number riding
  along in the accept response.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-08 | Draft | Filled in: eight endpoints, two tables, one place a phone number may appear. |
| 2026-09-08 | Implemented | All 20 criteria verified. Audit rows observed outliving deleted users, as ADR-0006 intends. |
