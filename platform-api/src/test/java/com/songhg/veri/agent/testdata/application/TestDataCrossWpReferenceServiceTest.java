package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.AcquireExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.command.ImportTestDataRecordsCommand;
import com.songhg.veri.agent.testdata.application.command.ReleaseExecutionAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.TestDataReportEvidenceQuery;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDataCrossWpReferenceServiceTest {

    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";
    private static final String RAW_PASSWORD = "P@ssw0rd-should-not-leak";
    private static final String RAW_RECORD_PAYLOAD = "customer-name-raw";
    private static final String RAW_CLEANUP_REASON = "cleanup-release-reason-raw";

    @Test
    void wp9AdapterReturnsOnlyLeaseReferenceAndSanitizedAccountSummary() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService().createAccountPool(poolCommand());
        fixture.poolService().addAccount(pool.id(), accountCommand());

        var lease = fixture.crossWpService().acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "run-001",
                60,
                "wp9-run-001-admin"
        ));

        assertThat(lease.accountLeaseRef()).isNotNull();
        assertThat(lease.status()).isEqualTo("ACTIVE");
        assertThat(lease.account().secretRefDigest()).matches("[0-9a-f]{64}");
        assertThat(lease.policy()).containsEntry("wp9StoresAccountLeaseRefOnly", true);
        assertThat(lease.policy()).containsEntry("secretPlaintextReturned", false);
        assertThat(lease.toString()).doesNotContain(SECRET_REF, "secret://", RAW_PASSWORD, "leaseTokenDigest");

        var duplicate = fixture.crossWpService().acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "run-001",
                60,
                "wp9-run-001-admin"
        ));
        assertThat(duplicate.accountLeaseRef()).isEqualTo(lease.accountLeaseRef());

        assertThatThrownBy(() -> fixture.crossWpService().releaseExecutionRunLease(
                lease.accountLeaseRef(),
                new ReleaseExecutionAccountLeaseCommand("another-run", "wrong owner", "AVAILABLE")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        var released = fixture.crossWpService().releaseExecutionRunLease(
                lease.accountLeaseRef(),
                new ReleaseExecutionAccountLeaseCommand("run-001", "execution finished", "AVAILABLE")
        );
        assertThat(released.status()).isEqualTo("RELEASED");
    }

    @Test
    void runnerContractReturnsDigestOnlyAndRejectsReleasedLease() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService().createAccountPool(poolCommand());
        fixture.poolService().addAccount(pool.id(), accountCommand());
        var lease = fixture.crossWpService().acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "run-002",
                60,
                "wp9-run-002-admin"
        ));

        var runnerContract = fixture.crossWpService().runnerAccountContract(lease.accountLeaseRef());

        assertThat(runnerContract.accountLeaseRef()).isEqualTo(lease.accountLeaseRef());
        assertThat(runnerContract.credentialPolicy())
                .containsEntry("runnerReceivesPasswordPlaintext", false)
                .containsEntry("secretRefDigestReturned", true);
        assertThat(runnerContract.account().secretRefDigest()).matches("[0-9a-f]{64}");
        assertThat(runnerContract.toString()).doesNotContain(SECRET_REF, "secret://", RAW_PASSWORD, "password", "token", "cookie");

        fixture.crossWpService().releaseExecutionRunLease(
                lease.accountLeaseRef(),
                new ReleaseExecutionAccountLeaseCommand("run-002", "done", "AVAILABLE")
        );
        assertThatThrownBy(() -> fixture.crossWpService().runnerAccountContract(lease.accountLeaseRef()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void resolvesRunnerCredentialInsideTrustedAdapterOnly() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService().createAccountPool(poolCommand());
        fixture.poolService().addAccount(pool.id(), accountCommand());
        var lease = fixture.crossWpService().acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "run-cred-001",
                60,
                "wp7-runner-cred-001"
        ));

        var resolution = fixture.crossWpService().resolveRunnerCredential(lease.accountLeaseRef(), "project-alpha");

        assertThat(resolution.accountLeaseRef()).isEqualTo(lease.accountLeaseRef());
        assertThat(resolution.secretRefDigest()).matches("[0-9a-f]{64}");
        assertThat(resolution.provider()).isEqualTo("unit-test-provider");
        assertThat(resolution.version()).isEqualTo("v1");
        assertThat(resolution.secretValue()).isEqualTo("Resolved-Runner-Password-001");
        assertThat(resolution.toString()).doesNotContain(SECRET_REF, "secret://", "Resolved-Runner-Password-001");
    }

    @Test
    void runnerCredentialResolveFailureReturnsProviderErrorWithoutSecretRefLeak() {
        Fixture fixture = fixture(true, List.of(new RejectingSecretProvider()));
        var pool = fixture.poolService().createAccountPool(poolCommand());
        fixture.poolService().addAccount(pool.id(), accountCommand());
        var lease = fixture.crossWpService().acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "run-cred-002",
                60,
                "wp7-runner-cred-002"
        ));

        assertThatThrownBy(() -> fixture.crossWpService().resolveRunnerCredential(lease.accountLeaseRef(), "project-alpha"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECRET_PROVIDER_ERROR);
                    assertThat(exception.getMessage()).contains("runner secretRef 未解析: sha256:");
                    assertThat(exception.getMessage()).doesNotContain(SECRET_REF, "secret://");
                });
    }

    @Test
    void wp10ReportEvidenceContainsOnlyReferencesCountsAndDigests() {
        Fixture fixture = fixture(true);
        var dataSet = fixture.dataSetService().createDataSet(dataSetCommand());
        fixture.dataSetService().importRecords(dataSet.id(), new ImportTestDataRecordsCommand(List.of(
                new ImportTestDataRecordsCommand.RecordItem(
                        "record-001",
                        sha256("record-001"),
                        Map.of(
                                "maskedName", "c***",
                                "payloadDigest", sha256(RAW_RECORD_PAYLOAD)
                        ),
                        sha256("external-record-001"),
                        List.of("SMOKE")
                )
        )));
        var task = fixture.taskService().createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                dataSet.id(),
                "CLEANUP",
                "cleanup-run-003",
                "lease:run-003",
                Map.of("reason", RAW_CLEANUP_REASON, "recordDigest", sha256("record-001"))
        ));
        var pool = fixture.poolService().createAccountPool(poolCommand());
        fixture.poolService().addAccount(pool.id(), accountCommand());
        var lease = fixture.crossWpService().acquireExecutionRunLease(new AcquireExecutionAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "run-003",
                60,
                "wp9-run-003-admin"
        ));

        var evidence = fixture.crossWpService().reportEvidence(new TestDataReportEvidenceQuery(
                "project-alpha",
                "report-run-003",
                List.of(dataSet.id()),
                List.of(lease.accountLeaseRef()),
                List.of(task.id())
        ));

        assertThat(evidence.dataSets()).singleElement()
                .satisfies(item -> {
                    assertThat(item.dataSetRef()).isEqualTo(dataSet.id());
                    assertThat(item.schemaFieldCount()).isEqualTo(1);
                    assertThat(item.recordCount()).isEqualTo(1);
                    assertThat(item.cleanupPolicyDigest()).matches("[0-9a-f]{64}");
                });
        assertThat(evidence.accountLeases()).singleElement()
                .satisfies(item -> {
                    assertThat(item.holderType()).isEqualTo("EXECUTION_RUN");
                    assertThat(item.holderRef()).isEqualTo("run-003");
                    assertThat(item.account().secretRefDigest()).matches("[0-9a-f]{64}");
                    assertThat(item.account().scopeSummary()).containsEntry("applicationId", "app-alpha");
                    assertThat(item.account().scopeSummary().toString()).doesNotContain(
                            SECRET_REF,
                            "secret://",
                            RAW_PASSWORD,
                            "password",
                            "token",
                            "cookie"
                    );
                });
        assertThat(evidence.cleanupTasks()).singleElement()
                .satisfies(item -> {
                    assertThat(item.targetRefDigest()).matches("[0-9a-f]{64}");
                    assertThat(item.resultSummaryDigest()).matches("[0-9a-f]{64}");
                    assertThat(item.resultSummaryKeys()).containsExactly("reason", "recordDigest");
                });
        assertThat(evidence.redactionPolicy())
                .containsEntry("rawRecordPayloadReturned", false)
                .containsEntry("cleanupResultPayloadReturned", false);
        assertThat(evidence.toString()).doesNotContain(
                SECRET_REF,
                "secret://",
                RAW_PASSWORD,
                RAW_RECORD_PAYLOAD,
                "lease:run-003",
                RAW_CLEANUP_REASON
        );
    }

    @Test
    void rejectsReportEvidenceFromAnotherProject() {
        Fixture fixture = fixture(true);
        var dataSet = fixture.dataSetService().createDataSet(dataSetCommand());

        assertThatThrownBy(() -> fixture.crossWpService().reportEvidence(new TestDataReportEvidenceQuery(
                "project-beta",
                "report-cross-project",
                List.of(dataSet.id()),
                List.of(),
                List.of()
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void resolvesUiE2eStepDataBindingByDataSetCode() {
        Fixture fixture = fixture(true);
        var dataSet = fixture.dataSetService().createDataSet(dataSetCommand());
        fixture.dataSetService().importRecords(dataSet.id(), new ImportTestDataRecordsCommand(List.of(
                new ImportTestDataRecordsCommand.RecordItem(
                        "record-001",
                        sha256("record-001"),
                        Map.of("usernameMasked", "masked-user-01", "emailMasked", "u***@example.test"),
                        sha256("external-record-001"),
                        List.of("SMOKE")
                )
        )));

        var resolution = fixture.crossWpService().resolveUiE2eStepDataBinding("project-alpha", Map.of(
                "dataSetCode", dataSet.code(),
                "recordKey", "record-001",
                "bindingAlias", "user"
        ));

        assertThat(resolution.dataSetId()).isEqualTo(dataSet.id());
        assertThat(resolution.dataSetCode()).isEqualTo(dataSet.code());
        assertThat(resolution.bindingAlias()).isEqualTo("user");
        assertThat(resolution.recordKey()).isEqualTo("record-001");
        assertThat(resolution.maskedSummary()).containsEntry("usernameMasked", "masked-user-01");
    }

    @Test
    void rejectsUiE2eStepDataBindingWhenActiveRecordIsAmbiguous() {
        Fixture fixture = fixture(true);
        var dataSet = fixture.dataSetService().createDataSet(dataSetCommand());
        fixture.dataSetService().importRecords(dataSet.id(), new ImportTestDataRecordsCommand(List.of(
                new ImportTestDataRecordsCommand.RecordItem(
                        "record-001",
                        sha256("record-001"),
                        Map.of("usernameMasked", "masked-user-01"),
                        sha256("external-record-001"),
                        List.of("SMOKE")
                ),
                new ImportTestDataRecordsCommand.RecordItem(
                        "record-002",
                        sha256("record-002"),
                        Map.of("usernameMasked", "masked-user-02"),
                        sha256("external-record-002"),
                        List.of("REGRESSION")
                )
        )));

        assertThatThrownBy(() -> fixture.crossWpService().resolveUiE2eStepDataBinding("project-alpha", Map.of(
                "dataSetCode", dataSet.code(),
                "bindingAlias", "user"
        ))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
            assertThat(exception.getMessage()).isEqualTo("UI_E2E_TEST_DATA_RECORD_AMBIGUOUS");
        });
    }

    private Fixture fixture(boolean enabled) {
        return fixture(enabled, List.of(new AcceptingSecretProvider()));
    }

    private Fixture fixture(boolean enabled, List<SecretProvider> secretProviders) {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = contextClient();
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-cross-wp-tester");
        TestDataProperties properties = new TestDataProperties(enabled, 10, 512, 60, 120, false, true);
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
                secretProviders,
                secretProperties,
                objectMapper
        );
        TestDataSetService dataSetService = new TestDataSetService(
                repository,
                contextClient,
                actorResolver,
                properties,
                objectMapper
        );
        TestDataTaskService taskService = new TestDataTaskService(
                repository,
                contextClient,
                actorResolver,
                properties,
                mock(AsyncTaskNotificationService.class),
                objectMapper
        );
        return new Fixture(
                poolService,
                dataSetService,
                taskService,
                new TestDataCrossWpReferenceService(
                        leaseService,
                        repository,
                        contextClient,
                        properties,
                        runnerCredentialResolver,
                        objectMapper
                )
        );
    }

    private TestDataPlatformContextClient contextClient() {
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(platformContext("project-alpha"));
        when(contextClient.projectContext("project-beta")).thenReturn(platformContext("project-beta"));
        doNothing().when(contextClient).writeAuditEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        return contextClient;
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

    private CreateTestAccountPoolCommand poolCommand() {
        return new CreateTestAccountPoolCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "pool-" + UUID.randomUUID(),
                "Pool alpha",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                60
        );
    }

    private UpsertTestPooledAccountCommand accountCommand() {
        return new UpsertTestPooledAccountCommand(
                "admin-" + UUID.randomUUID(),
                "Admin account",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of("applicationId", "app-alpha"),
                SECRET_REF,
                "HEALTHY",
                null
        );
    }

    private CreateTestDataSetCommand dataSetCommand() {
        return new CreateTestDataSetCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "checkout-users-" + UUID.randomUUID(),
                "Checkout users",
                "READY",
                Map.of("fields", List.of(Map.of("name", "userId", "type", "STRING", "sensitive", false))),
                "INTERNAL",
                Map.of("mode", "MANUAL_CONFIRM"),
                "EXTERNAL_REF",
                sha256("wp8-external-source")
        );
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Fixture(
            TestAccountPoolService poolService,
            TestDataSetService dataSetService,
            TestDataTaskService taskService,
            TestDataCrossWpReferenceService crossWpService
    ) {
    }

    private static final class AcceptingSecretProvider implements SecretProvider {

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            if (!SECRET_REF.equals(secretRef)) {
                return Optional.empty();
            }
            assertThat(context).isEqualTo(new SecretResolveContext(
                    "UI_E2E_RUNNER",
                    "wp7-ui-e2e-runner",
                    "PROJECT",
                    "project-alpha"
            ));
            return Optional.of(new ResolvedSecret(secretRef, "Resolved-Runner-Password-001", "unit-test-provider", "v1"));
        }
    }

    private static final class RejectingSecretProvider implements SecretProvider {

        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            return Optional.empty();
        }
    }
}
