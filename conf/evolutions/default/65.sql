-- !Ups

DROP VIEW user_group_is_in_area;
DROP VIEW user_is_in_user_group;
DROP VIEW user_is_invited_on_application;
DROP VIEW user_group_is_invited_on_application;
DROP VIEW answer_metadata;
DROP VIEW application_seen_by_user;
DROP VIEW application_metadata;


CREATE MATERIALIZED VIEW application_metadata AS
SELECT
    id,
    creation_date,
    creator_user_name,
    creator_user_id,
    creator_group_id,
    creator_group_name,
    invited_users,
    area,
    irrelevant,
    internal_id,
    closed,
    seen_by_user_ids,
    usefulness,
    closed_date,
    expert_invited,
    has_selected_subject,
    category,
    mandat_type,
    mandat_date,
    personal_data_wiped
FROM application;

CREATE MATERIALIZED VIEW application_seen_by_user AS
SELECT
  application.id AS application_id,
  seen.user_id AS user_id,
  regexp_replace(seen.last_seen_date, '\[.*\]', '')::timestamptz::timestamp AS last_seen_date,
  seen.last_seen_date AS last_seen_date_text
FROM application,
     LATERAL jsonb_to_recordset(seen_by_user_ids)
     AS seen(user_id uuid, last_seen_date text);

CREATE MATERIALIZED VIEW answer_metadata AS
SELECT
  answer.id,
  answer.application_id,
  regexp_replace(answer.creation_date, '\[.*\]', '')::timestamptz::timestamp AS creation_date,
  answer.answer_type,
  answer.creator_user_id,
  answer.creator_user_name,
  answer.invited_users,
  answer.invited_group_ids,
  answer.visible_by_helpers,
  answer.declare_application_has_irrelevant,
  answer.creation_date AS creation_date_text
FROM application,
     LATERAL jsonb_to_recordset(answers)
     AS answer(id uuid,
               application_id uuid,
               creation_date text,
               answer_type text,
               creator_user_id uuid,
               creator_user_name text,
               invited_users jsonb,
               invited_group_ids uuid[],
               visible_by_helpers boolean,
               declare_application_has_irrelevant boolean);

CREATE MATERIALIZED VIEW user_group_is_invited_on_application AS
SELECT *
FROM (
  SELECT
    unnest(invited_group_ids) AS group_id,
    id AS application_id,
    true AS at_creation
  FROM application
) a
UNION
(
  SELECT
    unnest(invited_group_ids) AS group_id,
    application_id,
    false AS at_creation
  FROM answer_metadata
);

CREATE MATERIALIZED VIEW user_is_invited_on_application AS
SELECT
  key::uuid AS user_id,
  id AS application_id,
  value::text AS user_name
FROM application,
     LATERAL jsonb_each_text(invited_users);

CREATE MATERIALIZED VIEW user_is_in_user_group AS
SELECT
  id as user_id,
  unnest(group_ids) as group_id
FROM "user";

CREATE MATERIALIZED VIEW user_group_is_in_area AS
SELECT
  id as group_id,
  unnest(area_ids) as area_id
FROM user_group;


CREATE INDEX application_creation_date_idx ON application (creation_date DESC);

CREATE INDEX answer_metadata_application_id_answer_type_idx ON answer_metadata (application_id, answer_type);
CREATE INDEX answer_metadata_creation_date_idx ON answer_metadata (creation_date DESC);

CREATE INDEX user_is_invited_on_application_user_id_application_id_idx ON user_is_invited_on_application (user_id, application_id);
CREATE INDEX user_is_invited_on_application_application_id_idx ON user_is_invited_on_application (application_id);

CREATE INDEX application_seen_by_user_user_id_application_id_idx ON application_seen_by_user (user_id, application_id);
CREATE INDEX application_seen_by_user_application_id_idx ON application_seen_by_user (application_id);
CREATE INDEX application_seen_by_user_last_seen_date_idx ON application_seen_by_user (last_seen_date);

CREATE INDEX user_group_is_invited_on_application_group_id_application_id_idx ON user_group_is_invited_on_application (group_id, application_id);
CREATE INDEX user_group_is_invited_on_application_application_id_idx ON user_group_is_invited_on_application (application_id);

CREATE INDEX user_is_in_user_group_user_id_idx ON user_is_in_user_group (user_id);
CREATE INDEX user_is_in_user_group_group_id_user_id_idx ON user_is_in_user_group (group_id, user_id);

