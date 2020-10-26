# --- !Ups
ALTER TABLE "user" ADD first_name character varying(100) NULL;
ALTER TABLE "user" ADD last_name character varying(100) NULL;

# --- !Downs
ALTER TABLE "user" DROP first_name;
ALTER TABLE "user" DROP last_name;