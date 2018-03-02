# --- !Ups
ALTER TABLE application ADD answers jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE answer RENAME application_is_declared_irrelevant TO declare_application_has_irrelevant;
ALTER TABLE answer RENAME TO answer_unused;
UPDATE application SET answers = answers || row_to_json(answer)::jsonb FROM (
      SELECT * FROM answer_unused
) AS answer WHERE application.id = answer.application_id;
ALTER TABLE application ADD closed boolean NOT NULL DEFAULT false;
UPDATE application SET closed = (status = 'Terminé');
ALTER TABLE application DROP status;

# --- !Downs
ALTER TABLE application ADD status character varying(100) NOT NULL;
UPDATE application SET status = 'Terminé' WHERE closed = true;
UPDATE application SET status = 'En cours' WHERE closed = false;
ALTER TABLE application DROP closed;
ALTER TABLE application DROP answers;
ALTER TABLE answer_unused RENAME TO answer;
ALTER TABLE answer RENAME declare_application_has_irrelevant TO application_is_declared_irrelevant;


