#!/bin/sh
set -eu

BASE_JAVA_OPTS="$(tr '\n' ' ' </app/jvm.options)"
JAVA_COMMAND="java ${BASE_JAVA_OPTS} ${JAVA_OPTS:-}"

if [ "$#" -gt 0 ]; then
  # shellcheck disable=SC2086
  exec ${JAVA_COMMAND} -jar /app/wikilinks.jar "$@"
fi

set -- server --port "${PORT:-8080}" --max-in-flight-routes "${MAX_IN_FLIGHT_ROUTES:-1}"
found_wiki=0
for wiki_file in /app/wikis/*.segment; do
  if [ ! -f "$wiki_file" ]; then
    continue
  fi
  found_wiki=1
  wiki_name=$(basename "$wiki_file" .segment)
  set -- "$@" "$wiki_name" "$wiki_file"
done

if [ "$found_wiki" -eq 0 ]; then
  echo "No .segment wiki files found in /app/wikis" >&2
  exit 1
fi

# shellcheck disable=SC2086
exec ${JAVA_COMMAND} -jar /app/wikilinks.jar "$@"
