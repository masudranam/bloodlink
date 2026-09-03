# SPEC-005: BloodCompatibilityService and the 8x8 matrix

**Status:** Draft
**Issue:** #6 (backlog item 5)
**Depends on:** SPEC-001

---

## Problem

Blood group compatibility is the one piece of this system where being wrong is dangerous rather
than merely annoying. A B+ patient can receive from B+, B-, O+ and O-; getting that table
backwards - confusing "who can donate to me" with "who can I donate to" - is an easy mistake
and an invisible one, because most pairs still look plausible.

It is also the one piece with no reason to touch anything else: no database, no clock, no user.
Given two blood groups it returns an answer. Keeping it that way means all 64 combinations can
be asserted exhaustively rather than sampled, and the rest of the system can depend on it
without dragging in a Spring context.

## Scope

### In

- `BloodCompatibilityService`, with no dependencies of any kind: no repository, no clock, no
  configuration, no state.
- `canReceive(patient, donor)` — the question SPEC-007 asks once per candidate donor.
- `compatibleDonorsFor(patient)` — the same answer as a set, which is what SPEC-007 actually
  needs to build a native `IN (...)` clause rather than filtering in Java.
- An exhaustive test over all 64 ordered pairs, plus an independent derivation of the table from
  antigen rules so the test is not merely the implementation typed twice.

### Out

- **Any HTTP endpoint.** Nothing in the backlog needs one, and compatibility reaches clients
  through donor search in SPEC-007. This means the manual verification for this spec is a printed
  matrix rather than curl, which is noted below.
- **Plasma and platelet compatibility.** This spec is red cell compatibility only. Plasma
  compatibility runs the *other* way — an AB patient is a universal plasma donor and O a
  universal plasma recipient — and mixing the two into one service would be exactly the
  invisible error this spec exists to prevent.
- **Minor antigen systems and crossmatching.** Kell, Duffy, Kidd, antibody screening. Real
  transfusion medicine does a crossmatch; RoktoLink introduces two people who then go to a
  hospital that does the actual work.
- **Weak D, partial D, Bombay phenotype** and every other rare case. Eight groups, as specified.
- **Storing compatibility anywhere.** It is a pure function of its two arguments; a lookup table
  in the database would be a cache of arithmetic.

## Acceptance Criteria

- **AC-1:** `canReceive(patient, donor)` returns exactly this table for all 64 ordered pairs.
  Rows are the patient receiving, columns the donor giving; a tick means the transfusion is
  allowed.

  | patient \ donor | O- | O+ | A- | A+ | B- | B+ | AB- | AB+ |
  | --------------- | -- | -- | -- | -- | -- | -- | --- | --- |
  | **O-**  | yes | no  | no  | no  | no  | no  | no  | no  |
  | **O+**  | yes | yes | no  | no  | no  | no  | no  | no  |
  | **A-**  | yes | no  | yes | no  | no  | no  | no  | no  |
  | **A+**  | yes | yes | yes | yes | no  | no  | no  | no  |
  | **B-**  | yes | no  | no  | no  | yes | no  | no  | no  |
  | **B+**  | yes | yes | no  | no  | yes | yes | no  | no  |
  | **AB-** | yes | no  | yes | no  | yes | no  | yes | no  |
  | **AB+** | yes | yes | yes | yes | yes | yes | yes | yes |

- **AC-2:** Exactly 27 of the 64 ordered pairs are compatible. The count is asserted directly, so
  a single flipped cell fails even if some other test happens to agree with it.

- **AC-3:** `O-` is a universal donor and `AB+` a universal recipient: `canReceive(p, O-)` is true
  for all eight patients, and `canReceive(AB+, d)` is true for all eight donors.

- **AC-4:** The relation is not symmetric, and the asymmetry is asserted rather than assumed.
  `canReceive(A+, O+)` is true while `canReceive(O+, A+)` is false, and there are **exactly 19
  unordered pairs whose two directions disagree** — 38 of the 64 ordered pairs. This is the
  criterion that catches the table being transposed, a bug that leaves the 8 self-pairs looking
  correct and is otherwise invisible.

- **AC-5:** `compatibleDonorsFor(patient)` returns precisely the set of donors for which
  `canReceive(patient, donor)` is true, for all eight patients. The returned set is unmodifiable:
  attempting to add to it throws.

- **AC-6:** The service has no dependencies. Asserted by reflection: it has a public no-argument
  constructor and no non-static instance fields. A future change that injects a repository into
  it fails this test, which is the point.

- **AC-7:** A null patient or a null donor throws `NullPointerException` with a message naming
  which argument was null. Returning false for null would let a caller's bug look like an
  incompatible match.

- **AC-8:** An independent derivation agrees with the table. The test computes compatibility from
  the antigen rule — a donor's A antigen requires an A patient, a donor's B antigen requires a B
  patient, and an Rh-positive donor requires an Rh-positive patient — and asserts it matches the
  hardcoded table for all 64 pairs. Without this, the test would only prove that two copies of
  the same table agree.

## API Contract

**None.** This spec adds no HTTP surface, by the scope decision above.

**Privacy note:** not applicable in the usual sense — the service never sees a person, only two
blood groups. That is itself worth preserving: `canReceive` taking a `DonorProfile` instead of a
`BloodGroup` would be the first step towards this class needing to know who someone is.

## Data Model Changes

**None.** No migration, no table, no column. Compatibility is a pure function of its two
arguments and is computed wherever it is needed.

### Java

```
com.roktolink.donor.service    BloodCompatibilityService
```

It lives beside `EligibilityCalculator` because both are pure functions over the donor domain,
and because `BloodGroup` already lives in `com.roktolink.donor`. It matches
`com.roktolink.*.service`, so the coverage gate applies to it — which for this class should mean
100%.

**Implementation shape:** the table is written out explicitly, one line per patient group, as an
`EnumMap<BloodGroup, Set<BloodGroup>>` built from `EnumSet`s. It is deliberately *not* derived
from antigen arithmetic in production code, even though that would be shorter. A reviewer with
medical knowledge and no Java should be able to read the table and check it against a reference
chart. The antigen derivation lives in the test instead (AC-8), where the two independent
statements of the same rule can be made to agree.

## Out of Scope & Risks

- **Risk — the direction of the question.** `canReceive(patient, donor)` reads as "can this
  patient receive from this donor". Every caller must pass the patient first. The parameter names
  are `patient` and `donor` rather than `a` and `b`, and AC-4 exists precisely because a
  transposed call site would still return plausible answers for most inputs. If a future caller
  needs the other direction, it gets its own explicitly named method rather than a boolean flag.
- **Risk — this is not medical advice.** The service answers a compatibility question about eight
  groups. It does not replace a crossmatch, and a hospital does the real work. Worth keeping in
  the README's framing when SPEC-009 puts this in front of users.
- **Risk — plasma is inverted.** If RoktoLink ever handles plasma or platelets, the temptation
  will be to add a parameter to this service. That would put two opposite tables behind one
  method name. A separate service, separately tested, is the only safe answer.
- **Out of scope — caching.** `compatibleDonorsFor` returns a prebuilt immutable set; there is
  nothing to cache and nothing to invalidate.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-03 | Draft | Filled in: eight acceptance criteria, the full 8x8 table, no API and no migration. |
