package com.onboarding.onboarding.service;

import com.onboarding.contracts.events.OnboardingRequestedEvent;
import com.onboarding.contracts.events.SagaResultEvent;
import com.onboarding.contracts.idempotency.InMemoryIdempotencyGuard;
import com.onboarding.onboarding.domain.OnboardingRequest;
import com.onboarding.onboarding.domain.OnboardingStatus;
import com.onboarding.onboarding.dto.CreateOnboardingRequest;
import com.onboarding.onboarding.messaging.OnboardingRequestedProducer;
import com.onboarding.onboarding.repository.OnboardingRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OnboardingService {

    private static final String DEFAULT_WORKFLOW_NAME = "onboarding";

    private final OnboardingRepository repository;
    private final OnboardingRequestedProducer producer;
    private final InMemoryIdempotencyGuard sagaResultIdempotencyGuard = new InMemoryIdempotencyGuard(100_000);

    public OnboardingService(OnboardingRepository repository, OnboardingRequestedProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    public synchronized OnboardingRequest start(CreateOnboardingRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            OnboardingRequest existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        String correlationId = resolveCorrelationId();

        OnboardingRequest onboardingRequest = new OnboardingRequest();
        onboardingRequest.setSagaId(UUID.randomUUID().toString());
        onboardingRequest.setCorrelationId(correlationId);
        onboardingRequest.setWorkflowName(resolveWorkflowName(request.workflowName()));
        onboardingRequest.setEmployeeId(request.employeeId());
        onboardingRequest.setEmployeeEmail(request.employeeEmail());
        onboardingRequest.setDepartment(request.department());
        onboardingRequest.setStatus(OnboardingStatus.IN_PROGRESS);
        onboardingRequest.setStatusMessage("Saga initiated");
        onboardingRequest.setCreatedAt(Instant.now());
        repository.save(onboardingRequest);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            repository.mapIdempotencyKey(idempotencyKey, onboardingRequest.getSagaId());
        }

        producer.publish(new OnboardingRequestedEvent(
            UUID.randomUUID().toString(),
            correlationId,
            onboardingRequest.getWorkflowName(),
            onboardingRequest.getSagaId(),
            onboardingRequest.getEmployeeId(),
            onboardingRequest.getEmployeeEmail(),
            onboardingRequest.getDepartment()
        ));

        return onboardingRequest;
    }

    public OnboardingRequest get(String sagaId) {
        return repository.findBySagaId(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Onboarding request not found: " + sagaId));
    }

    @Async("onboardingExecutor")
    public CompletableFuture<Void> applySagaResultAsync(SagaResultEvent event) {
        if (!sagaResultIdempotencyGuard.shouldProcess(event.eventId())) {
            return CompletableFuture.completedFuture(null);
        }

        repository.findBySagaId(event.sagaId()).ifPresent(request -> {
            if ("COMPLETED".equalsIgnoreCase(event.status())) {
                request.setStatus(OnboardingStatus.COMPLETED);
            } else {
                request.setStatus(OnboardingStatus.FAILED);
            }
            request.setStatusMessage(event.message());
            repository.save(request);
        });
        return CompletableFuture.completedFuture(null);
    }

    private String resolveCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private String resolveWorkflowName(String workflowName) {
        if (workflowName == null || workflowName.isBlank()) {
            return DEFAULT_WORKFLOW_NAME;
        }
        return workflowName;
    }
}
