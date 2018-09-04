# --- !Ups
ALTER TABLE answer_unused ADD user_infos jsonb NULL;

# --- !Downs
ALTER TABLE answer_unused DROP user_infos;
