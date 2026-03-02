package com.onboarding.onboarding.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.OnboardingRequestedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OnboardingRequestedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OnboardingRequestedProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OnboardingRequestedEvent event) {
        kafkaTemplate.send(KafkaTopics.ONBOARDING_REQUESTED, event.sagaId(), event);
    }
}
