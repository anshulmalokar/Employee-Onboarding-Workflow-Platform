package com.onboarding.device.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    @KafkaListener(topics = KafkaTopics.WORKFLOW_COMMANDS_DLQ, groupId = "device-service-dlq-group")
    public void consumeDlq(String payload) {
        log.error("Received DLQ message on {}: {}", KafkaTopics.WORKFLOW_COMMANDS_DLQ, payload);
    }
}
