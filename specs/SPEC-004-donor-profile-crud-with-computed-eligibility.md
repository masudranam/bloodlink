# SPEC-004: Donor profile CRUD with computed eligibility

**Status:** Draft
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
and today's date - so it is derived on read, everywhere, without exception. `nextEligibleDate`
is the same derivation shown forwards, so a donor can see when they become useful again.

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
