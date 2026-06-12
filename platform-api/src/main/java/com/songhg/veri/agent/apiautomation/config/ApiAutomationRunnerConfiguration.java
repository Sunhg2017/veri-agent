package com.songhg.veri.agent.apiautomation.config;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.infrastructure.DisabledApiAutomationRunnerAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiAutomationRunnerConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiAutomationRunnerPort.class)
    public ApiAutomationRunnerPort apiAutomationRunnerPort() {
        return new DisabledApiAutomationRunnerAdapter();
    }
}
