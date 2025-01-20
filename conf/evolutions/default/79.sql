--- !Ups

INSERT INTO organisation (id, short_name, name) VALUES
    ('DILA', 'DILA', 'Direction de l’information légale et administrative');



--- !Downs

DELETE FROM organisation WHERE id = 'DILA';
