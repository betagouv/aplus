-- !Ups

ALTER TABLE mandat ADD COLUMN version integer NOT NULL DEFAULT 1;
ALTER TABLE mandat ALTER COLUMN version DROP DEFAULT;

ALTER TABLE mandat ADD COLUMN group_id uuid NULL;


-- !Downs

ALTER TABLE mandat DROP COLUMN group_id;
ALTER TABLE mandat DROP COLUMN version;
