# ADR-0005: Haversine in native SQL

**Status:** Accepted
**Date:** 2026-09-08

## Context

SPEC-007 must return donors within N kilometres of a hospital, ranked nearest
first and paginated. Ranking and paginating are the hard part: a `LIMIT` is only
meaningful if the database is the thing doing the ordering.

Four options:

1. **PostGIS** — `geography` columns and `ST_DWithin`, with a GiST index.
2. **An external geo service** — a distance matrix API.
3. **Filter and sort in Java** — load candidate donors, compute distances in a
   loop, sort, then page the list in memory.
4. **Haversine written out in native SQL**, with the hospital's coordinates as
   bind parameters.

Two project constraints narrow this before any technical argument. External geo
services are explicitly out of scope for BloodLink, and no new dependency gets
added without asking. PostGIS is not a Maven dependency — it is a required
extension in every environment the app runs in, including CI and every
contributor's Docker Compose — which makes it the largest of the four.

## Decision

Haversine, written out once in a native query in `DonorSearchRepository`, with
the hospital's latitude and longitude bound as parameters and Earth's mean radius
as the literal 6371.

The expression is computed in an inner select aliased `"distanceKm"`, and the
outer select filters and orders on it. So the formula is written exactly once
rather than three times, and the page query and the count query share the same
inner select verbatim — a count that filtered differently from the page it counts
is the classic way for pagination to start lying.

The distance is filtered and ordered **unrounded**, and rounded to one decimal
only on the way out, in the service. Rounding first would admit a donor at
10.04 km to a 10 km search.

Filtering in Java (option 3) is rejected outright, not merely as slower: the
`LIMIT` would then apply to the wrong set, so page 2 of a search would be a
different question from page 2 of the same search a moment later.

## Consequences

- **No extension, no dependency, no network call.** The query runs on the
  PostgreSQL 16 that Compose already starts, and on the Testcontainers image CI
  already pulls.
- **The distance cannot use an index.** The point is supplied at query time, so
  no btree helps. This is accepted, and it is why SPEC-007 adds no migration: the
  four filters run in the order the planner chooses, availability and blood group
  narrow the set first (`ix_donor_profile_group_thana` from V1), and the distance
  is computed over a few hundred rows at most across 40 thanas.
- **It will not scale to national coverage unchanged.** At tens of thousands of
  donors this becomes a sequential scan with trigonometry on every row. The
  replacement is PostGIS or a bounding-box pre-filter on latitude and longitude
  before the exact distance, and the query is isolated in one repository method so
  that neither touches the service. That is a deliberate trade of headroom for
  having no extension to install today.
- **Haversine assumes a sphere**, which is off by up to about 0.5% against the
  WGS84 ellipsoid. At Dhaka distances that is metres, well inside the kilometre of
  error already accepted in ADR-0004 by using a thana centroid at all.
- **The SQL is readable, and its readability is load-bearing.** It is the one
  place in the project where the three pillars meet, so it is written to be read:
  `dp.blood_group in (:bloodGroups)` rather than a hand-written group list that
  would diverge from the tested matrix, and `cast(:today as date)` rather than
  `current_date`, which would move eligibility out of `Asia/Dhaka`. SPEC-007 AC-3
  and AC-5 assert both absences against the query text.
