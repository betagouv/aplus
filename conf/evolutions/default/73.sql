--- !Ups

CREATE INDEX application_creator_user_id_idx ON application (creator_user_id);
CREATE INDEX answer_creator_user_id_idx ON answer (creator_user_id);



--- !Downs

DROP INDEX answer_creator_user_id_idx;
DROP INDEX application_creator_user_id_idx;
