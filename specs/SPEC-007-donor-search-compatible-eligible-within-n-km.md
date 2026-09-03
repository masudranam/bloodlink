# SPEC-007: Donor search: compatible, eligible, within N km

**Status:** Draft
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
