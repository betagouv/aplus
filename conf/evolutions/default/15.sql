# --- !Ups
CREATE TABLE user_group (
  id uuid primary key,
  name character varying(50) NOT NULL,
  user_ids uuid[] NOT NULL DEFAULT array[]::uuid[],
  insee_code character varying(5) NOT NULL,
  creation_date timestamp with time zone NOT NULL,
  create_by_user_id uuid NOT NULL,
  area uuid NOT NULL
);

# --- !Downs
DROP TABLE user_group;