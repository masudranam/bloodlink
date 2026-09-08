# ADR-0006: The contact reveal log is append-only and carries no foreign keys

**Status:** Accepted
**Date:** 2026-09-08

## Context

SPEC-008 records every time somebody sees somebody else's phone number. The
point of the log is that "who has seen my number, and why" has a real answer
rather than a reassurance, so the log has to be trustworthy in a way ordinary
application tables do not.

The obvious schema gives `contact_reveal` foreign keys to `pledge` and to
`app_user`, twice. Referential integrity is the default for good reasons, and
every other table in this project uses it. But both available cascade options
break the log:

- **`on delete cascade`** would let deleting a donor profile delete the records
  that reveals happened. That is the one thing this table must never permit — it
  would mean anyone can erase the evidence about themselves by deleting their
  account.
- **`on delete restrict`** would mean a donor who has ever revealed contact can
  no longer delete their own profile. The log would hold people hostage, which
  in a privacy-first project is worse than the problem it solves.

There is no third cascade option that keeps the row and allows the deletion.

## Decision

`contact_reveal` has no foreign keys. It stores `pledge_id`, `request_id`,
`viewer_user_id` and `revealed_user_id` as plain `bigint` columns, constrained
only by `viewer_user_id <> revealed_user_id`.

The table is append-only in code as well as in intent:

- `ContactRevealRepository` extends Spring Data's bare `Repository` rather than
  `JpaRepository`, and declares exactly three methods — `save`, the paged read of
  one user's reveals, and a count. `deleteById`, `deleteAll` and `saveAll` are
  never published, so there is no delete method to call by accident.
- The `ContactReveal` entity has no setters and no `@PreUpdate`. Events do not
  change their minds.

`viewer_name` is denormalised into the row. It records who this person was at the
moment they looked, which is a historical fact and cannot change afterwards.
Resolving the name at read time would report a later rename, or nothing at all
for a deleted account, and would misdescribe the event.

## Consequences

- **The log outlives its subjects in both directions.** Deleting an account
  neither destroys the rows about it nor is blocked by them. This is observable:
  during manual verification of SPEC-008, three audit rows referring to
  since-deleted users still read correctly, naming the viewer and the role while
  `revealed_user_id` points at a user that no longer exists.
- **A reveal is an event, not a state.** Two looks write two rows. Deduplicating
  them, or storing a "contact revealed" flag on `pledge` instead, would answer a
  different question from the one the log's reader is asking.
- **The database will not stop a bad id being written.** Nothing prevents a bug
  inserting a `pledge_id` that does not exist. The mitigation is that exactly one
  method in the codebase writes to this table, immediately after loading the
  pledge it is recording, in the same transaction as the reveal itself.
- **`viewer_name` can disagree with `app_user.full_name` after a rename.** That
  is intended, and it is not the `is_eligible` mistake in a new costume:
  `is_eligible` would have stored the answer to a question that keeps changing,
  while a name at a moment in the past is fixed forever.
- **Joining the log to live data needs application code.** For the one query that
  exists — the reveals of one user's number, newest first — the row is
  self-contained and needs no join at all, which is why `request_id` is stored
  alongside `pledge_id`.
