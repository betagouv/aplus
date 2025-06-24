-- !Ups

CREATE TABLE user_inactivity_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type varchar(50) NOT NULL,
    event_date timestamptz NOT NULL,
    last_activity_reference_date timestamptz NOT NULL
);

-- !Downs

DROP TABLE user_inactivity_history;
