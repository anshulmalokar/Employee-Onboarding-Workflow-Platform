package com.onboarding.orchestrator;

import com.onboarding.orchestrator.workflow.WorkflowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(WorkflowProperties.class)
public class OnboardingOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingOrchestratorApplication.class, args);
    }
}
