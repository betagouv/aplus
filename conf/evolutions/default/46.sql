# --- !Ups
ALTER TABLE user_group ADD public_note text NULL;

# --- !Downs
ALTER TABLE user_group DROP public_note;
