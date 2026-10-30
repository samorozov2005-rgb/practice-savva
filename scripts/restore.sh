#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/lib/compose-common.sh
source "$script_dir/lib/compose-common.sh"

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 backups/file.dump" >&2
  exit 2
fi
require_docker
dump_file="$1"
restore_db="$(configured_value RESTORE_DB support_restore)"
case "$restore_db" in
  ""|*[!A-Za-z0-9_]*) echo "Invalid restore database name: $restore_db" >&2; exit 2 ;;
esac
# Variables in these commands intentionally expand inside the database container.
# shellcheck disable=SC2016
primary_db="$(compose exec -T db sh -c 'printf %s "$POSTGRES_DB"')"
if [[ "$restore_db" == "$primary_db" ]]; then
  echo "Refusing to restore over the primary database: $primary_db" >&2
  exit 2
fi

echo "Recreating isolated restore database: $restore_db"
# shellcheck disable=SC2016
compose exec -T db sh -c \
  'exec dropdb --if-exists --force --username="$POSTGRES_USER" "$1"' sh "$restore_db"
# shellcheck disable=SC2016
compose exec -T db sh -c \
  'exec createdb --username="$POSTGRES_USER" "$1"' sh "$restore_db"
# shellcheck disable=SC2016
if ! compose exec -T db sh -c \
  'exec pg_restore --exit-on-error --no-owner --username="$POSTGRES_USER" --dbname="$1"' sh "$restore_db" < "$dump_file"; then
  echo "Restore failed; the primary database was not modified" >&2
  exit 1
fi
printf 'Restored into isolated database: %s\n' "$restore_db"
