# --- !Ups
CREATE TABLE user_group (
  id uuid primary key,
  name character varying(50) NOT NULL,
  insee_code character varying(5) NOT NULL,
  creation_date timestamp with time zone NOT NULL,
  create_by_user_id uuid NOT NULL,
  area uuid NOT NULL
);
ALTER TABLE "user" ADD group_admin BOOLEAN DEFAULT false NOT NULL;
ALTER TABLE "user" ADD group_ids uuid[] NOT NULL DEFAULT array[]::uuid[];

# --- !Downs
ALTER TABLE "user" DROP group_admin;
ALTER TABLE "user" DROP group_ids;
DROP TABLE user_group;