CREATE INDEX user_group_is_in_area_area_id_group_id_idx ON user_group_is_in_area (area_id, group_id);
CREATE INDEX user_group_is_in_area_group_id_idx ON user_group_is_in_area (group_id);



-- !Downs

DROP INDEX user_group_is_in_area_group_id_idx;
DROP INDEX user_group_is_in_area_area_id_group_id_idx;

DROP INDEX user_is_in_user_group_group_id_user_id_idx;
DROP INDEX user_is_in_user_group_user_id_idx;

DROP INDEX user_group_is_invited_on_application_application_id_idx;
DROP INDEX user_group_is_invited_on_application_group_id_application_id_idx;

DROP INDEX application_seen_by_user_last_seen_date_idx;
DROP INDEX application_seen_by_user_application_id_idx;
DROP INDEX application_seen_by_user_user_id_application_id_idx;

DROP INDEX user_is_invited_on_application_application_id_idx;
DROP INDEX user_is_invited_on_application_user_id_application_id_idx;

DROP INDEX answer_metadata_creation_date_idx;
DROP INDEX answer_metadata_application_id_answer_type_idx;

DROP INDEX application_creation_date_idx;


DROP MATERIALIZED VIEW user_group_is_in_area;
DROP MATERIALIZED VIEW user_is_in_user_group;
DROP MATERIALIZED VIEW user_is_invited_on_application;
DROP MATERIALIZED VIEW user_group_is_invited_on_application;
DROP MATERIALIZED VIEW answer_metadata;
DROP MATERIALIZED VIEW application_seen_by_user;
DROP MATERIALIZED VIEW application_metadata;


CREATE VIEW application_metadata AS
SELECT
    id,
    creation_date,
    creator_user_name,
    creator_user_id,
    creator_group_id,
    creator_group_name,
    invited_users,
    area,
    irrelevant,
    internal_id,
    closed,
    seen_by_user_ids,
    usefulness,
    closed_date,
    expert_invited,
    has_selected_subject,
    category,
    mandat_type,
    mandat_date,
    personal_data_wiped
FROM application;

CREATE VIEW application_seen_by_user AS
SELECT
  application.id AS application_id,
  seen.user_id AS user_id,
  regexp_replace(seen.last_seen_date, '\[.*\]', '')::timestamptz::timestamp AS last_seen_date,
  seen.last_seen_date AS last_seen_date_text
FROM application,
     LATERAL jsonb_to_recordset(seen_by_user_ids)
     AS seen(user_id uuid, last_seen_date text);

CREATE VIEW answer_metadata AS
SELECT
  answer.id,
  answer.application_id,
  regexp_replace(answer.creation_date, '\[.*\]', '')::timestamptz::timestamp AS creation_date,
  answer.answer_type,
  answer.creator_user_id,
  answer.creator_user_name,
  answer.invited_users,
  answer.invited_group_ids,
  answer.visible_by_helpers,
  answer.declare_application_has_irrelevant,
  answer.creation_date AS creation_date_text
FROM application,
     LATERAL jsonb_to_recordset(answers)
     AS answer(id uuid,
               application_id uuid,
               creation_date text,
               answer_type text,
               creator_user_id uuid,
               creator_user_name text,
               invited_users jsonb,
               invited_group_ids uuid[],
               visible_by_helpers boolean,
               declare_application_has_irrelevant boolean);

CREATE VIEW user_group_is_invited_on_application AS
SELECT *
FROM (
  SELECT
    unnest(invited_group_ids) AS group_id,
    id AS application_id,
    true AS at_creation
  FROM application
) a
UNION
(
  SELECT
    unnest(invited_group_ids) AS group_id,
    application_id,
    false AS at_creation
  FROM answer_metadata
);

CREATE VIEW user_is_invited_on_application AS
SELECT
  key::uuid AS user_id,
  id AS application_id,
  value::text AS user_name
FROM application,
     LATERAL jsonb_each_text(invited_users);


CREATE VIEW user_is_in_user_group AS
SELECT
  id as user_id,
  unnest(group_ids) as group_id
FROM "user";


CREATE VIEW user_group_is_in_area AS
SELECT
  id as group_id,
  unnest(area_ids) as area_id
FROM user_group;
