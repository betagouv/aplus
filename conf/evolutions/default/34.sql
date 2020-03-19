# --- !Ups
ALTER TABLE "user" ADD observable_organisation_ids varchar[] DEFAULT ARRAY[]::varchar[] NOT NULL;

# --- !Downs
ALTER TABLE "user" DROP observable_organisation_ids;
