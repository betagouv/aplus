# --- !Ups
ALTER TABLE "user_group" ALTER COLUMN insee_code SET DATA TYPE character varying(5)[] USING ARRAY[insee_code]::character varying(5)[];

# --- !Downs
ALTER TABLE "user_group" ALTER COLUMN insee_code SET NOT NULL;
ALTER TABLE "user_group" ALTER COLUMN insee_code SET DATA TYPE character varying(5) USING insee_code[1];
