package com.onboarding.contracts.events;

public record SagaResultEvent(
    String eventId,
    String correlationId,
    String sagaId,
    String employeeId,
    String status,
    String message
) {
}
