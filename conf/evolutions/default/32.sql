# --- !Ups
ALTER TABLE "user" DROP has_accepted_charte;

# --- !Downs
ALTER TABLE "user" ADD has_accepted_charte boolean NOT NULL DEFAULT false;
