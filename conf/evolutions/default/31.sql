# --- !Ups
ALTER TABLE "user_group" ALTER COLUMN "area" DROP NOT NULL;
ALTER TABLE "user_group" ADD "area_ids" uuid[] NULL;
UPDATE "user_group" SET "area_ids" = ARRAY[area]::uuid[];
ALTER TABLE "user_group" ALTER COLUMN "area_ids" SET NOT NULL;

# --- !Downs
UPDATE "user_group" SET "area" = area_ids[1];
ALTER TABLE "user_group" DROP "area_ids";
ALTER TABLE "user_group" ALTER COLUMN "area" SET NOT NULL;
