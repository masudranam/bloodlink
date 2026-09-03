# SPEC-002: Domain model, Flyway migrations and Dhaka seed data

**Status:** Draft
**Issue:** #2
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

_To be defined._

### Out

_To be defined._

## Acceptance Criteria

> Numbered AC-1..AC-n, each independently testable. Filled in before
> implementation starts. Every criterion needs at least one test whose method
> name begins with its id (`ac1_`, `ac2_`, ...).

_To be defined._

## API Contract

_To be defined._

## Data Model Changes

_To be defined._

## Out of Scope & Risks

_To be defined._

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
