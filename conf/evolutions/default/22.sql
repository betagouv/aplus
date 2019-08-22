# --- !Ups
ALTER TABLE "user" ADD disabled boolean default false;

# --- !Downs
ALTER TABLE "user" DROP disabled;