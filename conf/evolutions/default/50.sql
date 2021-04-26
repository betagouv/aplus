# --- !Ups

INSERT INTO organisation (id, short_name, name) VALUES
    ('Association', 'Association', 'Association');



# --- !Downs

DELETE FROM organisation WHERE id = 'Association';
