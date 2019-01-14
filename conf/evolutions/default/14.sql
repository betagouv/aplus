# --- !Ups
ALTER TABLE "user" ADD commune_code character varying(5) DEFAULT '0' NOT NULL;

# --- !Downs
ALTER TABLE "user" DROP commune_code;