-- !Ups

ALTER TABLE application ADD personal_data_wiped boolean DEFAULT false NOT NULL;
ALTER TABLE mandat ADD personal_data_wiped boolean DEFAULT false NOT NULL;


-- !Downs

ALTER TABLE mandat DROP personal_data_wiped;
ALTER TABLE application DROP personal_data_wiped;
