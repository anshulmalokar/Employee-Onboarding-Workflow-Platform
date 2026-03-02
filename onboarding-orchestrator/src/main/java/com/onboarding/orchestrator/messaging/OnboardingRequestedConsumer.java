package com.onboarding.orchestrator.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.OnboardingRequestedEvent;
import com.onboarding.orchestrator.service.OrchestrationService;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OnboardingRequestedConsumer {

    private final OrchestrationService orchestrationService;

    public OnboardingRequestedConsumer(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @KafkaListener(topics = KafkaTopics.ONBOARDING_REQUESTED, groupId = "onboarding-orchestrator-group")
    public void consume(OnboardingRequestedEvent event) {
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            MDC.put("correlationId", event.correlationId());
        }

        try {
            orchestrationService.startSagaAsync(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
