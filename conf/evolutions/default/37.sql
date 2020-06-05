# --- !Ups
CREATE TABLE mandat (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    creation_date timestamp with time zone NOT NULL,
    application_id uuid NULL DEFAULT NULL,
    usager_prenom varchar(255) NOT NULL,
    usager_nom varchar(255) NOT NULL,
    -- 100 for an "letters only" date
    usager_birth_date varchar(100) NOT NULL,
    usager_phone_local varchar(20) NULL,
    -- NOT NULL because it is annoying and error prone to deal with NULL and empty arrays
    sms_thread jsonb NOT NULL DEFAULT '[]'::jsonb,
    sms_thread_closed boolean NOT NULL DEFAULT false,
    CONSTRAINT one_mandat_per_application UNIQUE(application_id)
);

# --- !Downs
DROP TABLE mandat;
