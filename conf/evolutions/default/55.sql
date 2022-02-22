-- !Ups

CREATE TABLE file_metadata (
    id uuid PRIMARY KEY,
    upload_date timestamp NOT NULL,
    filename character varying(200) NOT NULL,
    filesize integer NOT NULL,
    status character varying(50) NOT NULL,
    application_id uuid,
    answer_id uuid
);
CREATE INDEX file_metadata_application_id_idx ON file_metadata (application_id);
CREATE INDEX file_metadata_answer_id_idx ON file_metadata (answer_id);

CREATE VIEW file_metadata_view AS
SELECT
  id,
  upload_date,
  filesize,
  status,
  application_id,
  answer_id
FROM file_metadata;


-- !Downs

DROP VIEW file_metadata_view;
DROP INDEX file_metadata_application_id_idx;
DROP INDEX file_metadata_answer_id_idx;
DROP TABLE file_metadata;
