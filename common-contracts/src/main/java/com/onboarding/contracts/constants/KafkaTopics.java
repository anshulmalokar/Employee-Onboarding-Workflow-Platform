package com.onboarding.contracts.constants;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String ONBOARDING_REQUESTED = "onboarding-requested";
    public static final String WORKFLOW_COMMANDS = "workflow-commands";
    public static final String WORKFLOW_EVENTS = "workflow-events";
    public static final String ONBOARDING_SAGA_RESULT = "onboarding-saga-result";

    public static final String ONBOARDING_REQUESTED_DLQ = ONBOARDING_REQUESTED + ".dlq";
    public static final String WORKFLOW_COMMANDS_DLQ = WORKFLOW_COMMANDS + ".dlq";
    public static final String WORKFLOW_EVENTS_DLQ = WORKFLOW_EVENTS + ".dlq";
    public static final String ONBOARDING_SAGA_RESULT_DLQ = ONBOARDING_SAGA_RESULT + ".dlq";
}
