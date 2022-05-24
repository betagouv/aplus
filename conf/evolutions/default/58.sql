-- !Ups

CREATE INDEX event_idx_from_user_id_creation_date_desc ON event (from_user_id, creation_date DESC);


-- !Downs

DROP INDEX event_idx_from_user_id_creation_date_desc;
