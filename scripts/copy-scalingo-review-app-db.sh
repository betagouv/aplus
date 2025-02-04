#!/bin/sh

echo "Running the Scalingo Review App DB Copy Script"

if [ "$IS_REVIEW_APP" = "true" ]; then
  echo "Copying review app db"

  pg_dump --clean --if-exists --format c --dbname "${PARENT_APP_DATABASE_URL}" --no-owner --no-privileges --no-comments --exclude-schema 'information_schema' --exclude-schema '^pg_*' | pg_restore --clean --if-exists --no-owner --no-privileges --no-comments --dbname "${SCALINGO_POSTGRESQL_URL}"

  echo "Done"
else
  echo "Not a review app - skipping database copy"
fi
