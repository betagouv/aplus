# --- !Ups
ALTER TABLE application ADD mandat_type text NULL,
                        ADD mandat_date text NULL;

# --- !Downs
ALTER TABLE application DROP mandat_type,
                        DROP mandat_date;
