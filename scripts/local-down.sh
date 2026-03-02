#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_CMD=()
PID_FILE=".local-run/services.pid"
SERVICE_JAR_PATTERNS=(
  "onboarding-service/target/onboarding-service-1.0.0.jar"
  "onboarding-orchestrator/target/onboarding-orchestrator-1.0.0.jar"
  "account-service/target/account-service-1.0.0.jar"
  "device-service/target/device-service-1.0.0.jar"
  "access-service/target/access-service-1.0.0.jar"
)

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

compose() {
  "${COMPOSE_CMD[@]}" "$@"
}

detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
    return
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
    return
  fi

  echo "WARN: compose command not found; skipping infra shutdown"
}

if [[ -f "$PID_FILE" ]]; then
  log "Stopping local Spring Boot services"
  while IFS=: read -r name pid; do
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      echo "Stopped ${name} (pid ${pid})"
    fi
  done < "$PID_FILE"
  rm -f "$PID_FILE"
else
  echo "No local service PID file found at ${PID_FILE}"
fi

log "Stopping any stale local Spring Boot service processes"
for pattern in "${SERVICE_JAR_PATTERNS[@]}"; do
  pkill -f "$pattern" >/dev/null 2>&1 || true
done

detect_compose
if [[ ${#COMPOSE_CMD[@]} -gt 0 ]]; then
  log "Stopping Kafka and Zookeeper containers"
  compose stop kafka zookeeper >/dev/null 2>&1 || true
  compose rm -f -v kafka zookeeper >/dev/null 2>&1 || true
fi

echo "Local stack stopped."
