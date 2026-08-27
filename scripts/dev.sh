#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-task-manager}"
COMPOSE=(docker compose -p "$COMPOSE_PROJECT_NAME")

ensure_env() {
  if [[ ! -f .env ]]; then
    cp .env.example .env
    echo "Created .env from .env.example for local development. Replace defaults before any production-like use."
  fi
}

case "${1:-help}" in
  up)
    ensure_env
    "${COMPOSE[@]}" up -d postgres minio
    "${COMPOSE[@]}" --profile tools run --rm migrate
    "${COMPOSE[@]}" up -d server
    "${COMPOSE[@]}" ps
    echo
    echo "Server:       http://127.0.0.1:${SERVER_PORT:-8080}"
    echo "Health:       http://127.0.0.1:${SERVER_PORT:-8080}/health/ready"
    echo "MinIO console: http://127.0.0.1:${MINIO_CONSOLE_PORT:-9001}"
    ;;
  migrate)
    ensure_env
    "${COMPOSE[@]}" up -d postgres minio
    "${COMPOSE[@]}" --profile tools run --rm migrate
    ;;
  test)
    ensure_env
    TEST_PROJECT="${COMPOSE_PROJECT_NAME:-task-manager}-test"
    set +e
    docker compose -p "$TEST_PROJECT" --profile test up --build --abort-on-container-exit --exit-code-from smoke smoke
    smoke_status=$?
    docker compose -p "$TEST_PROJECT" --profile test down -v --remove-orphans
    cleanup_status=$?
    set -e

    if [[ "$cleanup_status" -ne 0 ]]; then
      echo "Smoke test cleanup failed with status $cleanup_status." >&2
      if [[ "$smoke_status" -eq 0 ]]; then
        exit "$cleanup_status"
      fi
    fi
    exit "$smoke_status"
    ;;
  down)
    "${COMPOSE[@]}" down --remove-orphans
    ;;
  logs)
    "${COMPOSE[@]}" logs -f "${2:-server}"
    ;;
  ps)
    "${COMPOSE[@]}" ps
    ;;
  reset)
    if [[ "${2:-}" != "--yes-local" ]]; then
      echo "Refusing to delete local volumes without explicit confirmation."
      echo "Run: ./scripts/dev.sh reset --yes-local"
      exit 2
    fi
    "${COMPOSE[@]}" down -v --remove-orphans
    echo "Deleted local Compose containers and project volumes for $COMPOSE_PROJECT_NAME."
    ;;
  backup)
    ensure_env
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    target="backups/$stamp"
    mkdir -p "$target"
    "${COMPOSE[@]}" exec -T postgres pg_dump -U "${POSTGRES_USER:-task_manager}" "${POSTGRES_DB:-task_manager}" > "$target/postgres.sql"
    "${COMPOSE[@]}" exec -T minio sh -c 'tar -C /data -czf - .' > "$target/minio-data.tar.gz"
    echo "Wrote local backup to $target"
    ;;
  restore)
    source_dir="${2:-}"
    if [[ -z "$source_dir" || ! -f "$source_dir/postgres.sql" || ! -f "$source_dir/minio-data.tar.gz" ]]; then
      echo "Usage: ./scripts/dev.sh restore backups/YYYYMMDDTHHMMSSZ"
      exit 2
    fi
    if [[ "${CONFIRM_RESTORE:-}" != "task-manager-local-restore" ]]; then
      echo "Refusing restore without CONFIRM_RESTORE=task-manager-local-restore."
      exit 2
    fi
    ensure_env
    "${COMPOSE[@]}" up -d postgres minio
    "${COMPOSE[@]}" exec -T postgres psql -U "${POSTGRES_USER:-task_manager}" "${POSTGRES_DB:-task_manager}" < "$source_dir/postgres.sql"
    "${COMPOSE[@]}" exec -T minio sh -c 'rm -rf /data/* && tar -C /data -xzf -' < "$source_dir/minio-data.tar.gz"
    echo "Restored local backup from $source_dir"
    ;;
  help|*)
    cat <<'HELP'
Usage: ./scripts/dev.sh COMMAND

Commands:
  up                Start postgres, MinIO, run migrations, then start the server
  migrate           Run migrations and ensure the object bucket exists
  test              Build images and run the containerised smoke test stack
  logs [service]    Follow logs, default service: server
  ps                Show Compose services
  down              Stop containers without deleting persistent volumes
  reset --yes-local Delete local containers and volumes for this project
  backup            Write PostgreSQL dump and MinIO data tarball under backups/
  restore DIR       Restore local backup; requires CONFIRM_RESTORE=task-manager-local-restore
HELP
    ;;
esac
