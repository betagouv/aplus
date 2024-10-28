-- !Ups

ALTER TABLE user_session ADD user_agent varchar(2048);


-- !Downs

ALTER TABLE user_session DROP user_agent;
