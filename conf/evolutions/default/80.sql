--- !Ups

UPDATE organisation SET short_name = 'France Travail' WHERE id = 'Pôle emploi';
UPDATE organisation SET name = 'France Travail' WHERE id = 'Pôle emploi';



--- !Downs

UPDATE organisation SET name = 'Pôle emploi' WHERE id = 'Pôle emploi';
UPDATE organisation SET short_name = 'Pôle emploi' WHERE id = 'Pôle emploi';
