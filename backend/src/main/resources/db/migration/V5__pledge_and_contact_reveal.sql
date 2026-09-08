-- SPEC-008: pledges, and the log of every contact reveal.
--
-- These two tables are the privacy rule made structural. Before them, a phone
-- number had nowhere to be revealed; after them, there is exactly one endpoint
-- that reveals one, and exactly one table recording that it happened.

create table pledge (
    id         bigint      generated always as identity primary key,
    request_id bigint      not null,
    donor_id   bigint      not null,
    status     varchar(16) not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    -- The double pledge is refused here, not only in a service. Two concurrent
    -- pledges would both pass a service-level "have you already pledged?" check
    -- and both insert, inflating the count so a requester believes their need is
    -- met when one person offered twice.
    constraint uq_pledge_request_donor unique (request_id, donor_id),
    constraint fk_pledge_request foreign key (request_id) references blood_request (id) on delete cascade,
    constraint fk_pledge_donor foreign key (donor_id) references donor_profile (id) on delete cascade,
    constraint ck_pledge_status
        check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN'))
);

-- Answers the question that drives a request back to OPEN: are there any active
-- pledges left on it? Asked on every decline and every withdrawal.
create index ix_pledge_request_status on pledge (request_id, status);

-- The audit log. One row per successful reveal, appended and never touched again.
--
-- It carries NO foreign keys, and that is the design rather than an oversight.
-- A cascade from pledge or app_user would let deleting a donor profile delete the
-- record that a reveal happened, which is the one thing this table must never
-- permit. Declaring the same keys "on delete restrict" instead would mean a donor
-- who has revealed contact can never delete their profile - the log holding
-- people hostage. So it holds ids without constraining them, and outlives its
-- subjects in both directions.
--
-- viewer_name is denormalised on purpose, and it is not the is_eligible mistake
-- wearing a new hat. is_eligible would have stored the answer to a question that
-- keeps changing; viewer_name stores who this person was at the moment they
-- looked, which is a historical fact and cannot change afterwards. Resolving the
-- name at read time would show a later rename, or nothing at all for a deleted
-- account, and would misreport the event.
create table contact_reveal (
    id               bigint      generated always as identity primary key,
    pledge_id        bigint      not null,
    request_id       bigint      not null,
    viewer_user_id   bigint      not null,
    viewer_name      varchar(120) not null,
    viewer_role      varchar(16) not null,
    revealed_user_id bigint      not null,
    revealed_at      timestamptz not null default now(),
    constraint ck_contact_reveal_viewer_role check (viewer_role in ('DONOR', 'REQUESTER')),
    -- Nobody is revealed to themselves. A row saying otherwise would mean the
    -- party check that guards the endpoint has gone wrong.
    constraint ck_contact_reveal_not_self check (viewer_user_id <> revealed_user_id)
);

-- Serves /api/me/reveals: the log read back to the person whose number it is,
-- newest first. A log nobody can read is a reassurance, which is the thing this
-- project exists to replace.
create index ix_contact_reveal_revealed_user on contact_reveal (revealed_user_id, revealed_at desc);

-- Deliberately absent: any "contact revealed" boolean on pledge. Whether a reveal
-- has happened is a question about this table, and a flag would be a second,
-- staler answer to it.
