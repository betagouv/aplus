# --- !Ups
ALTER TABLE application ADD internal_id SERIAL;
UPDATE application SET internal_id = x.row_number FROM (SELECT id, ROW_NUMBER () OVER (ORDER BY creation_date) FROM application) AS x WHERE application.id = x.id;
ALTER TABLE application ADD UNIQUE (internal_id);

# --- !Downs
ALTER TABLE application DROP internal_id;
