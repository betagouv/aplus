# --- !Ups
ALTER TABLE "user" ADD expert BOOLEAN DEFAULT false NOT NULL;

# --- !Downs
ALTER TABLE "user" DROP expert;