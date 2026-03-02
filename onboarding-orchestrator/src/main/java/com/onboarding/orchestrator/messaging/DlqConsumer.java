package com.onboarding.orchestrator.messaging;

import com.onboarding.contracts.constants.KafkaTopics;
import com.onboarding.orchestrator.admin.DlqRecord;
import com.onboarding.orchestrator.admin.DlqReplayService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqReplayService replayService;

    public DlqConsumer(DlqReplayService replayService) {
        this.replayService = replayService;
    }

    @KafkaListener(
        topics = {KafkaTopics.ONBOARDING_REQUESTED_DLQ, KafkaTopics.WORKFLOW_EVENTS_DLQ},
        groupId = "orchestrator-dlq-group"
    )
    public void consumeDlq(ConsumerRecord<String, Object> record) {
        DlqRecord captured = replayService.capture(record.topic(), record.key(), record.value());
        log.error("Captured DLQ record {} from topic {}", captured.id(), captured.sourceTopic());
    }
}
