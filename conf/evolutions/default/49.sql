# --- !Ups
CREATE TABLE signup_request (
    id uuid PRIMARY KEY,
    request_date timestamp with time zone NOT NULL,
    email character varying(200) NOT NULL,
    inviting_user_id uuid NOT NULL
);
CREATE INDEX signup_request_date_idx ON signup_request (request_date);
CREATE UNIQUE INDEX signup_request_lower_email_unique_idx ON signup_request (LOWER(email));

ALTER TABLE login_token ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE login_token ADD signup_id uuid NULL;


# --- !Downs

ALTER TABLE login_token DROP signup_id;
DELETE FROM login_token WHERE user_id IS NULL;
ALTER TABLE login_token ALTER COLUMN user_id SET NOT NULL;

DROP INDEX signup_request_lower_email_unique_idx;
DROP INDEX signup_request_date_idx;
DROP TABLE signup_request;
