# SPEC-002: Domain model, Flyway migrations and Dhaka seed data

**Status:** Implemented
**Issue:** #3 (backlog item 2)
**Depends on:** SPEC-001

---

## Problem

Every later spec needs somewhere to put data, and every query in this project is geographic:
"who is near Dhaka Medical College Hospital right now". That means locations cannot be free
text. A thana typed by hand as "Mirpur", "Mirpur-1" and "Mirpur 1" cannot be searched by
distance, and a request that only records a hospital name cannot be ranked at all.

The schema also has to be owned by migrations from the first commit. With `ddl-auto=validate`
there is exactly one source of truth for the schema, and a migration that disagrees with an
entity fails the build rather than silently rewriting a table.

Seed data is part of the problem, not a convenience: without real thana names and coordinates,
and a handful of real hospitals (DMCH, BSMMU, Square, Evercare), the distance ranking in
SPEC-007 cannot be demonstrated or tested against anything believable.

## Scope

### In

- Four tables, created by Flyway: `thana`, `hospital`, `app_user`, `donor_profile`.
- JPA entities for those four tables, plus the `BloodGroup` and `UserRole` enums and the
  attribute converter that maps `BloodGroup` to its canonical symbol.
- Constraints that make bad data impossible rather than merely discouraged: blood group and
  role check constraints, uniqueness on phone and thana name, foreign keys, coordinate range
  checks.
- Seed data for `thana` (40 Dhaka thanas with coordinates) and `hospital` (DMCH, BSMMU,
  Square, Evercare).
- The absence of any stored eligibility flag, asserted rather than assumed.

### Out

- **Repositories, services, controllers, endpoints.** This issue adds no Java beyond entities
  and enums. Each later spec adds the repository methods its own queries need.
- **`blood_request`, `pledge`, `contact_reveal_audit` tables.** Owned by SPEC-006 and SPEC-008,
  which add their own additive migrations.
- **Password hashing, login, tokens.** SPEC-003. This spec creates the `password_hash` column
  and nothing that writes to it.
- **Seeded users or donors.** No `app_user` or `donor_profile` rows are inserted. Demo data for
  exercising search belongs with SPEC-007, where there is something to search with.
- **An `email` column.** BloodLink identifies people by phone number, and email notification is
  out of scope for the whole project, so an email column would be dead weight.

## Acceptance Criteria

- **AC-1:** Against an empty database, application startup applies migrations `V1` and `V2`, and
  `flyway_schema_history` contains exactly those two rows, both with `success = true`.

- **AC-2:** With the migrated schema in place, the application starts with
  `spring.jpa.hibernate.ddl-auto=validate` and reports no schema validation error. Dropping or
  renaming any column an entity maps makes startup fail.

- **AC-3:** `thana` contains 40 rows. Every row has a non-null `name`, `district`, `latitude`
  and `longitude`; `latitude` lies between 23.60 and 23.95 and `longitude` between 90.30 and
  90.55; `name` is unique. The rows include at least Dhanmondi, Mirpur, Shahbagh, Gulshan,
  Uttara, Motijheel and Ramna.

- **AC-4:** `hospital` contains exactly 4 rows, named `Dhaka Medical College Hospital`,
  `Bangabandhu Sheikh Mujib Medical University`, `Square Hospitals Ltd` and
  `Evercare Hospital Dhaka`. Each has non-null coordinates and a `thana_id` that resolves to an
  existing `thana` row.

- **AC-5:** `donor_profile.blood_group` accepts each of the eight symbols
  `A+ A- B+ B- AB+ AB- O+ O-` and rejects any other value with a check constraint violation
  (SQLSTATE 23514).

- **AC-6:** `app_user.role` accepts `DONOR` and `REQUESTER` and rejects any other value
  (SQLSTATE 23514). Inserting a second `app_user` with an existing `phone` fails with a unique
  violation (SQLSTATE 23505).

- **AC-7:** No column whose name contains `eligib` exists anywhere in the `public` schema — the
  verification query below returns zero rows.

- **AC-8:** `donor_profile.user_id` is unique and references `app_user(id)`. Deleting an
  `app_user` that has a `donor_profile` deletes the profile with it; deleting a `thana`
  referenced by a `donor_profile` or a `hospital` is rejected (SQLSTATE 23503).

- **AC-9:** A second startup against the already-migrated database applies no further migrations
  and passes Flyway's checksum validation, proving `V1` and `V2` were not edited after the fact.

### Verification query for AC-7

```sql
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'public' AND column_name ILIKE '%eligib%';
```

## API Contract

**None.** This spec introduces no HTTP surface. Endpoints over this data arrive with SPEC-003
(auth), SPEC-004 (donor profile) and SPEC-007 (search).

**Privacy note:** `app_user.phone` is created here and is the field the whole privacy rule
exists to protect. Because there are no endpoints yet, it is unreachable from outside the
database. Every later spec that adds a read path over `app_user` must state which projection it
returns and confirm the phone is absent from it.

## Data Model Changes

Two new migrations. Nothing existing is edited.

### `V1__core_schema.sql`

