package com.onboarding.orchestrator.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.contracts.events.WorkflowCommand;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkflowCommandProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WorkflowCommandProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(WorkflowCommand command) {
        kafkaTemplate.send(KafkaTopics.WORKFLOW_COMMANDS, command.sagaId(), command);
    }
}
