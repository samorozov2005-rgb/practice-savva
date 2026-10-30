#!/usr/bin/env bash

# Shared, source-only helpers for Docker Compose operations. Compatible with Bash 3.2+.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"

dotenv_value() {
  file="$1"
  key="$2"
  [[ -f "$file" ]] || return 1
  awk -v wanted="$key" '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    {
      line=$0
      sub(/^[[:space:]]*/, "", line)
      if (index(line, wanted "=") == 1) {
        value=substr(line, length(wanted) + 2)
        sub(/\r$/, "", value)
        if (value ~ /^".*"$/ || value ~ /^\047.*\047$/) value=substr(value, 2, length(value)-2)
        print value
        exit
      }
    }
  ' "$file"
}

root_env_file="$PROJECT_ROOT/.env"
configured_deploy_dir="${DEPLOY_DIR:-}"
if [[ -z "$configured_deploy_dir" ]]; then
  if ! configured_deploy_dir="$(dotenv_value "$root_env_file" DEPLOY_DIR)"; then
    configured_deploy_dir=""
  fi
fi
configured_deploy_dir="${configured_deploy_dir:-$PROJECT_ROOT}"
case "$configured_deploy_dir" in
  /*) DEPLOY_DIR="$configured_deploy_dir" ;;
  *) DEPLOY_DIR="$PROJECT_ROOT/$configured_deploy_dir" ;;
esac

ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"
if [[ ! -f "$ENV_FILE" && -f "$root_env_file" ]]; then
  ENV_FILE="$root_env_file"
fi

configured_project_name="${COMPOSE_PROJECT_NAME:-}"
if [[ -z "$configured_project_name" ]]; then
  if ! configured_project_name="$(dotenv_value "$ENV_FILE" COMPOSE_PROJECT_NAME)"; then
    configured_project_name=""
  fi
fi
COMPOSE_PROJECT_NAME="${configured_project_name:-practice-savva}"
STATE_DIR="$DEPLOY_DIR/.deploy"

compose_args=(compose --project-name "$COMPOSE_PROJECT_NAME" --project-directory "$PROJECT_ROOT")
if [[ -f "$ENV_FILE" ]]; then
  compose_args+=(--env-file "$ENV_FILE")
fi
compose_args+=(-f "$PROJECT_ROOT/compose.yaml")

compose() {
  docker "${compose_args[@]}" "$@"
}

configured_value() {
  key="$1"
  fallback="$2"
  case "$key" in
    APP_PORT) explicit="${APP_PORT:-}" ;;
    BACKUP_DIR) explicit="${BACKUP_DIR:-}" ;;
    RESTORE_DB) explicit="${RESTORE_DB:-}" ;;
    HEALTH_ATTEMPTS) explicit="${HEALTH_ATTEMPTS:-}" ;;
    HEALTH_INTERVAL_SECONDS) explicit="${HEALTH_INTERVAL_SECONDS:-}" ;;
    HEALTH_REQUEST_TIMEOUT_SECONDS) explicit="${HEALTH_REQUEST_TIMEOUT_SECONDS:-}" ;;
    HEALTH_MAX_WAIT_SECONDS) explicit="${HEALTH_MAX_WAIT_SECONDS:-}" ;;
    *) explicit="" ;;
  esac
  if [[ -n "$explicit" ]]; then
    printf '%s\n' "$explicit"
    return
  fi
  if ! from_file="$(dotenv_value "$ENV_FILE" "$key")"; then
    from_file=""
  fi
  printf '%s\n' "${from_file:-$fallback}"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || { echo "Docker CLI is not installed" >&2; return 1; }
  docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable" >&2; return 1; }
  docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is unavailable" >&2; return 1; }
}

atomic_write() {
  value="$1"
  target="$2"
  mkdir -p "$(dirname "$target")"
  temp="$target.tmp.$$"
  umask 077
  printf '%s\n' "$value" > "$temp"
  mv -f "$temp" "$target"
}

read_state() {
  state_name="$1"
  state_file="$STATE_DIR/$state_name"
  [[ -s "$state_file" ]] || return 1
  IFS= read -r state_value < "$state_file"
  [[ -n "$state_value" ]] || return 1
  printf '%s\n' "$state_value"
}

acquire_operation_lock() {
  mkdir -p "$STATE_DIR"
  lock_dir="$STATE_DIR/operation.lock"
  if ! mkdir "$lock_dir" 2>/dev/null; then
    lock_pid=""
    [[ -r "$lock_dir/pid" ]] && IFS= read -r lock_pid < "$lock_dir/pid"
    if [[ -n "$lock_pid" ]] && kill -0 "$lock_pid" 2>/dev/null; then
      echo "Another deploy/rollback operation is running (PID $lock_pid)" >&2
      return 1
    fi
    echo "Removing stale deployment lock" >&2
    if [[ -e "$lock_dir/pid" ]]; then
      unlink "$lock_dir/pid"
    fi
    if ! rmdir "$lock_dir"; then
      echo "Stale lock contains unexpected files; remove it manually: $lock_dir" >&2
      return 1
    fi
    mkdir "$lock_dir" || return 1
  fi
  printf '%s\n' "$$" > "$lock_dir/pid"
  OPERATION_LOCK_DIR="$lock_dir"
  trap 'release_operation_lock' EXIT
}

release_operation_lock() {
  if [[ -n "${OPERATION_LOCK_DIR:-}" && -d "$OPERATION_LOCK_DIR" ]]; then
    if [[ -e "$OPERATION_LOCK_DIR/pid" ]]; then
      unlink "$OPERATION_LOCK_DIR/pid"
    fi
    if ! rmdir "$OPERATION_LOCK_DIR" 2>/dev/null; then
      echo "Could not remove operation lock: $OPERATION_LOCK_DIR" >&2
    fi
  fi
}

validate_image_reference() {
  image="$1"
  case "$image" in
    ""|-*|*[!A-Za-z0-9._/:@-]*)
      echo "Invalid Docker image reference: $image" >&2
      return 1
      ;;
  esac
  case "$image" in
    *:*) ;;
    *) echo "Image reference must include a tag or digest: $image" >&2; return 1 ;;
  esac
}

ensure_image_available() {
  image="$1"
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "Using local Docker image: $image"
    return 0
  fi
  echo "Pulling Docker image: $image"
  docker pull "$image" >/dev/null
  docker image inspect "$image" >/dev/null 2>&1 || {
    echo "Docker image is still unavailable after pull: $image" >&2
    return 1
  }
}

running_app_image() {
  container_id="$(compose ps --status running -q app 2>/dev/null)"
  [[ -n "$container_id" ]] || return 1
  docker inspect --format '{{.Config.Image}}' "$container_id"
}

start_app_image() {
  image="$1"
  export APP_IMAGE="$image"
  echo "Starting app with image: $image"
  compose up -d --no-build app
}

wait_readiness() {
  attempts="$(configured_value HEALTH_ATTEMPTS 18)"
  interval="$(configured_value HEALTH_INTERVAL_SECONDS 5)"
  request_timeout="$(configured_value HEALTH_REQUEST_TIMEOUT_SECONDS 5)"
  max_wait="$(configured_value HEALTH_MAX_WAIT_SECONDS 90)"
  app_port="$(configured_value APP_PORT 8080)"
  url="http://localhost:$app_port/actuator/health/readiness"
  case "$attempts:$interval:$request_timeout:$max_wait:$app_port" in
    *[!0-9:]*|:*|*:) echo "Health-check settings must be positive integers" >&2; return 2 ;;
  esac
  if [[ "$attempts" -eq 0 || "$interval" -eq 0 || "$request_timeout" -eq 0 || "$max_wait" -eq 0 || "$app_port" -eq 0 ]]; then
    echo "Health-check settings must be greater than zero" >&2
    return 2
  fi
  started_at="$(date +%s)"
  attempt=1
  while [[ "$attempt" -le "$attempts" ]]; do
    if curl --silent --show-error --fail --max-time "$request_timeout" "$url" >/dev/null 2>&1; then
      echo "Readiness succeeded: $url"
      return 0
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if [[ "$elapsed" -ge "$max_wait" ]]; then
      break
    fi
    echo "Readiness attempt $attempt/$attempts failed; waiting ${interval}s"
    sleep "$interval"
    attempt=$((attempt + 1))
  done
  echo "Readiness failed: $url" >&2
  return 1
}
