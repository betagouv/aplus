# --- !Ups
ALTER TABLE "user_group" ALTER COLUMN "area" DROP NOT NULL;
ALTER TABLE "user_group" ADD "area_ids" uuid[] NULL;
UPDATE "user_group" SET "area_ids" = ARRAY[area]::uuid[];
ALTER TABLE "user_group" ALTER COLUMN "area_ids" SET NOT NULL;

# --- !Downs
ALTER TABLE "user_group" DROP "area_ids";
ALTER TABLE "user_group" ALTER COLUMN "area" SET NOT NULL;
