package com.onboarding.account.messaging;

import com.onboarding.account.service.AccountProvisionService;
import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.WorkflowCommand;
import com.onboarding.contracts.enums.StepType;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowCommandConsumer {

    private final AccountProvisionService service;

    public WorkflowCommandConsumer(AccountProvisionService service) {
        this.service = service;
    }

    @KafkaListener(topics = KafkaTopics.WORKFLOW_COMMANDS, groupId = "account-service-group")
    public void consume(WorkflowCommand command) {
        if (command.stepType() != StepType.ACCOUNT_PROVISION) {
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
