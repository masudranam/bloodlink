# SPEC-004: Donor profile CRUD with computed eligibility

**Status:** Approved
**Issue:** #5 (backlog item 4)
**Depends on:** SPEC-003

---

## Problem

Nobody tracks who actually donated, so the same willing people are asked again while they are
still recovering. They either say no repeatedly until they stop answering, or they donate too
soon. The system needs to know when a donor last gave blood and to answer, at the moment of
asking, whether they may give again.

The trap is storing that answer. An `is_eligible` column is correct on the day it is written and
wrong every day after, and it drifts differently in every place that forgets to recompute it.
Eligibility is a function of `lastDonationDate`, a configurable interval defaulting to 90 days,
and today's date — so it is derived on read, everywhere, without exception. `nextEligibleDate`
is the same derivation shown forwards, so a donor can see when they become useful again.

## Scope

### In

- A donor managing their own profile: create, read, replace, delete. One profile per user,
  enforced by the unique `donor_profile.user_id` from SPEC-002.
- `isEligible` and `nextEligibleDate` computed on every read, from `lastDonationDate`, the
  configured interval, and today's date in Asia/Dhaka.
- The interval finally read from `roktolink.eligibility.donation-interval-days`, which has been
  sitting unused in `application.yml` since SPEC-001.
- **The first role-gated endpoints in the project.** Everything here requires `ROLE_DONOR`, which
  closes the gap left open by SPEC-003 AC-8, where the authority mapping was configured but
  nothing exercised it.
- A `Clock` bean, so "today" is injectable and the boundary arithmetic is testable without
  waiting for midnight.

### Out

- **Searching or listing other people's donor profiles.** SPEC-007. A donor can read their own
  profile and no one else's in this spec.
- **Recording a donation as an event.** There is no donation history table. `lastDonationDate` is
  a single date the donor maintains, and the honesty of it is theirs. A donation ledger would be
  the right answer if pledges were ever confirmed by a hospital, which is out of scope.
- **The `available` flag participating in eligibility.** They are different questions:
  `isEligible` is the 90-day rule, `available` is the donor opting in or out. SPEC-007 requires
  both, separately.
- **Administrative editing of someone else's profile.** No admin dashboards anywhere in this
  project.
- **A migration.** `donor_profile` already exists from `V1__core_schema.sql` and needs no change.

## Acceptance Criteria

- **AC-1:** `POST /api/donors/me` with a `DONOR` token and a valid body creates exactly one
  `donor_profile` row for that user and returns `201` with the profile, including
  `isEligible`, `nextEligibleDate` and `donationIntervalDays`.

- **AC-2:** A second `POST /api/donors/me` for a user who already has a profile returns `409`
  and creates no second row.

- **AC-3:** Every endpoint under `/api/donors/me` returns `403` for a valid `REQUESTER` token and
  `401` for no token. A requester cannot create, read, replace or delete a donor profile.

- **AC-4:** `GET /api/donors/me` returns `200` with the caller's own profile and the computed
  fields. `GET` for a `DONOR` who has no profile yet returns `404`.

- **AC-5:** `PUT /api/donors/me` replaces `bloodGroup`, `thanaId`, `available` and
  `lastDonationDate`, returns `200` with the recomputed fields, and updates exactly one row.
  Omitting `lastDonationDate` clears it.

- **AC-6:** `DELETE /api/donors/me` returns `204`, removes the `donor_profile` row, and leaves
  the `app_user` row intact. A second `DELETE` returns `404`.

- **AC-7:** With the interval at 90 days and today held fixed, eligibility is exactly:

  | `lastDonationDate` | `isEligible` | `nextEligibleDate` |
  | ------------------ | ------------ | ------------------ |
  | `null` | `true` | `null` |
  | `today - 92` | `true` | `today - 1` |
  | `today - 91` | `true` | `today` |
  | `today - 90` | `false` | `today + 1` |
  | `today - 89` | `false` | `today + 2` |
  | `today` | `false` | `today + 91` |

  `today - 91` is the boundary: `nextEligibleDate` lands exactly on today and the donor becomes
  eligible. A donor who is already eligible keeps a `nextEligibleDate` in the past, which is the
  date they became eligible; `isEligible` is the field a client should act on.

  That is, `nextEligibleDate = lastDonationDate + interval + 1`, and `isEligible` is
  `today >= nextEligibleDate`, which is the same statement as
  `lastDonationDate + interval < today`.

- **AC-8:** A donor who has never recorded a donation — `lastDonationDate` null — is eligible,
  with `nextEligibleDate` null rather than a date in the past.

- **AC-9:** Changing `roktolink.eligibility.donation-interval-days` to 120 and restarting changes
  `isEligible` and `nextEligibleDate` for the same unmodified row, with no data migration and no
  write. This is the criterion that proves nothing is stored.

- **AC-10:** No column whose name contains `eligib` exists anywhere in the `public` schema, still.
  The `information_schema` query from SPEC-002 AC-7 returns zero rows after this spec.

- **AC-11:** `lastDonationDate` in the future returns `400`. An unknown `thanaId` returns `400`.
  A `bloodGroup` outside the eight symbols returns `400` naming the field.

- **AC-12:** No response defined by this spec contains a phone number, including the donor's own
  profile. A donor reading their own profile has no need to be told their own number.

- **AC-13:** "Today" is evaluated in `Asia/Dhaka`, not the server's default zone. A donation
  recorded on a date that is yesterday in UTC but today in Dhaka is treated as today.

### Verification query for AC-10

```sql
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'public' AND column_name ILIKE '%eligib%';
```

## API Contract

