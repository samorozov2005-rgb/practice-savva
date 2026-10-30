#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/lib/compose-common.sh
source "$script_dir/lib/compose-common.sh"

require_docker
backup_dir="$(configured_value BACKUP_DIR "$DEPLOY_DIR/backups")"
case "$backup_dir" in
  /*) ;;
  *) backup_dir="$DEPLOY_DIR/$backup_dir" ;;
esac
mkdir -p "$backup_dir"
umask 077
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$backup_dir/support-$timestamp-$$.dump"
partial="$file.partial"
cleanup_partial() { rm -f "$partial"; }
trap cleanup_partial EXIT INT TERM

echo "Creating PostgreSQL backup in: $backup_dir"
# Variables intentionally expand inside the PostgreSQL container.
# shellcheck disable=SC2016
compose exec -T db sh -c \
  'exec pg_dump --format=custom --no-owner --username="$POSTGRES_USER" "$POSTGRES_DB"' > "$partial"
if [[ ! -s "$partial" ]]; then
  echo "Backup failed: pg_dump produced an empty file" >&2
  exit 1
fi
if [[ -e "$file" ]]; then
  echo "Backup target already exists; refusing to overwrite: $file" >&2
  exit 1
fi
ln "$partial" "$file"
rm -f "$partial"
trap - EXIT INT TERM
printf 'Backup created: %s\n' "$file"
