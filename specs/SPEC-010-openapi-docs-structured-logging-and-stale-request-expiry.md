# SPEC-010: OpenAPI docs, structured logging and stale request expiry

**Status:** Draft
**Issue:** #10
**Depends on:** SPEC-009

---

## Problem

Two loose ends remain once the features work. The first is that a request nobody cancels stays
OPEN forever: the patient was treated, the requester moved on, and the entry sits in the feed
misleading donors - the buried-post problem in a new form. Expiry has to happen without anyone
remembering to do it.

The second is operability. A privacy-critical system needs logs that can answer "what happened
to this request" without printing the things it promised to protect, and a published API
contract a reviewer can read to confirm that no list endpoint returns a phone number.

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
