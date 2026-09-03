## What and why

<!-- One or two sentences. Link the spec and the issue. -->

Spec: `specs/SPEC-0NN-<slug>.md`
Closes #<n>

## Acceptance criteria coverage

<!--
List every AC in the spec against the test that proves it. Method names must
start with the criterion id so a reviewer can grep for `ac3_` and find it.
-->

| AC | Test |
| -- | ---- |
| AC-1 | `ac1_<...>` in `<TestClass>` |
| AC-2 | `ac2_<...>` in `<TestClass>` |

## Definition of Done

- [ ] Spec status updated to Implemented
- [ ] One or more `acN_` tests per acceptance criterion
- [ ] Jacoco line coverage >= 80% on the service layer (build fails otherwise)
- [ ] OpenAPI spec regenerated
- [ ] No new Checkstyle or ESLint warnings
- [ ] README updated if setup changed

## Privacy check

- [ ] No list or search response in this change carries a phone number
- [ ] Any contact reveal added here is gated on an accepted pledge and written to the audit log
- [ ] No `isEligible` column, cached flag or denormalised copy was introduced

<!-- If any privacy box does not apply, say why rather than deleting it. -->

## Notes for the reviewer

<!-- Anything deliberately left out, follow-up work, or a decision worth an ADR. -->
