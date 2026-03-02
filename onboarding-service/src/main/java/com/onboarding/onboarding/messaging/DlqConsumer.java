package com.onboarding.onboarding.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    @KafkaListener(topics = KafkaTopics.ONBOARDING_SAGA_RESULT_DLQ, groupId = "onboarding-service-dlq-group")
    public void consumeSagaResultDlq(String payload) {
        log.error("Received DLQ message on {}: {}", KafkaTopics.ONBOARDING_SAGA_RESULT_DLQ, payload);
    }
}
