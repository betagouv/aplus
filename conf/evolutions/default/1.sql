# --- !Ups
CREATE TABLE application (
    id uuid primary key,
    status character varying(100) NOT NULL,
    creation_date date NOT NULL,
    helper_name character varying(100) NOT NULL,
    helper_user_id uuid NOT NULL,
    subject character varying(150) NOT NULL,
    description text NOT NULL,
    user_infos json NOT NULL,
    invited_user_ids uuid[] NOT NULL,
    area uuid NOT NULL
);

CREATE TABLE answer (
  id uuid primary key,
  status character varying(100) NOT NULL,
  creation_date date NOT NULL,
  helper_name character varying(100) NOT NULL,
  helper_user_id uuid NOT NULL,
  subject character varying(150) NOT NULL,
  description text NOT NULL,
  user_infos json NOT NULL,
  invited_user_ids uuid[] NOT NULL,
  area uuid NOT NULL
);


# --- !Downs
DROP TABLE application;
