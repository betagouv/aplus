# --- !Ups
CREATE TABLE event (
  id uuid primary key,
  level character varying(10) NOT NULL,
  code character varying(50) NOT NULL,
  from_user_name character varying(200) NOT NULL,
  from_user_id uuid NOT NULL,
  creation_date timestamp with time zone NOT NULL,
  description text NOT NULL,
  area uuid NOT NULL,
  to_application_id uuid NULL,
  to_user_id uuid NULL
);

# --- !Downs
DROP TABLE event;