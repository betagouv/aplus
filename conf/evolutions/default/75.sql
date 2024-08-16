--- !Ups

CREATE TABLE user_session(
  id text NOT NULL,
  user_id uuid NOT NULL,
  creation_date timestamptz NOT NULL,
  creation_ip_address inet NOT NULL,
  last_activity timestamptz NOT NULL,
  login_type text NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz DEFAULT NULL
);



--- !Downs

DROP TABLE user_session;
