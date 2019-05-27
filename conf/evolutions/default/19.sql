# --- !Ups
ALTER TABLE "user" ADD cgu_acceptation_date timestamp with time zone NULL,
    ADD newsletter_acceptation_date timestamp with time zone NULL;

# --- !Downs
ALTER TABLE "user" DROP cgu_acceptation_date,
    DROP newsletter_acceptation_date;