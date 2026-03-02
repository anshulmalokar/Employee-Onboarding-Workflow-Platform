package com.onboarding.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateOnboardingRequest(
    @NotBlank String employeeId,
    @Email @NotBlank String employeeEmail,
    @NotBlank String department,
    String workflowName
) {
}
