#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SKIP_BUILD=false
COMPOSE_CMD=()
RUN_DIR=".local-run"
PID_FILE="${RUN_DIR}/services.pid"
SERVICE_JAR_PATTERNS=(
  "onboarding-service/target/onboarding-service-1.0.0.jar"
  "onboarding-orchestrator/target/onboarding-orchestrator-1.0.0.jar"
  "account-service/target/account-service-1.0.0.jar"
  "device-service/target/device-service-1.0.0.jar"
  "access-service/target/access-service-1.0.0.jar"
)

usage() {
  cat <<'EOF2'
Usage: scripts/local-up.sh [--skip-build]

Starts:
- Kafka + Zookeeper in Docker
- All Spring Boot services on host (java -jar)

Options:
  --skip-build   Skip mvn clean package -DskipTests
EOF2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
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

  fail "Neither 'docker compose' nor 'docker-compose' is available"
}

wait_for_url() {
  local name="$1"
  local url="$2"

  for _ in $(seq 1 80); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "OK: ${name}"
      return 0
    fi
    sleep 2
  done

  fail "Timed out waiting for ${name} (${url})"
}

kill_stale_service_processes() {
  log "Cleaning stale local Spring Boot processes (if any)"
  for pattern in "${SERVICE_JAR_PATTERNS[@]}"; do
    pkill -f "$pattern" >/dev/null 2>&1 || true
  done
}

start_service() {
  local name="$1"
  local jar_path="$2"

  log "Starting ${name}"
  SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    nohup java -jar "$jar_path" > "${RUN_DIR}/${name}.log" 2>&1 &
  local pid=$!
  echo "${name}:${pid}" >> "$PID_FILE"
}

require_cmd docker
require_cmd mvn
require_cmd java
require_cmd curl

detect_compose

mkdir -p "$RUN_DIR"
: > "$PID_FILE"

kill_stale_service_processes

if [[ "$SKIP_BUILD" == "false" ]]; then
  log "Building jars"
  mvn clean package -DskipTests
fi

log "Starting infrastructure (zookeeper + kafka)"
compose up -d zookeeper kafka

log "Waiting for Kafka"
for _ in $(seq 1 80); do
  if compose exec -T kafka cub kafka-ready -b kafka:9092 1 20 >/dev/null 2>&1; then
    echo "OK: kafka"
    break
  fi
  sleep 2
done

start_service "account-service" "account-service/target/account-service-1.0.0.jar"
start_service "device-service" "device-service/target/device-service-1.0.0.jar"
start_service "access-service" "access-service/target/access-service-1.0.0.jar"
start_service "onboarding-service" "onboarding-service/target/onboarding-service-1.0.0.jar"
start_service "onboarding-orchestrator" "onboarding-orchestrator/target/onboarding-orchestrator-1.0.0.jar"

log "Waiting for local services"
wait_for_url "onboarding-service" "http://localhost:8081/actuator/health"
wait_for_url "onboarding-orchestrator" "http://localhost:8082/actuator/health"
wait_for_url "account-service" "http://localhost:8083/actuator/health"
wait_for_url "device-service" "http://localhost:8084/actuator/health"
wait_for_url "access-service" "http://localhost:8085/actuator/health"

log "All services are up"
echo "PID file: ${PID_FILE}"
echo "Logs: ${RUN_DIR}/*.log"
echo "Use ./scripts/local-down.sh to stop everything."
