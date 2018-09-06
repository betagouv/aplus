# --- !Ups
ALTER TABLE application ADD closed_date timestamp with time zone NULL;

# --- !Downs
ALTER TABLE application DROP closed_date;
