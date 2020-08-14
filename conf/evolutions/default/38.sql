# --- !Ups
CREATE UNIQUE INDEX user_lower_email_unique_idx ON "user" (LOWER(email));

# --- !Downs
DROP INDEX user_lower_email_unique_idx ON "user";
