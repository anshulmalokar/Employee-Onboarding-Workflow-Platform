package com.onboarding.onboarding.controller;

public record ApiError(
    String timestamp,
    int status,
    String message
) {
}
