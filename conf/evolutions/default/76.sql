-- !Ups

CREATE TABLE password (
  user_id uuid PRIMARY KEY,
  password_hash varchar(10000) NOT NULL,
  creation_date timestamptz NOT NULL,
  last_update timestamptz NOT NULL
);

CREATE TABLE password_recovery_token (
  token varchar(100) NOT NULL PRIMARY KEY,
  user_id uuid NOT NULL,
  creation_date timestamptz NOT NULL,
  expiration_date timestamptz NOT NULL,
  ip_address inet NOT NULL,
  used boolean DEFAULT false NOT NULL
);

CREATE INDEX password_recovery_token_user_id_idx ON password_recovery_token (user_id);

CREATE VIEW password_metadata AS
SELECT
  user_id,
  creation_date,
  last_update
FROM password;

CREATE VIEW password_recovery_token_metadata AS
SELECT
  user_id,
  creation_date,
  expiration_date,
  ip_address,
  used
FROM password_recovery_token;



ALTER TABLE "user" ADD password_activated boolean DEFAULT false NOT NULL;



-- !Downs

ALTER TABLE "user" DROP password_activated;

DROP VIEW password_recovery_token_metadata;
DROP VIEW password_metadata;

DROP INDEX password_recovery_token_user_id_idx;

DROP TABLE password_recovery_token;
DROP TABLE password;
