-- !Ups

CREATE VIEW application_seen_by_user AS
SELECT
  application.id AS application_id,
  seen.user_id AS user_id,
  regexp_replace(seen.last_seen_date, '\[.*\]', '')::timestamptz::timestamp AS last_seen_date,
  seen.last_seen_date AS last_seen_date_text
FROM application,
     LATERAL jsonb_to_recordset(seen_by_user_ids)
     AS seen(user_id uuid, last_seen_date text);



-- because answer_metadata depends on it
DROP VIEW user_group_is_invited_on_application;

DROP VIEW answer_metadata;

-- change answer.creation_date to a timestamp
-- (text one in answer_metadata.creation_date_text)
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




-- !Downs


DROP VIEW user_group_is_invited_on_application;
DROP VIEW answer_metadata;

CREATE VIEW answer_metadata AS
SELECT
  answer.id,
  answer.application_id,
  answer.creation_date,
  answer.answer_type,
  answer.creator_user_id,
  answer.creator_user_name,
  answer.invited_users,
  answer.invited_group_ids,
  answer.visible_by_helpers,
  answer.declare_application_has_irrelevant
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



DROP VIEW application_seen_by_user;
