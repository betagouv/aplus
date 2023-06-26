-- !Ups

-- Adds answer order in the materialized view


DROP MATERIALIZED VIEW user_group_is_invited_on_application;
DROP MATERIALIZED VIEW answer_metadata;

-- Note that jsonb_to_recordset does not support WITH ORDINALITY
CREATE MATERIALIZED VIEW answer_metadata AS
SELECT
  (answer.element ->> 'id')::uuid AS id,
  application.id AS application_id,
  answer.idx AS answer_order,
  regexp_replace(answer.element ->> 'creation_date', '\[.*\]', '')::timestamptz::timestamp AS creation_date,
  answer.element ->> 'answer_type' AS answer_type,
  (answer.element ->> 'creator_user_id')::uuid AS creator_user_id,
  answer.element ->> 'creator_user_name' AS creator_user_name,
  (answer.element ->> 'invited_users')::jsonb AS invited_users,
  ARRAY(SELECT jsonb_array_elements_text(answer.element -> 'invited_group_ids'))::uuid[] AS invited_group_ids,
  (answer.element ->> 'visible_by_helpers')::boolean AS visible_by_helpers,
  (answer.element ->> 'declare_application_has_irrelevant')::boolean AS declare_application_has_irrelevant,
  answer.element ->> 'creation_date' AS creation_date_text
FROM application,
     LATERAL jsonb_array_elements(answers) WITH ORDINALITY AS answer(element, idx);

CREATE INDEX answer_metadata_application_id_answer_type_idx ON answer_metadata (application_id, answer_type);
CREATE INDEX answer_metadata_creation_date_idx ON answer_metadata (creation_date DESC);


-- No changes here
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

CREATE INDEX user_group_is_invited_on_application_group_id_application_id_idx ON user_group_is_invited_on_application (group_id, application_id);
CREATE INDEX user_group_is_invited_on_application_application_id_idx ON user_group_is_invited_on_application (application_id);



-- !Downs

DROP MATERIALIZED VIEW user_group_is_invited_on_application;
DROP MATERIALIZED VIEW answer_metadata;

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

CREATE INDEX answer_metadata_application_id_answer_type_idx ON answer_metadata (application_id, answer_type);
CREATE INDEX answer_metadata_creation_date_idx ON answer_metadata (creation_date DESC);


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

CREATE INDEX user_group_is_invited_on_application_group_id_application_id_idx ON user_group_is_invited_on_application (group_id, application_id);
CREATE INDEX user_group_is_invited_on_application_application_id_idx ON user_group_is_invited_on_application (application_id);
