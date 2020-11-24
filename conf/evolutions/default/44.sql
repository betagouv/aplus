# --- !Ups


DROP VIEW answer_metadata;

-- add answer.invited_group_ids
CREATE VIEW answer_metadata AS
SELECT
  answer.id,
  answer.application_id,
  answer.creation_date,
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




# --- !Downs

DROP VIEW user_group_is_in_area;
DROP VIEW user_is_in_user_group;
DROP VIEW user_is_invited_on_application;
DROP VIEW user_group_is_invited_on_application;

DROP VIEW answer_metadata;

CREATE VIEW answer_metadata AS
SELECT
  answer.id,
  answer.application_id,
  answer.creation_date,
  answer.creator_user_id,
  answer.creator_user_name,
  answer.invited_users,
  answer.visible_by_helpers,
  answer.declare_application_has_irrelevant
FROM application,
     LATERAL jsonb_to_recordset(answers)
     AS answer(id uuid,
               application_id uuid,
               creation_date text,
               creator_user_id uuid,
               creator_user_name text,
               invited_users jsonb,
               visible_by_helpers boolean,
               declare_application_has_irrelevant boolean);
