# --- !Ups
CREATE TABLE application (
    id uuid primary key,
    status character varying(100) NOT NULL,
    creation_date date NOT NULL,
    creator_user_name character varying(200) NOT NULL,
    creator_user_id uuid NOT NULL,
    subject character varying(150) NOT NULL,
    description text NOT NULL,
    user_infos jsonb NOT NULL,
    invited_users jsonb NOT NULL,
    area uuid NOT NULL
);

CREATE TABLE answer (
  id uuid primary key,
  application_id uuid NOT NULL,
  creation_date date NOT NULL,
  message text NOT NULL,
  creator_user_id uuid NOT NULL,
  creator_user_name character varying(200) NOT NULL,
  invited_users jsonb NOT NULL,
  visible_by_helpers boolean NOT NULL,
  area uuid NOT NULL
);

# --- !Downs
DROP TABLE answer;
DROP TABLE application;