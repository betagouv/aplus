-- !Ups

ALTER TABLE user_group ADD is_in_france_services_network BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE application ADD is_in_france_services_network BOOLEAN NOT NULL DEFAULT true;


-- !Downs

ALTER TABLE application DROP is_in_france_services_network;
ALTER TABLE user_group DROP is_in_france_services_network;
