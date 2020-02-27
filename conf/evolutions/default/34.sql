# --- !Ups
ALTER TABLE "user" ADD observable_organisation_ids uuid[] DEFAULT ARRAY[]::uuid[] NOT NULL;

# --- !Downs
ALTER TABLE "user" DROP observable_organisation_ids;
