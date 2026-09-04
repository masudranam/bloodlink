# ADR-0001: Record architecture decisions

**Status:** Accepted
**Date:** 2026-09-03

## Context

BloodLink is built spec-first: a spec describes what a feature must do, and its
acceptance criteria are traced to tests. Specs are deliberately about behaviour,
not structure, so they are the wrong place to record *why* a structural choice
was made — why eligibility is derived rather than stored, why distance is
Haversine in SQL rather than a geo service.

Without somewhere to put those, the reasoning survives only in commit messages
and in the head of whoever wrote it.

## Decision

Structural decisions are recorded as numbered ADRs in `docs/adr`, using
`ADR-0000-template.md`. An accepted ADR is not edited; a change of mind is a new
ADR that supersedes it.

## Consequences

- A reviewer can see why a constraint exists before arguing against it.
- The three project rules (privacy, computed eligibility, pure compatibility)
  get a durable home beyond the README.
- Small overhead per decision, which is why only expensive decisions get one.

## Alternatives considered

- **A single DECISIONS.md:** append-only files grow into something nobody reads,
  and there is no way to mark one entry superseded.
- **Nothing, rely on the specs:** specs describe behaviour and are rewritten per
  issue; structural reasoning would be lost between them.
