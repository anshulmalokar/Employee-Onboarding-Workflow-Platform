package com.onboarding.orchestrator.workflow;

import com.onboarding.contracts.enums.StepType;
import java.util.ArrayList;
import java.util.List;

public class StepPolicy {

    private List<StepType> nextSteps = new ArrayList<>();
    private List<StepType> compensationSteps = new ArrayList<>();
    private int timeoutSeconds = 30;
    private int maxRetries = 2;

    public List<StepType> getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(List<StepType> nextSteps) {
        this.nextSteps = nextSteps;
    }

    public List<StepType> getCompensationSteps() {
        return compensationSteps;
    }

    public void setCompensationSteps(List<StepType> compensationSteps) {
        this.compensationSteps = compensationSteps;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
