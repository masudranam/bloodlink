-- SPEC-006: blood requests and their lifecycle.
--
-- The status column is stored, unlike eligibility, and the distinction is
-- deliberate. Eligibility is a pure function of a date and an interval, so
-- storing it stores an answer that rots. A status is the record of something a
-- person did - somebody cancelled, somebody confirmed - and cannot be
-- reconstructed from any other column. Storing it is not a cache, it is the fact.

create table blood_request (
    id                  bigint      generated always as identity primary key,
    requester_id        bigint      not null,
    patient_blood_group varchar(3)  not null,
    hospital_id         bigint      not null,
    units_needed        smallint    not null,
    needed_by           date        not null,
    status              varchar(16) not null default 'OPEN',
    note                varchar(500),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint fk_blood_request_requester foreign key (requester_id) references app_user (id) on delete cascade,
    constraint fk_blood_request_hospital foreign key (hospital_id) references hospital (id) on delete restrict,
    constraint ck_blood_request_blood_group
        check (patient_blood_group in ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    constraint ck_blood_request_units check (units_needed between 1 and 10),
    constraint ck_blood_request_status
        check (status in ('OPEN', 'PLEDGED', 'FULFILLED', 'CANCELLED', 'EXPIRED'))
);

-- Serves both readers of this table: the feed filters on status and orders by
-- recency, and SPEC-010's expiry job looks for open requests whose date has gone.
create index ix_blood_request_status_needed_by on blood_request (status, needed_by);

-- There is deliberately no constraint requiring needed_by to be in the future.
-- Postgres refuses non-immutable functions such as current_date in a CHECK, and
-- rightly: a row valid when written would rot as the clock moves. The rule is
-- enforced in the application, and a request whose date has passed is moved to
-- EXPIRED by a scheduled job rather than becoming invalid where it sits.
