# --- !Ups
ALTER TABLE "user_group" ALTER COLUMN "name" SET DATA TYPE character varying(100);

# --- !Downs
ALTER TABLE "user_group" ALTER COLUMN "name" SET DATA TYPE character varying(60) USING SUBSTR("name", 1, 60);
