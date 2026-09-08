# SPEC-009: React client: auth, feed, search and pledge flow

**Status:** Draft
**Issue:** #9 (backlog item 9)
**Depends on:** SPEC-008

---

## Problem

The API can be perfectly private and still useless to the people it is for. A requester at a
hospital at 2am is on a phone, on mobile data, with one hand free, and what they need is a short
list of people who can help and a way to reach one of them.

The client also has to make the privacy rule visible rather than merely true. If the UI shows a
donor card with a greyed-out phone number, users will assume the number is there and the app is
hiding it. The interface should make it obvious that contact details do not exist on the client
until a pledge is accepted, and that the reveal is a deliberate, recorded step.

## Scope

### In

- Register and log in for both roles; the token held in one place, sent on every call, cleared on
  logout and on any `401`.
- Client-side routing with role-gated routes, deep links that survive a login, and a real answer
  for a route the current role may not use.
- **Donor:** the request feed, a request in detail with a pledge button, their own pledges with a
  withdraw action, their profile with server-computed eligibility, and the log of who has seen
  their number.
- **Requester:** raising a request, their own requests, a request in detail with the ranked donor
  search and the pledges on it, accept and decline, and the same reveal log.
- **The reveal, as a deliberate step.** Contact details are fetched only when somebody presses a
  button that says the look will be recorded. No screen fetches them on load.
- Every API failure rendered from the server's own `ProblemDetail`: the `errors` map against the
  right form fields, the `detail` string for everything else.
- Hand-written TypeScript types mirroring the API responses, so a phone number has nowhere to
  live in a list type.
- Plain CSS, one stylesheet, no UI kit. Mobile-first: the layout is a single column that a phone
  can use with one hand.

### Out

- **New dependencies.** See the router decision below. Nothing is added to `package.json`.
- **Frontend tests.** The project's testing rule is tests only where the logic is genuinely
  non-obvious, and the non-obvious logic in this project is on the server. The client is verified
  by hand, with the steps transcribed in the pull request, and by `tsc -b` and ESLint in CI.
- **Design polish.** No animation, no icon set, no dark mode, no skeleton loaders. Legible,
  quick and honest.
- **Offline support, service workers, optimistic updates.** A donation is not a thing to guess
  about; every action waits for the server.
- **A "my requests" server filter.** The API has no owner filter on the feed, so this screen
  filters the feed client-side. See Out of Scope & Risks — it is a real limitation, not a
  preference.

## Acceptance Criteria

> Each criterion is independently testable and stated as an observable outcome.
> Verified by hand, with the transcript in the pull request.

- **AC-1:** Registering as a `DONOR` or a `REQUESTER` logs the person straight in and shows the
  navigation for their role and no other role's.
- **AC-2:** Logging out clears the token, and visiting a protected route with no token shows the
  login screen rather than an error or a blank page.
- **AC-3:** A `401` from any call — an expired or tampered token — clears the session, returns to
  the login screen and says the session ended, rather than leaving a screen half-rendered.
- **AC-4:** A donor opening a requester-only route, or a requester opening a donor-only route,
  gets a plain "this is not a screen for your role" with a way back. No crash, no blank page, and
  no request sent that would only 403.
- **AC-5:** Pasting the URL of a protected route while logged out lands on the login screen, and
  after logging in the app continues to the URL that was asked for.
- **AC-6:** The feed lists open requests newest first with hospital, patient group, units and the
  date needed, pages with next and previous, and says so plainly when there are none.
- **AC-7:** A requester raises a request from a form. A `400` renders the API's `errors` map
  against the matching fields — `unitsNeeded`, `neededBy`, `patientBloodGroup`, `hospitalId` —
  rather than one message at the top.
- **AC-8:** A donor pledges from a request in detail. Each refusal shows the server's own
  message: no profile, incompatible group, not eligible until a named date, already pledged, and
  a request that is over.
- **AC-9:** A requester on their own request sees the ranked donor list — name, group, thana,
  distance — with a radius control. No phone number and no coordinates appear anywhere on it.
- **AC-10:** A requester sees the pledges on their request and can accept or decline one; the
  list and the request's status refresh without a page reload.
- **AC-11:** **The reveal is never automatic.** Opening a request with an accepted pledge sends
  no request to the contact endpoint. The number arrives only after pressing a button that states
  the look will be recorded, and the reveal log confirms one row per press.
- **AC-12:** Once revealed, the number is shown as a `tel:` link with the time the reveal was
  recorded, and the reveal log lists the entry.
- **AC-13:** A donor creates and edits their profile. The screen shows `isEligible`,
  `nextEligibleDate` and the interval exactly as the server computed them; the client never
  derives eligibility from a date itself.
- **AC-14:** A donor sees their own pledges across requests with each status, and can withdraw a
  `PENDING` or `ACCEPTED` one.
