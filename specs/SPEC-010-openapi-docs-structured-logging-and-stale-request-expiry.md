# SPEC-010: OpenAPI docs, structured logging and stale request expiry

**Status:** Draft
**Issue:** #10 (backlog item 10)
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

- **A scheduled expiry job.** Requests past their `neededBy` that are still `OPEN` or `PLEDGED`
  become `EXPIRED`. Cron-driven, config-driven, and switchable off.
- The job moves requests **through `BloodRequestStateMachine`**, like every other status change
  in the project. `OPEN → EXPIRED` and `PLEDGED → EXPIRED` have been legal moves with nothing
  driving them since SPEC-006; this is what drives them.
- **A correlation id on every log line and on every response.** One id per HTTP request, in the
  MDC, so "what happened to this request" is one grep.
- **Structured event logs for the things that matter**: a pledge made, a pledge answered, a
  contact revealed, a request expired. Key-value fields, not prose.
- **A logging rule with teeth: no phone number is ever logged.** Not at DEBUG, not in an
  exception message, not by accident. Asserted by exercising every endpoint that touches a phone
  number and grepping the captured output for the digits.
- **OpenAPI polish**: every endpoint tagged and described, the bearer scheme usable from Swagger
  UI, and the document reachable at `/v3/api-docs` for a reviewer to read without running
  anything else.
- **README polish**: what it is, the three pillars, how to run it, the full endpoint list, the
  environment variables, and where the specs and ADRs are.

### Out

- **A log shipper, a metrics backend, tracing.** No Prometheus, no OpenTelemetry, no ELK. The
  correlation id is in the log line; whatever collects the line is somebody else's decision.
- **A JSON log encoder.** Considered and rejected: it would need a new dependency, and it makes
  local development harder to read for a project whose logs are read by a person in a terminal.
  Structured means consistent key-value fields, not a particular serialisation.
- **Notifying anybody that their request expired.** No SMS, no email — out of scope for the
  project, and the requester sees the status when they look.
- **A distributed lock on the job.** One instance runs it. Two would double-log and change
  nothing, because the state machine refuses the second move, but that is luck rather than
  design and is stated here rather than claimed as a feature.
- **Reviving an expired request.** `EXPIRED` is terminal (SPEC-006). A need that comes back is a
  new request with its own trail.

## Acceptance Criteria

> Each criterion is independently testable and stated as an observable outcome.
> Test method names begin with the id (`ac1_`, `ac2_`, ...).

- **AC-1:** A request whose `neededBy` is before today and whose status is `OPEN` becomes
  `EXPIRED` when the job runs, and leaves the open feed.
- **AC-2:** A `PLEDGED` request past its date also expires. Somebody offering does not extend the
  date the blood was needed by.
- **AC-3:** A request whose `neededBy` **is today** is untouched. The rule is
  `needed_by < today`, so the last day counts as live — a request needed today is needed today.
- **AC-4:** `FULFILLED`, `CANCELLED` and already-`EXPIRED` requests are untouched, and the job
  does not so much as attempt a transition on them.
- **AC-5:** The job performs every change through `BloodRequestService.transition`, so
  `BloodRequestStateMachine` decides. It has no ability to set a status directly.
- **AC-6:** "Today" is the same configured `Clock` in `Asia/Dhaka` that eligibility uses. A fixed
  clock moved forward by one day expires one more request, with the data unchanged.
- **AC-7:** The job is idempotent. Running it twice in a row expires the same requests once, and
  the second run reports zero.
- **AC-8:** The schedule is config-driven (`bloodlink.expiry.cron`) and the job can be switched
  off entirely (`bloodlink.expiry.enabled: false`), which is how the manual verification runs it
  on demand instead of waiting for a cron tick.
- **AC-9:** Each run logs one summary line with a count, and one line per expired request naming
  its id and previous status. A run that expires nothing logs at DEBUG, not INFO — a job that
  says "expired 0 requests" every hour trains people to ignore it.
- **AC-10:** Every log line produced while handling an HTTP request carries the same correlation
  id, and that id is returned in the `X-Correlation-Id` response header. A client-supplied
  `X-Correlation-Id` is echoed rather than replaced, so a caller can join its logs to the
  server's.
- **AC-11:** **No phone number is ever logged.** With the application at `DEBUG`, registering,
  logging in, pledging and revealing contact produce no log line containing the phone number in
  any format — `01712000301`, `+8801712000301` or `8801712000301`.
- **AC-12:** A contact reveal logs the event — pledge id, viewer id, revealed user id, viewer
  role — and **not** the number. The log records that a reveal happened; the database records
  what was revealed.
- **AC-13:** `GET /v3/api-docs` returns a document listing every endpoint, each under a tag, with
  the `bearer-jwt` security scheme declared. Swagger UI at `/swagger-ui.html` can authorise and
  call a protected endpoint.
- **AC-14:** The document is checkable against the privacy rule: no schema for any list or page
  response declares a `phone` property. Exactly one schema does — the contact response's
  counterparty.
- **AC-15:** The README states what the project is, the three pillars, the commands to run
  everything, the full endpoint list with its role gates, the environment variables, and where
  the specs and ADRs live. Someone who has never seen the repository can start it from the README
  alone.

## API Contract

No new endpoints. Two changes to existing behaviour:

| Change | Detail |
| ------ | ------ |
| `X-Correlation-Id` | Returned on every response. Echoed if the client sent one, generated otherwise. |
| Expiry | A request may now change status without anybody calling anything. `EXPIRED` was already a documented status and a legal target from `OPEN` and `PLEDGED`. |

**Privacy note.** This spec adds a second place a phone number could leak — the log — and AC-11
is the guard. The rule is absolute rather than best-effort: nothing logs a phone number, so there
is no scrubbing layer to get wrong and no "we redact it in production" configuration to forget.
The reveal event is logged by id only.

## Data Model Changes

**No migration.** The expiry job needs `(status, needed_by)`, which
`ix_blood_request_status_needed_by` from `V4` already provides — it was created for exactly this
reader, and `V4`'s comment says so.

Nothing about expiry is stored beyond the status itself. There is no `expired_at` column: the
status changed and `updated_at` records when, which is the same information without a second
copy of it.

## Out of Scope & Risks

- **Risk: the job writes a status directly.** It is a background process with no user to answer
  to, which makes it the most tempting place in the codebase to bypass the state machine and
  `UPDATE blood_request SET status = 'EXPIRED'`. AC-5 requires it to go through
  `transition`, so a fulfilled request can never be expired out from under its requester.
- **Risk: the boundary is off by a day.** `needed_by <= today` would expire requests on the
  morning of the day the blood is needed, which is the worst possible day to remove one from the
  feed. AC-3 pins it.
- **Risk: a phone number reaches a log.** The likely routes are an exception message that
  interpolates a user, a `toString()` on an entity, and a DEBUG line added while chasing
  something else. AC-11 greps the actual captured output rather than trusting a review.
- **Risk: the correlation id leaks across requests.** MDC is thread-local, and a value not
  cleared in a `finally` block reappears on whatever request the thread serves next, attributing
  one person's activity to another. The filter clears it unconditionally.
- **Out of scope: two instances running the job.** They would both scan and both try; the state
  machine would refuse the loser. Correct by accident, so it is recorded here rather than relied
  on.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-08 | Draft | Filled in: expiry through the state machine, a correlation id, and no phone number in any log. |
