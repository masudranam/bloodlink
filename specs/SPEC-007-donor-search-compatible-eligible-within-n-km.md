# SPEC-007: Donor search: compatible, eligible, within N km

**Status:** Implemented
**Issue:** #11 (backlog item 7)
**Depends on:** SPEC-004, SPEC-005, SPEC-006

---

## Problem

This is the query the whole project is built around, and it has three filters that must all hold
at once: the donor's group must be compatible with the patient's, the donor must be eligible
today, and the donor must be close enough to actually turn up. Dropping any one of them
reproduces the current experience - a long list of people who cannot help.

Distance is computed with Haversine in native SQL against stored coordinates, ranked nearest
first and paginated, because a requester at DMCH needs the ten closest people, not four hundred
unordered ones.

This is also where the privacy rule is most likely to break. Search returns many donors to
someone who has no relationship with any of them yet, so the response must be a projection with
no phone field to leak - not an entity with a field left blank, and not a DTO that a later
change could quietly widen.

## Scope

### In

- `GET /api/requests/{id}/donors`: the donors who could serve one blood request, nearest first,
  paginated.
- Four filters applied together in one query: compatibility, eligibility, radius, availability.
- Compatibility supplied by `BloodCompatibilityService.compatibleDonorsFor(patient)`. The 8x8
  matrix is **never** restated in SQL; the query receives a set of blood groups and filters on it.
- Eligibility expressed as `last_donation_date + interval < today`, with both the interval and
  today passed in as bind parameters derived from `EligibilityCalculator`.
- Haversine distance in native SQL from the request's hospital to each donor's thana centroid,
  rounded to one decimal place.
- A deterministic three-key sort so paging is stable: distance, then longest-rested, then id.
- A projection type with no phone accessor, and a query whose select list names no phone column.

### Out

- **Contacting a donor.** Nothing here reveals a phone number or notifies anybody. A donor
  appearing in these results has not been asked and does not know they were listed. The pledge,
  the acceptance and the contact reveal are SPEC-008.
- **Search without a request.** There is no free-form `?bloodGroup=&hospitalId=` endpoint. Every
  search hangs off a request the caller owns, which is what makes the patient group, the hospital
  and the ownership check unambiguous.
- **A spatial index, PostGIS, or a bounding-box pre-filter.** See Out of Scope & Risks.
- **Donor-side visibility of who searched for them.** No audit row is written here; a search is
  not a reveal. Audit belongs to SPEC-008, where something private actually changes hands.

## Acceptance Criteria

> Each criterion is independently testable and stated as an observable outcome.
> Test method names begin with the id (`ac1_`, `ac2_`, ...).

- **AC-1:** Given a requester and an `OPEN` request they raised, when they `GET
  /api/requests/{id}/donors`, then the response is `200` with a `PageResponse` of matches ordered
  nearest first, and `totalElements` counts every match rather than only the ones on this page.
- **AC-2:** Given a `B+` patient, when donors of all eight groups exist in the same thana, then
  exactly the `B+`, `B-`, `O+` and `O-` donors are returned and the other four are absent.
- **AC-3:** For each of the eight patient groups, the set of blood groups present in the results
  equals `BloodCompatibilityService.compatibleDonorsFor(patient)`. The service is the only source
  of that set: the repository takes it as a parameter.
- **AC-4:** With the interval at its default 90 days, a donor who last gave blood on
  `today - 90` is absent, a donor who last gave on `today - 91` is present, and a donor with no
  recorded donation is present. (The same boundary as SPEC-004 AC-5, seen through search.)
- **AC-5:** Today comes from the injected `Clock` in `Asia/Dhaka`, not from the database. Holding
  the data still and moving a fixed clock forward by one day turns an absent donor into a present
  one, and the query text contains no `current_date`.
- **AC-6:** A donor whose thana centroid is beyond the radius is absent and one inside it is
  present. Omitting `radiusKm` applies a default of 10 km.
- **AC-7:** A donor with `available = false` is absent, even when compatible, eligible and next
  door.
- **AC-8:** Results are sorted by distance ascending, then by `lastDonationDate` ascending with
  nulls first (the longest-rested donor of a tied thana comes first), then by donor id. Reading
  the same result set as two pages of size 1 returns two different donors, and never repeats or
  skips one.
- **AC-9:** `distanceKm` is the Haversine distance in kilometres rounded to one decimal, and it
  matches an independently hand-computed Haversine distance to within 0.1 km for every thana in
  the fixture.
  > **Corrected 2026-09-08, during implementation.** As merged, this criterion also claimed the
  > value would be `0.0` "for a donor whose thana is the hospital's own thana". That is wrong: a
  > hospital carries its own coordinates rather than inheriting its thana's centroid (ADR-0004),
  > so a Chawkbazar donor is 0.9 km from Dhaka Medical College, which is in Chawkbazar. The
  > clause was a mistaken example, not a requirement on the code, and no behaviour changed. It is
  > struck rather than silently edited.
- **AC-10:** `page` below 0, `size` above 100, `radiusKm` below 1 and `radiusKm` above 50 each
  return `400` with an `errors` map naming the parameter.
- **AC-11:** A `DONOR` token gets `403`; no token gets `401`.
- **AC-12:** A request raised by a different requester gets `403`; an id that does not exist gets
  `404`.
- **AC-13:** A request in a terminal status (`FULFILLED`, `CANCELLED`, `EXPIRED`) gets `409`
  naming the status. There is nobody left to find blood for.
