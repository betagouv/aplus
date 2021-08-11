# --- !Ups
DROP VIEW application_metadata;

ALTER TABLE "application" ALTER COLUMN "creator_user_name" SET DATA TYPE text;

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

ALTER TABLE "application" ALTER COLUMN "creator_user_name" SET DATA TYPE character varying(200) USING SUBSTR("creator_user_name", 1, 200);

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
