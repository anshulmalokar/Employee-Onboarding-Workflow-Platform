package com.onboarding.orchestrator.admin;

public record DlqReplayResponse(
    long recordId,
    boolean replayed,
    String message
) {
}
