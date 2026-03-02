package com.onboarding.access.service;

import com.onboarding.access.messaging.WorkflowEventProducer;
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
public class AccessGrantService {

    private final WorkflowEventProducer eventProducer;
    private final Set<String> grantedUsers = ConcurrentHashMap.newKeySet();
    private final InMemoryIdempotencyGuard idempotencyGuard = new InMemoryIdempotencyGuard(100_000);

    public AccessGrantService(WorkflowEventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Async("accessExecutor")
    public CompletableFuture<Void> handleAsync(WorkflowCommand command) {
        if (!idempotencyGuard.shouldProcess(command.eventId())) {
            return CompletableFuture.completedFuture(null);
        }

        if (command.commandType() == CommandType.EXECUTE_STEP) {
            if (command.employeeId().contains("fail-access")) {
                eventProducer.publish(new WorkflowEvent(
                    UUID.randomUUID().toString(),
                    command.correlationId(),
                    command.sagaId(),
                    command.employeeId(),
                    StepType.ACCESS_GRANT,
                    EventType.STEP_FAILED,
                    "IAM policy assignment failed"
                ));
                return CompletableFuture.completedFuture(null);
            }

            grantedUsers.add(command.employeeId());
            eventProducer.publish(new WorkflowEvent(
                UUID.randomUUID().toString(),
                command.correlationId(),
                command.sagaId(),
                command.employeeId(),
                StepType.ACCESS_GRANT,
                EventType.STEP_SUCCEEDED,
                "Access granted"
            ));
            return CompletableFuture.completedFuture(null);
        }

        grantedUsers.remove(command.employeeId());
        eventProducer.publish(new WorkflowEvent(
            UUID.randomUUID().toString(),
            command.correlationId(),
            command.sagaId(),
            command.employeeId(),
            StepType.ACCESS_GRANT,
            EventType.COMPENSATION_SUCCEEDED,
            "Granted access revoked"
        ));

        return CompletableFuture.completedFuture(null);
    }
}
