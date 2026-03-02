package com.onboarding.orchestrator.state;

import com.onboarding.contracts.enums.StepType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SagaState {

    private final String sagaId;
    private final String correlationId;
    private final String workflowName;
    private final String employeeId;
    private final String employeeEmail;
    private final String department;

    private final Set<StepType> completedSteps = EnumSet.noneOf(StepType.class);
    private final Set<StepType> inProgressSteps = EnumSet.noneOf(StepType.class);
    private final Set<StepType> compensationDispatched = EnumSet.noneOf(StepType.class);
    private final Map<StepType, Integer> attemptsByStep = new EnumMap<>(StepType.class);
    private final Map<StepType, Long> lastCommandEpochMillisByStep = new EnumMap<>(StepType.class);
    private final List<StepType> completionOrder = new ArrayList<>();

    private boolean terminal;

    public SagaState(
        String sagaId,
        String correlationId,
        String workflowName,
        String employeeId,
        String employeeEmail,
        String department
    ) {
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.workflowName = workflowName;
        this.employeeId = employeeId;
        this.employeeEmail = employeeEmail;
        this.department = department;
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public String getDepartment() {
        return department;
    }

    public synchronized boolean isTerminal() {
        return terminal;
    }

    public synchronized void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public synchronized void markCommandDispatched(StepType stepType) {
        inProgressSteps.add(stepType);
        attemptsByStep.merge(stepType, 1, Integer::sum);
        lastCommandEpochMillisByStep.put(stepType, System.currentTimeMillis());
    }

    public synchronized void markStepSucceeded(StepType stepType) {
        inProgressSteps.remove(stepType);
        if (completedSteps.add(stepType)) {
            completionOrder.add(stepType);
        }
    }

    public synchronized void clearInProgress(StepType stepType) {
        inProgressSteps.remove(stepType);
    }

    public synchronized boolean hasCompleted(StepType stepType) {
        return completedSteps.contains(stepType);
    }

    public synchronized boolean isInProgress(StepType stepType) {
        return inProgressSteps.contains(stepType);
    }

    public synchronized int getAttempts(StepType stepType) {
        return attemptsByStep.getOrDefault(stepType, 0);
    }

    public synchronized Long getLastCommandEpochMillis(StepType stepType) {
        return lastCommandEpochMillisByStep.get(stepType);
    }

    public synchronized List<StepType> inProgressSnapshot() {
        return new ArrayList<>(inProgressSteps);
    }

    public synchronized List<StepType> compensationCandidatesInReverse() {
        List<StepType> copy = new ArrayList<>(completionOrder);
        java.util.Collections.reverse(copy);
        return copy;
    }

    public synchronized boolean markCompensationDispatched(StepType stepType) {
        return compensationDispatched.add(stepType);
    }

    public synchronized boolean isWorkflowCompleted(Set<StepType> allSteps) {
        return completedSteps.containsAll(allSteps);
    }
}
