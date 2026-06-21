package com.songhg.veri.agent.uie2e.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.TestAccountLeaseService;
import com.songhg.veri.agent.testdata.application.TestAccountPoolService;
import com.songhg.veri.agent.testdata.application.TestDataActorResolver;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.TestDataPlatformContextClient;
import com.songhg.veri.agent.testdata.application.TestDataRunnerCredentialResolver;
import com.songhg.veri.agent.testdata.application.command.AcquireExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpWorkerUiE2eRunnerAdapterTest {

    private static final String PROJECT_ID = "8e78afe7-f4e6-441c-a2ba-0d1041e3844f";
    private static final String ENVIRONMENT_KEY = "staging";
    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";
    private static final UUID SCENE_ID = UUID.fromString("a1111111-f4e6-441c-a2ba-0d1041e3844f");
    private static final UUID BUNDLE_ID = UUID.fromString("b2222222-f4e6-441c-a2ba-0d1041e3844f");
    private static final UUID RUN_ID = UUID.fromString("c3333333-f4e6-441c-a2ba-0d1041e3844f");

    @TempDir
    Path tempDir;

    @Test
    void runsAgainstExternalWorkerAndStoresInlineArtifacts() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> cancelAuthorization = new AtomicReference<>();
        HttpServer server = startWorkerServer(authorization, requestBody, cancelAuthorization);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            Fixture fixture = fixture(
                    baseUrl + "/run",
                    baseUrl + "/cancel",
                    "worker-token-001",
                    tempDir.resolve("artifacts")
            );

            UiE2eRunnerPort.RunnerValidation validation = fixture.adapter().validate(new UiE2eRunnerPort.RunnerValidationRequest(
                    SCENE_ID,
                    BUNDLE_ID,
                    PROJECT_ID,
                    "https://portal.example.test",
                    fixture.leaseRef().toString(),
                    fixture.accountSummary()
            ));

            assertThat(validation.accepted()).isTrue();

            UiE2eRunnerPort.RunnerRunResult result = fixture.adapter().run(request(fixture.leaseRef(), fixture.accountSummary()));

            assertThat(result.status()).isEqualTo("SUCCEEDED");
            assertThat(result.runnerMode()).isEqualTo("HTTP_ADAPTER");
            assertThat(result.failureCode()).isNull();
            assertThat(result.executionSummary()).containsEntry("remoteRunnerMode", "REMOTE_BROWSER");
            assertThat(authorization.get()).isEqualTo("Bearer worker-token-001");
            assertThat(requestBody.get()).contains("\"credentialPlan\"", "\"browserTypes\":[\"CHROMIUM\"]");
            assertThat(result.stepResults()).singleElement().satisfies(step -> {
                assertThat(step.status()).isEqualTo("SUCCEEDED");
                assertThat(step.summary()).containsEntry("aggregateOnly", true);
            });
            assertThat(result.artifacts()).anySatisfy(artifact -> {
                assertThat(artifact.artifactType()).isEqualTo("SCREENSHOT");
                assertThat(artifact.captureStatus()).isEqualTo("CAPTURED");
                assertThat(artifact.storageRef()).startsWith("artifact://ui-e2e/" + RUN_ID + "/");
                assertThat(artifact.redactionFlags()).containsEntry("rawArtifactStored", true);
                assertThat(artifact.redactionFlags()).containsEntry("rawArtifactDownloadReady", true);
                assertThat(fixture.artifactStorage().isDownloadReady(artifact.storageRef())).isTrue();
            });

            UiE2eRunnerPort.RunnerCancelResult canceled = fixture.adapter().cancel(RUN_ID);

            assertThat(canceled.accepted()).isTrue();
            assertThat(cancelAuthorization.get()).isEqualTo("Bearer worker-token-001");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blocksValidationWhenWorkerUrlIsMissing() {
        Fixture fixture = fixture("", "", "", tempDir.resolve("artifacts-missing"));

        UiE2eRunnerPort.RunnerValidation validation = fixture.adapter().validate(new UiE2eRunnerPort.RunnerValidationRequest(
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                fixture.leaseRef().toString(),
                fixture.accountSummary()
        ));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.errorCode()).isEqualTo("UI_E2E_RUNNER_WORKER_NOT_CONFIGURED");
        assertThat(validation.errorSummary()).contains("not configured");
    }

    private HttpServer startWorkerServer(
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody,
            AtomicReference<String> cancelAuthorization
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/run", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "status": "SUCCEEDED",
                      "runnerMode": "REMOTE_BROWSER",
                      "stepResults": [
                        {
                          "sceneStepId": "%s",
                          "stepOrder": 1,
                          "status": "SUCCEEDED",
                          "durationMs": 187,
                          "summary": {
                            "stepType": "LOGIN",
                            "workerNode": "runner-a"
                          }
                        }
                      ],
                      "artifacts": [
                        {
                          "artifactType": "SCREENSHOT",
                          "artifactDigest": "%s",
                          "sizeBytes": 10,
                          "redactionFlags": {
                            "workerStored": false
                          },
                          "captureStatus": "CAPTURED",
                          "fileName": "screenshot.png",
                          "contentBase64": "%s"
                        }
                      ],
                      "executionSummary": {
                        "workerRequestAccepted": true
                      }
                    }
                    """.formatted(
                    UUID.fromString("d4444444-f4e6-441c-a2ba-0d1041e3844f"),
                    "a".repeat(64),
                    Base64.getEncoder().encodeToString("png-bytes-1".getBytes(StandardCharsets.UTF_8))
            ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/cancel", exchange -> {
            cancelAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                    {
                      "accepted": true
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private Fixture fixture(
            String workerUrl,
            String cancelUrl,
            String workerToken,
            Path artifactRoot
    ) {
        InMemoryUiE2eRepository repository = new InMemoryUiE2eRepository();
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        repository.insertScene(new UiE2eScene(
                SCENE_ID,
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                "portal-http-scene",
                "后台管理员登录回归",
                "APPROVED",
                "HIGH",
                "{\"pageRefs\":[\"page-1\"]}",
                "[\"login\",\"smoke\"]",
                "tester",
                "tester",
                null,
                now,
                now
        ));
        repository.replaceSceneSteps(SCENE_ID, supportedLoginScene(now));
        repository.insertBundle(new UiE2eBundle(
                BUNDLE_ID,
                SCENE_ID,
                PROJECT_ID,
                "APPROVED",
                "sha256:bundle-http",
                "{\"sceneCode\":\"portal-http-scene\"}",
                "{\"fixtures\":[\"lease\"]}",
                "{\"staticCheck\":\"PASSED\"}",
                "tester",
                "reviewer",
                now,
                now,
                null,
                "tester",
                "tester",
                null,
                now,
                now
        ));

        UiE2eProperties properties = new UiE2eProperties(
                true,
                true,
                "http-adapter",
                10,
                60,
                1,
                20 * 1024 * 1024L,
                20,
                2,
                List.of("https://portal.example.test"),
                true,
                false,
                false,
                true,
                false,
                true,
                "node",
                "../portal-web/node_modules",
                workerUrl,
                cancelUrl,
                workerToken,
                5,
                artifactRoot.toString()
        );
        TestDataFixture testDataFixture = referenceService();
        UUID leaseRef = acquireLease(testDataFixture.referenceService(), testDataFixture.poolId());
        UiE2eArtifactStorage artifactStorage = new LocalUiE2eArtifactStorage(properties);
        return new Fixture(
                new HttpWorkerUiE2eRunnerAdapter(
                        repository,
                        properties,
                        testDataFixture.referenceService(),
                        new ObjectMapper(),
                        artifactStorage,
                        java.net.http.HttpClient.newHttpClient()
                ),
                leaseRef,
                testDataFixture.referenceService(),
                artifactStorage
        );
    }

    private TestDataFixture referenceService() {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext(PROJECT_ID)).thenReturn(platformContext(PROJECT_ID));
        doNothing().when(contextClient).writeAuditEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-runner-tester");
        TestDataProperties properties = new TestDataProperties(true, 10, 512, 60, 120, false, true);
        ObjectMapper objectMapper = new ObjectMapper();
        SecretProviderProperties secretProperties = new SecretProviderProperties(
                "0123456789abcdef0123456789abcdef",
                "v1",
                "",
                "",
                3,
                1,
                "",
                "",
                ""
        );
        TestAccountPoolService poolService = new TestAccountPoolService(
                repository,
                contextClient,
                actorResolver,
                properties,
                secretProperties,
                objectMapper
        );
        TestAccountLeaseService leaseService = new TestAccountLeaseService(
                repository,
                contextClient,
                actorResolver,
                properties,
                objectMapper
        );
        TestDataRunnerCredentialResolver runnerCredentialResolver = new TestDataRunnerCredentialResolver(
                repository,
                List.of(new AcceptingUiE2eSecretProvider()),
                secretProperties,
                objectMapper
        );
        var pool = poolService.createAccountPool(new CreateTestAccountPoolCommand(
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                "pool-runner",
                "Pool runner",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                60
        ));
        poolService.addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of("applicationId", "app-alpha"),
                SECRET_REF,
                "HEALTHY",
                null
        ));
        return new TestDataFixture(
                new TestDataCrossWpReferenceService(
                        leaseService,
                        repository,
                        contextClient,
                        properties,
                        runnerCredentialResolver,
                        objectMapper
                ),
                pool.id()
        );
    }

    private UUID acquireLease(TestDataCrossWpReferenceService referenceService, UUID poolId) {
        return referenceService.acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                PROJECT_ID,
                "app-alpha",
                ENVIRONMENT_KEY,
                poolId,
                List.of("ADMIN"),
                "run-" + UUID.randomUUID(),
                60,
                "lease-" + UUID.randomUUID()
        )).accountLeaseRef();
    }

    private UiE2eRunnerPort.RunnerRunRequest request(UUID leaseRef, Map<String, Object> accountSummary) {
        return new UiE2eRunnerPort.RunnerRunRequest(
                RUN_ID,
                SCENE_ID,
                BUNDLE_ID,
                PROJECT_ID,
                "https://portal.example.test",
                leaseRef.toString(),
                accountSummary
        );
    }

    private List<UiE2eSceneStep> supportedLoginScene(Instant now) {
        return List.of(new UiE2eSceneStep(
                UUID.fromString("d4444444-f4e6-441c-a2ba-0d1041e3844f"),
                SCENE_ID,
                PROJECT_ID,
                1,
                "LOGIN",
                "{\"principalField\":\"data-testid=username\",\"credentialField\":\"data-testid=password\",\"submitAction\":\"click\"}",
                "{\"preferred\":\"testId\",\"target\":\"login-form\"}",
                "{\"successSignal\":\"url contains /dashboard\"}",
                "{\"timeoutSeconds\":5}",
                "tester",
                "tester",
                now,
                now
        ));
    }

    private PlatformContext platformContext(String projectId) {
        return new PlatformContext(
                "PROJECT",
                projectId,
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        );
    }

    private record Fixture(
            HttpWorkerUiE2eRunnerAdapter adapter,
            UUID leaseRef,
            TestDataCrossWpReferenceService referenceService,
            UiE2eArtifactStorage artifactStorage
    ) {
        Map<String, Object> accountSummary() {
            var contract = referenceService.runnerAccountContract(leaseRef);
            return Map.of(
                    "accountLeaseRef", contract.accountLeaseRef().toString(),
                    "status", contract.status(),
                    "accountKey", contract.account().accountKey(),
                    "displayName", contract.account().displayName(),
                    "secretRefDigest", contract.account().secretRefDigest()
            );
        }
    }

    private record TestDataFixture(
            TestDataCrossWpReferenceService referenceService,
            UUID poolId
    ) {
    }

    private static final class AcceptingUiE2eSecretProvider implements SecretProvider {

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            if (!SECRET_REF.equals(secretRef)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSecret(secretRef, "Resolved-Runner-Password-001", "unit-test-provider", "v1"));
        }
    }
}
