# --- !Ups

DROP VIEW application_metadata;
-- CREATE TEMP COLUMN
ALTER TABLE "application" ADD seen_by_user_ids_temp jsonb default '[]'::jsonb;
-- MIGRATE UUID ARRAY TO JSON OBJECT ARRAY
WITH data AS (SELECT a.id,
                     json_agg(
                             row_to_json(
                                     (SELECT r
                                      FROM (SELECT a2.creation_date as last_seen_date,
                                                   a2.ids           as user_id
                                           ) r
                                     ),
                                     true
                                 )
                         ) as seen_by_user_ids
              FROM application a
                       JOIN (SELECT DISTINCT id, creation_date, unnest(a.seen_by_user_ids) AS ids FROM application a) a2
                            ON a.id = a2.id
              GROUP BY a.id)
UPDATE application a
SET seen_by_user_ids_temp = (select seen_by_user_ids from data d where d.id = a.id);
-- DROP OLD COLUMN
ALTER TABLE "application" DROP COLUMN seen_by_user_ids;
-- RENAME COLUMN TO FIT OLD ONE
ALTER TABLE "application" RENAME COLUMN seen_by_user_ids_temp TO seen_by_user_ids;
-- ADD CONSTRAINT
UPDATE application SET seen_by_user_ids = '[]'::jsonb WHERE seen_by_user_ids IS NULL;
ALTER TABLE application ALTER COLUMN seen_by_user_ids set not null;

CREATE VIEW application_metadata AS
SELECT
    id,
    creation_date,
    creator_user_name,
    creator_user_id,
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
    mandat_date
FROM application;

# --- !Downs
DROP VIEW application_metadata;
ALTER TABLE application ADD seen_by_user_ids_temp uuid[];

WITH data AS(select id, creation_date,
                    (select array_agg(t::uuid) from jsonb_array_elements_text(data.seen_by_user_ids) as x(t)) as seen_by_user_ids
             from (select id, creation_date,
                          (select jsonb_agg(t -> 'user_id') from jsonb_array_elements(seen_by_user_ids) as x(t)) as seen_by_user_ids
                   from application) as data)
UPDATE application a SET seen_by_user_ids_temp = (select seen_by_user_ids from data d where d.id = a.id);

ALTER TABLE application DROP COLUMN seen_by_user_ids;
ALTER TABLE application RENAME COLUMN seen_by_user_ids_temp TO seen_by_user_ids;

UPDATE application SET seen_by_user_ids = '{}'::uuid[] WHERE seen_by_user_ids IS NULL;
ALTER TABLE application ALTER COLUMN seen_by_user_ids set not null;

CREATE VIEW application_metadata AS
SELECT
    id,
    creation_date,
    creator_user_name,
    creator_user_id,
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
    mandat_date
FROM application;