# Employee Onboarding Workflow Platform

A production-oriented Spring Boot microservices project that demonstrates a complex onboarding workflow using:

- Kafka-only service-to-service communication
- Asynchronous processing (`@Async`, `CompletableFuture`)
- SAGA orchestration with compensating actions
- Dynamic workflow engine (config-driven step graph)
- Per-step timeout + retry policy + watchdog handling
- In-memory idempotency controls (API + consumer side)
- Kafka retry with exponential backoff and dead-letter topics (DLQ)
- Correlation-ID propagation for traceable logs
- DLQ replay admin APIs
- Docker, Kubernetes, GitHub Actions CI

## Workflow Engine

Workflow definitions are configurable in:

- `onboarding-orchestrator/src/main/resources/application.yml`

Example capabilities:

- configurable `start-step`
- configurable `next-steps`
- configurable `max-retries` per step
- configurable `timeout-seconds` per step
- configurable `compensation-steps`

## Services

- `onboarding-service` (REST API + in-memory onboarding state)
- `onboarding-orchestrator` (dynamic SAGA state machine)
- `account-service` (workflow worker)
- `device-service` (workflow worker)
- `access-service` (workflow worker)
- `common-contracts` (shared event model, topic constants, idempotency utility)

## Kafka Topics

- `onboarding-requested`
- `workflow-commands`
- `workflow-events`
- `onboarding-saga-result`

DLQ topics are auto-derived with `.dlq` suffix.

## Production Features Added

- **Dynamic workflow graph** from config (no hardcoded step sequence).
- **Watchdog timeout monitor** for stuck steps.
- **Policy-driven retries** per step (`max-retries`, timeout-based and failure-based).
- **SAGA compensation policies** defined by workflow configuration.
- **Consumer idempotency guard** using `eventId`.
- **Request idempotency** in API using `X-Idempotency-Key`.
- **Correlation ID tracing** via `X-Correlation-Id` propagated across all events.
- **Retry + DLQ** with Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.
- **DLQ replay API**:
  - `GET /api/admin/dlq`
  - `POST /api/admin/dlq/replay/{recordId}`
  - `POST /api/admin/dlq/replay-all?sourceTopic=workflow-events.dlq`
- **Operational hardening**:
  - graceful shutdown
  - actuator liveness/readiness probes
  - Kubernetes resource requests/limits, PDB, HPA

## Run Locally

### Build

```bash
mvn clean verify
```

### Run full local verification script

```bash
./scripts/test.sh
```

Optional flags:

- `--skip-build`
- `--keep-up`
- `--include-dlq`
- `--include-timeout`
- `--include-concurrency`
- `--full-suite`

The script validates:
- readiness/health for all services
- happy path and failure/compensation flow
- idempotency (`X-Idempotency-Key`)
- correlation-id propagation
- default workflow resolution
- API error handling (`400`/`404`)

### Run microservices on host + infra in Docker

```bash
./scripts/local-up.sh
```

This starts:
- Kafka + Zookeeper in Docker
- all Spring Boot microservices on your machine (`java -jar`)

Stop everything:

```bash
./scripts/local-down.sh
```

### Start stack

```bash
docker compose up --build
```

If your setup uses legacy Compose:

```bash
docker-compose up --build
```

### Create onboarding request

```bash
curl -X POST http://localhost:8081/api/onboarding \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: corr-12345' \
  -H 'X-Idempotency-Key: idem-emp-1001' \
  -d '{
    "employeeId": "emp-1001",
    "employeeEmail": "emp-1001@company.com",
    "department": "engineering",
    "workflowName": "onboarding"
  }'
```

### Check onboarding status

```bash
curl http://localhost:8081/api/onboarding/<sagaId>
```

## Failure Simulation

Use `employeeId` patterns:

- `fail-account-*`
- `fail-device-*`
- `fail-access-*`

## Kubernetes

```bash
kubectl apply -f infra/k8s/namespace.yml
kubectl apply -f infra/k8s/kafka-statefulset.yml
kubectl apply -f infra/k8s/services.yml
kubectl apply -f infra/k8s/resilience.yml
kubectl apply -f infra/k8s/ingress.yml
```

## Architecture

- System design + flow diagrams: `docs/system-design.md`
- API flow + Postman guide: `docs/api-flow.md`
- Postman files: `postman/Employee-Onboarding-Workflow.postman_collection.json`, `postman/local.postman_environment.json`
