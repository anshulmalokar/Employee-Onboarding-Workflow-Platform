package com.onboarding.account.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.WorkflowEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WorkflowEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(WorkflowEvent event) {
        kafkaTemplate.send(KafkaTopics.WORKFLOW_EVENTS, event.sagaId(), event);
    }
}