- **AC-14:** No response body from this endpoint contains a phone number. Asserted structurally
  rather than by grepping one response: the projection type declares no phone accessor, and the
  native query's select list names no phone column.
- **AC-15:** A search that matches nobody is `200` with `content: []` and `totalElements: 0`. "No
  eligible donor nearby" is an answer, not an error.

## API Contract

### `GET /api/requests/{id}/donors`

**Auth:** `REQUESTER`, and only for a request they raised.

Query parameters:

| Name | Type | Default | Bounds | Meaning |
| ---- | ---- | ------- | ------ | ------- |
| `radiusKm` | int | `10` | 1..50 | How far from the hospital to look |
| `page` | int | `0` | >= 0 | Zero-based page |
| `size` | int | `20` | 1..100 | Page size, the same cap as the request feed |

Response `200`:

```json
{
  "content": [
    {
      "donorId": 12,
      "fullName": "Rahim Uddin",
      "bloodGroup": "O-",
      "thana": { "id": 7, "name": "Chawkbazar", "district": "Dhaka" },
      "distanceKm": 0.9,
      "lastDonationDate": null,
      "nextEligibleDate": null
    },
    {
      "donorId": 31,
      "fullName": "Nusrat Jahan",
      "bloodGroup": "B+",
      "thana": { "id": 12, "name": "Lalbagh", "district": "Dhaka" },
      "distanceKm": 1.4,
      "lastDonationDate": "2026-03-02",
      "nextEligibleDate": "2026-06-01"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

Failure modes:

| Status | When | Body |
| ------ | ---- | ---- |
| 400 | `radiusKm`, `page` or `size` outside its bounds | `ProblemDetail` + `errors` |
| 401 | No token | `ProblemDetail` |
| 403 | A `DONOR` token, or a request raised by somebody else | `ProblemDetail` |
| 404 | No request with that id | `ProblemDetail` |
| 409 | The request is in a terminal status | `ProblemDetail` |

**Privacy note.** Absent from every response in this spec:

- **`phone`** - the whole point. A requester has no relationship with these donors yet. The
  number is revealed only after a donor pledges and the requester accepts (SPEC-008), and that
  reveal is audited.
- **`userId`** - `donorId` is the profile id. Handing out user ids invites a client to try
  `/api/users/{id}` and makes a donor correlatable across endpoints.
- **`latitude` / `longitude`** - the thana centroid is an internal input to the ranking.
  `distanceKm` is derived from it, and one rounded scalar is what a requester needs.
- **`isEligible`** - every donor returned is eligible by construction. A field that is always
  `true` is noise, and publishing it would invite a client to filter on it as though it might ever
  be `false`.

`fullName` **is** present. A requester choosing between four strangers needs to see a person, and
a name on its own cannot be used to reach anybody.

## Data Model Changes

**None. No migration in this spec.**

That is a deliberate answer rather than an omission:

- The compatibility filter is served by `ix_donor_profile_group_thana` from `V1`, already in
  `(blood_group, thana_id)` order.
- Eligibility must **not** get an index. It is a predicate over `last_donation_date` compared to a
  moving today, and `V1` records the absence of that index for the same reason it records the
  absence of an `is_eligible` column.
- Haversine against a point supplied at query time cannot use a btree index at all, so adding one
  would cost writes and buy nothing.

Nothing is stored by a search. It is a read, and its results are computed from three moving inputs
- the clock, the interval and the radius - which is exactly why none of them is persisted.

## Out of Scope & Risks

- **Out of scope: PostGIS, a spatial index, or a bounding-box pre-filter.** With 40 thanas the
  distance is computed for at most a few hundred rows once the group, eligibility and availability
  filters have run. Buying an extension for that would be premature, and the query is isolated in
  one repository method so it can be replaced without touching the service.
- **Out of scope: distance to a donor's actual address.** A donor's location is their thana
  centroid, roughly a kilometre coarse, and that is a privacy decision rather than a shortcut.
  ADR-0004 records it.
- **Risk: the matrix gets reimplemented in SQL.** The obvious shortcut is an
  `in ('B+','B-','O+','O-')` list written by hand into the query, and it would silently diverge
  from the tested table. AC-3 catches it by asserting, for all eight groups, that the groups
  returned equal the service's answer.
- **Risk: `current_date` creeps into the query.** It reads as harmless, and it moves eligibility
  off the configured clock and out of `Asia/Dhaka`, breaking pillar 2 in the one place nobody
  looks. AC-5 catches it by moving a fixed clock and by asserting the query text.
- **Risk: ties make paging lie.** Every donor in one thana has an identical distance, so a sort on
  distance alone lets the database return them in any order per page, repeating some donors and
  hiding others. AC-8 catches it with two pages of size 1.
- **Risk: the projection widens.** A later spec adds a phone to the donor DTO for a good reason
  and quietly changes what search returns. AC-14 asserts the type has no such accessor, so a
  widening fails a test rather than waiting for somebody to notice.

## Decisions needing an ADR

- **ADR-0004: a donor's location is their thana centroid.** Promised by SPEC-002.
- **ADR-0005: Haversine in native SQL**, rather than PostGIS, an external geo service, or
  filtering in Java.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-08 | Draft | Filled in: four filters, one query, no migration, two ADRs. |
| 2026-09-08 | Implemented | All 15 criteria verified; AC-9's `0.0` example corrected in place, see the note there. |
