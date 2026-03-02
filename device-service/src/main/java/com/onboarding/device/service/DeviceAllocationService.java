package com.onboarding.device.service;

import com.onboarding.contracts.events.WorkflowCommand;
import com.onboarding.contracts.events.WorkflowEvent;
import com.onboarding.contracts.enums.CommandType;
import com.onboarding.contracts.enums.EventType;
import com.onboarding.contracts.enums.StepType;
import com.onboarding.contracts.idempotency.InMemoryIdempotencyGuard;
import com.onboarding.device.messaging.WorkflowEventProducer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DeviceAllocationService {

    private final WorkflowEventProducer eventProducer;
    private final Map<String, String> allocatedDevicesByEmployee = new ConcurrentHashMap<>();
    private final InMemoryIdempotencyGuard idempotencyGuard = new InMemoryIdempotencyGuard(100_000);

    public DeviceAllocationService(WorkflowEventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Async("deviceExecutor")
    public CompletableFuture<Void> handleAsync(WorkflowCommand command) {
        if (!idempotencyGuard.shouldProcess(command.eventId())) {
            return CompletableFuture.completedFuture(null);
        }

        if (command.commandType() == CommandType.EXECUTE_STEP) {
            if (command.employeeId().contains("fail-device")) {
                eventProducer.publish(new WorkflowEvent(
                    UUID.randomUUID().toString(),
                    command.correlationId(),
                    command.sagaId(),
                    command.employeeId(),
                    StepType.DEVICE_ALLOCATION,
                    EventType.STEP_FAILED,
                    "No hardware inventory available"
                ));
                return CompletableFuture.completedFuture(null);
            }

            allocatedDevicesByEmployee.put(command.employeeId(), "LAPTOP-" + command.employeeId());
            eventProducer.publish(new WorkflowEvent(
                UUID.randomUUID().toString(),
                command.correlationId(),
                command.sagaId(),
                command.employeeId(),
                StepType.DEVICE_ALLOCATION,
                EventType.STEP_SUCCEEDED,
                "Device allocated"
            ));
            return CompletableFuture.completedFuture(null);
        }

        allocatedDevicesByEmployee.remove(command.employeeId());
        eventProducer.publish(new WorkflowEvent(
            UUID.randomUUID().toString(),
            command.correlationId(),
            command.sagaId(),
            command.employeeId(),
            StepType.DEVICE_ALLOCATION,
            EventType.COMPENSATION_SUCCEEDED,
            "Allocated device released"
        ));

        return CompletableFuture.completedFuture(null);
    }
}
