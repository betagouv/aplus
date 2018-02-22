# --- !Ups
ALTER TABLE application ADD irrelevant boolean NOT NULL DEFAULT false;
ALTER TABLE answer ADD application_is_declared_irrelevant boolean NOT NULL DEFAULT false;

# --- !Downs
ALTER TABLE answer DROP application_is_declared_irrelevant;
ALTER TABLE application DROP irrelevant;