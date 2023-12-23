--- !Ups
CREATE TABLE account_creation_request (
    id uuid PRIMARY KEY,
    request_date timestamp with time zone NOT NULL,
    email character varying(200) NOT NULL,
    is_named_account boolean NOT NULL,
    first_name character varying(200) NOT NULL,
    last_name character varying(200) NOT NULL,
    phone_number character varying(40),
    area_ids uuid[] NOT NULL,
    qualite character varying(100),
    organisation_id character varying(200),
    misc_organisation character varying(1000),
    is_manager boolean NOT NULL,
    is_instructor boolean NOT NULL,
    message character varying(10000),

    rejection_user_id uuid,
    rejection_date timestamp with time zone,
    rejection_reason character varying(1000)
);
CREATE INDEX account_creation_request_date_idx ON account_creation_request (request_date);
CREATE INDEX account_creation_request_lower_email_idx ON account_creation_request (LOWER(email));

CREATE TABLE account_creation_request_signature (
    id uuid PRIMARY KEY,
    form_id uuid NOT NULL,
    first_name character varying(200) NOT NULL,
    last_name character varying(200) NOT NULL,
    phone_number character varying(40)
);

--- !Downs
DROP TABLE account_creation_request_signature;
DROP INDEX account_creation_request_lower_email_idx;
DROP INDEX account_creation_request_date_idx;
DROP TABLE account_creation_request;
