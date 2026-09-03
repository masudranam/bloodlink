# SPEC-006: Blood request lifecycle and transition guards

**Status:** Draft
**Issue:** #6
**Depends on:** SPEC-003

---

## Problem

A Facebook post has no state. It is equally live an hour after it is written and a month after
the patient was discharged, which is why donors stop trusting any of them and why requesters
keep getting calls about a need that was met yesterday.

A request here is a small state machine - OPEN, PLEDGED, FULFILLED, plus CANCELLED and EXPIRED -
and its value comes entirely from the transitions it refuses. A fulfilled request must not
reopen for pledges; a cancelled one must not be fulfilled; an expired one must not accept a
donor who is about to cross Dhaka for nothing. The guards are the feature; the happy path is
the easy part.

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
