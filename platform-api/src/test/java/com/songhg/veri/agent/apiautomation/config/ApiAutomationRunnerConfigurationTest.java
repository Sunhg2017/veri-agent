package com.songhg.veri.agent.apiautomation.config;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.infrastructure.DisabledApiAutomationRunnerAdapter;
import com.songhg.veri.agent.apiautomation.infrastructure.ManagedHttpApiAutomationRunnerAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAutomationRunnerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApiAutomationRunnerConfiguration.class);

    @Test
    void createsDisabledRunnerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ApiAutomationRunnerPort.class);
            assertThat(context.getBean(ApiAutomationRunnerPort.class))
                    .isInstanceOf(DisabledApiAutomationRunnerAdapter.class);
        });
    }

    @Test
    void createsManagedHttpRunnerWhenRunnerIsEnabled() {
        contextRunner
                .withPropertyValues("veri-agent.api-automation.runner-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApiAutomationRunnerPort.class);
                    assertThat(context.getBean(ApiAutomationRunnerPort.class))
                            .isInstanceOf(ManagedHttpApiAutomationRunnerAdapter.class);
                });
    }
}
