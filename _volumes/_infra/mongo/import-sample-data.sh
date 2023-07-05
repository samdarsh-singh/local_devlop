#!/usr/bin/env bash
set -eu

dir_path='/docker-entrypoint-initdb.d/data'

if [ ! -d "$dir_path" ]; then
  echo '[WARN] Skipping data-initialisation as directory was not found:' "$dir_path"
  exit
fi

MONGO_URI="mongodb://$MONGO_INITDB_ROOT_USERNAME:$MONGO_INITDB_ROOT_PASSWORD@127.0.0.1:27017/$MONGO_INITDB_DATABASE?authSource=admin"

for file in "$dir_path"/*.json; do
  if [ -f "$file" ]; then
    collection=$(basename "${file:0:-5}")
    echo "Importing [$collection] from file [$file]."
    mongoimport --uri "$MONGO_URI" --jsonArray --file "$file" --collection "$collection"
  fi
done

echo 'OK: Mongo data initialisation completed successfully.'
