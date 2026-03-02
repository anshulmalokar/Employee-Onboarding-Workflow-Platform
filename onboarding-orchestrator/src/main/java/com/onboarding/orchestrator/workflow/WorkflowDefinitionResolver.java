package com.onboarding.orchestrator.workflow;

import com.onboarding.contracts.enums.StepType;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkflowDefinitionResolver {

    private final WorkflowProperties workflowProperties;

    public WorkflowDefinitionResolver(WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    @PostConstruct
    public void validate() {
        if (workflowProperties.getDefinitions().isEmpty()) {
            throw new IllegalStateException("No workflow definitions configured");
        }

        WorkflowDefinition defaultDefinition = workflowProperties.getDefinitions().get(workflowProperties.getDefaultName());
        if (defaultDefinition == null) {
            throw new IllegalStateException("Default workflow definition not found: " + workflowProperties.getDefaultName());
        }

        for (Map.Entry<String, WorkflowDefinition> entry : workflowProperties.getDefinitions().entrySet()) {
            validateDefinition(entry.getKey(), entry.getValue());
        }
    }

    public WorkflowDefinition resolve(String workflowName) {
        String resolved = resolveWorkflowName(workflowName);
        WorkflowDefinition definition = workflowProperties.getDefinitions().get(resolved);
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + resolved);
        }
        return definition;
    }

    public String resolveWorkflowName(String workflowName) {
        if (workflowName == null || workflowName.isBlank()) {
            return workflowProperties.getDefaultName();
        }
        return workflowName;
    }

    public long getWatchdogIntervalMs() {
        return workflowProperties.getWatchdogIntervalMs();
    }

    private void validateDefinition(String name, WorkflowDefinition definition) {
        if (definition.getStartStep() == null) {
            throw new IllegalStateException("Workflow " + name + " is missing startStep");
        }

        if (!definition.getSteps().containsKey(definition.getStartStep())) {
            throw new IllegalStateException("Workflow " + name + " startStep must be present in steps map");
        }

        for (Map.Entry<StepType, StepPolicy> stepEntry : definition.getSteps().entrySet()) {
            StepPolicy stepPolicy = stepEntry.getValue();
            for (StepType nextStep : stepPolicy.getNextSteps()) {
                if (!definition.getSteps().containsKey(nextStep)) {
                    throw new IllegalStateException("Workflow " + name + " has unknown nextStep: " + nextStep);
                }
            }
            for (StepType compensationStep : stepPolicy.getCompensationSteps()) {
                if (!definition.getSteps().containsKey(compensationStep)) {
                    throw new IllegalStateException("Workflow " + name + " has unknown compensationStep: " + compensationStep);
                }
            }
        }
    }
}
