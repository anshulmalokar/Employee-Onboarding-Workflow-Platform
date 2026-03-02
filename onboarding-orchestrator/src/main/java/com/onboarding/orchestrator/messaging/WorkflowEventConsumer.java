package com.onboarding.orchestrator.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.WorkflowEvent;
import com.onboarding.orchestrator.service.OrchestrationService;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventConsumer {

    private final OrchestrationService orchestrationService;

    public WorkflowEventConsumer(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @KafkaListener(topics = KafkaTopics.WORKFLOW_EVENTS, groupId = "onboarding-orchestrator-group")
    public void consume(WorkflowEvent event) {
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            MDC.put("correlationId", event.correlationId());
        }

        try {
            orchestrationService.handleWorkflowEventAsync(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
