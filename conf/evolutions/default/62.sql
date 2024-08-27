-- !Ups

ALTER TABLE application ADD creator_group_id uuid NULL;
ALTER TABLE application ADD creator_group_name varchar(1000) NULL;


-- !Downs

ALTER TABLE application DROP creator_group_name;
ALTER TABLE application DROP creator_group_id;
