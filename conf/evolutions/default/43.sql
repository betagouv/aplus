# --- !Ups
ALTER TABLE application ADD invited_group_ids uuid[] DEFAULT ARRAY[]::uuid[] NOT NULL;

# --- !Downs
ALTER TABLE application DROP invited_group_ids;
