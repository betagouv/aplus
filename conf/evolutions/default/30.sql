# --- !Ups
ALTER TABLE "user_group" ALTER COLUMN "name" SET DATA TYPE character varying(60);

# --- !Downs
ALTER TABLE "user_group" ALTER COLUMN "name" SET DATA TYPE character varying(50);
