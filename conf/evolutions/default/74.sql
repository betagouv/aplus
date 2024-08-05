--- !Ups

CREATE TABLE agent_connect_claims(
  subject text NOT NULL,
  email text NOT NULL,
  given_name text,
  usual_name text,
  uid text,
  siret text,
  creation_date timestamptz NOT NULL,
  last_auth_time timestamptz,
  -- a user can be linked to multiple claims
  user_id uuid
);

CREATE UNIQUE INDEX agent_connect_claims_subject_unique_idx ON agent_connect_claims (subject);
CREATE INDEX agent_connect_claims_lower_email_idx ON agent_connect_claims (lower(email));


CREATE TABLE user_session(
  id text NOT NULL,
  user_id uuid NOT NULL,
  creation_date timestamptz NOT NULL,
  last_activity timestamptz NOT NULL,
  login_type text NOT NULL,
  expires_at timestamptz NOT NULL,
  is_revoked boolean DEFAULT NULL
);



--- !Downs

DROP TABLE agent_connect_claims;
DROP TABLE user_session;
