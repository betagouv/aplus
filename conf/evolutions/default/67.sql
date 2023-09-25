-- !Ups

CREATE INDEX event_to_user_id_creation_date_idx ON "event" (to_user_id, creation_date DESC);



-- !Downs

DROP INDEX event_to_user_id_creation_date_idx;
