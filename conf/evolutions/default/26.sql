# --- !Ups
ALTER TABLE user_group DROP CONSTRAINT user_group_email_key;

# --- !Downs
ALTER TABLE user_group ADD CONSTRAINT user_group_email_key UNIQUE (email);