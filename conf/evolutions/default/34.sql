# --- !Ups
ALTER TABLE "user" ADD already_exists boolean NOT NULL DEFAULT true;
ALTER TABLE "user_group" ADD already_exists boolean NOT NULL DEFAULT true;

# --- !Downs
ALTER TABLE "user" DROP already_exists;
ALTER TABLE "user_group" DROP already_exists;
