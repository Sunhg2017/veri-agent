package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationGenerationTaskCommand;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.command.ReviewApiAutomationScriptBundleCommand;
import com.songhg.veri.agent.apiautomation.application.command.SyncApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunExportResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationScriptBundleResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncPreviewResponse;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.DisabledApiAutomationRunnerAdapter;
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
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderHealth;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                new DisabledApiAutomationRunnerAdapter(),
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
    void reportsDockerSandboxRunnerPolicyInHealth() {
        ApiAutomationService service = new ApiAutomationService(
                mock(ApiAutomationRepository.class),
                new DisabledApiAutomationRunnerAdapter(),
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(
                        65_536,
                        50,
                        true,
                        120,
                        100,
                        "*.example.test",
                        1_048_576,
                        "wp6-api-automation-v1",
                        true,
                        "sandbox",
                        "python3 -m pytest",
                        "docker",
                        "veri-agent/wp6-pytest-runner:test",
                        "wp6-sandbox"
                ),
                mock(ApiAutomationPlatformContextClient.class),
                mock(ApiAutomationActorResolver.class),
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );

        var response = service.health();

        assertThat(response.runnerEnabled()).isTrue();
        assertThat(response.policy()).containsEntry("runnerMode", "pytest-docker-sandbox")
                .containsEntry("runnerExecutionIsolation", "DOCKER_SANDBOX")
                .containsEntry("runnerSandboxEnabled", true)
                .containsEntry("runnerSandboxReady", true)
                .containsEntry("runnerSandboxImageConfigured", true)
                .containsEntry("runnerSandboxNetwork", "wp6-sandbox")
                .containsEntry("runnerAllowedBaseUrlConfigured", true);
    }

    @Test
    void evaluatesDiffStatusesAgainstWp3ApiAssets() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        AssetApiService assetApiService = mock(AssetApiService.class);
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        AssetTestCaseService assetTestCaseService = mock(AssetTestCaseService.class);
        ApiAutomationService service = new ApiAutomationService(
                repository,
                new DisabledApiAutomationRunnerAdapter(),
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
    void previewsSyncActionsWithoutPersistingOrWritingWp3() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        AssetApiService assetApiService = mock(AssetApiService.class);
        ApiAutomationService service = new ApiAutomationService(
                repository,
                new DisabledApiAutomationRunnerAdapter(),
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                mock(ApiAutomationPlatformContextClient.class),
                mock(ApiAutomationActorResolver.class),
                assetApiService,
                mock(AssetTestCaseService.class),
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

        ApiAutomationSyncPreviewResponse response = service.syncPreview(specId);

        assertThat(response.counts()).containsEntry("CREATE", 1)
                .containsEntry("UPDATE", 1)
                .containsEntry("REVIEW", 1)
                .containsEntry("SKIP", 2);
        assertThat(response.policy()).containsEntry("dryRun", true)
                .containsEntry("wp3Write", false)
                .containsEntry("endpointSnapshotWrite", false);
        assertThat(response.items()).extracting("path")
                .contains("/v1/matched", "/v1/changed", "/v1/new", "/v1/conflict");
        assertThat(response.items()).filteredOn(item -> "/v1/new".equals(item.path()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.action()).isEqualTo("CREATE");
                    assertThat(item.reason()).isEqualTo("NO_MATCHING_WP3_API");
                    assertThat(item.payloadSummary()).containsEntry("aggregateOnly", true)
                            .containsEntry("dryRun", true)
                            .containsEntry("wp3Write", false)
                            .containsEntry("rawSchemaStored", false)
                            .containsEntry("rawRequestResponseStored", false)
                            .containsEntry("httpMethod", "GET")
                            .containsEntry("path", "/v1/new");
                    assertThat(item.payloadSummary()).containsKeys("requestSchemaDigest", "responseSchemaDigest");
                });
        assertThat(repository.endpointSnapshots(specId)).extracting(ApiAutomationEndpointSnapshot::diffStatus)
                .containsOnly("UNKNOWN");
        verify(assetApiService, never()).createOpenApiSyncedApi(any());
        verify(assetApiService, never()).updateOpenApiSyncedApi(any(), any());
    }

    @Test
    void archivesSpecAndBlocksParseDiffSyncAndGenerationRetry() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        OpenApiSpecParser parser = mock(OpenApiSpecParser.class);
        when(actorResolver.currentActor()).thenReturn("api-archiver");
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(parser.parse(any(), anyInt())).thenThrow(new BusinessException(
                com.songhg.veri.agent.common.error.ErrorCode.VALIDATION_ERROR,
                "OPENAPI_PARSE_FAILED"
        ));
        ApiAutomationService service = new ApiAutomationService(
                repository,
                new DisabledApiAutomationRunnerAdapter(),
                parser,
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
        ApiAutomationSpec failedSpec = parseFailedSpec("project-alpha", specId);
        repository.insertSpec(failedSpec);

        assertThatThrownBy(() -> service.parseSpec(specId))
                .isInstanceOf(BusinessException.class);
        assertThat(repository.spec(specId).orElseThrow().status()).isEqualTo("PARSE_FAILED");
        clearInvocations(parser);

        var archived = service.archiveSpec(specId);

        assertThat(archived.spec().status()).isEqualTo("ARCHIVED");
        assertThat(archived.spec().parseErrorSummary()).isEqualTo("OPENAPI_PARSE_FAILED");
        assertThat(repository.spec(specId).orElseThrow().updatedBy()).isEqualTo("api-archiver");
        service.archiveSpec(specId);
        assertThat(repository.spec(specId).orElseThrow().status()).isEqualTo("ARCHIVED");
        assertThatThrownBy(() -> service.parseSpec(specId)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.diffSpec(specId)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.syncPreview(specId)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.syncSpec(specId, new SyncApiAutomationSpecCommand(List.of(), true)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createGenerationTask(new CreateApiAutomationGenerationTaskCommand(
                "project-alpha",
                specId,
                List.of(),
                List.of(),
                List.of("SMOKE"),
                "FALLBACK_ONLY",
                1,
                "archived-spec"
        ))).isInstanceOf(BusinessException.class);
        verify(parser, never()).parse(any(), anyInt());
        verify(contextClient).writeAuditEvent(
                eq("api_automation.spec.archived"),
                eq("API_AUTOMATION_SPEC"),
                eq(specId.toString()),
                eq("project-alpha"),
                eq("SUCCESS"),
                argThat(payload -> "ARCHIVED".equals(payload.get("status"))
                        && "PARSE_FAILED".equals(payload.get("previousStatus")))
        );
    }

    @Test
    void includesWp3TestCaseSummariesInGenerationInput() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        AssetTestCaseService assetTestCaseService = mock(AssetTestCaseService.class);
        AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
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
                new DisabledApiAutomationRunnerAdapter(),
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                assetTestCaseService,
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper(),
                notificationService
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
        verify(notificationService).notifyApiAutomationGenerationTaskFinished(argThat(task ->
                response.task().id().equals(task.id()) && "COMPLETED".equals(task.status())));
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
                new DisabledApiAutomationRunnerAdapter(),
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
                .containsEntry("secretValuesStored", false)
                .containsEntry("pytestRunnerContractReady", true);
        assertThat(bundle.fileTreeSummary().get("runtimeInputs").toString())
                .contains("WP6_RUNNER_SECRET_HEADERS_JSON")
                .contains("WP6_RUNNER_SECRET_VALUE_")
                .contains("^X-VA-WP6-Secret-[1-9][0-9]*$")
                .doesNotContain("secret://", "resolved-payment-secret");
        assertThat(bundle.staticCheckSummary()).containsEntry("pythonSyntax", "PASSED")
                .containsEntry("secretPatternHits", 0)
                .containsEntry("runtimeSecretHeaderMapping", "PASSED");
        assertThat(bundle.dependencySummary().toString())
                .contains("pytest", "httpx", "ENV_JSON_TO_CONTROLLED_HEADERS")
                .doesNotContain("secret://", "resolved-payment-secret");

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
                new DisabledApiAutomationRunnerAdapter(),
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
    void createsBlockedRunWhenRunnerIsDisabledAndStoresOnlyTargetDigest() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationPlatformContextClient contextClient = mock(ApiAutomationPlatformContextClient.class);
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of(),
                Instant.EPOCH
        ));
        when(actorResolver.currentActor()).thenReturn("api-runner");
        ApiAutomationService service = new ApiAutomationService(
                repository,
                new DisabledApiAutomationRunnerAdapter(),
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, false, 120, 100, "wp6-api-automation-v1", true),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper(),
                notificationService
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
                        "runner-disabled"
                )
        );
        UUID bundleId = generated.scriptBundles().getFirst().id();
        service.submitScriptBundleReview(bundleId, new ReviewApiAutomationScriptBundleCommand("ready"));
        service.approveScriptBundle(bundleId, new ReviewApiAutomationScriptBundleCommand("approved"));

        ApiAutomationRunDetailResponse response = service.createRun(new CreateApiAutomationRunCommand(
                bundleId,
                "staging",
                "https://api.example.test/service",
                List.of(generated.cases().getFirst().id()),
                30,
                List.of(" secret://wp6/payment-token ", "secret://wp6/payment-token")
        ));

        assertThat(response.run().status()).isEqualTo("BLOCKED");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_DISABLED");
        assertThat(response.run().baseUrlHost()).isEqualTo("api.example.test");
        assertThat(response.run().baseUrlDigest()).hasSize(64);
        assertThat(response.run().toString()).doesNotContain("https://api.example.test/service");
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().status()).isEqualTo("BLOCKED");
        assertThat(service.runDetail(response.run().id()).run().id()).isEqualTo(response.run().id());

        ApiAutomationRunExportResponse exported = service.exportRun(response.run().id());
        assertThat(exported.schemaVersion()).isEqualTo("wp6-run-export-v1");
        assertThat(exported.resultCounts()).containsEntry("BLOCKED", 1);
        assertThat(exported.redactionPolicy()).containsEntry("rawBaseUrlExported", false)
                .containsEntry("rawRequestResponseExported", false)
                .containsEntry("stdoutStderrExported", false);
        assertThat(exported.toString()).contains("api.example.test")
                .doesNotContain("https://api.example.test/service");
        verify(notificationService).notifyApiAutomationGenerationTaskFinished(argThat(task ->
                generated.task().id().equals(task.id()) && "COMPLETED".equals(task.status())));
        verify(notificationService).notifyApiAutomationRunFinished(argThat(run ->
                response.run().id().equals(run.id()) && "BLOCKED".equals(run.status())));
        verify(contextClient, atLeastOnce()).writeAuditEvent(
                eq("api_automation.exported"),
                eq("API_AUTOMATION_RUN"),
                eq(response.run().id().toString()),
                eq("project-alpha"),
                eq("SUCCESS"),
                argThat(payload -> Boolean.FALSE.equals(payload.get("rawBaseUrlExported"))
                        && Boolean.FALSE.equals(payload.get("rawRequestResponseExported"))
                        && !payload.toString().contains("https://api.example.test/service"))
        );
        verify(contextClient, atLeastOnce()).writeAuditEvent(
                eq("api_automation.run.started"),
                eq("API_AUTOMATION_RUN"),
                eq(response.run().id().toString()),
                eq("project-alpha"),
                eq("FAILED"),
                argThat(payload -> Integer.valueOf(1).equals(payload.get("secretRefCount"))
                        && payload.toString().contains("sha256:")
                        && !payload.toString().contains("secret://wp6/payment-token"))
        );
        assertThatThrownBy(() -> service.createRun(new CreateApiAutomationRunCommand(
                bundleId,
                "staging",
                "https://api.example.test/service",
                List.of(generated.cases().getFirst().id()),
                30,
                List.of("env:WP6_TOKEN")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("secretRefs 必须使用 secret:// 引用");
    }

    @Test
    void resolvesRunSecretsOnlyForActiveRunnerAndPassesControlledHeaders() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        CapturingRunner runner = new CapturingRunner();
        CapturingSecretProvider secretProvider = new CapturingSecretProvider(
                "secret://wp6/payment-token",
                "resolved-payment-secret"
        );
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
        when(actorResolver.currentActor()).thenReturn("api-runner");
        ApiAutomationService service = new ApiAutomationService(
                repository,
                runner,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(
                        65_536,
                        50,
                        true,
                        120,
                        100,
                        "api.example.test",
                        1_048_576,
                        "wp6-api-automation-v1",
                        true
                ),
                contextClient,
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper(),
                mock(AsyncTaskNotificationService.class),
                List.of(secretProvider)
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
                        "runner-secret-provider"
                )
        );
        UUID bundleId = generated.scriptBundles().getFirst().id();
        service.submitScriptBundleReview(bundleId, new ReviewApiAutomationScriptBundleCommand("ready"));
        service.approveScriptBundle(bundleId, new ReviewApiAutomationScriptBundleCommand("approved"));

        ApiAutomationRunDetailResponse response = service.createRun(new CreateApiAutomationRunCommand(
                bundleId,
                "staging",
                "https://api.example.test/service",
                List.of(generated.cases().getFirst().id()),
                30,
                List.of(" secret://wp6/payment-token ")
        ));

        assertThat(response.run().status()).isEqualTo("PASSED");
        assertThat(secretProvider.lastSecretRef).isEqualTo("secret://wp6/payment-token");
        assertThat(secretProvider.lastContext).isEqualTo(new SecretResolveContext(
                "API_AUTOMATION_RUNNER",
                "wp6-api-automation-runner",
                "PROJECT",
                "project-alpha"
        ));
        assertThat(runner.lastRequest.secretRefDigests()).singleElement()
                .satisfies(digest -> assertThat(digest).startsWith("sha256:").hasSize(71));
        assertThat(runner.lastRequest.secrets()).singleElement().satisfies(secret -> {
            assertThat(secret.headerName()).isEqualTo("X-VA-WP6-Secret-1");
            assertThat(secret.secretRefDigest()).isEqualTo(runner.lastRequest.secretRefDigests().getFirst());
            assertThat(secret.value()).isEqualTo("resolved-payment-secret");
        });
        assertThat(runner.lastRequest.toString()).doesNotContain("secret://wp6/payment-token", "resolved-payment-secret");
        assertThat(response.toString()).doesNotContain("secret://wp6/payment-token", "resolved-payment-secret");
        assertThat(service.exportRun(response.run().id()).toString())
                .doesNotContain("secret://wp6/payment-token", "resolved-payment-secret");
        verify(contextClient, atLeastOnce()).writeAuditEvent(
                eq("api_automation.run.started"),
                eq("API_AUTOMATION_RUN"),
                eq(response.run().id().toString()),
                eq("project-alpha"),
                eq("SUCCESS"),
                argThat(payload -> Integer.valueOf(1).equals(payload.get("secretRefCount"))
                        && payload.toString().contains("sha256:")
                        && !payload.toString().contains("secret://wp6/payment-token")
                        && !payload.toString().contains("resolved-payment-secret"))
        );
    }

    @Test
    void foldsOversizedRunnerArtifactBeforePersistenceAndExport() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        OversizedArtifactRunner runner = new OversizedArtifactRunner();
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
        when(actorResolver.currentActor()).thenReturn("api-runner");
        ApiAutomationService service = new ApiAutomationService(
                repository,
                runner,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(
                        65_536,
                        50,
                        true,
                        120,
                        100,
                        "api.example.test",
                        128,
                        "wp6-api-automation-v1",
                        true
                ),
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
        repository.insertEndpointSnapshot(syncedEndpoint(spec, "/v1/payments", "GET", "digest-payments", assetApiId));
        ApiAutomationGenerationTaskDetailResponse generated = service.createGenerationTask(
                new CreateApiAutomationGenerationTaskCommand(
                        "project-alpha",
                        specId,
                        List.of(assetApiId),
                        List.of(),
                        List.of("SMOKE"),
                        "FALLBACK_ONLY",
                        1,
                        "runner-artifact-limit"
                )
        );
        UUID bundleId = generated.scriptBundles().getFirst().id();
        service.submitScriptBundleReview(bundleId, new ReviewApiAutomationScriptBundleCommand("ready"));
        service.approveScriptBundle(bundleId, new ReviewApiAutomationScriptBundleCommand("approved"));

        ApiAutomationRunDetailResponse response = service.createRun(new CreateApiAutomationRunCommand(
                bundleId,
                "staging",
                "https://api.example.test/service",
                List.of(generated.cases().getFirst().id()),
                30,
                null
        ));

        assertThat(response.run().status()).isEqualTo("FAILED");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_ARTIFACT_TOO_LARGE");
        assertThat(response.run().errorSummary()).doesNotContain(OversizedArtifactRunner.RAW_ARTIFACT_MARKER);
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("ERROR");
            assertThat(result.errorCode()).isEqualTo("RUNNER_ARTIFACT_TOO_LARGE");
            assertThat(result.assertionSummary())
                    .containsEntry("aggregateOnly", true)
                    .containsEntry("rawRequestResponseStored", false)
                    .containsEntry("secretValuesStored", false)
                    .containsEntry("artifactStored", false)
                    .containsEntry("artifactTooLarge", true)
                    .containsEntry("artifactMaxBytes", 128);
            assertThat(result.toString()).doesNotContain(OversizedArtifactRunner.RAW_ARTIFACT_MARKER);
        });

        ApiAutomationRunExportResponse exported = service.exportRun(response.run().id());
        assertThat(exported.resultCounts()).containsEntry("ERROR", 1);
        assertThat(exported.toString()).doesNotContain(OversizedArtifactRunner.RAW_ARTIFACT_MARKER);
    }

    @Test
    void blocksLocalhostTargetEvenWhenRunnerIsEnabled() {
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
        when(actorResolver.currentActor()).thenReturn("api-runner");
        ApiAutomationService service = new ApiAutomationService(
                repository,
                new DisabledApiAutomationRunnerAdapter(),
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(
                        65_536,
                        50,
                        true,
                        120,
                        100,
                        "api.example.test",
                        1_048_576,
                        "wp6-api-automation-v1",
                        true
                ),
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
                        List.of("SMOKE"),
                        "FALLBACK_ONLY",
                        1,
                        "runner-localhost-block"
                )
        );
        UUID bundleId = generated.scriptBundles().getFirst().id();
        service.submitScriptBundleReview(bundleId, new ReviewApiAutomationScriptBundleCommand("ready"));
        service.approveScriptBundle(bundleId, new ReviewApiAutomationScriptBundleCommand("approved"));

        ApiAutomationRunDetailResponse response = service.createRun(new CreateApiAutomationRunCommand(
                bundleId,
                null,
                "http://127.0.0.1:8080",
                List.of(),
                null,
                null
        ));

        assertThat(response.run().status()).isEqualTo("BLOCKED");
        assertThat(response.run().runnerMode()).isEqualTo("NOOP");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_TARGET_BLOCKED");
        assertThat(response.run().baseUrlHost()).isEqualTo("127.0.0.1");
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
                new DisabledApiAutomationRunnerAdapter(),
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
                new DisabledApiAutomationRunnerAdapter(),
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

    @Test
    void cancelsActiveRunWhenRunnerAcceptsCancel() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
        when(actorResolver.currentActor()).thenReturn("api-canceler");
        AcceptingCancelRunner runner = new AcceptingCancelRunner(new ApiAutomationRunnerPort.RunnerCancelResult(
                true,
                "RUNNER_CANCELED",
                "cancel accepted"
        ));
        ApiAutomationService service = new ApiAutomationService(
                repository,
                runner,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, true, 120, 100, "wp6-api-automation-v1", true),
                mock(ApiAutomationPlatformContextClient.class),
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper(),
                notificationService
        );
        UUID runId = UUID.randomUUID();
        repository.insertRun(run(runId, "RUNNING"));

        ApiAutomationRunDetailResponse response = service.cancelRun(runId);

        assertThat(runner.cancelCalls).isEqualTo(1);
        assertThat(runner.lastRunId).isEqualTo(runId);
        assertThat(response.run().status()).isEqualTo("CANCELED");
        assertThat(response.run().errorCode()).isEqualTo("RUNNER_CANCELED");
        assertThat(response.run().errorSummary()).isEqualTo("cancel accepted");
        assertThat(repository.run(runId).orElseThrow().updatedBy()).isEqualTo("api-canceler");
        verify(notificationService).notifyApiAutomationRunFinished(argThat(run ->
                runId.equals(run.id()) && "CANCELED".equals(run.status())));
    }

    @Test
    void skipsRunnerCancelForTerminalRun() {
        InMemoryApiAutomationRepository repository = new InMemoryApiAutomationRepository();
        ApiAutomationActorResolver actorResolver = mock(ApiAutomationActorResolver.class);
        AcceptingCancelRunner runner = new AcceptingCancelRunner(new ApiAutomationRunnerPort.RunnerCancelResult(
                true,
                "RUNNER_CANCELED",
                "cancel accepted"
        ));
        ApiAutomationService service = new ApiAutomationService(
                repository,
                runner,
                mock(OpenApiSpecParser.class),
                new ApiAutomationProperties(65_536, 50, true, 120, 100, "wp6-api-automation-v1", true),
                mock(ApiAutomationPlatformContextClient.class),
                actorResolver,
                mock(AssetApiService.class),
                mock(AssetTestCaseService.class),
                mock(ModelInvocationService.class),
                new ApiAutomationModelOutputParser(new ObjectMapper()),
                new ObjectMapper()
        );
        UUID runId = UUID.randomUUID();
        repository.insertRun(run(runId, "PASSED"));

        ApiAutomationRunDetailResponse response = service.cancelRun(runId);

        assertThat(runner.cancelCalls).isZero();
        assertThat(response.run().status()).isEqualTo("PASSED");
        assertThat(response.run().errorCode()).isNull();
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

    private ApiAutomationSpec parseFailedSpec(String projectId, UUID id) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationSpec(
                id,
                projectId,
                "TEXT",
                null,
                "invalid-openapi",
                "2026.06",
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                128,
                "{}",
                "{\"parseFailed\":true,\"aggregateOnly\":true}",
                "PARSE_FAILED",
                OpenApiSpecParser.PARSER_VERSION,
                0,
                "OPENAPI_PARSE_FAILED",
                "tester",
                "tester",
                null,
                now,
                now
        );
    }

    private ApiAutomationRun run(UUID id, String status) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationRun(
                id,
                "project-alpha",
                UUID.randomUUID(),
                "staging",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "api.example.test",
                status,
                120,
                1,
                "trc_wp6_cancel",
                "MANAGED",
                null,
                null,
                "tester",
                "tester",
                now,
                terminalRunStatus(status) ? now : null,
                now,
                now
        );
    }

    private boolean terminalRunStatus(String status) {
        return Set.of("BLOCKED", "PASSED", "FAILED", "TIMEOUT", "CANCELED").contains(status);
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

    private static final class AcceptingCancelRunner implements ApiAutomationRunnerPort {

        private final RunnerCancelResult cancelResult;
        private int cancelCalls;
        private UUID lastRunId;

        private AcceptingCancelRunner(RunnerCancelResult cancelResult) {
            this.cancelResult = cancelResult;
        }

        @Override
        public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
            return new RunnerValidation(true, null, null);
        }

        @Override
        public RunnerRunResult run(RunnerRunRequest request) {
            return new RunnerRunResult("PASSED", "MANAGED", null, null, List.of());
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            cancelCalls++;
            lastRunId = runId;
            return cancelResult;
        }
    }

    private static final class CapturingRunner implements ApiAutomationRunnerPort {

        private RunnerRunRequest lastRequest;

        @Override
        public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
            return new RunnerValidation(true, null, null);
        }

        @Override
        public RunnerRunResult run(RunnerRunRequest request) {
            lastRequest = request;
            return new RunnerRunResult("PASSED", "MANAGED", null, null, List.of());
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            return new RunnerCancelResult(false, "NOT_RUNNING", "capturing runner is synchronous");
        }
    }

    private static final class OversizedArtifactRunner implements ApiAutomationRunnerPort {

        private static final String RAW_ARTIFACT_MARKER = "raw-runner-artifact-should-not-persist";

        @Override
        public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
            return new RunnerValidation(true, null, null);
        }

        @Override
        public RunnerRunResult run(RunnerRunRequest request) {
            return new RunnerRunResult(
                    "PASSED",
                    "MANAGED",
                    null,
                    null,
                    List.of(new RunnerCaseResult(
                            request.cases().getFirst().id(),
                            "PASSED",
                            12,
                            "{\"stdout\":\"" + RAW_ARTIFACT_MARKER + " ".repeat(220) + "\"}",
                            null,
                            null
                    ))
            );
        }

        @Override
        public RunnerCancelResult cancel(UUID runId) {
            return new RunnerCancelResult(false, "NOT_RUNNING", "oversized artifact runner is synchronous");
        }
    }

    private static final class CapturingSecretProvider implements SecretProvider {

        private final String acceptedSecretRef;
        private final String value;
        private String lastSecretRef;
        private SecretResolveContext lastContext;

        private CapturingSecretProvider(String acceptedSecretRef, String value) {
            this.acceptedSecretRef = acceptedSecretRef;
            this.value = value;
        }

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            lastSecretRef = secretRef;
            lastContext = context;
            if (!acceptedSecretRef.equals(secretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(secretRef, value, "unit-test-provider", "v1"));
        }

        @Override
        public SecretProviderHealth health() {
            return SecretProviderHealth.unsupported("unit-test-provider", "UNKNOWN");
        }
    }
}
