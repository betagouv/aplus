-- !Ups

ALTER TABLE "user" ALTER COLUMN password_activated SET DEFAULT true;


-- !Downs

ALTER TABLE "user" ALTER COLUMN password_activated SET DEFAULT false;
