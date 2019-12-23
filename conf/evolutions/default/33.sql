# --- !Ups
ALTER TABLE "user_group" DROP create_by_user_id;

# --- !Downs
ALTER TABLE "user_group" ADD create_by_user_id uuid NULL;