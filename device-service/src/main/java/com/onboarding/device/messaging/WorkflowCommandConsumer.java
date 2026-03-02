package com.onboarding.device.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.WorkflowCommand;
import com.onboarding.contracts.enums.StepType;
import com.onboarding.device.service.DeviceAllocationService;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowCommandConsumer {

    private final DeviceAllocationService service;

    public WorkflowCommandConsumer(DeviceAllocationService service) {
        this.service = service;
    }

    @KafkaListener(topics = KafkaTopics.WORKFLOW_COMMANDS, groupId = "device-service-group")
    public void consume(WorkflowCommand command) {
        if (command.stepType() != StepType.DEVICE_ALLOCATION) {
            return;
        }

        if (command.correlationId() != null && !command.correlationId().isBlank()) {
            MDC.put("correlationId", command.correlationId());
        }

        try {
            service.handleAsync(command);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
