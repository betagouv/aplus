# --- !Ups
ALTER TABLE user_group ADD email character varying(200) NULL UNIQUE;

# --- !Downs
ALTER TABLE user_group DROP email;