All bodies are `application/json`. All failures are RFC 7807 `ProblemDetail`.
Every endpoint requires a bearer token with `ROLE_DONOR`.

### `POST /api/donors/me`

**Auth:** DONOR

Request:

```json
{
  "bloodGroup": "B+",
  "thanaId": 12,
  "lastDonationDate": "2026-08-15",
  "available": true
}
```

`lastDonationDate` is optional and may be null, meaning the donor has never recorded a donation.
`available` is optional and defaults to `true`.

Response `201`:

```json
{
  "id": 1,
  "bloodGroup": "B+",
  "thana": { "id": 12, "name": "Dhanmondi", "district": "Dhaka" },
  "lastDonationDate": "2026-08-15",
  "available": true,
  "isEligible": false,
  "nextEligibleDate": "2026-11-14",
  "donationIntervalDays": 90
}
```

`donationIntervalDays` is echoed so a client can explain the answer without hardcoding 90, and
so the computation is visible rather than magic.

| Status | When | Body |
| ------ | ---- | ---- |
| 400 | Bad blood group, unknown `thanaId`, `lastDonationDate` in the future, missing required field | `ProblemDetail` with an `errors` map |
| 401 | No token, or an invalid one | `ProblemDetail` |
| 403 | A valid token whose role is `REQUESTER` | `ProblemDetail` |
| 409 | This user already has a donor profile | `ProblemDetail` |

### `GET /api/donors/me`

**Auth:** DONOR

Response `200`: the same shape as above.

| Status | When |
| ------ | ---- |
| 401 / 403 | as above |
| 404 | This donor has not created a profile yet |

### `PUT /api/donors/me`

**Auth:** DONOR

Request: the same shape as `POST`. A full replacement — an omitted `lastDonationDate` clears it,
an omitted `available` resets it to `true`.

Response `200`: the profile, recomputed.

| Status | When |
| ------ | ---- |
| 400 / 401 / 403 | as above |
| 404 | No profile to replace |

**Why PUT and not PATCH:** `lastDonationDate` is nullable, and clearing a nullable field through
PATCH needs either JSON Patch or a three-state "absent, null, present" convention. Full
replacement of four fields is simpler to specify and simpler to be correct about.

### `DELETE /api/donors/me`

**Auth:** DONOR

Response `204`, no body. The `app_user` row survives: deleting a donor profile means "I am not
offering to donate", not "delete my account".

| Status | When |
| ------ | ---- |
| 401 / 403 | as above |
| 404 | No profile to delete |

**Privacy note:** no response in this spec carries a phone number, and no endpoint here exposes
any profile but the caller's own. The `thana` is returned as a name so a client can show a
location, which is deliberately coarse — a thana, never coordinates.

## Data Model Changes

**None.** `donor_profile` was created by `V1__core_schema.sql` with exactly the columns this
spec needs, and this spec adds no migration.

### Still deliberately not stored

- **No `is_eligible` column**, and no cached copy, and no materialised view. AC-9 and AC-10 exist
  to make that checkable rather than a matter of trust.
- **No `next_eligible_date` column** either. It is the same derivation shown forwards, and
  storing it would rot at exactly the same rate.
- **No donation history.** `last_donation_date` is one date, not a ledger.

### Note on database-level validation

A check constraint enforcing `last_donation_date <= current_date` is not possible: Postgres
rejects non-immutable functions in a `CHECK`, and correctly so — a row valid when written would
become invalid as the clock moves. The future-date rule is therefore enforced in the application
only, and AC-11 tests it there.

### Configuration

```yaml
roktolink:
  eligibility:
    donation-interval-days: 90
    zone: Asia/Dhaka
```

`zone` is new. Eligibility is a question about a calendar day, and the calendar day that matters
is the donor's, not the server's. Without it, a server running in UTC would call a donor eligible
six hours late.

### Java

```
com.roktolink.donor            DonorProfileController, request and response records
com.roktolink.donor.service    DonorProfileService, EligibilityCalculator, EligibilityProperties
com.roktolink.donor            DonorProfileRepository (added to the existing package)
```

`EligibilityCalculator` takes a `Clock` and the interval, and has no other dependencies — the
same discipline as `BloodCompatibilityService` in SPEC-005. It is the one piece here worth
testing exhaustively.

**Wire naming:** the JSON field is `isEligible`. A Java record component named `isEligible`
serialises as `eligible` by default, so the response record names it explicitly.

## Out of Scope & Risks

- **Risk — a donor's `lastDonationDate` is self-reported.** Nothing verifies it, so a donor who
  wants to donate too soon can simply lie, and one who forgets to update it stays invisible. The
  alternative is hospital confirmation, which needs an institutional relationship this project
  does not have. Accepted, and the honest framing is that RoktoLink reduces harm rather than
  eliminating it.
- **Risk — the interval is one number for everyone.** In practice the safe interval differs by
  sex, weight and donation type. 90 days is the common guidance for whole blood in Bangladesh.
  Config-driven, so it can be corrected without a deployment, but it is not per-donor.
- **Risk — clock skew and the day boundary.** A donor refreshing at 00:00 Dhaka time sees their
  status flip. That is correct behaviour, and it means any cache in front of this must not
  outlive the day.
- **Out of scope — soft delete.** `DELETE` removes the row. A donor who returns creates a new
  profile and re-enters their last donation date.
- **Out of scope — validating that a thana is in the donor's district.** Any seeded thana is
  acceptable.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-03 | Draft | Filled in: four endpoints, thirteen acceptance criteria, no migration. |
| 2026-09-03 | Approved | Approved on review of PR #16. |
