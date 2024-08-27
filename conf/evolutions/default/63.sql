-- !Ups
DROP VIEW application_metadata;

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



-- !Downs

DROP VIEW application_metadata;

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
