package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.InMemoryApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
                mock(AssetApiService.class),
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

    @Test
    void evaluatesDiffStatusesAgainstWp3ApiAssets() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        AssetApiService assetApiService = mock(AssetApiService.class);
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                mock(ApiAutomationActorResolver.class),
                assetApiService,
                new ObjectMapper()
        );
        UUID specId = UUID.randomUUID();
        ApiAutomationSpec spec = spec("project-alpha", specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(endpoint(spec, "/v1/matched", "GET", "digest-match"));
        repository.insertEndpointSnapshot(endpoint(spec, "/v1/changed", "POST", "digest-new"));
        repository.insertEndpointSnapshot(endpoint(spec, "/v1/new", "GET", "digest-new-api"));
        repository.insertEndpointSnapshot(endpoint(spec, "/v1/conflict", "GET", "digest-conflict"));
        repository.insertEndpointSnapshot(endpoint(spec, "/v1/" + "too-long".repeat(40), "GET", "digest-skipped"));
        List<ApiResponseDTO> assets = List.of(
                asset("project-alpha", "/v1/matched", "GET", "digest-match"),
                asset("project-alpha", "/v1/changed", "POST", "digest-old"),
                asset("project-alpha", "/v1/conflict", "GET", "digest-conflict"),
                asset("project-alpha", "/v1/conflict", "GET", "digest-conflict")
        );
        when(assetApiService.listApis(any(AssetListRequest.class)))
                .thenReturn(PageResponse.of(assets, 0, 100, assets.size()));

        ApiAutomationDiffResponse response = service.diffSpec(specId);

        assertThat(response.counts()).containsEntry("NEW", 1)
                .containsEntry("CHANGED", 1)
                .containsEntry("MATCHED", 1)
                .containsEntry("CONFLICT", 1)
                .containsEntry("SKIPPED", 1);
        Map<String, String> statusByPath = response.endpoints().stream()
                .collect(Collectors.toMap(value -> value.path().startsWith("/v1/too-long") ? "too-long" : value.path(),
                        value -> value.diffStatus()));
        assertThat(statusByPath).containsEntry("/v1/matched", "MATCHED")
                .containsEntry("/v1/changed", "CHANGED")
                .containsEntry("/v1/new", "NEW")
                .containsEntry("/v1/conflict", "CONFLICT")
                .containsEntry("too-long", "SKIPPED");
    }

    private ApiAutomationSpec spec(String projectId) {
        return spec(projectId, UUID.randomUUID());
    }

    private ApiAutomationSpec spec(String projectId, UUID id) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationSpec(
                id,
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

    private ApiAutomationEndpointSnapshot endpoint(ApiAutomationSpec spec, String path, String httpMethod, String schemaDigest) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationEndpointSnapshot(
                UUID.randomUUID(),
                spec.id(),
                spec.projectId(),
                "billing",
                httpMethod.toLowerCase() + "Billing",
                httpMethod,
                path,
                httpMethod + " " + path,
                "billing",
                1,
                false,
                "200",
                schemaDigest,
                "UNKNOWN",
                null,
                "{}",
                null,
                null,
                null,
                now,
                now
        );
    }

    private ApiResponseDTO asset(String projectId, String path, String httpMethod, String schemaDigest) {
        Instant now = Instant.EPOCH;
        UUID id = UUID.randomUUID();
        return new ApiResponseDTO(
                id,
                "API-" + id.toString().replace("-", "").substring(0, 12),
                httpMethod + " " + path,
                "Existing API",
                httpMethod,
                path,
                "OPENAPI",
                "wp6:existing",
                "1",
                "{\"wp6SchemaDigest\":\"" + schemaDigest + "\"}",
                "{}",
                projectId,
                "ACTIVE",
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }
}
