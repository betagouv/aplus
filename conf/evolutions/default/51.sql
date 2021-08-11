# --- !Ups
ALTER TABLE "application" ALTER COLUMN "creator_user_name" SET DATA TYPE text;

# --- !Downs
ALTER TABLE "application" ALTER COLUMN "creator_user_name" SET DATA TYPE character varying(200) USING SUBSTR("creator_user_name", 1, 200);
