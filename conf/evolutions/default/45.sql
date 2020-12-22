# --- !Ups

CREATE INDEX event_idx_code_creation_date_desc ON event (code, creation_date DESC);


# --- !Downs

DROP INDEX event_idx_code_creation_date_desc;
