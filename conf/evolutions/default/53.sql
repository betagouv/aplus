-- !Ups

CREATE TEXT SEARCH CONFIGURATION french_unaccent ( COPY = french );
ALTER TEXT SEARCH CONFIGURATION french_unaccent
  ALTER MAPPING FOR hword, hword_part, word WITH unaccent, french_stem;


-- !Downs

DROP TEXT SEARCH CONFIGURATION french_unaccent;
