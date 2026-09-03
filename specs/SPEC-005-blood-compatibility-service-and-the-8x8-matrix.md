# SPEC-005: BloodCompatibilityService and the 8x8 matrix

**Status:** Draft
**Issue:** #5
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