| Table | Column | Type | Constraints |
| ----- | ------ | ---- | ----------- |
| `thana` | `id` | `bigint` | PK, `generated always as identity` |
| | `name` | `varchar(80)` | not null, unique |
| | `district` | `varchar(80)` | not null |
| | `latitude` | `numeric(9,6)` | not null, check between -90 and 90 |
| | `longitude` | `numeric(9,6)` | not null, check between -180 and 180 |
| `hospital` | `id` | `bigint` | PK, identity |
| | `name` | `varchar(160)` | not null, unique |
| | `thana_id` | `bigint` | not null, FK to `thana(id)` on delete restrict |
| | `latitude` | `numeric(9,6)` | not null, range check |
| | `longitude` | `numeric(9,6)` | not null, range check |
| `app_user` | `id` | `bigint` | PK, identity |
| | `full_name` | `varchar(120)` | not null |
| | `phone` | `varchar(20)` | not null, unique |
| | `password_hash` | `varchar(72)` | not null |
| | `role` | `varchar(16)` | not null, check in (`DONOR`, `REQUESTER`) |
| | `created_at` | `timestamptz` | not null, default `now()` |
| | `updated_at` | `timestamptz` | not null, default `now()` |
| `donor_profile` | `id` | `bigint` | PK, identity |
| | `user_id` | `bigint` | not null, unique, FK to `app_user(id)` on delete cascade |
| | `blood_group` | `varchar(3)` | not null, check in the eight symbols |
| | `thana_id` | `bigint` | not null, FK to `thana(id)` on delete restrict |
| | `last_donation_date` | `date` | nullable — null means never donated |
| | `available` | `boolean` | not null, default `true` |
| | `created_at` | `timestamptz` | not null, default `now()` |
| | `updated_at` | `timestamptz` | not null, default `now()` |

Indexes: `donor_profile(blood_group, thana_id)` — the shape SPEC-007 filters on. No index on
`last_donation_date`: eligibility is a computed predicate, not a lookup.

### `V2__seed_reference_data.sql`

Plain `INSERT` statements, no upsert. 40 `thana` rows and 4 `hospital` rows.

Thanas seeded, `district = 'Dhaka'` for all forty: Adabor, Airport, Badda, Banani, Bangshal,
Bhashantek, Cantonment, Chawkbazar, Dakshinkhan, Darus Salam, Demra, Dhanmondi, Gendaria,
Gulshan, Hazaribagh, Jatrabari, Kadamtali, Kafrul, Kamrangirchar, Khilgaon, Khilkhet, Kotwali,
Lalbagh, Mirpur, Mohammadpur, Motijheel, Mugda, New Market, Pallabi, Paltan, Ramna, Rampura,
Sabujbagh, Shahbagh, Shahjahanpur, Sher-e-Bangla Nagar, Shyampur, Sutrapur, Tejgaon, Uttara.

Hospitals seeded, each with its own coordinates and a `thana_id`:

| Hospital | Thana |
| -------- | ----- |
| Dhaka Medical College Hospital | Chawkbazar |
| Bangabandhu Sheikh Mujib Medical University | Shahbagh |
| Square Hospitals Ltd | Tejgaon |
| Evercare Hospital Dhaka | Badda |

### Deliberately not stored

- **No `is_eligible` column, and no cached copy of it.** Eligibility is
  `last_donation_date + interval < today`, derived at query time, every time. AC-7 makes the
  absence checkable rather than a matter of trust.
- **No donor coordinates.** A donor's location is their thana's centroid, reached through
  `thana_id`. Storing a donor's own latitude and longitude would mean holding a home address
  precise to a few metres for people whose contact details this project goes out of its way to
  protect. The cost is that donors in one thana tie on distance, broken by a secondary sort in
  SPEC-007. This is debatable and gets an ADR when SPEC-007 is implemented.

### Java

One package per bounded area:

```
com.bloodlink.reference   Thana, Hospital
com.bloodlink.user        AppUser, UserRole
com.bloodlink.donor       DonorProfile, BloodGroup, BloodGroupConverter
```

`BloodGroup` constants are `A_POSITIVE`, `A_NEGATIVE` and so on, because `+` is not legal in a
Java identifier, while the database stores the readable symbol `A+`. A JPA `AttributeConverter`
bridges the two, so the native SQL written by hand in SPEC-007 stays readable.

## Out of Scope & Risks

- **Risk — coordinates are approximate.** Thana and hospital coordinates are centroid estimates
  accurate to roughly a kilometre, not survey data. Adequate for ranking donors by distance to a
  hospital, inadequate for anything navigational. If precision starts to matter, a later
  migration replaces the seed and nothing else changes.
- **Risk — the thana list is a snapshot.** Dhaka's police thana boundaries change. The list is
  reference data in a migration, so corrections are additive.
- **Risk — `varchar` plus check constraint rather than a native enum type.** Postgres enum types
  are awkward to alter; a check constraint is dropped and recreated in one statement, which
  matters when SPEC-006 adds request statuses.
- **Out of scope — districts beyond Dhaka.** The `district` column exists so the model is not
  Dhaka-only, but every seeded row says `Dhaka`.
- **Out of scope — soft deletes and audit columns beyond `created_at`/`updated_at`.** The audit
  trail SPEC-008 needs is a separate table, not a column on these.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-03 | Draft | Filled in: scope, nine acceptance criteria, two migrations, entity layout. |
| 2026-09-03 | Approved | Approved on review of PR #1. Implementation on feat/002. |
| 2026-09-03 | Implemented | V1 and V2 applied, entities mapped, all nine criteria verified by hand against a fresh database. |
