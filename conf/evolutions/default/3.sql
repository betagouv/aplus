# --- !Ups
ALTER TABLE application ALTER creation_date TYPE timestamp with time zone;
ALTER TABLE answer ALTER creation_date TYPE timestamp with time zone;

# --- !Downs
ALTER TABLE application ALTER creation_date TYPE date;
ALTER TABLE answer ALTER creation_date TYPE date;