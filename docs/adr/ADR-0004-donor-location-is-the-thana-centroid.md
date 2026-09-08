# ADR-0004: A donor's location is their thana centroid

**Status:** Accepted
**Date:** 2026-09-08

## Context

SPEC-007 ranks donors by how far they are from the hospital in a blood request.
That needs a coordinate for each donor, and there are three places one could come
from:

1. **The donor's own address**, geocoded once at registration and stored as
   latitude and longitude on `donor_profile`.
2. **The donor's device**, captured at some point and refreshed.
3. **The centroid of the donor's thana**, chosen from a list of 40 at
   registration, stored once in reference data and shared by every donor there.

The first two are more accurate, and accuracy is the whole point of a distance
ranking. A requester at Dhaka Medical College wants the closest ten people, and
a thana is a few kilometres across.

But BloodLink exists because a donor's contact details currently spread
uncontrollably: numbers get copied into Facebook groups and the person gets
called for years. A home address accurate to a few metres is a strictly worse
thing to leak than a phone number, and this project would be holding one for
every donor in order to sort a list.

The privacy rule protects the phone number at the API boundary. It would be
incoherent to protect the number while storing something more sensitive behind
it.

## Decision

A donor's location is the centroid of the thana they selected. It is stored once
on `thana` as reference data, and `donor_profile` reaches it through `thana_id`.
There is no latitude or longitude column on `donor_profile`, and there is no
geocoding step anywhere in the project.

Coordinates are never returned to a client, for a donor or for a hospital. The
search response carries one derived scalar, `distanceKm`, rounded to one decimal
place because a centroid is only accurate to about a kilometre and further digits
would be false precision.

A hospital, by contrast, does carry its own coordinates. It is one building at a
publicly known address, and it is the fixed point distance is measured from.

## Consequences

- **The distance is a "which part of the city" answer, not a "which street"
  answer.** For deciding who to ask first in Dhaka, that is the right
  granularity. It would not be for a rural district with thanas 30 km across, and
  that is a real limitation of the model rather than a bug.
- **Every donor in one thana ties on distance exactly.** This is the sharpest
  consequence, and it is not cosmetic: a sort on distance alone lets Postgres
  return tied rows in any order, so paging repeats some donors and hides others.
  SPEC-007 therefore sorts on three keys — distance, then `lastDonationDate`
  ascending with nulls first, then donor id — and AC-8 asserts it with two pages
  of size 1.
- **The tie-break does useful work.** Breaking towards the longest-rested donor
  spreads the asking around instead of exhausting whoever happens to sort first,
  which is one of the complaints the project exists to answer.
- **A donor cannot be located from anything BloodLink stores or returns.** The
  most precise fact in the database about where a donor lives is the name of an
  administrative area containing hundreds of thousands of people.
- **Nothing needs re-geocoding when a donor moves.** They pick a different thana.
- **If finer location is ever genuinely needed**, it is an additive migration and
  a change to one repository method — but it would also be a reversal of this
  decision and would need its own ADR arguing why the exposure is now worth it.
