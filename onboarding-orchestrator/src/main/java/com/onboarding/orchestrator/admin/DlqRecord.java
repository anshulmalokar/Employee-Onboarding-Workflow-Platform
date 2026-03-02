package com.onboarding.orchestrator.admin;

public record DlqRecord(
    long id,
    String sourceTopic,
    String targetTopic,
    String key,
    Object payload,
    long capturedAtEpochMillis
) {
}
