-- !Ups

CREATE INDEX event_idx_creation_date_desc ON event (creation_date DESC);


-- !Downs

DROP INDEX event_idx_creation_date_desc;
