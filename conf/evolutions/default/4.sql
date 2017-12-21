# --- !Ups
ALTER TABLE "user" ADD delegations jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE "user" ADD creation_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;

# --- !Downs
ALTER TABLE "user" DROP delegations;
ALTER TABLE "user" DROP creation_date;