package com.songhg.veri.agent.uie2e.config;

import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.infrastructure.DisabledUiE2eRunnerAdapter;
import com.songhg.veri.agent.uie2e.infrastructure.ManagedPreviewUiE2eRunnerAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UiE2eRunnerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UiE2eRunnerConfiguration.class)
            .withBean(UiE2eRepository.class, () -> mock(UiE2eRepository.class))
            .withBean(TestDataCrossWpReferenceService.class, () -> mock(TestDataCrossWpReferenceService.class));

    @Test
    void createsDisabledRunnerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(UiE2eRunnerPort.class);
            assertThat(context.getBean(UiE2eRunnerPort.class))
                    .isInstanceOf(DisabledUiE2eRunnerAdapter.class);
        });
    }

    @Test
    void createsManagedPreviewRunnerWhenRunnerIsEnabled() {
        contextRunner
                .withPropertyValues("veri-agent.ui-e2e.runner-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(UiE2eRunnerPort.class);
                    assertThat(context.getBean(UiE2eRunnerPort.class))
                            .isInstanceOf(ManagedPreviewUiE2eRunnerAdapter.class);
                });
    }
}
