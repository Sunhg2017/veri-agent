package com.songhg.veri.agent.apiautomation.config;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.infrastructure.DisabledApiAutomationRunnerAdapter;
import com.songhg.veri.agent.apiautomation.infrastructure.ManagedHttpApiAutomationRunnerAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiAutomationRunnerConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiAutomationRunnerPort.class)
    @ConditionalOnProperty(prefix = "veri-agent.api-automation", name = "runner-enabled", havingValue = "true")
    public ApiAutomationRunnerPort managedHttpApiAutomationRunnerPort() {
        return new ManagedHttpApiAutomationRunnerAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(ApiAutomationRunnerPort.class)
    @ConditionalOnProperty(
            prefix = "veri-agent.api-automation",
            name = "runner-enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public ApiAutomationRunnerPort disabledApiAutomationRunnerPort() {
        return new DisabledApiAutomationRunnerAdapter();
    }
}
