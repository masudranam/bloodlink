# ADR-0007: The reveal is a separate audited endpoint

**Status:** Accepted
**Date:** 2026-09-08

## Context

SPEC-008 has to get two phone numbers to two people at the moment a requester
accepts a donor's pledge. The simplest version puts the number in the response to
`POST /api/pledges/{id}/accept`: the requester accepts and receives the donor's
number in the same round trip, which is one fewer request and obviously what they
wanted.

The trouble is what that shape implies elsewhere. The donor was not the one who
called accept, so they still need somewhere to read the requester's number —
which means either the pledge list grows a phone field for the accepted pledge,
or a second endpoint exists anyway. And a phone number in the accept response has
to be recorded as a reveal, so the accept handler acquires an audit write inside
an operation that is really about a status change.

The privacy rule is easy to state and easy to erode. Every erosion looks like a
small convenience: a field added to a list DTO so the client does not have to
click again, a number included in a response that already had the data loaded.

## Decision

The reveal is its own endpoint: `GET /api/pledges/{id}/contact`. It is the only
endpoint in BloodLink that returns a phone number, and every successful call
appends one row to `contact_reveal` in the same transaction.

Accepting a pledge returns a pledge. It contains no phone number, exactly like
every other pledge response.

The reveal is symmetrical — the requester sees the donor, the donor sees the
requester — and it is gated on the pledge's status rather than the request's, so
a request cancelled after an acceptance still leaves two people able to reach
each other.

It lives in its own service, `ContactRevealService`, which no other operation
calls.

## Consequences

- **There is one place to read to know when a number leaves the database.** One
  service, one method, one audit write. A reviewer asking "where can a phone
  number get out?" has a single answer rather than a set of conditions spread
  across handlers.
- **"How many times was this number seen" is answerable.** Because looking is a
  distinct act, the log counts looks rather than acceptances. Two looks are two
  rows, and SPEC-008 AC-17 asserts exactly that.
- **A client can render a pledge list having never held a phone number.** The
  number arrives only when a person deliberately asks for it, which is also the
  moment they can be told it was recorded — the response echoes back the audit
  row's own timestamp.
- **The cost is one extra round trip**, at the moment it matters least: the
  requester has already decided, and is about to make a phone call.
- **The reveal can be refused independently of the acceptance.** A withdrawn
  pledge stops revealing immediately while the acceptance that happened stays in
  the record — which would be incoherent if the number had been handed over as
  part of accepting.
- **Rate limiting is now possible in one place** if this were ever public. It is
  deliberately not implemented: fifty calls write fifty audit rows and reveal
  nothing the caller did not already have.
