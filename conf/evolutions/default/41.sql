# --- !Ups
CREATE TABLE "unvalidated_user"
(
    id                  uuid primary key,
    email               character varying(200) NOT NULL UNIQUE,
    first_name          character varying(100),
    last_name           character varying(100),
    shared_account_name character varying(500),
    instructor          boolean                NOT NULL,
    area_ids            uuid[]                 NOT NULL,
    creation_date       timestamp with time zone,
    group_manager       boolean                NOT NULL,
    group_ids           uuid[]                 NOT NULL DEFAULT array []::uuid[],
    phone_number        character varying(40)  NULL
);

# --- !Downs
DROP TABLE "unvalidated_user";