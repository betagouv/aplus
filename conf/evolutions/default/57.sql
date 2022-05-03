-- !Ups

CREATE TABLE france_service (
    group_id uuid NOT NULL,
    matricule integer NOT NULL,
    PRIMARY KEY (group_id, matricule)
);
CREATE INDEX france_service_matricule_idx ON france_service (matricule);


-- !Downs

DROP INDEX france_service_matricule_idx;
DROP TABLE france_service;
