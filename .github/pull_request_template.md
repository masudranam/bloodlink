## What and why

<!-- One or two sentences. Link the spec and the issue. -->

Spec: `specs/SPEC-0NN-<slug>.md`
Closes #<n>

## Acceptance criteria coverage

<!--
Every AC in the spec, against the evidence that it holds. Evidence is either a
test whose method name starts with the criterion id, or manual verification
transcribed in this PR. Say which. A criterion with neither is not done, and a
criterion only partly verified says so rather than being ticked.
-->

| AC | Evidence |
| -- | -------- |
| AC-1 | `ac1_<...>` in `<TestClass>` |
| AC-2 | manual — see "Manual verification" below |
| AC-3 | partly: <what was checked, and what is still open> |

## Definition of Done

- [ ] Spec status updated to Implemented
- [ ] Every acceptance criterion traced above to an `acN_` test or to manual evidence in this PR
- [ ] Manual verification transcribed: the request made, and the response or database state it returned
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
