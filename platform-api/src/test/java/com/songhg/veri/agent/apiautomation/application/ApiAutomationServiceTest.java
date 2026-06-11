package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.command.ReviewApiAutomationScriptBundleCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationScriptBundleResponse;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.InMemoryApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.AssetTestCaseService;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseStepResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
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
        AssetTestCaseService assetTestCaseService = mock(AssetTestCaseService.class);
        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                mock(ApiAutomationActorResolver.class),
                assetApiService,
                assetTestCaseService,
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
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

    @Test
    void includesWp3TestCaseSummariesInGenerationInput() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        AssetTestCaseService assetTestCaseService = mock(AssetTestCaseService.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("api-tester");

        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                assetTestCaseService,
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );
        UUID specId = UUID.randomUUID();
        UUID assetApiId = UUID.randomUUID();
        UUID assetTestCaseId = UUID.randomUUID();
        ApiAutomationSpec spec = spec("project-alpha", specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/payments", "POST", "digest-payments", assetApiId));
        when(assetTestCaseService.getTestCase(assetTestCaseId)).thenReturn(testCase(assetTestCaseId, assetApiId));

        ApiAutomationGenerationTaskDetailResponse response = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        "project-alpha",
                        specId,
                        List.of(assetApiId),
                        List.of(assetTestCaseId),
                        List.of("SMOKE"),
                        "FALLBACK_ONLY",
                        1,
                        "with-wp3-case"
                )
        );

        assertThat(response.task().inputSummary()).containsEntry("sourceTestCaseCount", 1);
        assertThat(response.task().inputSummary().toString())
                .doesNotContain("secret-value", "Bearer abcdefgh1234", "token=abc123456");
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().assetTestCaseId()).isEqualTo(assetTestCaseId);
        assertThat(response.cases().getFirst().source()).isEqualTo("FALLBACK");

        List<?> sourceCases = (List<?>) response.task().inputSummary().get("sourceTestCases");
        assertThat(sourceCases).hasSize(1);
        Map<?, ?> sourceSummary = (Map<?, ?>) sourceCases.getFirst();
        assertThat(sourceSummary.get("assetTestCaseId")).isEqualTo(assetTestCaseId.toString());
        assertThat(sourceSummary.get("assetApiId")).isEqualTo(assetApiId.toString());
        assertThat(sourceSummary.get("rawCandidateStored")).isEqualTo(false);
        assertThat(sourceSummary.get("reviewCommentStored")).isEqualTo(false);
        assertThat(sourceSummary.get("sourceRefDigest")).isNotNull();
    }

    @Test
    void generatesStaticCheckedScriptBundleAndApprovesReviewFlow() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("api-reviewer");

        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );
        UUID specId = UUID.randomUUID();
        UUID assetApiId = UUID.randomUUID();
        ApiAutomationSpec spec = spec("project-alpha", specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/payments", "POST", "digest-payments", assetApiId));

        ApiAutomationGenerationTaskDetailResponse generated = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        "project-alpha",
                        specId,
                        List.of(assetApiId),
                        List.of(),
                        List.of("SMOKE"),
                        "FALLBACK_ONLY",
                        1,
                        "script-bundle-review"
                )
        );

        assertThat(generated.scriptBundles()).hasSize(1);
        ApiAutomationScriptBundleResponse bundle = generated.scriptBundles().getFirst();
        assertThat(bundle.status()).isEqualTo("DRAFT");
        assertThat(bundle.staticCheckStatus()).isEqualTo("PASSED");
        assertThat(bundle.fileCount()).isEqualTo(6);
        assertThat(bundle.fileTreeSummary()).containsEntry("rawSourceStored", false)
                .containsEntry("secretValuesStored", false);
        assertThat(bundle.staticCheckSummary()).containsEntry("pythonSyntax", "PASSED")
                .containsEntry("secretPatternHits", 0);
        assertThat(bundle.dependencySummary().toString()).contains("pytest", "httpx");

        ApiAutomationScriptBundleResponse submitted = service.submitScriptBundleReview(
                bundle.id(),
                new ReviewApiAutomationScriptBundleCommand("ready for review")
        );
        assertThat(submitted.status()).isEqualTo("REVIEWING");
        assertThat(submitted.submittedBy()).isEqualTo("api-reviewer");

        ApiAutomationScriptBundleResponse approved = service.approveScriptBundle(
                bundle.id(),
                new ReviewApiAutomationScriptBundleCommand("approved")
        );
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvedBy()).isEqualTo("api-reviewer");

        ApiAutomationScriptBundleResponse existing = service.generateScriptBundle(generated.task().id());
        assertThat(existing.id()).isEqualTo(bundle.id());
    }

    @Test
    void rejectsScriptBundleOnlyWhenReasonIsProvided() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("api-reviewer");

        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );
        UUID specId = UUID.randomUUID();
        UUID assetApiId = UUID.randomUUID();
        ApiAutomationSpec spec = spec("project-alpha", specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/refunds", "POST", "digest-refunds", assetApiId));
        ApiAutomationGenerationTaskDetailResponse generated = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        "project-alpha",
                        specId,
                        List.of(assetApiId),
                        List.of(),
                        List.of("EXCEPTION"),
                        "FALLBACK_ONLY",
                        1,
                        "script-bundle-reject"
                )
        );
        UUID bundleId = generated.scriptBundles().getFirst().id();
        service.submitScriptBundleReview(bundleId, new ReviewApiAutomationScriptBundleCommand(null));

        assertThatThrownBy(() -> service.rejectScriptBundle(bundleId, new ReviewApiAutomationScriptBundleCommand(" ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回原因必填");

        ApiAutomationScriptBundleResponse rejected = service.rejectScriptBundle(
                bundleId,
                new ReviewApiAutomationScriptBundleCommand("missing assertion")
        );
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.reviewNote()).isEqualTo("missing assertion");
    }

    @Test
    void createsModelGeneratedApiAutomationCasesWithWp2TraceMetadata() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("api-tester");

        UUID specId = UUID.randomUUID();
        UUID assetApiId = UUID.randomUUID();
        ApiAutomationSpec spec = spec("project-alpha", specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/payments", "POST", "digest-payments", assetApiId));
        UUID invocationId = UUID.fromString("00000000-0000-4000-8000-000000000606");
        ArgumentCaptor<ModelInvocationCommand> commandCaptor = ArgumentCaptor.forClass(ModelInvocationCommand.class);
        when(invocationService.invoke(commandCaptor.capture(), any(ServicePrincipal.class))).thenReturn(new ModelInvocationResult(
                invocationId,
                UUID.fromString("00000000-0000-4000-8000-000000000707"),
                "local-echo-primary",
                "test-local-model",
                1,
                false,
                """
                        {
                          "schemaVersion": "wp6-api-automation-v1",
                          "cases": [
                            {
                              "assetApiId": "%s",
                              "title": "[SMOKE] POST /v1/payments",
                              "method": "POST",
                              "path": "/v1/payments",
                              "coverageType": "SMOKE",
                              "expectedStatus": 201,
                              "assertions": ["STATUS_CODE", "RESPONSE_TIME_BOUNDED"],
                              "requestTemplate": {
                                "aggregateOnly": true,
                                "parameterCount": 1,
                                "requestBodyPresent": true,
                                "bodyTemplateStored": false,
                                "secretValuesStored": false
                              },
                              "rationale": "覆盖支付创建冒烟路径"
                            }
                          ]
                        }
                        """.formatted(assetApiId),
                20,
                10,
                new BigDecimal("0.0003")
        ));
        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                invocationService,
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );

        ApiAutomationGenerationTaskDetailResponse response = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        "project-alpha",
                        specId,
                        List.of(assetApiId),
                        List.of(),
                        List.of("SMOKE"),
                        "MODEL_WITH_FALLBACK",
                        1,
                        "model-success"
                )
        );

        assertThat(response.task().modelInvocationId()).isEqualTo(invocationId.toString());
        assertThat(response.task().promptVersion()).isEqualTo("1");
        assertThat(response.task().fallbackUsed()).isFalse();
        assertThat(response.task().inputSummary()).containsEntry("modelOutputValidated", true)
                .containsEntry("rawModelResponseStored", false);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().source()).isEqualTo("MODEL");
        assertThat(response.cases().getFirst().assertionSummary().get("assertions").toString())
                .contains("STATUS_CODE", "RESPONSE_TIME_BOUNDED");
        assertThat(response.cases().getFirst().requestTemplate()).containsEntry("modelOutputValidated", true)
                .containsEntry("secretValuesStored", false);
        assertThat(commandCaptor.getValue().promptKey()).isEqualTo("wp6-api-automation-v1");
        assertThat(commandCaptor.getValue().messages().getFirst().content())
                .contains("WP6_API_AUTOMATION_GENERATION_V1")
                .doesNotContain("secret-value");
    }

    @Test
    void fallsBackWhenModelOutputSchemaIsInvalid() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        ModelInvocationService invocationService = mock(ModelInvocationService.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("api-tester");

        UUID specId = UUID.randomUUID();
        UUID assetApiId = UUID.randomUUID();
        ApiAutomationSpec spec = spec("project-alpha", specId);
        repository.insertSpec(spec);
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/refunds", "POST", "digest-refunds", assetApiId));
        UUID invocationId = UUID.fromString("00000000-0000-4000-8000-000000000616");
        when(invocationService.invoke(any(ModelInvocationCommand.class), any(ServicePrincipal.class)))
                .thenReturn(new ModelInvocationResult(
                        invocationId,
                        UUID.fromString("00000000-0000-4000-8000-000000000717"),
                        "local-echo-primary",
                        "test-local-model",
                        1,
                        false,
                        """
                                {"schemaVersion":"wp6-api-automation-v1","cases":[{"title":"bad","method":"POST","path":"/v1/refunds","coverageType":"SMOKE","expectedStatus":99,"assertions":[],"requestTemplate":{"aggregateOnly":false}}]}
                                """,
                        20,
                        10,
                        new BigDecimal("0.0003")
                ));
        ApiAutomationService service = new ApiAutomationService(
                repository,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                invocationService,
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );

        ApiAutomationGenerationTaskDetailResponse response = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        "project-alpha",
                        specId,
                        List.of(assetApiId),
                        List.of(),
                        List.of("SMOKE"),
                        "MODEL_WITH_FALLBACK",
                        1,
                        "model-invalid-fallback"
                )
        );

        assertThat(response.task().modelInvocationId()).isEqualTo(invocationId.toString());
        assertThat(response.task().promptVersion()).isEqualTo("1");
        assertThat(response.task().fallbackUsed()).isTrue();
        assertThat(response.task().errorSummary()).contains("VALIDATION_ERROR");
        assertThat(response.task().inputSummary()).containsEntry("modelOutputValidated", false)
                .containsEntry("fallbackUsed", true);
        assertThat((String) response.task().inputSummary().get("fallbackReason"))
                .contains("WP6 模型输出结构校验不通过");
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().source()).isEqualTo("FALLBACK");
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

    private ApiAutomationEndpointSnapshot syncedEndpoint(
            ApiAutomationSpec spec,
            String path,
            String httpMethod,
            String schemaDigest,
            UUID assetApiId
    ) {
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
                true,
                "201,400",
                schemaDigest,
                "MATCHED",
                assetApiId,
                "{}",
                now,
                now,
                null,
                now,
                now
        );
    }

    private TestCaseResponse testCase(UUID id, UUID assetApiId) {
        Instant now = Instant.EPOCH;
        return new TestCaseResponse(
                id,
                "TC-" + id.toString().replace("-", "").substring(0, 12),
                "Payment smoke Bearer abcdefgh1234",
                "Published WP3 case secret=secret-value",
                null,
                assetApiId,
                "AI_GENERATED",
                "wp5-candidate-token=secret-value",
                "project-alpha",
                "APPROVED",
                "HIGH",
                "smoke,payment",
                List.of(new TestCaseStepResponse(0, "POST payment token=abc123456", "expect 201")),
                1,
                "ACTIVE",
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
