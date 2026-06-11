package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiAutomationServiceTest {

    @Test
    void normalizesProjectFilterBeforeRepositoryQuery() {
        ApiAutomationRepository repository = mock(ApiAutomationRepository.class);
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        when(contextClient.projectContext("project-code")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-resource-id",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(repository.specs(any())).thenReturn(List.of(spec("project-resource-id")));
        when(repository.countSpecs(any())).thenReturn(1L);

        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                mock(ApiAutomationActorResolver.class),
                new ObjectMapper()
        );

        ApiAutomationSpecPageRequest request = new ApiAutomationSpecPageRequest();
        request.setProjectId("project-code");
        request.setKeyword("billing");
        request.setSize(10);

        service.specs(request);

        ArgumentCaptor<ApiAutomationSpecQuery> queryCaptor = ArgumentCaptor.forClass(ApiAutomationSpecQuery.class);
        verify(repository).specs(queryCaptor.capture());
        assertThat(queryCaptor.getValue().projectId()).isEqualTo("project-resource-id");
        assertThat(queryCaptor.getValue().keyword()).isEqualTo("billing");
    }

    private ApiAutomationSpec spec(String projectId) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationSpec(
                UUID.randomUUID(),
                projectId,
                "TEXT",
                null,
                "billing-openapi",
                "2026.06",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                128,
                "{}",
                "{}",
                "PARSED",
                OpenApiSpecParser.PARSER_VERSION,
                1,
                null,
                "tester",
                "tester",
                now,
                now,
                now
        );
    }
}
