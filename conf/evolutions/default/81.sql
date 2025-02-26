-- !Ups

ALTER TABLE user_group ADD is_in_france_services_network BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE application ADD is_in_france_services_network BOOLEAN NOT NULL DEFAULT true;

DROP MATERIALIZED VIEW application_metadata;

CREATE MATERIALIZED VIEW application_metadata AS
SELECT
    id,
    creation_date,
    creator_user_name,
    creator_user_id,
    creator_group_id,
    creator_group_name,
    invited_users,
    is_in_france_services_network,
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

DROP MATERIALIZED VIEW application_metadata;

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

ALTER TABLE application DROP is_in_france_services_network;
ALTER TABLE user_group DROP is_in_france_services_network;
