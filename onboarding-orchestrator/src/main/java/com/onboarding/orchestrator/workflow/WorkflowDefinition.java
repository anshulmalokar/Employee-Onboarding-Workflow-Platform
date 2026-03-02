package com.onboarding.orchestrator.workflow;

import com.onboarding.contracts.enums.StepType;
import java.util.EnumMap;
import java.util.Map;

public class WorkflowDefinition {

    private StepType startStep;
    private Map<StepType, StepPolicy> steps = new EnumMap<>(StepType.class);

    public StepType getStartStep() {
        return startStep;
    }

    public void setStartStep(StepType startStep) {
        this.startStep = startStep;
    }

    public Map<StepType, StepPolicy> getSteps() {
        return steps;
    }

    public void setSteps(Map<StepType, StepPolicy> steps) {
        this.steps = steps;
    }

    public StepPolicy getPolicy(StepType stepType) {
        return steps.get(stepType);
    }
}
