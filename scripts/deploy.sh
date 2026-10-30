#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/lib/compose-common.sh
source "$script_dir/lib/compose-common.sh"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 ghcr.io/owner/support-ticket-service:commit-sha" >&2
  exit 2
fi
new_image="$1"
validate_image_reference "$new_image" || exit 2
require_docker
acquire_operation_lock
ensure_image_available "$new_image"

if ! actual_current="$(running_app_image)"; then actual_current=""; fi
if ! recorded_current="$(read_state current-image)"; then recorded_current=""; fi
current_image="${actual_current:-$recorded_current}"

if [[ "$actual_current" == "$new_image" ]] && wait_readiness; then
  atomic_write "$new_image" "$STATE_DIR/current-image"
  rm -f "$STATE_DIR/pending-image"
  echo "Image is already deployed and healthy: $new_image"
  exit 0
fi

atomic_write "$new_image" "$STATE_DIR/pending-image"
deployment_ok=false
if start_app_image "$new_image" && wait_readiness; then
  deployment_ok=true
fi

if [[ "$deployment_ok" == true ]]; then
  if [[ -n "$current_image" && "$current_image" != "$new_image" ]]; then
    atomic_write "$current_image" "$STATE_DIR/previous-image"
  elif [[ -z "$current_image" ]]; then
    rm -f "$STATE_DIR/previous-image"
  fi
  atomic_write "$new_image" "$STATE_DIR/current-image"
  rm -f "$STATE_DIR/pending-image"
  echo "Deployment completed successfully: $new_image"
  exit 0
fi

echo "Deployment failed for image: $new_image" >&2
rm -f "$STATE_DIR/pending-image"
if [[ -z "$current_image" || "$current_image" == "$new_image" ]]; then
  echo "No distinct last known working image is available for automatic rollback" >&2
  exit 1
fi

echo "Attempting automatic rollback to: $current_image" >&2
if ensure_image_available "$current_image" && start_app_image "$current_image" && wait_readiness; then
  atomic_write "$current_image" "$STATE_DIR/current-image"
  echo "Automatic rollback succeeded; deployment is still reported as failed" >&2
else
  echo "Automatic rollback failed; inspect Docker Compose logs immediately" >&2
fi
exit 1
