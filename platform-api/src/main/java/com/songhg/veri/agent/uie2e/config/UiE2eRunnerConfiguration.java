package com.songhg.veri.agent.uie2e.config;

import com.songhg.veri.agent.uie2e.application.UiE2eRunnerExecutionPool;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.infrastructure.DisabledUiE2eRunnerAdapter;
import com.songhg.veri.agent.uie2e.infrastructure.LocalUiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.infrastructure.ManagedPreviewUiE2eRunnerAdapter;
import com.songhg.veri.agent.uie2e.infrastructure.PlaywrightSubprocessUiE2eRunnerAdapter;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UiE2eProperties.class)
public class UiE2eRunnerConfiguration {

    @Bean
    @ConditionalOnMissingBean(UiE2eRunnerExecutionPool.class)
    public UiE2eRunnerExecutionPool uiE2eRunnerExecutionPool(UiE2eProperties properties) {
        return new UiE2eRunnerExecutionPool(properties);
    }

    @Bean
    @ConditionalOnMissingBean(UiE2eArtifactStorage.class)
    public UiE2eArtifactStorage uiE2eArtifactStorage(UiE2eProperties properties) {
        return new LocalUiE2eArtifactStorage(properties);
    }

    @Bean
    @ConditionalOnMissingBean(UiE2eRunnerPort.class)
    @ConditionalOnProperty(
            prefix = "veri-agent.ui-e2e",
            name = "runner-enabled",
            havingValue = "true"
    )
    public UiE2eRunnerPort enabledUiE2eRunnerPort(
            UiE2eRepository repository,
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            UiE2eArtifactStorage artifactStorage
    ) {
        if (Set.of("playwright-subprocess", "real-browser").contains(properties.effectiveRunnerMode())) {
            return new PlaywrightSubprocessUiE2eRunnerAdapter(
                    repository,
                    properties,
                    testDataCrossWpReferenceService,
                    artifactStorage
            );
        }
        return new ManagedPreviewUiE2eRunnerAdapter(repository, properties, testDataCrossWpReferenceService);
    }

    @Bean
    @ConditionalOnMissingBean(UiE2eRunnerPort.class)
    @ConditionalOnProperty(
            prefix = "veri-agent.ui-e2e",
            name = "runner-enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public UiE2eRunnerPort disabledUiE2eRunnerPort() {
        return new DisabledUiE2eRunnerAdapter();
    }
}
