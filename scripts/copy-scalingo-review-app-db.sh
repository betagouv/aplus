#!/bin/sh

echo "Running Scalingo review app DB copy script"

if [ "$IS_REVIEW_APP" = "true" ]; then
  dbclient-fetcher pgsql 15

  echo "Copying review app db"

  echo "Checking number of rows in 'user' table"

  USER_COUNT=$(/app/bin/psql "${SCALINGO_POSTGRESQL_URL}" -A -t -c 'SELECT COUNT(*) FROM "user";')

  if [ "$USER_COUNT" = "0" ]; then
    echo "Table 'user' has $USER_COUNT rows. Copying parent app db to review app db."

    /app/bin/pg_dump --clean --if-exists --format c --dbname "${PARENT_APP_DATABASE_URL}" --no-owner --no-privileges --no-comments --exclude-schema 'information_schema' --exclude-schema '^pg_*' | /app/bin/pg_restore --clean --if-exists --no-owner --no-privileges --no-comments --dbname "${SCALINGO_POSTGRESQL_URL}"

  else
    echo "Table 'user' has $USER_COUNT rows. Doing nothing."
  fi

else
  echo "Not a review app - skipping database copy"
fi
