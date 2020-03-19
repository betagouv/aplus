# --- !Ups
ALTER TABLE "user" ADD shared_account boolean NOT NULL DEFAULT false;

# --- !Downs
ALTER TABLE "user" DROP shared_account;
