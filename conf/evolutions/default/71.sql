--- !Ups

INSERT INTO organisation (id, short_name, name) VALUES
    ('Chèque énergie', 'Chèque énergie', 'Chèque énergie');



--- !Downs

DELETE FROM organisation WHERE id = 'Chèque énergie';
