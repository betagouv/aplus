# --- !Ups
ALTER TABLE application ADD expert_invited boolean NOT NULL DEFAULT false;

# --- !Downs
ALTER TABLE application DROP expert_invited;
