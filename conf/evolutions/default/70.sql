--- !Ups

ALTER TABLE "user" ADD COLUMN managing_organisation_ids varchar[] DEFAULT ARRAY[]::varchar[] NOT NULL;
ALTER TABLE "user" ADD COLUMN managing_area_ids varchar[] DEFAULT ARRAY[]::varchar[] NOT NULL;


--- !Downs

ALTER TABLE "user" DROP COLUMN managing_area_ids;
ALTER TABLE "user" DROP COLUMN managing_organisation_ids;
