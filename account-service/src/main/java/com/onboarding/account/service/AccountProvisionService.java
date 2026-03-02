package com.onboarding.account.service;

import com.onboarding.account.messaging.WorkflowEventProducer;
import com.onboarding.contracts.events.WorkflowCommand;
import com.onboarding.contracts.events.WorkflowEvent;
import com.onboarding.contracts.enums.CommandType;
import com.onboarding.contracts.enums.EventType;
import com.onboarding.contracts.enums.StepType;
import com.onboarding.contracts.idempotency.InMemoryIdempotencyGuard;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AccountProvisionService {

    private final WorkflowEventProducer eventProducer;
    private final Set<String> provisionedEmployees = ConcurrentHashMap.newKeySet();
    private final InMemoryIdempotencyGuard idempotencyGuard = new InMemoryIdempotencyGuard(100_000);

    public AccountProvisionService(WorkflowEventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Async("accountExecutor")
    public CompletableFuture<Void> handleAsync(WorkflowCommand command) {
        if (!idempotencyGuard.shouldProcess(command.eventId())) {
            return CompletableFuture.completedFuture(null);
        }

        if (command.commandType() == CommandType.EXECUTE_STEP) {
            if (command.employeeId().contains("fail-account")) {
                eventProducer.publish(new WorkflowEvent(
                    UUID.randomUUID().toString(),
                    command.correlationId(),
                    command.sagaId(),
                    command.employeeId(),
                    StepType.ACCOUNT_PROVISION,
                    EventType.STEP_FAILED,
                    "Directory account creation failed"
                ));
                return CompletableFuture.completedFuture(null);
            }

            provisionedEmployees.add(command.employeeId());
            eventProducer.publish(new WorkflowEvent(
                UUID.randomUUID().toString(),
                command.correlationId(),
                command.sagaId(),
                command.employeeId(),
                StepType.ACCOUNT_PROVISION,
                EventType.STEP_SUCCEEDED,
                "Account provisioned"
            ));
            return CompletableFuture.completedFuture(null);
        }

        provisionedEmployees.remove(command.employeeId());
        eventProducer.publish(new WorkflowEvent(
            UUID.randomUUID().toString(),
            command.correlationId(),
            command.sagaId(),
            command.employeeId(),
            StepType.ACCOUNT_PROVISION,
            EventType.COMPENSATION_SUCCEEDED,
            "Account deprovisioned"
        ));

        return CompletableFuture.completedFuture(null);
    }
}
