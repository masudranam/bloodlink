# SPEC-000: <Title>

> Copy this file to `SPEC-0NN-<kebab-title>.md` and fill it in. A spec is merged
> before a line of production code for it is written. See the README for the workflow.

**Status:** Draft
**Issue:** #<n>
**Depends on:** <SPEC ids, or "none">

---

## Problem

What is broken or missing today, in terms of the people using BloodLink — a
requester at 2am, a donor who has been called six times this month. Two or three
paragraphs. No solution here.

## Scope

### In

- <A bullet per capability this spec delivers.>

### Out

- <A bullet per adjacent thing a reader might assume is included but is not,
  with the spec or issue that owns it instead.>

## Acceptance Criteria

> Each criterion must be independently testable and stated as an observable
> outcome, not an implementation step. Number them AC-1, AC-2, ... and never
> renumber them after merge — test method names reference these ids.

- **AC-1:** <Given ... when ... then ...>
- **AC-2:** <...>

## API Contract

> Endpoints introduced or changed. Include the failure cases: a contract that
> only documents 200s is not a contract. Delete the section if the spec adds no HTTP surface.

### `<METHOD> /api/<path>`

**Auth:** <none | DONOR | REQUESTER | authenticated>

Request:

```json
{}
```

Response `200`:

```json
{}
```

Failure modes:

| Status | When | Body |
| ------ | ---- | ---- |
| 400 | <...> | `ProblemDetail` |
| 403 | <...> | `ProblemDetail` |

**Privacy note:** state explicitly which fields are *absent* from every response
in this spec, and why. Any spec that returns a phone number must say who is
allowed to see it and where the reveal is audited.

## Data Model Changes

> Flyway migration number and what it does. Tables, columns, indexes,
> constraints. Delete the section if the spec changes no schema.

`V<n>__<description>.sql`

| Table | Change | Notes |
| ----- | ------ | ----- |
| `<table>` | <add/alter> | <constraints, defaults, indexes> |

Anything deliberately *not* stored (and why) goes here too — for example,
eligibility is derived at query time and must never become a column.

## Out of Scope & Risks

- **Out of scope:** <things explicitly deferred>
- **Risk:** <what could go wrong, and how the acceptance criteria catch it>

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| YYYY-MM-DD | Draft | Created. |

<!-- Status values: Draft -> Approved -> In Progress -> Implemented -->
