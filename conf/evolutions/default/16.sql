# --- !Ups
CREATE TABLE login_token (
     token character varying(20) primary key,
     user_id uuid NOT NULL,
     creation_date timestamp with time zone NOT NULL,
     expiration_date timestamp with time zone NOT NULL
);

# --- !Downs
DROP TABLE login_token;