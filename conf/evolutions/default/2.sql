# --- !Ups
CREATE TABLE "user" (
    id uuid primary key,
    key character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    qualite character varying(100) NOT NULL,
    email character varying(200) NOT NULL UNIQUE,
    helper boolean NOT NULL,
    instructor boolean NOT NULL,
    admin boolean NOT NULL,
    areas uuid[] NOT NULL
);

# --- !Downs
DROP TABLE "user";