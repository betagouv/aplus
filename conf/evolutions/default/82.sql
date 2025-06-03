--- !Ups

INSERT INTO organisation (id, short_name, name) VALUES
    ('France Rénov''', 'France Rénov''', 'France Rénov''');



--- !Downs

DELETE FROM organisation WHERE id = 'France Rénov''';
