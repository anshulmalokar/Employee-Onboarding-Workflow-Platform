package com.onboarding.orchestrator.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.SagaResultEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SagaResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SagaResultProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(SagaResultEvent event) {
        kafkaTemplate.send(KafkaTopics.ONBOARDING_SAGA_RESULT, event.sagaId(), event);
    }
}
