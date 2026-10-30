#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/lib/compose-common.sh
source "$script_dir/lib/compose-common.sh"

require_docker
restore_db="$(configured_value RESTORE_DB support_restore)"
case "$restore_db" in
  ""|*[!A-Za-z0-9_]*) echo "Invalid restore database name: $restore_db" >&2; exit 2 ;;
esac
# Variables in these commands intentionally expand inside the database container.
# shellcheck disable=SC2016
primary_db="$(compose exec -T db sh -c 'printf %s "$POSTGRES_DB"')"
if [[ "$restore_db" == "$primary_db" ]]; then
  echo "Refusing to verify the primary database as a restore target" >&2
  exit 2
fi

query="SELECT
  CASE WHEN to_regclass('public.categories') IS NOT NULL THEN 1 ELSE 0 END,
  CASE WHEN to_regclass('public.tickets') IS NOT NULL THEN 1 ELSE 0 END,
  CASE WHEN to_regclass('public.ticket_status_history') IS NOT NULL THEN 1 ELSE 0 END,
  (SELECT count(*) FROM categories),
  (SELECT count(*) FROM tickets),
  (SELECT count(*) FROM ticket_status_history);"

database_counts() {
  database="$1"
  # shellcheck disable=SC2016
  compose exec -T db sh -c \
    'exec psql --username="$POSTGRES_USER" --dbname="$1" --no-align --tuples-only --set=ON_ERROR_STOP=1 --field-separator=, --command="$2"' \
    sh "$database" "$query" | tr -d '[:space:]'
}

echo "Checking restored database availability and required tables: $restore_db"
restored="$(database_counts "$restore_db")"
source_counts="$(database_counts "$primary_db")"
case "$restored" in
  1,1,1,*) ;;
  *) echo "Restore verification failed; required tables are missing: $restored" >&2; exit 1 ;;
esac

restored_metrics="${restored#1,1,1,}"
source_metrics="${source_counts#1,1,1,}"
echo "Restored counts (categories,tickets,history): $restored_metrics"
echo "Source counts   (categories,tickets,history): $source_metrics"
if [[ "$restored_metrics" != "$source_metrics" ]]; then
  echo "Restore verification failed: source and restored counts differ" >&2
  exit 1
fi
echo "Restore verification succeeded"
