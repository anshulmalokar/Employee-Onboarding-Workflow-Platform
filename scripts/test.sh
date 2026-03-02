#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

KEEP_UP=false
SKIP_BUILD=false
INCLUDE_DLQ=false
INCLUDE_TIMEOUT=false
INCLUDE_CONCURRENCY=false
FULL_SUITE=false

WAIT_ATTEMPTS="${WAIT_ATTEMPTS:-120}"
WAIT_INTERVAL_SECONDS="${WAIT_INTERVAL_SECONDS:-2}"
INITIAL_GRACE_SECONDS="${INITIAL_GRACE_SECONDS:-12}"

usage() {
  cat <<'EOF2'
Usage: scripts/test.sh [options]

Options:
  --skip-build          Skip mvn clean verify
  --keep-up             Keep docker compose stack running after tests
  --include-dlq         Run DLQ capture/replay checks
  --include-timeout     Run timeout/retry smoke check (stops device-service temporarily)
  --include-concurrency Run concurrent onboarding requests smoke check
  --full-suite          Run all optional checks (DLQ + timeout + concurrency)
  -h, --help            Show this help

Environment overrides:
  WAIT_ATTEMPTS         Health-check attempts (default: 120)
  WAIT_INTERVAL_SECONDS Seconds between attempts (default: 2)
  INITIAL_GRACE_SECONDS Initial sleep before checks (default: 12)
EOF2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true ;;
    --keep-up) KEEP_UP=true ;;
    --include-dlq) INCLUDE_DLQ=true ;;
    --include-timeout) INCLUDE_TIMEOUT=true ;;
    --include-concurrency) INCLUDE_CONCURRENCY=true ;;
    --full-suite) FULL_SUITE=true ;;
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

extract_json_field() {
  local json="$1"
  local key="$2"

  if command -v jq >/dev/null 2>&1; then
    jq -r ".${key} // empty" <<<"$json"
    return
  fi

  sed -n "s/.*\"${key}\":\"\([^\"]*\)\".*/\1/p" <<<"$json"
}

STACK_STARTED=false
COMPOSE_CMD=()

compose() {
  "${COMPOSE_CMD[@]}" "$@"
}

