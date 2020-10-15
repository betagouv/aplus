# --- !Ups
CREATE TABLE "unvalidated_user"
(
    id                  uuid primary key,
    key                 character varying(100) NOT NULL,
    email               character varying(200) NOT NULL UNIQUE,
    firstname           character varying(100),
    lastname            character varying(100),
    shared_account_name character varying(500),
    instructor          boolean                NOT NULL,
    areas               uuid[]                 NOT NULL,
    creation_date       timestamp with time zone,
    groups_manager      boolean                NOT NULL,
    group_ids           uuid[]                 NOT NULL DEFAULT array []::uuid[],
    phone_number        character varying(40)  NULL

);

# --- !Downs
DROP TABLE "unvalidated_user";