# --- !Ups
ALTER TABLE application ADD invited_group_ids varchar[] DEFAULT ARRAY[]::varchar[] NOT NULL;

# --- !Downs
ALTER TABLE application DROP invited_group_ids;