detect_compose_cmd() {
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

dump_service_diagnostics() {
  local service="$1"

  log "Diagnostics: compose ps"
  compose ps || true

  log "Diagnostics: recent logs for ${service}"
  compose logs --tail=200 "$service" || true
}

wait_for_health() {
  local service="$1"
  local url="$2"
  local fallback_url="${3:-}"
  local response=""
  local status=""

  for _ in $(seq 1 "$WAIT_ATTEMPTS"); do
    response="$(curl -s "$url" || true)"
    status="$(extract_json_field "$response" "status")"

    if [[ "$status" != "UP" && -n "$fallback_url" ]]; then
      response="$(curl -s "$fallback_url" || true)"
      status="$(extract_json_field "$response" "status")"
    fi

    if [[ "$status" == "UP" ]]; then
      echo "OK: ${service}"
      return 0
    fi

    sleep "$WAIT_INTERVAL_SECONDS"
  done

  dump_service_diagnostics "$service"
  fail "Health check failed for ${service} (${url}). Last response: ${response:-<empty>}"
}

post_onboarding() {
  local employee_id="$1"
  local email="$2"
  local department="$3"
  local idempotency_key="$4"
  local correlation_id="$5"
  local workflow_name="${6:-onboarding}"

  curl -fsS -X POST "http://localhost:8081/api/onboarding" \
    -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: ${idempotency_key}" \
    -H "X-Correlation-Id: ${correlation_id}" \
    -d "{\"employeeId\":\"${employee_id}\",\"employeeEmail\":\"${email}\",\"department\":\"${department}\",\"workflowName\":\"${workflow_name}\"}"
}

post_onboarding_without_workflow() {
  local employee_id="$1"
  local email="$2"
  local department="$3"
  local idempotency_key="$4"
  local correlation_id="$5"

  curl -fsS -X POST "http://localhost:8081/api/onboarding" \
    -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: ${idempotency_key}" \
    -H "X-Correlation-Id: ${correlation_id}" \
    -d "{\"employeeId\":\"${employee_id}\",\"employeeEmail\":\"${email}\",\"department\":\"${department}\"}"
}

assert_http_status() {
  local url="$1"
  local expected="$2"
  local method="${3:-GET}"
  local data="${4:-}"
  local temp_file
  temp_file="$(mktemp)"

  local status
  if [[ -n "$data" ]]; then
    status="$(curl -s -o "$temp_file" -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' "$url" -d "$data")"
  else
    status="$(curl -s -o "$temp_file" -w '%{http_code}' -X "$method" "$url")"
  fi

  if [[ "$status" != "$expected" ]]; then
    local body
    body="$(cat "$temp_file")"
    rm -f "$temp_file"
    fail "Expected HTTP ${expected} from ${url}, got ${status}. Body: ${body}"
  fi

  rm -f "$temp_file"
}

cleanup() {
  if [[ "$STACK_STARTED" == "true" && "$KEEP_UP" == "false" ]]; then
    log "Stopping docker compose stack"
    compose down -v >/dev/null
  fi
}

trap cleanup EXIT

require_cmd docker
require_cmd curl
require_cmd mvn

detect_compose_cmd

if [[ "$FULL_SUITE" == "true" ]]; then
  INCLUDE_DLQ=true
  INCLUDE_TIMEOUT=true
  INCLUDE_CONCURRENCY=true
fi

if [[ "$SKIP_BUILD" == "false" ]]; then
  log "Building project"
  mvn clean verify
fi

log "Starting docker compose stack"
compose up --build -d
STACK_STARTED=true

log "Allowing initial startup grace (${INITIAL_GRACE_SECONDS}s)"
sleep "$INITIAL_GRACE_SECONDS"

log "Waiting for readiness endpoints"
wait_for_health "onboarding-service" "http://localhost:8081/actuator/health/readiness" "http://localhost:8081/actuator/health"
wait_for_health "onboarding-orchestrator" "http://localhost:8082/actuator/health/readiness" "http://localhost:8082/actuator/health"
wait_for_health "account-service" "http://localhost:8083/actuator/health/readiness" "http://localhost:8083/actuator/health"
wait_for_health "device-service" "http://localhost:8084/actuator/health/readiness" "http://localhost:8084/actuator/health"
wait_for_health "access-service" "http://localhost:8085/actuator/health/readiness" "http://localhost:8085/actuator/health"

log "Running happy-path onboarding test"
HAPPY_RESP="$(post_onboarding "emp-1001" "emp-1001@company.com" "engineering" "idem-happy-1" "corr-happy-1")"
HAPPY_SAGA_ID="$(extract_json_field "$HAPPY_RESP" "sagaId")"
[[ -n "$HAPPY_SAGA_ID" ]] || fail "Could not parse sagaId from happy-path response: $HAPPY_RESP"
sleep 5
HAPPY_STATUS_RESP="$(curl -fsS "http://localhost:8081/api/onboarding/${HAPPY_SAGA_ID}")"
HAPPY_STATUS="$(extract_json_field "$HAPPY_STATUS_RESP" "status")"
[[ "$HAPPY_STATUS" == "COMPLETED" ]] || fail "Expected COMPLETED, got: $HAPPY_STATUS_RESP"

log "Running failure/compensation test"
FAIL_RESP="$(post_onboarding "fail-device-1002" "emp-1002@company.com" "engineering" "idem-fail-1" "corr-fail-1")"
FAIL_SAGA_ID="$(extract_json_field "$FAIL_RESP" "sagaId")"
[[ -n "$FAIL_SAGA_ID" ]] || fail "Could not parse sagaId from failure response: $FAIL_RESP"
sleep 5
FAIL_STATUS_RESP="$(curl -fsS "http://localhost:8081/api/onboarding/${FAIL_SAGA_ID}")"
FAIL_STATUS="$(extract_json_field "$FAIL_STATUS_RESP" "status")"
[[ "$FAIL_STATUS" == "FAILED" ]] || fail "Expected FAILED, got: $FAIL_STATUS_RESP"

log "Running idempotency test"
IDEM_RESP1="$(post_onboarding "emp-2001" "emp-2001@company.com" "ops" "idem-same-1" "corr-idem-1")"
IDEM_RESP2="$(post_onboarding "emp-2001" "emp-2001@company.com" "ops" "idem-same-1" "corr-idem-2")"
IDEM_SAGA1="$(extract_json_field "$IDEM_RESP1" "sagaId")"
IDEM_SAGA2="$(extract_json_field "$IDEM_RESP2" "sagaId")"
[[ -n "$IDEM_SAGA1" && -n "$IDEM_SAGA2" ]] || fail "Could not parse idempotency responses"
[[ "$IDEM_SAGA1" == "$IDEM_SAGA2" ]] || fail "Idempotency failed: saga IDs differ ($IDEM_SAGA1 vs $IDEM_SAGA2)"

log "Validating correlation-id propagation"
CORR_RESP="$(post_onboarding "emp-3001" "emp-3001@company.com" "platform" "idem-corr-1" "corr-check-123")"
CORR_VALUE="$(extract_json_field "$CORR_RESP" "correlationId")"
[[ "$CORR_VALUE" == "corr-check-123" ]] || fail "CorrelationId propagation failed. Response: $CORR_RESP"

log "Validating default workflow resolution"
WF_RESP="$(post_onboarding_without_workflow "emp-4001" "emp-4001@company.com" "ops" "idem-wf-1" "corr-wf-1")"
WF_VALUE="$(extract_json_field "$WF_RESP" "workflowName")"
[[ "$WF_VALUE" == "onboarding" ]] || fail "Expected workflowName=onboarding, got response: $WF_RESP"

log "Validating API error handling"
assert_http_status "http://localhost:8081/api/onboarding/does-not-exist" "404" "GET"
assert_http_status "http://localhost:8081/api/onboarding" "400" "POST" '{"employeeId":"","employeeEmail":"invalid","department":""}'

if [[ "$INCLUDE_TIMEOUT" == "true" ]]; then
  log "Running timeout/retry smoke test"
  compose stop device-service >/dev/null
  TIMEOUT_RESP="$(post_onboarding "emp-timeout-1" "emp-timeout-1@company.com" "engineering" "idem-timeout-1" "corr-timeout-1")"
  TIMEOUT_SAGA_ID="$(extract_json_field "$TIMEOUT_RESP" "sagaId")"
  [[ -n "$TIMEOUT_SAGA_ID" ]] || fail "Could not parse timeout saga ID"
  sleep 30
  compose start device-service >/dev/null
  sleep 10
  curl -fsS "http://localhost:8081/api/onboarding/${TIMEOUT_SAGA_ID}" >/dev/null
fi

if [[ "$INCLUDE_DLQ" == "true" ]]; then
  log "Running DLQ capture/replay smoke test"
  compose exec -T kafka kafka-console-producer \
    --bootstrap-server kafka:9092 \
    --topic onboarding-requested.dlq <<'EOF2'
{"eventId":"evt-dlq-1","correlationId":"corr-dlq-1","workflowName":"onboarding","sagaId":"saga-dlq-1","employeeId":"emp-dlq-1","employeeEmail":"emp-dlq-1@company.com","department":"it"}
EOF2
  sleep 2
  curl -fsS "http://localhost:8082/api/admin/dlq" >/dev/null
  curl -fsS -X POST "http://localhost:8082/api/admin/dlq/replay-all" >/dev/null
fi

if [[ "$INCLUDE_CONCURRENCY" == "true" ]]; then
  log "Running concurrent request smoke test"
  for i in $(seq 1 25); do
    (
      post_onboarding "emp-conc-${i}" "emp-conc-${i}@company.com" "engineering" "idem-conc-${i}" "corr-conc-${i}" >/dev/null
    ) &
  done
  wait
fi

log "All checks passed"
echo "SUCCESS: Local verification suite completed."
if [[ "$KEEP_UP" == "true" ]]; then
  echo "docker compose stack is still running (--keep-up)."
fi
