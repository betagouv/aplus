# --- !Ups
ALTER TABLE "user" ADD internal_support_comment text NULL;
ALTER TABLE user_group ADD internal_support_comment text NULL;

# --- !Downs
ALTER TABLE user_group DROP internal_support_comment;
ALTER TABLE "user" DROP internal_support_comment;
