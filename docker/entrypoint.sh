#!/bin/sh
set -eu

if [ "$#" -gt 0 ]; then
  exec java ${JAVA_OPTS:-} -jar /app/wikilinks.jar "$@"
fi

set -- server --port "${PORT:-8080}"
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

exec java ${JAVA_OPTS:-} -jar /app/wikilinks.jar "$@"
