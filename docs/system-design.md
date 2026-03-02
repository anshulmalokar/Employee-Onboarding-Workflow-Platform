# Employee Onboarding Workflow: Production-Oriented System Design

## High-Level Flow

```mermaid
flowchart LR
    C[HR Client] -->|POST /api/onboarding| OS[Onboarding Service]
    OS -->|onboarding-requested| K[(Kafka)]
    K -->|consume| ORCH[Onboarding Orchestrator]

    ORCH -->|workflow-commands ACCOUNT_PROVISION| K
    K --> AS[Account Service]
    AS -->|workflow-events| K

    ORCH -->|workflow-commands DEVICE_ALLOCATION| K
    K --> DS[Device Service]
    DS -->|workflow-events| K

    ORCH -->|workflow-commands ACCESS_GRANT| K
    K --> ACS[Access Service]
    ACS -->|workflow-events| K

    ORCH -->|onboarding-saga-result| K
    K -->|consume| OS
```

## Dynamic Workflow Engine

The orchestrator reads workflow definitions from configuration. Each workflow defines:

- start step
- next steps (supports fan-out)
- max retries per step
- timeout per step
- compensation graph

This enables changing behavior without code-level orchestration rewrites.

## Event Model

All events/commands carry:

- `eventId` for idempotent processing
- `correlationId` for traceability across services
- `sagaId` for workflow-level grouping
- `workflowName` at saga start

## Success Workflow

```mermaid
sequenceDiagram
    autonumber
    participant H as HR App
    participant O as Onboarding Service
    participant K as Kafka
    participant S as Orchestrator
    participant A as Account Service
    participant D as Device Service
    participant X as Access Service

    H->>O: Create onboarding request (X-Correlation-Id, X-Idempotency-Key)
    O->>K: onboarding-requested(eventId, workflowName)
    K->>S: onboarding-requested

    S->>K: command ACCOUNT_PROVISION
    K->>A: ACCOUNT_PROVISION
    A->>K: STEP_SUCCEEDED

    S->>K: command DEVICE_ALLOCATION
    K->>D: DEVICE_ALLOCATION
    D->>K: STEP_SUCCEEDED

    S->>K: command ACCESS_GRANT
    K->>X: ACCESS_GRANT
    X->>K: STEP_SUCCEEDED

    S->>K: onboarding-saga-result COMPLETED
    K->>O: COMPLETED
    O->>O: Async status update
```

## Failure, Retry, Timeout and Compensation

```mermaid
sequenceDiagram
    autonumber
    participant S as Orchestrator
    participant K as Kafka
    participant D as Device Service
    participant A as Account Service

    S->>K: command DEVICE_ALLOCATION (attempt 1)
    K->>D: DEVICE_ALLOCATION
    D-->>K: no response before timeout

    Note over S: Watchdog detects timeout
    S->>K: command DEVICE_ALLOCATION (retry)
    K->>D: DEVICE_ALLOCATION
    D->>K: STEP_FAILED

    Note over S: Retry budget exhausted
    S->>K: command COMPENSATE ACCOUNT_PROVISION
    K->>A: COMPENSATE ACCOUNT_PROVISION
    A->>K: COMPENSATION_SUCCEEDED

    S->>K: onboarding-saga-result FAILED
```

## DLQ Replay Operations

The orchestrator captures DLQ records in-memory and exposes replay APIs:

- `GET /api/admin/dlq`
- `POST /api/admin/dlq/replay/{recordId}`
- `POST /api/admin/dlq/replay-all?sourceTopic=<topic.dlq>`

This provides operational recovery without touching Kafka directly.

## Reliability and Operability

- In-memory idempotency guards in all consumers to ignore duplicate `eventId`.
- API idempotency through `X-Idempotency-Key`.
- Retry with exponential backoff and automatic dead-letter routing (`*.dlq`).
- Dedicated DLQ consumers for operational visibility.
- Graceful shutdown with health/readiness probes for Kubernetes.
- Resource requests/limits, PDB, and HPA to improve runtime resilience.
