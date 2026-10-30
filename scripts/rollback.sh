#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/lib/compose-common.sh
source "$script_dir/lib/compose-common.sh"

if [[ $# -ne 0 ]]; then
  echo "Usage: $0" >&2
  exit 2
fi
require_docker
acquire_operation_lock

if ! target_image="$(read_state previous-image)"; then target_image=""; fi
if [[ -z "$target_image" ]]; then
  echo "No previous successfully deployed image is recorded" >&2
  exit 1
fi
validate_image_reference "$target_image" || exit 1

if ! actual_current="$(running_app_image)"; then actual_current=""; fi
if ! recorded_current="$(read_state current-image)"; then recorded_current=""; fi
current_image="${actual_current:-$recorded_current}"

if [[ "$current_image" == "$target_image" ]] && wait_readiness; then
  atomic_write "$target_image" "$STATE_DIR/current-image"
  rm -f "$STATE_DIR/previous-image" "$STATE_DIR/pending-image"
  echo "Previous image is already running and healthy: $target_image"
  exit 0
fi

ensure_image_available "$target_image"
atomic_write "$target_image" "$STATE_DIR/pending-image"
if start_app_image "$target_image" && wait_readiness; then
  atomic_write "$target_image" "$STATE_DIR/current-image"
  rm -f "$STATE_DIR/previous-image" "$STATE_DIR/pending-image"
  echo "Rollback completed successfully: $target_image"
  echo "Database migrations were not rolled back"
  exit 0
fi

echo "Rollback failed for image: $target_image" >&2
rm -f "$STATE_DIR/pending-image"
if [[ -n "$current_image" && "$current_image" != "$target_image" ]]; then
  echo "Attempting to restore the image that was running before rollback: $current_image" >&2
  if ensure_image_available "$current_image" && start_app_image "$current_image" && wait_readiness; then
    atomic_write "$current_image" "$STATE_DIR/current-image"
    echo "Original image restored, but rollback remains failed" >&2
  else
    echo "Could not restore the original image" >&2
  fi
fi
exit 1