- **AC-15:** Both roles can see the log of who has looked at their number, with the viewer's name
  and role and the time.
- **AC-16:** No phone number is rendered anywhere in the client except the contact panel.
  Asserted structurally: exactly one component reads a `phone` field, and no list type in
  `api/types.ts` declares one.
- **AC-17:** `npm run lint` and `npm run build` both pass clean, with `strict`,
  `noUnusedLocals` and `noUnusedParameters` already on in `tsconfig.app.json`.
- **AC-18:** Every screen has a loading state and an error state. No screen renders empty on a
  failed call.

## Screens and routes

| Route | Role | What it is |
| ----- | ---- | ---------- |
| `/login` | public | Phone and password, with a link to register |
| `/register` | public | Name, phone, password, role |
| `/` | both | The open request feed |
| `/requests/new` | REQUESTER | Raise a request |
| `/requests/mine` | REQUESTER | Their own requests, all statuses |
| `/requests/:id` | both | One request. Requester: donor search, pledges, accept/decline, contact. Donor: pledge, and contact once accepted |
| `/pledges` | DONOR | Their own pledges, with withdraw |
| `/profile` | DONOR | Blood group, thana, last donation, availability, computed eligibility |
| `/reveals` | both | Who has seen my number |

Anything else renders a not-found screen with a link home.

## The router

**No new dependency.** `react-router-dom` is the obvious choice and it is not added, because the
project's rule is that no dependency arrives without being asked for, and this spec cannot ask.
Instead: about sixty lines over the History API — a `<Link>` that calls `pushState`, a
`popstate` listener, and one path-to-route match with a single `:id` segment.

This is a deliberate trade and a reviewer may reasonably want it reversed. Swapping to
`react-router-dom` later is one dependency, one provider and a rename of `<Link>`; nothing else in
the client depends on how routing is implemented.

## Data and session

- **React Query** owns all server state. No global store, no context holding fetched data.
- **The token** lives in `localStorage` under one key, read once into memory on load, and is
  attached by the single `fetch` wrapper in `api/client.ts`. Nothing else calls `fetch`.
- **The role** comes from the login response, not from decoding the JWT. The client has no
  business parsing a token it cannot verify, and a role read out of an unverified payload is a
  suggestion rather than a fact — the server decides on every call regardless.
- **Query keys** mirror the endpoints, so a mutation invalidates exactly what it changed: accepting
  a pledge invalidates that request's pledges and the request itself, nothing else.

**Privacy note.** The client's types are the second line of defence. `RequestSummary`,
`DonorMatch`, `PledgeSummary` and `RevealEntry` declare no `phone` field, so a component cannot
render one even if the server were changed to send it. `Counterparty` is the only type with a
`phone`, and `ContactPanel` is the only component that reads it. The reveal is behind a press,
never a page load, because a `useQuery` on mount would write an audit row for a page nobody
looked at — the log would fill with reveals that never happened in any meaningful sense.

## Data Model Changes

None. This spec adds no endpoint and no migration; it consumes SPEC-003 through SPEC-008 exactly
as merged.

## Out of Scope & Risks

- **Risk: a phone number reaches a list screen.** Mitigated by the types, by there being one
  component that reads `phone`, and by AC-16.
- **Risk: an audit row is written by a page load.** A `useQuery` on the contact endpoint would do
  it, and it is the kind of change that looks like a simplification. AC-11 checks the reveal log
  is untouched by opening the screen.
- **Risk: the client derives eligibility.** A `lastDonationDate + 90` in a component would be
  pillar 2 broken on the client while the server stays correct. The API already returns
  `isEligible`, `nextEligibleDate` and `donationIntervalDays`; AC-13 requires all three to come
  from the response.
- **Limitation: "my requests" filters the feed client-side.** The API has no owner filter, so this
  screen fetches each status and keeps the ones whose `requester.id` is the current user. It is
  correct but wasteful, and it would be wrong at scale. The fix is a `mine` filter on
  `GET /api/requests`, which is a change to SPEC-006's contract and therefore needs its own spec
  rather than being smuggled in here.
- **Limitation: the token is in `localStorage`.** Readable by any script on the origin, so an XSS
  becomes a stolen token. The alternative is an httpOnly cookie, which ADR-0003 ruled out for a
  cross-origin SPA. Noted rather than solved; there is no third option that does not change the
  auth design.
- **Out of scope: refreshing the token.** It lasts twelve hours and there is no refresh endpoint,
  so a long session ends with the `401` handling in AC-3.

## Status

| Date | Status | Note |
| ---- | ------ | ---- |
| 2026-09-03 | Draft | Stub created with the repo skeleton. |
| 2026-09-08 | Draft | Filled in: nine routes, no new dependencies, the reveal behind a press. |
