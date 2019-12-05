# --- !Ups
ALTER TABLE "user" ADD phone_number character varying(40) NULL;

# --- !Downs
ALTER TABLE "user" DROP phone_number;