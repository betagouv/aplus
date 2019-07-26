# --- !Ups
ALTER TABLE application ADD has_selected_subject boolean NOT NULL DEFAULT false,
                        ADD category character varying(50) NULL;

# --- !Downs
ALTER TABLE application DROP has_selected_subject,
                        DROP category;
