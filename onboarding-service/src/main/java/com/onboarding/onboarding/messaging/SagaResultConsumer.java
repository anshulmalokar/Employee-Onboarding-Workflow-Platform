package com.onboarding.onboarding.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.SagaResultEvent;
import com.onboarding.onboarding.service.OnboardingService;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SagaResultConsumer {

    private final OnboardingService onboardingService;

    public SagaResultConsumer(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @KafkaListener(topics = KafkaTopics.ONBOARDING_SAGA_RESULT, groupId = "onboarding-service-group")
    public void consume(SagaResultEvent event) {
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            MDC.put("correlationId", event.correlationId());
        }

        try {
            onboardingService.applySagaResultAsync(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
