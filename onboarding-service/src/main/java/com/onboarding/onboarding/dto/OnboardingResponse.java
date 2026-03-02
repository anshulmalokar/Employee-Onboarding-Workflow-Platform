package com.onboarding.onboarding.dto;

import com.onboarding.onboarding.domain.OnboardingRequest;
import com.onboarding.onboarding.domain.OnboardingStatus;

public record OnboardingResponse(
    String sagaId,
    String correlationId,
    String workflowName,
    String employeeId,
    String employeeEmail,
    String department,
    OnboardingStatus status,
    String statusMessage
) {
    public static OnboardingResponse from(OnboardingRequest request) {
        return new OnboardingResponse(
            request.getSagaId(),
            request.getCorrelationId(),
            request.getWorkflowName(),
            request.getEmployeeId(),
            request.getEmployeeEmail(),
            request.getDepartment(),
            request.getStatus(),
            request.getStatusMessage()
        );
    }
}
