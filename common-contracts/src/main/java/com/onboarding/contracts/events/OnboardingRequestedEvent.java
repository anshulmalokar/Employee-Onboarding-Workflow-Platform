package com.onboarding.contracts.events;

public record OnboardingRequestedEvent(
    String eventId,
    String correlationId,
    String workflowName,
    String sagaId,
    String employeeId,
    String employeeEmail,
    String department
) {
}
