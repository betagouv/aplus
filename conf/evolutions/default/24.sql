# --- !Ups
ALTER TABLE application ADD files jsonb NOT NULL DEFAULT '{}'::jsonb;

# --- !Downs
ALTER TABLE application DROP files;
