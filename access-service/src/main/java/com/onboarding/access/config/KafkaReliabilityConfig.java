package com.onboarding.access.config;

import com.onboarding.contracts.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaReliabilityConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition())
        );

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4000L);
        backOff.setMaxElapsedTime(15000L);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public NewTopic workflowCommandsTopic() {
        return new NewTopic(KafkaTopics.WORKFLOW_COMMANDS, 6, (short) 1);
    }

    @Bean
    public NewTopic workflowEventsTopic() {
        return new NewTopic(KafkaTopics.WORKFLOW_EVENTS, 6, (short) 1);
    }

    @Bean
    public NewTopic workflowCommandsDlqTopic() {
        return new NewTopic(KafkaTopics.WORKFLOW_COMMANDS_DLQ, 6, (short) 1);
    }

    @Bean
    public NewTopic workflowEventsDlqTopic() {
        return new NewTopic(KafkaTopics.WORKFLOW_EVENTS_DLQ, 6, (short) 1);
    }
}
