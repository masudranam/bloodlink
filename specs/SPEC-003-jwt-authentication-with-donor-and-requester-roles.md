# SPEC-003: JWT authentication with DONOR and REQUESTER roles

**Status:** Draft
**Issue:** #4 (backlog item 3)
**Depends on:** SPEC-002

---

## Problem

The privacy rule only means something if the system knows who is asking. "Phone numbers never
appear in a list response" is unenforceable against anonymous traffic, and "only the requester
who accepted this pledge may see the donor's number" needs an authenticated identity attached
to every request.

The two roles are genuinely different actors with different rights, not a permission flag. A
donor publishes availability and pledges; a requester publishes need and accepts. Conflating
them would let anyone manufacture a request to harvest contact details, which is precisely the
Facebook-group failure mode this project exists to fix.

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
