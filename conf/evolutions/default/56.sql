-- !Ups

UPDATE organisation SET name = 'Caisse d’allocations familiales' where id = 'CAF';


-- !Downs

UPDATE organisation SET name = 'Caisse d’allocations familiale' where id = 'CAF';
