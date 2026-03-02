package com.onboarding.contracts.events;

import com.onboarding.contracts.enums.CommandType;
import com.onboarding.contracts.enums.StepType;

public record WorkflowCommand(
    String eventId,
    String correlationId,
    String sagaId,
    String employeeId,
    String employeeEmail,
    String department,
    StepType stepType,
    CommandType commandType,
    String reason
) {
}
