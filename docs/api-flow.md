# Complete API Flow and Local Testing Guide

## 1) End-to-End Flow

1. Client calls `POST /api/onboarding` on `onboarding-service`.
2. `onboarding-service` creates `sagaId`, persists in-memory request state, and publishes `onboarding-requested` to Kafka.
3. `onboarding-orchestrator` consumes event and starts workflow from configured `start-step`.
4. Orchestrator publishes `workflow-commands` for each step.
5. Worker services consume commands:
   - `account-service` -> `ACCOUNT_PROVISION`
   - `device-service` -> `DEVICE_ALLOCATION`
   - `access-service` -> `ACCESS_GRANT`
6. Workers publish `workflow-events` (success/failure).
7. Orchestrator drives next step / retry / compensation (SAGA).
8. Orchestrator publishes `onboarding-saga-result`.
9. `onboarding-service` consumes result and updates final status.
10. Client checks final result via `GET /api/onboarding/{sagaId}`.

## 2) Run Modes

### A) Full Docker stack (all microservices in containers)

```bash
docker compose up --build -d
docker compose ps
```

### B) Hybrid local mode (Kafka/Zookeeper in Docker, Spring services on host)

```bash
./scripts/local-up.sh
# ... test APIs
./scripts/local-down.sh
```

## 3) API Endpoints

### Health checks
- `GET http://localhost:8081/actuator/health`
- `GET http://localhost:8082/actuator/health`
- `GET http://localhost:8083/actuator/health`
- `GET http://localhost:8084/actuator/health`
- `GET http://localhost:8085/actuator/health`

### Core onboarding APIs
- `POST http://localhost:8081/api/onboarding`
- `GET http://localhost:8081/api/onboarding/{sagaId}`

### Admin DLQ APIs
- `GET http://localhost:8082/api/admin/dlq`
- `POST http://localhost:8082/api/admin/dlq/replay/{recordId}`
- `POST http://localhost:8082/api/admin/dlq/replay-all`

## 4) Default Request Bodies

### Happy path
```json
{
  "employeeId": "emp-1001",
  "employeeEmail": "emp-1001@company.com",
  "department": "engineering",
  "workflowName": "onboarding"
}
```

### Trigger device failure (for compensation flow)
```json
{
  "employeeId": "fail-device-1002",
  "employeeEmail": "emp-1002@company.com",
  "department": "engineering",
  "workflowName": "onboarding"
}
```

### Default workflow test (omit workflowName)
```json
{
  "employeeId": "emp-4001",
  "employeeEmail": "emp-4001@company.com",
  "department": "ops"
}
```

## 5) cURL Commands (Postman-import friendly)

### Create onboarding (happy)
```bash
curl -X POST http://localhost:8081/api/onboarding \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: corr-happy-1' \
  -H 'X-Idempotency-Key: idem-happy-1' \
  -d '{
    "employeeId": "emp-1001",
    "employeeEmail": "emp-1001@company.com",
    "department": "engineering",
    "workflowName": "onboarding"
  }'
```

### Get onboarding by sagaId
```bash
curl http://localhost:8081/api/onboarding/<sagaId>
```

### Create onboarding (failure + compensation)
```bash
curl -X POST http://localhost:8081/api/onboarding \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: corr-fail-1' \
  -H 'X-Idempotency-Key: idem-fail-1' \
  -d '{
    "employeeId": "fail-device-1002",
    "employeeEmail": "emp-1002@company.com",
    "department": "engineering",
    "workflowName": "onboarding"
  }'
```

### Default workflow resolution
```bash
curl -X POST http://localhost:8081/api/onboarding \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: corr-default-1' \
  -H 'X-Idempotency-Key: idem-default-1' \
  -d '{
    "employeeId": "emp-4001",
    "employeeEmail": "emp-4001@company.com",
    "department": "ops"
  }'
```

### API error handling checks
```bash
curl -i http://localhost:8081/api/onboarding/does-not-exist

curl -i -X POST http://localhost:8081/api/onboarding \
  -H 'Content-Type: application/json' \
  -d '{"employeeId":"","employeeEmail":"invalid","department":""}'
```

### DLQ APIs
```bash
curl http://localhost:8082/api/admin/dlq
curl -X POST http://localhost:8082/api/admin/dlq/replay-all
curl -X POST http://localhost:8082/api/admin/dlq/replay/<recordId>
```

## 6) Postman Import

Import these files:

- `postman/Employee-Onboarding-Workflow.postman_collection.json`
- `postman/local.postman_environment.json`

Then choose `Local` environment and run collection in order.

