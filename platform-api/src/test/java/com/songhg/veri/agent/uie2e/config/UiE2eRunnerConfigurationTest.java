package com.songhg.veri.agent.uie2e.config;

import com.songhg.veri.agent.common.storage.PlatformStorageConfiguration;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.infrastructure.DisabledUiE2eRunnerAdapter;
import com.songhg.veri.agent.uie2e.infrastructure.HttpWorkerUiE2eRunnerAdapter;
import com.songhg.veri.agent.uie2e.infrastructure.LocalUiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.infrastructure.ManagedPreviewUiE2eRunnerAdapter;
import com.songhg.veri.agent.uie2e.infrastructure.OpaqueUiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.infrastructure.PlaywrightSubprocessUiE2eRunnerAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UiE2eRunnerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PlatformStorageConfiguration.class, UiE2eRunnerConfiguration.class)
            .withPropertyValues(
                    "veri-agent.storage.provider=local",
                    "veri-agent.storage.root-dir=/tmp/veri-agent-storage-test"
            )
            .withBean(UiE2eRepository.class, () -> mock(UiE2eRepository.class))
            .withBean(TestDataCrossWpReferenceService.class, () -> mock(TestDataCrossWpReferenceService.class));

    @Test
    void createsDisabledRunnerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(UiE2eArtifactStorage.class);
            assertThat(context.getBean(UiE2eArtifactStorage.class))
                    .isInstanceOf(OpaqueUiE2eArtifactStorage.class);
            assertThat(context).hasSingleBean(UiE2eRunnerPort.class);
            assertThat(context.getBean(UiE2eRunnerPort.class))
                    .isInstanceOf(DisabledUiE2eRunnerAdapter.class);
        });
    }

    @Test
    void keepsDedicatedLocalArtifactStorageWhenExplicitDirectoryConfigured() {
        contextRunner
                .withPropertyValues("veri-agent.ui-e2e.artifact-storage-dir=/tmp/wp7-artifacts")
                .run(context -> {
                    assertThat(context).hasSingleBean(UiE2eArtifactStorage.class);
                    assertThat(context.getBean(UiE2eArtifactStorage.class))
                            .isInstanceOf(LocalUiE2eArtifactStorage.class);
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

    @Test
    void createsPlaywrightSubprocessRunnerWhenRealBrowserModeIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "veri-agent.ui-e2e.runner-enabled=true",
                        "veri-agent.ui-e2e.runner-mode=playwright-subprocess"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UiE2eRunnerPort.class);
                    assertThat(context.getBean(UiE2eRunnerPort.class))
                            .isInstanceOf(PlaywrightSubprocessUiE2eRunnerAdapter.class);
                });
    }

    @Test
    void createsHttpWorkerRunnerWhenHttpAdapterModeIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "veri-agent.ui-e2e.runner-enabled=true",
                        "veri-agent.ui-e2e.runner-mode=http-adapter",
                        "veri-agent.ui-e2e.runner-worker-url=http://127.0.0.1:18080/ui-e2e/run"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UiE2eRunnerPort.class);
                    assertThat(context.getBean(UiE2eRunnerPort.class))
                            .isInstanceOf(HttpWorkerUiE2eRunnerAdapter.class);
                });
    }
}
