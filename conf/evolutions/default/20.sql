# --- !Ups
ALTER TABLE user_group ADD organisation character varying(20) NULL;

# --- !Downs
ALTER TABLE user_group DROP organisation;