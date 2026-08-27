#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
DOCKER_LOG="$TMP_DIR/docker.log"
ENV_CREATED=0

cleanup() {
  rm -rf "$TMP_DIR"
  if [[ "$ENV_CREATED" -eq 1 ]]; then
    rm -f "$ROOT_DIR/.env"
  fi
}
trap cleanup EXIT

if [[ ! -f "$ROOT_DIR/.env" ]]; then
  ENV_CREATED=1
fi

mkdir -p "$TMP_DIR/bin"
cat > "$TMP_DIR/bin/docker" <<'STUB'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >> "${DOCKER_STUB_LOG:?}"

if [[ "$*" == "compose -p task-manager-test --profile test up --build --abort-on-container-exit --exit-code-from smoke smoke" ]]; then
  exit "${DOCKER_STUB_UP_STATUS:-0}"
fi

if [[ "$*" == "compose -p task-manager-test --profile test down -v --remove-orphans" ]]; then
  exit "${DOCKER_STUB_DOWN_STATUS:-0}"
fi

printf 'unexpected docker command: %s\n' "$*" >&2
exit 99
STUB
chmod +x "$TMP_DIR/bin/docker"

run_dev_test() {
  local expected_status="$1"
  local up_status="$2"
  local down_status="$3"

  : > "$DOCKER_LOG"
  set +e
  env \
    PATH="$TMP_DIR/bin:$PATH" \
    DOCKER_STUB_LOG="$DOCKER_LOG" \
    DOCKER_STUB_UP_STATUS="$up_status" \
    DOCKER_STUB_DOWN_STATUS="$down_status" \
    COMPOSE_PROJECT_NAME=task-manager \
    "$ROOT_DIR/scripts/dev.sh" test >/dev/null 2>"$TMP_DIR/stderr.log"
  local actual_status=$?
  set -e

  if [[ "$actual_status" -ne "$expected_status" ]]; then
    printf 'expected exit %s, got %s\n' "$expected_status" "$actual_status" >&2
    printf 'stderr:\n' >&2
    sed 's/^/  /' "$TMP_DIR/stderr.log" >&2
    return 1
  fi

  mapfile -t commands < "$DOCKER_LOG"
  if [[ "${#commands[@]}" -ne 2 ]]; then
    printf 'expected 2 docker commands, got %s\n' "${#commands[@]}" >&2
    printf 'commands:\n' >&2
    sed 's/^/  /' "$DOCKER_LOG" >&2
    return 1
  fi

  if [[ "${commands[0]}" != "compose -p task-manager-test --profile test up --build --abort-on-container-exit --exit-code-from smoke smoke" ]]; then
    printf 'unexpected first command: %s\n' "${commands[0]}" >&2
    return 1
  fi

  if [[ "${commands[1]}" != "compose -p task-manager-test --profile test down -v --remove-orphans" ]]; then
    printf 'unexpected second command: %s\n' "${commands[1]}" >&2
    return 1
  fi
}

run_dev_test 0 0 0
run_dev_test 17 17 0
run_dev_test 23 0 23
run_dev_test 17 17 23

printf 'scripts/dev.sh test teardown regression checks passed\n'
