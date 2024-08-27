--- !Ups

DROP MATERIALIZED VIEW user_group_is_invited_on_application;
DROP MATERIALIZED VIEW answer_metadata;



CREATE TABLE answer(
  -- Not a primary key as there are duplicates, however (id, answer_order) is unique
  id uuid NOT NULL,
  application_id uuid NOT NULL,
  answer_order integer NOT NULL,
  creation_date timestamptz NOT NULL,
  answer_type text,
  message text NOT NULL,
  user_infos jsonb,
  creator_user_id uuid NOT NULL,
  creator_user_name text NOT NULL,
  invited_users jsonb NOT NULL,
  invited_group_ids uuid[] NOT NULL,
  visible_by_helpers boolean NOT NULL,
  declare_application_is_irrelevant boolean NOT NULL,
  UNIQUE (id, answer_order),
  UNIQUE (application_id, answer_order)
);

INSERT INTO answer(
  id,
  application_id,
  answer_order,
  creation_date,
  answer_type,
  message,
  user_infos,
  creator_user_id,
  creator_user_name,
  invited_users,
  invited_group_ids,
  visible_by_helpers,
  declare_application_is_irrelevant
)
SELECT
  (answer.element ->> 'id')::uuid AS id,
  application.id AS application_id,
  answer.idx AS answer_order,
  regexp_replace(answer.element ->> 'creation_date', '\[.*\]', '')::timestamptz::timestamp AS creation_date,
  answer.element ->> 'answer_type' AS answer_type,
  answer.element ->> 'message' AS message,
  (answer.element ->> 'user_infos')::jsonb AS user_infos,
  (answer.element ->> 'creator_user_id')::uuid AS creator_user_id,
  answer.element ->> 'creator_user_name' AS creator_user_name,
  (answer.element ->> 'invited_users')::jsonb AS invited_users,
  ARRAY(SELECT jsonb_array_elements_text(answer.element -> 'invited_group_ids'))::uuid[] AS invited_group_ids,
  (answer.element ->> 'visible_by_helpers')::boolean AS visible_by_helpers,
  (answer.element ->> 'declare_application_has_irrelevant')::boolean AS declare_application_is_irrelevant
FROM application,
     LATERAL jsonb_array_elements(answers) WITH ORDINALITY AS answer(element, idx)
;

UPDATE answer SET answer_type = 'custom' WHERE answer_type IS NULL;
ALTER TABLE answer ALTER COLUMN answer_type SET NOT NULL;

UPDATE answer SET user_infos = '{}'::jsonb WHERE user_infos IS NULL;
ALTER TABLE answer ALTER COLUMN user_infos SET NOT NULL;
ALTER TABLE answer ALTER COLUMN user_infos SET DEFAULT '{}'::jsonb;

CREATE INDEX answer_creation_date_idx ON answer (creation_date DESC);
CREATE INDEX answer_invited_group_ids_idx ON answer USING GIN (invited_group_ids);


-- Note: we also want a GIN index for application.invited_group_ids
CREATE INDEX application_invited_group_ids_idx ON application USING GIN (invited_group_ids);



ALTER TABLE "application" RENAME COLUMN answers TO old_answers;



CREATE VIEW answer_metadata AS
SELECT
  id,
  application_id,
  answer_order,
  creation_date,
  answer_type,
  creator_user_id,
  creator_user_name,
  invited_users,
  invited_group_ids,
  visible_by_helpers,
  declare_application_is_irrelevant,
  declare_application_is_irrelevant AS declare_application_has_irrelevant
FROM answer;



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
  FROM answer
);

-- we don't want to have an identifier too long
CREATE INDEX user_group_is_invited_on_application_group_application_idx ON user_group_is_invited_on_application (group_id, application_id);
CREATE INDEX user_group_is_invited_on_application_application_id_idx ON user_group_is_invited_on_application (application_id);





--- !Downs

DROP MATERIALIZED VIEW user_group_is_invited_on_application;
DROP VIEW answer_metadata;



ALTER TABLE "application" RENAME COLUMN old_answers TO answers;

DROP INDEX application_invited_group_ids_idx;

DROP TABLE answer;



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
