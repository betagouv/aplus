# --- !Ups
ALTER TABLE application ADD selected_subject boolean NOT NULL DEFAULT false,
                        ADD category character varying(50) NULL;

# --- !Downs
ALTER TABLE application DROP selected_subject,
                        DROP category;
