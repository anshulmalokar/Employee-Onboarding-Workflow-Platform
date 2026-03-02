package com.onboarding.onboarding.controller;

import com.onboarding.onboarding.dto.CreateOnboardingRequest;
import com.onboarding.onboarding.dto.OnboardingResponse;
import com.onboarding.onboarding.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OnboardingResponse create(
        @Valid @RequestBody CreateOnboardingRequest request,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        return OnboardingResponse.from(onboardingService.start(request, idempotencyKey));
    }

    @GetMapping("/{sagaId}")
    public OnboardingResponse get(@PathVariable("sagaId") String sagaId) {
        return OnboardingResponse.from(onboardingService.get(sagaId));
    }
}
