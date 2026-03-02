package com.onboarding.orchestrator.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SagaTimeoutWatchdog {

    private final OrchestrationService orchestrationService;

    public SagaTimeoutWatchdog(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @Scheduled(fixedDelayString = "${workflow.watchdog-interval-ms:5000}")
    public void scanForStuckSagas() {
        orchestrationService.handleTimeouts();
    }
}
