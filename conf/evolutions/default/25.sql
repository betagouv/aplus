# --- !Ups
ALTER TABLE user_group ADD description varchar NULL;

# --- !Downs
ALTER TABLE user_group DROP description;
