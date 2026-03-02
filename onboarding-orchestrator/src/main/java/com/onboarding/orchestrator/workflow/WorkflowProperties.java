package com.onboarding.orchestrator.workflow;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    private String defaultName = "onboarding";
    private long watchdogIntervalMs = 5000;
    private Map<String, WorkflowDefinition> definitions = new HashMap<>();

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }

    public long getWatchdogIntervalMs() {
        return watchdogIntervalMs;
    }

    public void setWatchdogIntervalMs(long watchdogIntervalMs) {
        this.watchdogIntervalMs = watchdogIntervalMs;
    }

    public Map<String, WorkflowDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, WorkflowDefinition> definitions) {
        this.definitions = definitions;
    }
}
