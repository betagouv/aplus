# --- !Ups
CREATE UNIQUE INDEX name_user_group_unique_idx ON user_group (LOWER(name));

# --- !Downs
DROP INDEX name_user_group_unique_idx ON user_group;