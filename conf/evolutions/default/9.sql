# --- !Ups
ALTER TABLE application ADD seen_by_user_ids uuid[] DEFAULT ARRAY[]::uuid[] NOT NULL;

# --- !Downs
ALTER TABLE application DROP seen_by_user_ids;
