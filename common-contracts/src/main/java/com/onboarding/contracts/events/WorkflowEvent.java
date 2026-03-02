package com.onboarding.contracts.events;

import com.onboarding.contracts.enums.EventType;
import com.onboarding.contracts.enums.StepType;

public record WorkflowEvent(
    String eventId,
    String correlationId,
    String sagaId,
    String employeeId,
    StepType stepType,
    EventType eventType,
    String detail
) {
}
