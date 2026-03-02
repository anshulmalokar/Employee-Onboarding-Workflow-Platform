package com.onboarding.orchestrator.service;

import com.onboarding.contracts.events.OnboardingRequestedEvent;
import com.onboarding.contracts.events.SagaResultEvent;
import com.onboarding.contracts.events.WorkflowCommand;
import com.onboarding.contracts.events.WorkflowEvent;
import com.onboarding.contracts.enums.CommandType;
import com.onboarding.contracts.enums.EventType;
import com.onboarding.contracts.enums.StepType;
import com.onboarding.contracts.idempotency.InMemoryIdempotencyGuard;
import com.onboarding.orchestrator.messaging.SagaResultProducer;
import com.onboarding.orchestrator.messaging.WorkflowCommandProducer;
import com.onboarding.orchestrator.state.SagaState;
import com.onboarding.orchestrator.workflow.StepPolicy;
import com.onboarding.orchestrator.workflow.WorkflowDefinition;
import com.onboarding.orchestrator.workflow.WorkflowDefinitionResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final WorkflowCommandProducer commandProducer;
    private final SagaResultProducer resultProducer;
    private final WorkflowDefinitionResolver workflowResolver;
    private final Map<String, SagaState> sagaStates = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> sagaLocks = new ConcurrentHashMap<>();
    private final InMemoryIdempotencyGuard onboardingRequestedGuard = new InMemoryIdempotencyGuard(100_000);
    private final InMemoryIdempotencyGuard workflowEventGuard = new InMemoryIdempotencyGuard(100_000);

    public OrchestrationService(
        WorkflowCommandProducer commandProducer,
        SagaResultProducer resultProducer,
        WorkflowDefinitionResolver workflowResolver
    ) {
        this.commandProducer = commandProducer;
        this.resultProducer = resultProducer;
        this.workflowResolver = workflowResolver;
    }

    @Async("orchestratorExecutor")
    public CompletableFuture<Void> startSagaAsync(OnboardingRequestedEvent event) {
        if (!onboardingRequestedGuard.shouldProcess(event.eventId())) {
            return CompletableFuture.completedFuture(null);
        }

        executeWithSagaLock(event.sagaId(), () -> {
            if (sagaStates.containsKey(event.sagaId())) {
                return null;
            }

            String workflowName = workflowResolver.resolveWorkflowName(event.workflowName());
            WorkflowDefinition workflowDefinition;

            try {
                workflowDefinition = workflowResolver.resolve(workflowName);
            } catch (IllegalArgumentException exception) {
                publishFailure(event.correlationId(), event.sagaId(), event.employeeId(), exception.getMessage());
                return null;
            }

            SagaState state = new SagaState(
                event.sagaId(),
                event.correlationId(),
                workflowName,
                event.employeeId(),
                event.employeeEmail(),
                event.department()
            );
            sagaStates.put(event.sagaId(), state);

            dispatchExecute(state, workflowDefinition.getStartStep(), "Start workflow " + workflowName);
            return null;
        });

        return CompletableFuture.completedFuture(null);
    }

    @Async("orchestratorExecutor")
    public CompletableFuture<Void> handleWorkflowEventAsync(WorkflowEvent event) {
        if (!workflowEventGuard.shouldProcess(event.eventId())) {
            return CompletableFuture.completedFuture(null);
        }

        executeWithSagaLock(event.sagaId(), () -> {
            SagaState state = sagaStates.get(event.sagaId());
            if (state == null || state.isTerminal()) {
                return null;
            }

            WorkflowDefinition workflowDefinition;
            try {
                workflowDefinition = workflowResolver.resolve(state.getWorkflowName());
            } catch (Exception ex) {
                triggerCompensationAndFail(state, null, null, "Workflow definition unavailable");
                return null;
            }

            if (event.eventType() == EventType.STEP_SUCCEEDED) {
                handleStepSuccess(state, workflowDefinition, event);
                return null;
            }

            if (event.eventType() == EventType.STEP_FAILED) {
                handleStepFailure(state, workflowDefinition, event);
                return null;
            }

            if (event.eventType() == EventType.COMPENSATION_SUCCEEDED || event.eventType() == EventType.COMPENSATION_FAILED) {
                state.clearInProgress(event.stepType());
            }

            return null;
        });

        return CompletableFuture.completedFuture(null);
    }

    public void handleTimeouts() {
        long now = System.currentTimeMillis();
        List<String> sagaIds = new ArrayList<>(sagaStates.keySet());

        for (String sagaId : sagaIds) {
            executeWithSagaLock(sagaId, () -> {
                SagaState state = sagaStates.get(sagaId);
                if (state == null || state.isTerminal()) {
                    return null;
                }

                WorkflowDefinition workflowDefinition;
                try {
                    workflowDefinition = workflowResolver.resolve(state.getWorkflowName());
                } catch (Exception ex) {
                    triggerCompensationAndFail(state, null, null, "Workflow definition unavailable");
                    return null;
                }

                for (StepType inProgressStep : state.inProgressSnapshot()) {
                    StepPolicy policy = workflowDefinition.getPolicy(inProgressStep);
                    if (policy == null) {
                        continue;
                    }

                    Long lastCommandAt = state.getLastCommandEpochMillis(inProgressStep);
                    if (lastCommandAt == null) {
                        continue;
                    }

                    long elapsed = now - lastCommandAt;
                    long timeoutMillis = Math.max(1, policy.getTimeoutSeconds()) * 1000L;

                    if (elapsed < timeoutMillis) {
                        continue;
                    }

                    int attempts = state.getAttempts(inProgressStep);
                    if (attempts <= policy.getMaxRetries()) {
                        dispatchExecute(
                            state,
                            inProgressStep,
                            "Retry after timeout (attempt " + (attempts + 1) + ")"
                        );
                    } else {
                        triggerCompensationAndFail(
                            state,
                            workflowDefinition,
                            inProgressStep,
                            "Step timed out after max retries"
                        );
                    }
                }
                return null;
            });
        }
    }

    private void handleStepSuccess(SagaState state, WorkflowDefinition workflowDefinition, WorkflowEvent event) {
        if (state.hasCompleted(event.stepType())) {
            return;
        }

        state.markStepSucceeded(event.stepType());
        StepPolicy policy = workflowDefinition.getPolicy(event.stepType());

        if (policy == null) {
            triggerCompensationAndFail(state, workflowDefinition, event.stepType(), "Missing step policy");
            return;
        }

        for (StepType nextStep : policy.getNextSteps()) {
            if (!state.hasCompleted(nextStep) && !state.isInProgress(nextStep)) {
                dispatchExecute(state, nextStep, "Triggered by " + event.stepType());
            }
        }

        if (state.isWorkflowCompleted(workflowDefinition.getSteps().keySet())) {
            publishSuccess(state);
        }
    }

    private void handleStepFailure(SagaState state, WorkflowDefinition workflowDefinition, WorkflowEvent event) {
        state.clearInProgress(event.stepType());

        StepPolicy policy = workflowDefinition.getPolicy(event.stepType());
        if (policy == null) {
            triggerCompensationAndFail(state, workflowDefinition, event.stepType(), event.detail());
            return;
        }

        int attempts = state.getAttempts(event.stepType());
        if (attempts <= policy.getMaxRetries()) {
            dispatchExecute(
                state,
                event.stepType(),
                "Retry after failure (attempt " + (attempts + 1) + "): " + event.detail()
            );
            return;
        }

        triggerCompensationAndFail(state, workflowDefinition, event.stepType(), event.detail());
    }

    private void triggerCompensationAndFail(
        SagaState state,
        WorkflowDefinition workflowDefinition,
        StepType failedStep,
        String detail
    ) {
        if (state.isTerminal()) {
            return;
        }

        List<StepType> compensationSteps = resolveCompensationSteps(state, workflowDefinition, failedStep);
        for (StepType stepToCompensate : compensationSteps) {
            if (!state.hasCompleted(stepToCompensate)) {
                continue;
            }
            if (!state.markCompensationDispatched(stepToCompensate)) {
                continue;
            }
            dispatchCompensation(
                state,
                stepToCompensate,
                "Compensating due to failure at " + failedStep + ": " + detail
            );
        }

        publishFailure(
            state.getCorrelationId(),
            state.getSagaId(),
            state.getEmployeeId(),
            "Onboarding failed at " + failedStep + ": " + detail
        );
        cleanupSagaState(state);
    }

    private List<StepType> resolveCompensationSteps(SagaState state, WorkflowDefinition workflowDefinition, StepType failedStep) {
        if (workflowDefinition != null && failedStep != null) {
            StepPolicy policy = workflowDefinition.getPolicy(failedStep);
            if (policy != null && !policy.getCompensationSteps().isEmpty()) {
                return policy.getCompensationSteps();
            }
        }
        return state.compensationCandidatesInReverse();
    }

    private void dispatchExecute(SagaState state, StepType stepType, String reason) {
        state.markCommandDispatched(stepType);

        commandProducer.publish(new WorkflowCommand(
            UUID.randomUUID().toString(),
            state.getCorrelationId(),
            state.getSagaId(),
            state.getEmployeeId(),
            state.getEmployeeEmail(),
            state.getDepartment(),
            stepType,
            CommandType.EXECUTE_STEP,
            reason
        ));
    }

    private void dispatchCompensation(SagaState state, StepType stepType, String reason) {
        commandProducer.publish(new WorkflowCommand(
            UUID.randomUUID().toString(),
            state.getCorrelationId(),
            state.getSagaId(),
            state.getEmployeeId(),
            state.getEmployeeEmail(),
            state.getDepartment(),
            stepType,
            CommandType.COMPENSATE_STEP,
            reason
        ));
    }

    private void publishSuccess(SagaState state) {
        if (state.isTerminal()) {
            return;
        }

        resultProducer.publish(new SagaResultEvent(
            UUID.randomUUID().toString(),
            state.getCorrelationId(),
            state.getSagaId(),
            state.getEmployeeId(),
            "COMPLETED",
            "Workflow " + state.getWorkflowName() + " completed"
        ));
        cleanupSagaState(state);
    }

    private void publishFailure(String correlationId, String sagaId, String employeeId, String message) {
        log.warn("Saga failed. sagaId={}, reason={}", sagaId, message);
        resultProducer.publish(new SagaResultEvent(
            UUID.randomUUID().toString(),
            correlationId,
            sagaId,
            employeeId,
            "FAILED",
            message
        ));
    }

    private <T> T executeWithSagaLock(String sagaId, Supplier<T> supplier) {
        ReentrantLock lock = sagaLocks.computeIfAbsent(sagaId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
            if (!sagaStates.containsKey(sagaId) && !lock.hasQueuedThreads()) {
                sagaLocks.remove(sagaId, lock);
            }
        }
    }

    private void cleanupSagaState(SagaState state) {
        state.setTerminal(true);
        sagaStates.remove(state.getSagaId());
    }
}
