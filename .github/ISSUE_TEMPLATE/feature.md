---
name: Feature
about: A unit of work driven by a merged spec
title: "<short imperative title>"
labels: feature
---

## Spec

<!--
Required. No production code is written before its spec is merged.
Link the file, e.g. specs/SPEC-004-donor-profile-crud-with-computed-eligibility.md
-->

Spec file: `specs/SPEC-0NN-<slug>.md`
Spec status: Draft | Approved | In Progress | Implemented

## Goal

<!-- One line. What is true after this issue that was not true before. -->

## Depends on

<!-- Issue numbers or spec ids that must be merged first. See docs/BACKLOG.md. -->

## Acceptance criteria

<!--
Copy the AC list from the spec verbatim, ids included. If the spec's criteria
are still empty, fill them in there first and update this issue - the spec is
the source of truth, not this issue.
-->

- AC-1:
- AC-2:

## Done when

- [ ] Every AC above traced to an `acN_` test or to manual verification in the PR
- [ ] Spec status moved to Implemented
- [ ] CI green (Checkstyle, Testcontainers tests, coverage gate, frontend lint and build)
