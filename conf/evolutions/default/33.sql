# --- !Ups
ALTER TABLE "user" DROP delegations;

# --- !Downs
ALTER TABLE "user" ADD delegations jsonb NOT NULL DEFAULT '{}'::jsonb;
