-- !Ups

ALTER TABLE "user" ADD COLUMN first_login_date timestamp with time zone DEFAULT NOW();



-- !Downs

ALTER TABLE "user" DROP COLUMN first_login_date;
