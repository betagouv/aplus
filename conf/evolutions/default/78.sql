-- !Ups

CREATE MATERIALIZED VIEW IF NOT EXISTS user_event_metrics AS
SELECT
  from_user_id AS user_id,
  MAX(creation_date) AS last_activity
FROM event
WHERE 
  code NOT IN (
    'GENERATE_TOKEN', 
    'EXPIRED_TOKEN', 
    'TOKEN_DOUBLE_USAGE', 
    'TOKEN_ERROR', 
    'TRY_LOGIN_BY_KEY'
  )
GROUP BY from_user_id;

CREATE INDEX user_event_metrics_idx_user_id_last_activity ON user_event_metrics(user_id, last_activity DESC);



-- !Downs

DROP INDEX user_event_metrics_idx_user_id_last_activity;
DROP MATERIALIZED VIEW user_event_metrics;
