# --- !Ups
CREATE TABLE login_token (
     token character varying(20) primary key,
     user_id uuid NOT NULL,
     creation_date timestamp with time zone NOT NULL,
     expiration_date timestamp with time zone NOT NULL,
     ip_address inet NOT NULL
);
ALTER TABLE event ADD ip_address inet DEFAULT NOT NULL;

# --- !Downs
ALTER TABLE event DROP commune_code;
DROP TABLE login_token;