# --- !Ups
ALTER TABLE application ADD usefulness character varying(100) NULL;

# --- !Downs
ALTER TABLE application DROP usefulness;
