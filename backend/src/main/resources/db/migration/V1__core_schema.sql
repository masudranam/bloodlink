-- SPEC-002: core schema.
--
-- Flyway owns this schema outright. Hibernate runs with ddl-auto=validate, so a
-- disagreement between an entity and this file fails startup instead of quietly
-- rewriting a table.

create table thana (
    id        bigint        generated always as identity primary key,
    name      varchar(80)   not null,
    district  varchar(80)   not null,
    latitude  numeric(9, 6) not null,
    longitude numeric(9, 6) not null,
    constraint uq_thana_name unique (name),
    constraint ck_thana_latitude check (latitude between -90 and 90),
    constraint ck_thana_longitude check (longitude between -180 and 180)
);

create table hospital (
    id        bigint        generated always as identity primary key,
    name      varchar(160)  not null,
    thana_id  bigint        not null,
    latitude  numeric(9, 6) not null,
    longitude numeric(9, 6) not null,
    constraint uq_hospital_name unique (name),
    constraint fk_hospital_thana foreign key (thana_id) references thana (id) on delete restrict,
    constraint ck_hospital_latitude check (latitude between -90 and 90),
    constraint ck_hospital_longitude check (longitude between -180 and 180)
);

-- Identity is the phone number: it is what people in Bangladesh actually have,
-- and it is the field the whole privacy rule exists to protect. There is no
-- email column, because email notification is out of scope for the project.
create table app_user (
    id            bigint       generated always as identity primary key,
    full_name     varchar(120) not null,
    phone         varchar(20)  not null,
    password_hash varchar(72)  not null,
    role          varchar(16)  not null,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint uq_app_user_phone unique (phone),
    constraint ck_app_user_role check (role in ('DONOR', 'REQUESTER'))
);

-- A donor's location is their thana's centroid, reached through thana_id. Storing
-- a donor's own coordinates would mean holding a home address accurate to a few
-- metres for people whose contact details this project goes out of its way to
-- protect. SPEC-007 breaks the resulting distance ties with a secondary sort.
create table donor_profile (
    id                 bigint      generated always as identity primary key,
    user_id            bigint      not null,
    blood_group        varchar(3)  not null,
    thana_id           bigint      not null,
    last_donation_date date,
    available          boolean     not null default true,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint uq_donor_profile_user unique (user_id),
    constraint fk_donor_profile_user foreign key (user_id) references app_user (id) on delete cascade,
    constraint fk_donor_profile_thana foreign key (thana_id) references thana (id) on delete restrict,
    constraint ck_donor_profile_blood_group
        check (blood_group in ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'))
);

-- The shape SPEC-007 filters on. There is deliberately no index on
-- last_donation_date: eligibility is a computed predicate, not a lookup.
create index ix_donor_profile_group_thana on donor_profile (blood_group, thana_id);

-- Deliberately absent, and asserted by SPEC-002 AC-7: any is_eligible column, or
-- any cached copy of one. Eligibility is last_donation_date + interval < today,
-- derived at query time, every time.
