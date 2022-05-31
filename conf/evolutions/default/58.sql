-- !Ups

ALTER TABLE application RENAME COLUMN files TO files_old_unused_wiped;

-- !Downs

ALTER TABLE application RENAME COLUMN files_old_unused_wiped TO files;
