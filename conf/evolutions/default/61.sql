-- !Ups

ALTER TABLE file_metadata ALTER COLUMN filename DROP NOT NULL;


-- !Downs

UPDATE file_metadata SET filename = '' WHERE filename IS NULL;
ALTER TABLE file_metadata ALTER COLUMN filename SET NOT NULL;
