# --- !Ups

CREATE VIEW application_metadata AS
SELECT
  id,
  creation_date,
  creator_user_name,
  creator_user_id,
  invited_users,
  area,
  irrelevant,
  internal_id,
  closed,
  seen_by_user_ids,
  usefulness,
  closed_date,
  expert_invited,
  has_selected_subject,
  category,
  mandat_type,
  mandat_date
FROM application;


-- Due to changes in json's datetime serializer, some dates are like this
-- 2019-08-12T15:52:51.526+02:00
-- others are like this
-- 2020-08-04T17:05:21.675+02:00[Europe/Paris]
-- we use text here, subsequent users should be careful
CREATE VIEW answer_metadata AS
SELECT
  answer.id,
  answer.application_id,
  answer.creation_date,
  answer.creator_user_id,
  answer.creator_user_name,
  answer.invited_users,
  answer.visible_by_helpers,
  answer.declare_application_has_irrelevant
FROM application,
     LATERAL jsonb_to_recordset(answers)
     AS answer(id uuid,
               application_id uuid,
               creation_date text,
               creator_user_id uuid,
               creator_user_name text,
               invited_users jsonb,
               visible_by_helpers boolean,
               declare_application_has_irrelevant boolean);


-- We can't just remove personal data from sms_thread,
-- they would only be "pseudomized" and not "anonymized"
CREATE VIEW mandat_metadata AS
SELECT
  id,
  user_id,
  creation_date,
  application_id,
  sms_thread_closed,
  jsonb_array_length(sms_thread) AS sms_thread_size
FROM mandat;



CREATE VIEW login_token_metadata AS
SELECT
  user_id,
  creation_date,
  expiration_date,
  ip_address
FROM login_token;



# --- !Downs

DROP VIEW application_metadata;
DROP VIEW answer_metadata;
DROP VIEW mandat_metadata;
DROP VIEW login_token_metadata;
