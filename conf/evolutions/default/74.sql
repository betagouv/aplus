--- !Ups

ALTER TABLE file_metadata ADD COLUMN encryption_key_id text NULL;



--- !Downs

ALTER TABLE file_metadata DROP COLUMN encryption_key_id;
