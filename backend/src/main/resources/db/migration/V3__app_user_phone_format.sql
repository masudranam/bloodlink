-- SPEC-003: make the canonical phone format a schema invariant.
--
-- Storage is always E.164 for a Bangladeshi mobile: +8801 followed by an operator
-- digit and eight more. The API accepts 01XXXXXXXXX, 8801XXXXXXXXX and
-- +8801XXXXXXXXX and normalises before anything reaches the database, so a person
-- cannot end up with two accounts by typing their own number two ways.
--
-- Applied to an empty table: registration does not exist until this migration's
-- own release, so there are no rows to migrate.

alter table app_user
    add constraint ck_app_user_phone_format
    check (phone ~ '^\+8801[3-9][0-9]{8}$');
