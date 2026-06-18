package com.songhg.veri.agent.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.query.AssetListQuery;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersion;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import com.songhg.veri.agent.auth.infrastructure.JdbcAuthSessionStore;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobStatus;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelAccessPolicyOverride;
import com.songhg.veri.agent.modelaccess.infrastructure.JdbcModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.JdbcModelAccessRepository;
import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.domain.ReportDefectDraft;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportExportManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignConflictOperationQuery;
import com.songhg.veri.agent.testdesign.domain.TestDesignAuditChainAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignContextPolicyOverride;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpAuditDetailBucket;
import com.songhg.veri.agent.testdesign.domain.TestDesignCrossWpOperationsAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignModelObservationBucket;
import com.songhg.veri.agent.testdesign.domain.TestDesignOperationsAuditAggregate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignQueueAlertSubscription;
import com.songhg.veri.agent.testdesign.domain.TestDesignReviewRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.application.query.TestAccountLeaseQuery;
import com.songhg.veri.agent.testdata.application.query.TestAccountPoolQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataSetQuery;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskQuery;
import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.domain.TestAccountPool;
import com.songhg.veri.agent.testdata.domain.TestDataRecord;
import com.songhg.veri.agent.testdata.domain.TestDataSet;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import com.songhg.veri.agent.testdata.infrastructure.JdbcTestDataRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eBundleQuery;
import com.songhg.veri.agent.uie2e.application.query.UiE2eFlakyMarkQuery;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunQuery;
import com.songhg.veri.agent.uie2e.application.query.UiE2eSceneQuery;
import com.songhg.veri.agent.uie2e.domain.UiE2eArtifactManifest;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundleReview;
import com.songhg.veri.agent.uie2e.domain.UiE2eFlakyMark;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import com.songhg.veri.agent.uie2e.domain.UiE2eRunStepResult;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import com.songhg.veri.agent.uie2e.infrastructure.JdbcUiE2eRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.locations=filesystem:../db/migration/wp1",
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.auth.session-cleanup-enabled=false",
        "veri-agent.audit.retention-cleanup-enabled=false",
        "veri-agent.secret.local-master-key=0123456789abcdef0123456789abcdef",
        "veri-agent.document-input.webhook-secret=test-webhook-secret-32-byte-minimum!"
})
@ActiveProfiles("db")
class DbProfileRepositoryContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthSessionStore authSessionStore;

    @Autowired
    private ModelAccessRepository modelAccessRepository;

    @Autowired
    private ModelInvocationJobRepository modelInvocationJobRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TestDesignRepository testDesignRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ReportingRepository reportingRepository;

    @Autowired
    private TestDataRepository testDataRepository;

    @Autowired
    private UiE2eRepository uiE2eRepository;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dbProfileWiresJdbcImplementations() {
        assertThat(authSessionStore).isInstanceOf(JdbcAuthSessionStore.class);
        assertThat(modelAccessRepository).isInstanceOf(JdbcModelAccessRepository.class);
        assertThat(modelInvocationJobRepository).isInstanceOf(JdbcModelInvocationJobRepository.class);
        assertThat(applicationContext.getBeanNamesForType(AssetRepository.class)).hasSize(1);
        assertThat(testDataRepository).isInstanceOf(JdbcTestDataRepository.class);
        assertThat(applicationContext.getBeanNamesForType(TestDataRepository.class)).hasSize(1);
        assertThat(uiE2eRepository).isInstanceOf(JdbcUiE2eRepository.class);
        assertThat(applicationContext.getBeanNamesForType(UiE2eRepository.class)).hasSize(1);
    }

    @Test
    void testDataRepositoryPersistsDataSetsAndRecordsThroughJdbc() {
        Instant now = Instant.now();
        UUID dataSetId = UUID.randomUUID();
        String projectId = "project-wp8-db-" + UUID.randomUUID();

        testDataRepository.insertDataSet(new TestDataSet(
                dataSetId,
                projectId,
                "app-db",
                "env-db",
                "dataset-db",
                "DB dataset",
                "DRAFT",
                "{\"fields\":[{\"name\":\"customerId\",\"type\":\"STRING\"}]}",
                "CONFIDENTIAL",
                "{\"mode\":\"MANUAL_CONFIRM\"}",
                "EXTERNAL_REF",
                "a".repeat(64),
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));

        assertThat(testDataRepository.dataSetByProjectAndCode(projectId, "dataset-db"))
                .isPresent()
                .get()
                .extracting(TestDataSet::schemaJson)
                .asString()
                .contains("customerId");

        TestDataSet ready = new TestDataSet(
                dataSetId,
                projectId,
                "app-db",
                "env-db",
                "dataset-db",
                "DB dataset ready",
                "READY",
                "{\"fields\":[{\"name\":\"customerId\",\"type\":\"STRING\"}]}",
                "CONFIDENTIAL",
                "{\"mode\":\"MANUAL_CONFIRM\",\"ttlDays\":7}",
                "EXTERNAL_REF",
                "a".repeat(64),
                "db-tester",
                "db-updater",
                null,
                now,
                now.plusSeconds(1)
        );
        testDataRepository.updateDataSet(ready);

        TestDataSetQuery query = new TestDataSetQuery(projectId, "app-db", "env-db", "READY", "dataset", 0, 10);
        assertThat(testDataRepository.countDataSets(query)).isEqualTo(1);
        assertThat(testDataRepository.dataSets(query))
                .singleElement()
                .extracting(TestDataSet::name)
                .isEqualTo("DB dataset ready");

        testDataRepository.upsertRecords(List.of(new TestDataRecord(
                UUID.randomUUID(),
                dataSetId,
                projectId,
                "customer:001",
                "ACTIVE",
                "b".repeat(64),
                "{\"customerEmail\":\"c***@example.test\"}",
                "c".repeat(64),
                "[\"sanitized\",\"db\"]",
                "db-tester",
                "db-tester",
                now,
                now
        )));
        testDataRepository.upsertRecords(List.of(new TestDataRecord(
                UUID.randomUUID(),
                dataSetId,
                projectId,
                "customer:001",
                "ACTIVE",
                "d".repeat(64),
                "{\"customerEmail\":\"updated***@example.test\"}",
                null,
                "[\"sanitized\"]",
                "db-tester",
                "db-updater",
                now,
                now.plusSeconds(2)
        )));

        assertThat(testDataRepository.countRecords(dataSetId)).isEqualTo(1);
        assertThat(testDataRepository.records(dataSetId))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.recordDigest()).isEqualTo("d".repeat(64));
                    assertThat(record.maskedSummaryJson()).contains("updated***@example.test");
                    assertThat(record.tagsJson()).contains("sanitized");
                });

        testDataRepository.archiveDataSet(new TestDataSet(
                dataSetId,
                projectId,
                "app-db",
                "env-db",
                "dataset-db",
                "DB dataset ready",
                "ARCHIVED",
                "{\"fields\":[{\"name\":\"customerId\",\"type\":\"STRING\"}]}",
                "CONFIDENTIAL",
                "{\"mode\":\"MANUAL_CONFIRM\",\"ttlDays\":7}",
                "EXTERNAL_REF",
                "a".repeat(64),
                "db-tester",
                "db-archiver",
                now.plusSeconds(3),
                now,
                now.plusSeconds(3)
        ));

        assertThat(testDataRepository.dataSetProjectScopeId(dataSetId)).contains(projectId);
        assertThat(testDataRepository.dataSet(dataSetId))
                .isPresent()
                .get()
                .extracting(TestDataSet::status)
                .isEqualTo("ARCHIVED");
    }

    @Test
    void uiE2eRepositoryPersistsScenesAndStepsThroughJdbc() {
        Instant now = Instant.now();
        UUID sceneId = UUID.randomUUID();
        String projectId = "project-wp7-db-" + UUID.randomUUID();

        uiE2eRepository.insertScene(new UiE2eScene(
                sceneId,
                projectId,
                "app-db",
                "env-db",
                "portal-admin-login",
                "Portal admin login",
                "DRAFT",
                "HIGH",
                "{\"pageRefs\":[]}",
                "[\"login\",\"smoke\"]",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));
        uiE2eRepository.replaceSceneSteps(sceneId, List.of(
                new UiE2eSceneStep(
                        UUID.randomUUID(),
                        sceneId,
                        projectId,
                        1,
                        "LOGIN",
                        "{\"submitAction\":\"click\"}",
                        "{\"preferred\":\"testId\"}",
                        "{\"successSignal\":\"/dashboard\"}",
                        "{\"timeoutSeconds\":5}",
                        "db-tester",
                        "db-tester",
                        now,
                        now
                )
        ));

        assertThat(uiE2eRepository.sceneByProjectAndCode(projectId, "portal-admin-login"))
                .isPresent()
                .get()
                .extracting(UiE2eScene::riskLevel)
                .isEqualTo("HIGH");
        assertThat(uiE2eRepository.sceneSteps(sceneId))
                .singleElement()
                .extracting(UiE2eSceneStep::stepType)
                .isEqualTo("LOGIN");

        UiE2eScene approved = new UiE2eScene(
                sceneId,
                projectId,
                "app-db",
                "env-db",
                "portal-admin-login",
                "Portal admin login approved",
                "APPROVED",
                "CRITICAL",
                "{\"pageRefs\":[]}",
                "[\"critical\"]",
                "db-tester",
                "db-updater",
                null,
                now,
                now.plusSeconds(1)
        );
        uiE2eRepository.updateScene(approved);
        uiE2eRepository.replaceSceneSteps(sceneId, List.of(
                new UiE2eSceneStep(
                        UUID.randomUUID(),
                        sceneId,
                        projectId,
                        1,
                        "ASSERT",
                        "{\"action\":\"check\"}",
                        "{\"preferred\":\"role\"}",
                        "{\"expected\":\"dashboard\"}",
                        "{\"timeoutSeconds\":3}",
                        "db-updater",
                        "db-updater",
                        now.plusSeconds(1),
                        now.plusSeconds(1)
                )
        ));

        UiE2eSceneQuery query = new UiE2eSceneQuery(projectId, "app-db", "env-db", "APPROVED", "CRITICAL", "critical", "portal", 0, 10);
        assertThat(uiE2eRepository.countScenes(query)).isEqualTo(1);
        assertThat(uiE2eRepository.scenes(query))
                .singleElement()
                .extracting(UiE2eScene::name)
                .isEqualTo("Portal admin login approved");
        assertThat(uiE2eRepository.sceneSteps(sceneId))
                .singleElement()
                .extracting(UiE2eSceneStep::stepType)
                .isEqualTo("ASSERT");

        uiE2eRepository.archiveScene(new UiE2eScene(
                sceneId,
                projectId,
                "app-db",
                "env-db",
                "portal-admin-login",
                "Portal admin login approved",
                "ARCHIVED",
                "CRITICAL",
                "{\"pageRefs\":[]}",
                "[\"critical\"]",
                "db-tester",
                "db-archiver",
                now.plusSeconds(2),
                now,
                now.plusSeconds(2)
        ));

        assertThat(uiE2eRepository.scene(sceneId))
                .isPresent()
                .get()
                .extracting(UiE2eScene::status)
                .isEqualTo("ARCHIVED");
    }

    @Test
    void uiE2eRepositoryPersistsBundlesAndReviewsThroughJdbc() {
        Instant now = Instant.now();
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        String projectId = "project-wp7-bundle-db-" + UUID.randomUUID();

        uiE2eRepository.insertScene(new UiE2eScene(
                sceneId,
                projectId,
                "app-db",
                "env-db",
                "portal-bundle-review",
                "Portal bundle review",
                "APPROVED",
                "HIGH",
                "{\"pageRefs\":[]}",
                "[\"review\"]",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));

        uiE2eRepository.insertBundle(new UiE2eBundle(
                bundleId,
                sceneId,
                projectId,
                "DRAFT",
                "a".repeat(64),
                "{\"templateVersion\":\"wp7-playwright-summary-v1\"}",
                "{\"requiredFixtures\":[\"page\"]}",
                "{\"status\":\"PASSED\",\"findingCount\":0}",
                null,
                null,
                null,
                null,
                null,
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));

        assertThat(uiE2eRepository.activeBundleBySceneAndDigest(sceneId, "a".repeat(64)))
                .isPresent()
                .get()
                .extracting(UiE2eBundle::status)
                .isEqualTo("DRAFT");

        UiE2eBundle reviewing = new UiE2eBundle(
                bundleId,
                sceneId,
                projectId,
                "REVIEWING",
                "a".repeat(64),
                "{\"templateVersion\":\"wp7-playwright-summary-v1\"}",
                "{\"requiredFixtures\":[\"page\"]}",
                "{\"status\":\"PASSED\",\"findingCount\":0}",
                "db-reviewer",
                null,
                now.plusSeconds(1),
                null,
                null,
                "db-tester",
                "db-reviewer",
                null,
                now,
                now.plusSeconds(1)
        );
        uiE2eRepository.updateBundle(reviewing);
        uiE2eRepository.insertBundleReview(new UiE2eBundleReview(
                UUID.randomUUID(),
                bundleId,
                projectId,
                "SUBMITTED",
                "ready",
                "db-reviewer",
                now.plusSeconds(1),
                "db-reviewer",
                "db-reviewer",
                now.plusSeconds(1),
                now.plusSeconds(1)
        ));

        UiE2eBundleQuery query = new UiE2eBundleQuery(projectId, sceneId, "REVIEWING", "portal", 0, 10);
        assertThat(uiE2eRepository.countBundles(query)).isEqualTo(1);
        assertThat(uiE2eRepository.bundles(query))
                .singleElement()
                .extracting(UiE2eBundle::status)
                .isEqualTo("REVIEWING");
        assertThat(uiE2eRepository.bundleReviews(bundleId))
                .singleElement()
                .extracting(UiE2eBundleReview::reviewStatus)
                .isEqualTo("SUBMITTED");

        assertThat(uiE2eRepository.bundleProjectScopeId(bundleId)).contains(projectId);
    }

    @Test
    void uiE2eRepositoryPersistsRunsThroughJdbc() {
        Instant now = Instant.now();
        UUID sceneId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String projectId = "project-wp7-run-db-" + UUID.randomUUID();
        String requestKey = "wp7-run-db-001";

        uiE2eRepository.insertScene(new UiE2eScene(
                sceneId,
                projectId,
                "app-db",
                "env-db",
                "portal-run-db",
                "Portal run db",
                "APPROVED",
                "HIGH",
                "{\"pageRefs\":[]}",
                "[\"run\"]",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));
        uiE2eRepository.insertBundle(new UiE2eBundle(
                bundleId,
                sceneId,
                projectId,
                "APPROVED",
                "a".repeat(64),
                "{\"templateVersion\":\"wp7-playwright-summary-v1\"}",
                "{\"requiredFixtures\":[\"page\"]}",
                "{\"status\":\"PASSED\",\"findingCount\":0}",
                "db-reviewer",
                "db-reviewer",
                now,
                now,
                null,
                "db-tester",
                "db-reviewer",
                null,
                now,
                now
        ));

        uiE2eRepository.insertRun(new UiE2eRun(
                runId,
                sceneId,
                bundleId,
                projectId,
                "RUNNING",
                requestKey,
                "MANAGED",
                "b".repeat(64),
                UUID.randomUUID().toString(),
                "{\"accountLeaseRef\":\"lease-001\",\"secretRefDigest\":\"c".repeat(1) + "\"}",
                null,
                null,
                "trc_wp7_run_db",
                "db-tester",
                now,
                null,
                now,
                now
        ));

        assertThat(uiE2eRepository.run(runId))
                .isPresent()
                .get()
                .satisfies(run -> {
                    assertThat(run.status()).isEqualTo("RUNNING");
                    assertThat(run.requestKey()).isEqualTo(requestKey);
                    assertThat(run.accountSummaryJson()).contains("accountLeaseRef");
                });
        assertThat(uiE2eRepository.runByProjectSceneAndRequestKey(projectId, sceneId, requestKey))
                .isPresent()
                .get()
                .extracting(UiE2eRun::id)
                .isEqualTo(runId);

        uiE2eRepository.updateRun(new UiE2eRun(
                runId,
                sceneId,
                bundleId,
                projectId,
                "CANCELED",
                requestKey,
                "MANAGED",
                "b".repeat(64),
                UUID.randomUUID().toString(),
                "{\"accountLeaseRef\":\"lease-001\",\"secretRefDigest\":\"digest-001\"}",
                "UI_E2E_RUNNER_CANCELED",
                "runner cancel accepted",
                "trc_wp7_run_db",
                "db-updater",
                now,
                now.plusSeconds(5),
                now,
                now.plusSeconds(5)
        ));

        UiE2eRunQuery query = new UiE2eRunQuery(projectId, sceneId, bundleId, "CANCELED", "wp7-run-db", 0, 10);
        assertThat(uiE2eRepository.countRuns(query)).isEqualTo(1);
        assertThat(uiE2eRepository.runs(query))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.status()).isEqualTo("CANCELED");
                    assertThat(run.failureCode()).isEqualTo("UI_E2E_RUNNER_CANCELED");
                    assertThat(run.finishedAt()).isEqualTo(now.plusSeconds(5));
                });
        assertThat(uiE2eRepository.runProjectScopeId(runId)).contains(projectId);
    }

    @Test
    void uiE2eRepositoryPersistsRunEvidenceAndFlakyMarksThroughJdbc() {
        Instant now = Instant.now();
        UUID sceneId = UUID.randomUUID();
        UUID sceneStepId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String projectId = "project-wp7-evidence-db-" + UUID.randomUUID();

        uiE2eRepository.insertScene(new UiE2eScene(
                sceneId,
                projectId,
                "app-db",
                "env-db",
                "portal-run-evidence-db",
                "Portal run evidence db",
                "APPROVED",
                "HIGH",
                "{\"pageRefs\":[]}",
                "[\"run\",\"evidence\"]",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));
        uiE2eRepository.replaceSceneSteps(sceneId, List.of(
                new UiE2eSceneStep(
                        sceneStepId,
                        sceneId,
                        projectId,
                        1,
                        "LOGIN",
                        "{\"submitAction\":\"click\"}",
                        "{\"preferred\":\"testId\"}",
                        "{\"successSignal\":\"/dashboard\"}",
                        "{\"timeoutSeconds\":5}",
                        "db-tester",
                        "db-tester",
                        now,
                        now
                )
        ));
        uiE2eRepository.insertBundle(new UiE2eBundle(
                bundleId,
                sceneId,
                projectId,
                "APPROVED",
                "d".repeat(64),
                "{\"templateVersion\":\"wp7-playwright-summary-v1\"}",
                "{\"requiredFixtures\":[\"page\"]}",
                "{\"status\":\"PASSED\",\"findingCount\":0}",
                "db-reviewer",
                "db-reviewer",
                now,
                now,
                null,
                "db-tester",
                "db-reviewer",
                null,
                now,
                now
        ));
        uiE2eRepository.insertRun(new UiE2eRun(
                runId,
                sceneId,
                bundleId,
                projectId,
                "FAILED",
                "wp7-run-evidence-db-001",
                "MANAGED",
                "e".repeat(64),
                UUID.randomUUID().toString(),
                "{\"accountLeaseRef\":\"lease-001\",\"secretRefDigest\":\"digest-001\"}",
                "ASSERTION_FAILED",
                "assertion mismatch",
                "trc_wp7_run_evidence_db",
                "db-tester",
                now,
                now.plusSeconds(5),
                now,
                now.plusSeconds(5)
        ));

        uiE2eRepository.replaceRunStepResults(runId, List.of(
                new UiE2eRunStepResult(
                        UUID.randomUUID(),
                        runId,
                        sceneStepId,
                        1,
                        "FAILED",
                        240,
                        "ASSERTION",
                        "ASSERTION_FAILED",
                        "{\"aggregateOnly\":true,\"stepType\":\"LOGIN\"}",
                        "db-worker",
                        "db-worker",
                        now,
                        now
                ),
                new UiE2eRunStepResult(
                        UUID.randomUUID(),
                        runId,
                        null,
                        2,
                        "SKIPPED",
                        0,
                        null,
                        null,
                        "{\"aggregateOnly\":true,\"stepType\":\"ASSERT\"}",
                        "db-worker",
                        "db-worker",
                        now.plusSeconds(1),
                        now.plusSeconds(1)
                )
        ));
        uiE2eRepository.replaceArtifacts(runId, List.of(
                new UiE2eArtifactManifest(
                        UUID.randomUUID(),
                        runId,
                        "SCREENSHOT",
                        "artifact://wp7/screenshot-001",
                        "f".repeat(64),
                        2048,
                        "{\"aggregateOnly\":true,\"scanStatus\":\"clean\"}",
                        "CAPTURED",
                        "db-worker",
                        "db-worker",
                        now,
                        now
                ),
                new UiE2eArtifactManifest(
                        UUID.randomUUID(),
                        runId,
                        "LOG",
                        null,
                        null,
                        0,
                        "{\"aggregateOnly\":true,\"captureBlockedReason\":\"artifactRefIncomplete\"}",
                        "FAILED",
                        "db-worker",
                        "db-worker",
                        now.plusSeconds(1),
                        now.plusSeconds(1)
                )
        ));

        UiE2eFlakyMark sceneMark = new UiE2eFlakyMark(
                UUID.randomUUID(),
                projectId,
                sceneId,
                null,
                "FLAKY_CANDIDATE",
                "LOCATOR_DRIFT",
                "locator drift candidate",
                "db-tester",
                "db-tester",
                now,
                now
        );
        uiE2eRepository.upsertFlakyMark(sceneMark);
        uiE2eRepository.upsertFlakyMark(new UiE2eFlakyMark(
                UUID.randomUUID(),
                projectId,
                sceneId,
                runId,
                "CONFIRMED_FLAKY",
                "LOCATOR_DRIFT",
                "locator drift confirmed",
                "db-tester",
                "db-updater",
                now,
                now.plusSeconds(2)
        ));

        assertThat(uiE2eRepository.runStepResults(runId))
                .hasSize(2)
                .first()
                .satisfies(step -> {
                    assertThat(step.stepOrder()).isEqualTo(1);
                    assertThat(step.failureBucket()).isEqualTo("ASSERTION");
                    assertThat(step.summaryJson()).contains("aggregateOnly");
                });
        assertThat(uiE2eRepository.artifacts(runId))
                .hasSize(2)
                .first()
                .satisfies(artifact -> {
                    assertThat(artifact.artifactType()).isEqualTo("SCREENSHOT");
                    assertThat(artifact.storageRef()).isEqualTo("artifact://wp7/screenshot-001");
                });
        assertThat(uiE2eRepository.flakyMarkByRun(runId))
                .isPresent()
                .get()
                .satisfies(mark -> {
                    assertThat(mark.id()).isEqualTo(sceneMark.id());
                    assertThat(mark.status()).isEqualTo("CONFIRMED_FLAKY");
                    assertThat(mark.runId()).isEqualTo(runId);
                });

        UiE2eFlakyMarkQuery flakyQuery = new UiE2eFlakyMarkQuery(projectId, sceneId, runId, "CONFIRMED_FLAKY", "locator", 0, 10);
        assertThat(uiE2eRepository.countFlakyMarks(flakyQuery)).isEqualTo(1);
        assertThat(uiE2eRepository.flakyMarks(flakyQuery))
                .singleElement()
                .extracting(UiE2eFlakyMark::reasonSummary)
                .isEqualTo("locator drift confirmed");
    }

    @Test
    void testDataRepositoryPersistsAccountPoolsAndAccountsThroughJdbcWithoutSecretCipherProjection() {
        Instant now = Instant.now();
        UUID poolId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String projectId = "project-wp8-account-db-" + UUID.randomUUID();

        testDataRepository.insertAccountPool(new TestAccountPool(
                poolId,
                projectId,
                "app-db",
                "env-db",
                "pool-db",
                "DB account pool",
                "READY",
                "{\"sharing\":\"EXCLUSIVE\"}",
                300,
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));

        assertThat(testDataRepository.accountPoolByProjectAndCode(projectId, "pool-db"))
                .isPresent()
                .get()
                .extracting(TestAccountPool::leasePolicyJson)
                .asString()
                .contains("EXCLUSIVE");

        TestAccountPoolQuery query = new TestAccountPoolQuery(projectId, "app-db", "env-db", "READY", "pool", 0, 10);
        assertThat(testDataRepository.countAccountPools(query)).isEqualTo(1);
        assertThat(testDataRepository.accountPools(query))
                .singleElement()
                .extracting(TestAccountPool::defaultTtlSeconds)
                .isEqualTo(300);

        testDataRepository.insertPooledAccount(new TestPooledAccount(
                accountId,
                poolId,
                projectId,
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                "[\"ADMIN\",\"APPROVER\"]",
                "{\"applicationId\":\"app-db\"}",
                "e".repeat(64),
                "HEALTHY",
                "manual smoke passed",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));
        jdbcTemplate.update(
                "update test_pooled_account set secret_ref_cipher = ? where id = ?",
                "ciphertext-should-not-be-projected",
                accountId
        );

        assertThat(testDataRepository.countPooledAccounts(poolId, null)).isEqualTo(1);
        assertThat(testDataRepository.countPooledAccounts(poolId, "AVAILABLE")).isEqualTo(1);
        assertThat(testDataRepository.pooledAccountByPoolAndKey(poolId, "admin-01"))
                .isPresent()
                .get()
                .satisfies(account -> {
                    assertThat(account.secretRefDigest()).isEqualTo("e".repeat(64));
                    assertThat(account.toString()).doesNotContain("ciphertext-should-not-be-projected");
                    assertThat(account.roleTagsJson()).contains("ADMIN");
                });

        testDataRepository.updatePooledAccount(new TestPooledAccount(
                accountId,
                poolId,
                projectId,
                "admin-01",
                "Admin 01 locked",
                "LOCKED",
                "[\"ADMIN\"]",
                "{\"reason\":\"manual\"}",
                "f".repeat(64),
                "LOCKED",
                "manual lock",
                "db-tester",
                "db-updater",
                null,
                now,
                now.plusSeconds(1)
        ));

        assertThat(testDataRepository.pooledAccounts(poolId))
                .singleElement()
                .satisfies(account -> {
                    assertThat(account.status()).isEqualTo("LOCKED");
                    assertThat(account.secretRefDigest()).isEqualTo("f".repeat(64));
                    assertThat(account.toString()).doesNotContain("ciphertext-should-not-be-projected");
                });
        assertThat(testDataRepository.pooledAccountProjectScopeId(accountId)).contains(projectId);

        testDataRepository.archiveAccountPool(new TestAccountPool(
                poolId,
                projectId,
                "app-db",
                "env-db",
                "pool-db",
                "DB account pool",
                "ARCHIVED",
                "{\"sharing\":\"EXCLUSIVE\"}",
                300,
                "db-tester",
                "db-archiver",
                now.plusSeconds(2),
                now,
                now.plusSeconds(2)
        ));

        assertThat(testDataRepository.accountPoolProjectScopeId(poolId)).contains(projectId);
        assertThat(testDataRepository.accountPool(poolId))
                .isPresent()
                .get()
                .extracting(TestAccountPool::status)
                .isEqualTo("ARCHIVED");

        assertThatThrownBy(() -> testDataRepository.insertPooledAccount(new TestPooledAccount(
                UUID.randomUUID(),
                poolId,
                projectId,
                "invalid account key",
                "Invalid",
                "AVAILABLE",
                "[]",
                "{}",
                "a".repeat(64),
                null,
                null,
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testDataRepositoryPersistsLeasesAndCleanupTasksThroughJdbc() {
        Instant now = Instant.now();
        UUID poolId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String projectId = "project-wp8-lease-db-" + UUID.randomUUID();

        testDataRepository.insertAccountPool(new TestAccountPool(
                poolId,
                projectId,
                "app-db",
                "env-db",
                "pool-lease-db",
                "DB lease pool",
                "READY",
                "{\"sharing\":\"EXCLUSIVE\"}",
                300,
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));
        testDataRepository.insertPooledAccount(new TestPooledAccount(
                accountId,
                poolId,
                projectId,
                "admin-lease-01",
                "Admin Lease 01",
                "AVAILABLE",
                "[\"ADMIN\",\"APPROVER\"]",
                "{\"applicationId\":\"app-db\"}",
                "a".repeat(64),
                "HEALTHY",
                "manual smoke passed",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));

        assertThat(testDataRepository.firstAvailableAccount(poolId, List.of("ADMIN")))
                .isPresent()
                .get()
                .extracting(TestPooledAccount::accountKey)
                .isEqualTo("admin-lease-01");
        assertThat(testDataRepository.markAccountLeased(accountId, "db-lease")).isTrue();
        assertThat(testDataRepository.markAccountLeased(accountId, "db-lease")).isFalse();

        assertThat(testDataRepository.insertAccountLeaseIfAbsent(new TestAccountLease(
                leaseId,
                poolId,
                accountId,
                projectId,
                "ACTIVE",
                "EXECUTION_RUN",
                "run-db-001",
                "lease-db-001",
                "d".repeat(64),
                "b".repeat(64),
                now.plusSeconds(300),
                null,
                null,
                "db-tester",
                now,
                now
        ))).isTrue();

        assertThat(testDataRepository.accountLeaseByProjectAndRequestKey(projectId, "lease-db-001"))
                .isPresent()
                .get()
                .extracting(TestAccountLease::status)
                .isEqualTo("ACTIVE");
        assertThat(testDataRepository.countAccountLeases(
                new TestAccountLeaseQuery(projectId, poolId, accountId, "ACTIVE", "run-db-001", 0, 10)
        )).isEqualTo(1);
        assertThat(testDataRepository.insertAccountLeaseIfAbsent(new TestAccountLease(
                UUID.randomUUID(),
                poolId,
                accountId,
                projectId,
                "ACTIVE",
                "EXECUTION_RUN",
                "run-db-002",
                "lease-db-002",
                "e".repeat(64),
                "c".repeat(64),
                now.plusSeconds(300),
                null,
                null,
                "db-tester",
                now,
                now
        ))).isFalse();

        TestAccountLease released = new TestAccountLease(
                leaseId,
                poolId,
                accountId,
                projectId,
                "RELEASED",
                "EXECUTION_RUN",
                "run-db-001",
                "lease-db-001",
                "d".repeat(64),
                "b".repeat(64),
                now.plusSeconds(300),
                now.plusSeconds(30),
                "run finished",
                "db-tester",
                now,
                now.plusSeconds(30)
        );
        testDataRepository.updateAccountLease(released);
        assertThat(testDataRepository.accountLeaseProjectScopeId(leaseId)).contains(projectId);
        assertThat(testDataRepository.updateAccountStatus(accountId, "AVAILABLE", "db-release")).isTrue();

        assertThat(testDataRepository.insertDataTaskIfAbsent(new TestDataTask(
                taskId,
                projectId,
                null,
                "CLEANUP",
                "PENDING",
                "cleanup-db-001",
                "lease:run-db-001",
                1,
                "{\"reason\":\"release\"}",
                null,
                null,
                "trc_db",
                "db-tester",
                null,
                null,
                now,
                now
        ))).isTrue();
        assertThat(testDataRepository.dataTaskByProjectAndRequestKey(projectId, "cleanup-db-001"))
                .isPresent()
                .get()
                .extracting(TestDataTask::taskType)
                .isEqualTo("CLEANUP");
        assertThat(testDataRepository.countDataTasks(new TestDataTaskQuery(projectId, null, "CLEANUP", "PENDING", 0, 10)))
                .isEqualTo(1);
        assertThat(testDataRepository.updateDataTaskIfRequestKeyAvailable(new TestDataTask(
                taskId,
                projectId,
                null,
                "CLEANUP",
                "FAILED",
                "cleanup-db-001",
                "lease:run-db-001",
                1,
                "{\"reason\":\"release\"}",
                "CLEANUP_FAILED",
                "adapter disabled",
                "trc_db",
                "db-tester",
                now.plusSeconds(1),
                now.plusSeconds(2),
                now,
                now.plusSeconds(2)
        ))).isTrue();
        assertThat(testDataRepository.dataTaskProjectScopeId(taskId)).contains(projectId);
        assertThat(testDataRepository.dataTask(taskId))
                .isPresent()
                .get()
                .extracting(TestDataTask::status)
                .isEqualTo("FAILED");
        assertThat(testDataRepository.retryDataTaskIfCurrentAttempt(new TestDataTask(
                taskId,
                projectId,
                null,
                "CLEANUP",
                "PENDING",
                "cleanup-db-002",
                "lease:run-db-001",
                2,
                "{\"retry\":\"manual\"}",
                null,
                null,
                "trc_db_retry",
                "db-tester",
                null,
                null,
                now,
                now.plusSeconds(3)
        ), 1)).isTrue();
        assertThat(testDataRepository.retryDataTaskIfCurrentAttempt(new TestDataTask(
                taskId,
                projectId,
                null,
                "CLEANUP",
                "PENDING",
                "cleanup-db-003",
                "lease:run-db-001",
                2,
                "{}",
                null,
                null,
                "trc_db_stale_retry",
                "db-tester",
                null,
                null,
                now,
                now.plusSeconds(4)
        ), 1)).isFalse();
        assertThat(testDataRepository.dataTask(taskId))
                .isPresent()
                .get()
                .extracting(TestDataTask::status, TestDataTask::attempt, TestDataTask::requestKey)
                .containsExactly("PENDING", 2, "cleanup-db-002");
    }

    @Test
    void executionRepositoryPersistsRunsAndNodeRunsThroughJdbc() {
        Instant now = Instant.now();
        UUID planId = UUID.randomUUID();
        UUID apiNodeId = UUID.randomUUID();
        UUID reportNodeId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String projectId = "project-wp9-db-" + UUID.randomUUID();
        String requestKey = "manual-" + UUID.randomUUID();

        executionRepository.insertPlan(new ExecutionPlan(
                planId,
                projectId,
                "DB execution plan",
                "READY",
                "staging",
                "{\"manualEnabled\":true}",
                "a".repeat(64),
                "db profile contract",
                "db-tester",
                "db-tester",
                null,
                now,
                now
        ));
        executionRepository.replacePlanNodes(planId, List.of(
                new ExecutionPlanNode(
                        apiNodeId,
                        planId,
                        "api-smoke",
                        "API_TEST",
                        "",
                        "{\"apiAutomationBundleId\":\"00000000-0000-0000-0000-000000000001\"}",
                        "FAIL_FAST",
                        180,
                        "{\"maxAttempts\":1}",
                        now,
                        now
                ),
                new ExecutionPlanNode(
                        reportNodeId,
                        planId,
                        "report",
                        "REPORT_HANDOFF",
                        "api-smoke",
                        "{\"summaryOnly\":true}",
                        "CONTINUE",
                        60,
                        "{}",
                        now,
                        now
                )
        ));

        boolean inserted = executionRepository.insertRun(new ExecutionRun(
                runId,
                planId,
                projectId,
                "QUEUED",
                "MANUAL",
                requestKey,
                null,
                1,
                "trc_dbexecutioncontract",
                "{\"nodeCount\":2,\"runnerDispatched\":false}",
                null,
                null,
                "db-tester",
                null,
                null,
                now,
                now
        ));
        ExecutionNodeRun apiNodeRun = new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                apiNodeId,
                "QUEUED",
                1,
                "WP6_API",
                null,
                null,
                null,
                "{\"dispatchReady\":false}",
                null,
                now,
                null,
                null,
                now,
                now
        );
        ExecutionNodeRun reportNodeRun = new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                reportNodeId,
                "PENDING",
                1,
                "REPORT",
                null,
                null,
                null,
                "{\"dispatchReady\":false}",
                null,
                null,
                null,
                null,
                now,
                now
        );
        executionRepository.insertNodeRuns(List.of(apiNodeRun, reportNodeRun));

        assertThat(inserted).isTrue();
        assertThat(executionRepository.runByPlanAndRequestKey(planId, requestKey))
                .get()
                .extracting(ExecutionRun::id)
                .isEqualTo(runId);
        assertThat(executionRepository.insertRun(new ExecutionRun(
                UUID.randomUUID(),
                planId,
                projectId,
                "QUEUED",
                "MANUAL",
                requestKey,
                null,
                1,
                "trc_duplicate",
                "{}",
                null,
                null,
                "db-tester",
                null,
                null,
                now,
                now
        ))).isFalse();
        assertThat(executionRepository.nodeRuns(runId))
                .extracting(ExecutionNodeRun::planNodeId)
                .containsExactly(apiNodeId, reportNodeId);
        assertThat(executionRepository.runs(new ExecutionRunQuery(projectId, planId, "QUEUED", 10, 0)))
                .singleElement()
                .extracting(ExecutionRun::id)
                .isEqualTo(runId);
        assertThat(executionRepository.countRuns(new ExecutionRunQuery(projectId, planId, "QUEUED", 10, 0)))
                .isEqualTo(1);
        assertThat(executionRepository.runProjectScopeId(runId)).contains(projectId);

        Instant retriedAt = now.plusSeconds(5);
        executionRepository.updateNodeRuns(List.of(new ExecutionNodeRun(
                apiNodeRun.id(),
                apiNodeRun.runId(),
                apiNodeRun.planNodeId(),
                "FAILED",
                apiNodeRun.attempt(),
                apiNodeRun.runnerType(),
                null,
                "EXECUTION_NODE_DISPATCH_FAILED",
                "sanitized dispatch failure",
                "{\"dispatchReady\":false,\"failed\":true}",
                null,
                apiNodeRun.queuedAt(),
                null,
                retriedAt,
                apiNodeRun.createdAt(),
                retriedAt
        )));
        executionRepository.updateRun(new ExecutionRun(
                runId,
                planId,
                projectId,
                "FAILED",
                "MANUAL",
                requestKey,
                null,
                1,
                "trc_dbexecutioncontract",
                "{\"nodeCount\":2,\"failedNodeCount\":1,\"runnerDispatched\":false}",
                "EXECUTION_NODE_DISPATCH_FAILED",
                "sanitized run failure",
                "db-tester",
                null,
                retriedAt,
                now,
                retriedAt
        ));
        executionRepository.insertNodeRuns(List.of(new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                apiNodeId,
                "QUEUED",
                2,
                "WP6_API",
                null,
                null,
                null,
                "{\"retryAttempt\":2,\"runnerDispatched\":false}",
                null,
                retriedAt,
                null,
                null,
                retriedAt,
                retriedAt
        )));
        executionRepository.updateRun(new ExecutionRun(
                runId,
                planId,
                projectId,
                "QUEUED",
                "RETRY",
                requestKey,
                null,
                2,
                "trc_dbexecutioncontract_retry",
                "{\"retryInFlight\":true,\"runnerDispatched\":false}",
                null,
                null,
                "db-tester",
                null,
                null,
                now,
                retriedAt
        ));

        assertThat(executionRepository.run(runId))
                .get()
                .extracting(ExecutionRun::status, ExecutionRun::errorCode)
                .containsExactly("QUEUED", null);
        assertThat(executionRepository.nodeRuns(runId))
                .extracting(ExecutionNodeRun::planNodeId, ExecutionNodeRun::attempt, ExecutionNodeRun::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(apiNodeId, 1, "FAILED"),
                        org.assertj.core.groups.Tuple.tuple(apiNodeId, 2, "QUEUED"),
                        org.assertj.core.groups.Tuple.tuple(reportNodeId, 1, "PENDING")
                );

        ExecutionNodeRun queuedRetryNode = executionRepository.queuedNodeRuns(10).stream()
                .filter(nodeRun -> runId.equals(nodeRun.runId()) && apiNodeId.equals(nodeRun.planNodeId()))
                .findFirst()
                .orElseThrow();
        ExecutionQueueClaim claim = new ExecutionQueueClaim(
                UUID.randomUUID(),
                queuedRetryNode.id(),
                "wp9_claim_db_" + UUID.randomUUID().toString().replace("-", ""),
                "db-worker",
                retriedAt,
                retriedAt,
                retriedAt.plusSeconds(180),
                "CLAIMED",
                retriedAt,
                retriedAt
        );
        assertThat(executionRepository.tryInsertQueueClaim(claim)).isTrue();
        assertThat(executionRepository.tryInsertQueueClaim(new ExecutionQueueClaim(
                UUID.randomUUID(),
                queuedRetryNode.id(),
                "wp9_claim_db_" + UUID.randomUUID().toString().replace("-", ""),
                "db-worker-duplicate",
                retriedAt,
                retriedAt,
                retriedAt.plusSeconds(180),
                "CLAIMED",
                retriedAt,
                retriedAt
        ))).isFalse();

        Instant runningAt = retriedAt.plusSeconds(1);
        assertThat(executionRepository.updateNodeRunIfStatus(new ExecutionNodeRun(
                queuedRetryNode.id(),
                queuedRetryNode.runId(),
                queuedRetryNode.planNodeId(),
                "RUNNING",
                queuedRetryNode.attempt(),
                queuedRetryNode.runnerType(),
                null,
                null,
                null,
                "{\"schedulerClaimCreated\":true,\"runnerDispatched\":false}",
                runningAt,
                queuedRetryNode.queuedAt(),
                runningAt,
                null,
                queuedRetryNode.createdAt(),
                runningAt
        ), "QUEUED")).isTrue();
        assertThat(executionRepository.updateNodeRunIfStatus(queuedRetryNode, "QUEUED")).isFalse();
        assertThat(executionRepository.activeQueueClaim(queuedRetryNode.id()))
                .get()
                .extracting(ExecutionQueueClaim::workerId)
                .isEqualTo("db-worker");
        assertThat(executionRepository.queueClaimByToken(claim.claimToken()))
                .get()
                .extracting(ExecutionQueueClaim::nodeRunId)
                .isEqualTo(queuedRetryNode.id());

        Instant heartbeatAt = runningAt.plusSeconds(30);
        ExecutionQueueClaim heartbeatClaim = new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                heartbeatAt,
                heartbeatAt.plusSeconds(180),
                "CLAIMED",
                claim.createdAt(),
                heartbeatAt
        );
        assertThat(executionRepository.updateQueueClaimIfStatus(heartbeatClaim, "CLAIMED")).isTrue();
        assertThat(executionRepository.updateQueueClaimIfStatus(new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                heartbeatAt,
                heartbeatAt.plusSeconds(180),
                "EXPIRED",
                claim.createdAt(),
                heartbeatAt
        ), "COMPLETED")).isFalse();

        assertThat(executionRepository.expiredQueueClaims(heartbeatAt.plusSeconds(60), 10)).isEmpty();
        assertThat(executionRepository.runningNodeRunsStartedBefore(runningAt.plusSeconds(1), 10))
                .extracting(ExecutionNodeRun::id)
                .contains(queuedRetryNode.id());

        Instant expiredAt = heartbeatAt.plusSeconds(181);
        ExecutionQueueClaim expiredClaim = new ExecutionQueueClaim(
                claim.id(),
                claim.nodeRunId(),
                claim.claimToken(),
                claim.workerId(),
                claim.claimedAt(),
                heartbeatClaim.heartbeatAt(),
                heartbeatClaim.expiresAt(),
                "EXPIRED",
                claim.createdAt(),
                expiredAt
        );
        assertThat(executionRepository.expiredQueueClaims(expiredAt, 10))
                .extracting(ExecutionQueueClaim::id)
                .contains(claim.id());
        assertThat(executionRepository.updateExpiredQueueClaim(expiredClaim, expiredAt)).isTrue();
        assertThat(executionRepository.activeQueueClaim(queuedRetryNode.id())).isEmpty();

        ExecutionQueueClaim secondClaim = new ExecutionQueueClaim(
                UUID.randomUUID(),
                queuedRetryNode.id(),
                "wp9_claim_db_" + UUID.randomUUID().toString().replace("-", ""),
                "db-worker-second",
                expiredAt.plusSeconds(1),
                expiredAt.plusSeconds(1),
                expiredAt.plusSeconds(181),
                "CLAIMED",
                expiredAt.plusSeconds(1),
                expiredAt.plusSeconds(1)
        );
        assertThat(executionRepository.tryInsertQueueClaim(secondClaim)).isTrue();

        Instant completedAt = expiredAt.plusSeconds(2);
        executionRepository.updateQueueClaim(new ExecutionQueueClaim(
                secondClaim.id(),
                secondClaim.nodeRunId(),
                secondClaim.claimToken(),
                secondClaim.workerId(),
                secondClaim.claimedAt(),
                completedAt,
                secondClaim.expiresAt(),
                "COMPLETED",
                secondClaim.createdAt(),
                completedAt
        ));
        assertThat(executionRepository.activeQueueClaim(queuedRetryNode.id())).isEmpty();
    }

    @Test
    void testDesignRepositoryPersistsIdempotencyFieldsAndLocksThroughJdbc() {
        String projectId = "project-wp5-idem-db-" + UUID.randomUUID();
        String idempotencyKey = "db-idempotency-" + UUID.randomUUID();
        String requestDigest = "a".repeat(64);
        String inputDigest = "b".repeat(64);
        String contextSummaryJson = "{\"contextVersion\":\"wp5-context-v1\"}";
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();
        AssetRequirement requirement = requirement(
                projectId,
                "REQ-WP5-REVIEW-DB",
                "WP5 评审记录 DB 合同需求",
                "SRC-WP5-REVIEW-DB",
                now.minusSeconds(1)
        );
        assetRepository.saveRequirement(requirement);

        transactionTemplate.executeWithoutResult(status -> {
            testDesignRepository.lockTaskIdempotencyKey(projectId, idempotencyKey);
            testDesignRepository.saveTask(new TestDesignTask(
                    taskId,
                    projectId,
                    "DB 幂等创建任务",
                    TestDesignTaskStatus.SUCCEEDED.name(),
                    requirement.id().toString(),
                    "SMOKE",
                    "wp5.case.generate",
                    "v1",
                    null,
                    null,
                    "RULE_TEMPLATE",
                    1,
                    1,
                    0,
                    0,
                    null,
                    "db-contract",
                    idempotencyKey,
                    requestDigest,
                    inputDigest,
                    contextSummaryJson,
                    now,
                    now
            ));
        });

        assertThat(testDesignRepository.taskByIdempotencyKey(projectId, idempotencyKey))
                .get()
                .satisfies(task -> {
                    assertThat(task.id()).isEqualTo(taskId);
                    assertThat(task.requestDigest()).isEqualTo(requestDigest);
                    assertThat(task.inputDigest()).isEqualTo(inputDigest);
                    assertThat(task.contextSummaryJson()).contains("wp5-context-v1");
                });

        UUID candidateId = UUID.randomUUID();
        testDesignRepository.saveCandidate(new TestDesignCandidate(
                candidateId,
                taskId,
                projectId,
                requirement.id(),
                null,
                "DB 候选评审记录",
                "DB contract candidate",
                "SMOKE",
                "HIGH",
                "EDITED",
                null,
                "[{\"action\":\"open\",\"expectedResult\":\"shown\"}]",
                "shown",
                "db,contract",
                "db-contract-duplicate",
                0.95D,
                "wp5.case.generate",
                "v1",
                null,
                null,
                "RULE_TEMPLATE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                2L,
                now.plusSeconds(1),
                now.plusSeconds(1)
        ));
        UUID olderReviewId = UUID.randomUUID();
        UUID newerReviewId = UUID.randomUUID();
        testDesignRepository.saveReviewRecord(new TestDesignReviewRecord(
                olderReviewId,
                candidateId,
                taskId,
                projectId,
                "UPDATE",
                "GENERATED",
                "EDITED",
                "db-reviewer",
                "db contract update",
                "{\"changedFields\":[\"title\"],\"version\":{\"before\":0,\"after\":1}}",
                now.plusSeconds(2)
        ));
        testDesignRepository.saveReviewRecord(new TestDesignReviewRecord(
                newerReviewId,
                candidateId,
                taskId,
                projectId,
                "CONFIRMED",
                "EDITED",
                "CONFIRMED",
                "db-reviewer",
                "db contract confirm",
                "{\"changedFields\":[\"status\"],\"version\":{\"before\":1,\"after\":2}}",
                now.plusSeconds(3)
        ));

        assertThat(testDesignRepository.countReviewRecords(taskId)).isEqualTo(2);
        assertThat(testDesignRepository.reviewRecords(taskId, PageQuery.of(0, 1)))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.id()).isEqualTo(newerReviewId);
                    assertThat(record.diffJson()).contains("changedFields", "status");
                });
        assertThat(testDesignRepository.reviewRecords(taskId, PageQuery.of(1, 1)))
                .singleElement()
                .extracting(TestDesignReviewRecord::id)
                .isEqualTo(olderReviewId);

        UUID queuedTaskId = UUID.randomUUID();
        UUID staleRunningTaskId = UUID.randomUUID();
        UUID freshRunningTaskId = UUID.randomUUID();
        Instant staleUpdatedAt = now.minusSeconds(120);
        testDesignRepository.saveTask(testDesignTask(
                queuedTaskId,
                projectId,
                TestDesignTaskStatus.QUEUED,
                "DB 条件认领任务",
                staleUpdatedAt
        ));
        testDesignRepository.saveTask(testDesignTask(
                staleRunningTaskId,
                projectId,
                TestDesignTaskStatus.RUNNING,
                "DB 超时运行任务",
                staleUpdatedAt
        ));
        testDesignRepository.saveTask(testDesignTask(
                freshRunningTaskId,
                projectId,
                TestDesignTaskStatus.RUNNING,
                "DB 新鲜运行任务",
                now
        ));

        assertThat(testDesignRepository.markTaskStatus(
                queuedTaskId,
                TestDesignTaskStatus.QUEUED,
                TestDesignTaskStatus.RUNNING,
                now.plusSeconds(1)
        )).isTrue();
        assertThat(testDesignRepository.markTaskStatus(
                queuedTaskId,
                TestDesignTaskStatus.QUEUED,
                TestDesignTaskStatus.RUNNING,
                now.plusSeconds(2)
        )).isFalse();

        assertThat(testDesignRepository.markStaleRunningTasksFailed(
                now.plusSeconds(3),
                now.minusSeconds(60),
                "stale recovery",
                1
        )).isEqualTo(1);
        assertThat(testDesignRepository.task(staleRunningTaskId)).get()
                .extracting(TestDesignTask::status, TestDesignTask::errorMessage)
                .containsExactly(TestDesignTaskStatus.FAILED.name(), "stale recovery");
        assertThat(testDesignRepository.task(freshRunningTaskId)).get()
                .extracting(TestDesignTask::status)
                .isEqualTo(TestDesignTaskStatus.RUNNING.name());
    }

    @Test
    void authSessionStorePersistsFindsRevokesAndCleansSessionsThroughJdbc() {
        UUID userId = createEnabledUser("session-user");
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        AuthSessionDraft draft = new AuthSessionDraft(
                sessionId,
                userId,
                "access-" + sessionId,
                "refresh-" + sessionId,
                1,
                now.plusSeconds(3600)
        );

        authSessionStore.create(draft);

        assertThat(authSessionStore.isActive(sessionId, userId, 1, now)).isTrue();
        AuthSessionRecord stored = authSessionStore.findByRefreshTokenHash(draft.refreshTokenHash()).orElseThrow();
        assertThat(stored.sessionId()).isEqualTo(sessionId);
        assertThat(stored.activeAt(now)).isTrue();

        authSessionStore.revoke(sessionId, userId, "db contract revoke");

        assertThat(authSessionStore.isActive(sessionId, userId, 1, now)).isFalse();
        assertThat(authSessionStore.findByRefreshTokenHash(draft.refreshTokenHash()).orElseThrow().revoked()).isTrue();
        assertThat(authSessionStore.cleanupExpiredSessions(now.minusSeconds(1), now.plusSeconds(3600))).isEqualTo(1);
        assertThat(authSessionStore.findByRefreshTokenHash(draft.refreshTokenHash())).isEmpty();
    }

    @Test
    void modelAccessRepositoryPersistsInvocationsAndRunsDistinctQueriesThroughJdbc() {
        String projectId = "project-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        modelAccessRepository.saveInvocation(invocation(projectId, "wp4-document-input", now.minusSeconds(20), UUID.randomUUID(), "Auditor"));
        modelAccessRepository.saveInvocation(invocation(projectId, "wp4-document-input", now.minusSeconds(10)));
        modelAccessRepository.saveInvocation(invocation(projectId + "-other", "wp5-test-design", now.minusSeconds(5)));

        InvocationQuery projectQuery = new InvocationQuery(
                projectId,
                null,
                null,
                InvocationStatus.SUCCEEDED,
                null,
                null,
                now.minusSeconds(60),
                now.plusSeconds(60),
                PageQuery.of(0, 10)
        );

        assertThat(modelAccessRepository.countInvocations(projectQuery)).isEqualTo(2);
        assertThat(modelAccessRepository.invocationSummary(projectQuery).totalCost()).isEqualByComparingTo("0.03000000");
        InvocationQuery roleQuery = new InvocationQuery(
                projectId,
                null,
                "env-db",
                null,
                InvocationStatus.SUCCEEDED,
                null,
                null,
                "Auditor",
                now.minusSeconds(60),
                now.plusSeconds(60),
                PageQuery.of(0, 10)
        );
        assertThat(modelAccessRepository.countInvocations(roleQuery)).isEqualTo(1);
        assertThat(modelAccessRepository.invocations(roleQuery))
                .singleElement()
                .satisfies(record -> assertThat(record.roleScope()).isEqualTo("Auditor"));
        assertThat(modelAccessRepository.distinctProjectIds(now.minusSeconds(60), now.plusSeconds(60)))
                .contains(projectId, projectId + "-other");
        assertThat(modelAccessRepository.distinctActorServices(now.minusSeconds(60), now.plusSeconds(60)))
                .contains("wp4-document-input", "wp5-test-design");
    }

    @Test
    void modelAccessRepositoryPersistsRuntimePolicyOverridesThroughJdbc() {
        String projectScope = "project-policy-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        ModelAccessPolicyOverride policy = new ModelAccessPolicyOverride(
                UUID.randomUUID(),
                "PROJECT",
                projectScope,
                true,
                false,
                true,
                new BigDecimal("3.00000000"),
                new BigDecimal("0.7500"),
                "BLOCK",
                "private",
                "db policy",
                "db-admin",
                now,
                now
        );

        modelAccessRepository.saveModelAccessPolicy(policy);

        assertThat(modelAccessRepository.modelAccessPolicy("PROJECT", projectScope))
                .hasValueSatisfying(stored -> {
                    assertThat(stored.modelInvocationEnabled()).isFalse();
                    assertThat(stored.publicModelAllowed()).isTrue();
                    assertThat(stored.dailyBudgetLimit()).isEqualByComparingTo("3.00000000");
                    assertThat(stored.routingGroup()).isEqualTo("private");
                });
        assertThat(modelAccessRepository.modelAccessPolicies("PROJECT", null))
                .extracting(ModelAccessPolicyOverride::scopeKey)
                .contains(projectScope);

        ModelAccessPolicyOverride updated = new ModelAccessPolicyOverride(
                policy.id(),
                policy.scopeType(),
                policy.scopeKey(),
                false,
                true,
                false,
                null,
                null,
                "FALLBACK",
                null,
                "db policy updated",
                "db-admin-2",
                policy.createdAt(),
                now.plusSeconds(1)
        );
        modelAccessRepository.saveModelAccessPolicy(updated);

        assertThat(modelAccessRepository.modelAccessPolicy("PROJECT", projectScope))
                .hasValueSatisfying(stored -> {
                    assertThat(stored.enabled()).isFalse();
                    assertThat(stored.modelInvocationEnabled()).isTrue();
                    assertThat(stored.publicModelAllowed()).isFalse();
                    assertThat(stored.dailyBudgetLimit()).isNull();
                    assertThat(stored.budgetOverrunAction()).isEqualTo("FALLBACK");
                    assertThat(stored.updatedBy()).isEqualTo("db-admin-2");
                });
    }

    @Test
    void modelInvocationJobRepositoryPersistsLifecycleAndRecoveryStateThroughJdbc() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        Instant now = Instant.now();
        String requestJson = """
                {"projectId":"project-job-db","messages":[{"role":"user","content":"db job"}],"allowPublicModel":false}
                """;
        ModelInvocationResult response = new ModelInvocationResult(
                invocationId,
                null,
                "local-echo-primary",
                "test-local-model",
                1,
                false,
                "local model response: db job",
                10,
                5,
                new BigDecimal("0.00001000")
        );

        modelInvocationJobRepository.save(new ModelInvocationJobRecord(
                jobId,
                ModelInvocationJobStatus.QUEUED,
                requestJson,
                "wp5-test-design",
                "db-user",
                "SuperAdmin,Auditor",
                "trc_db_job",
                now,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(modelInvocationJobRepository.queuedJobs())
                .extracting(ModelInvocationJobRecord::jobId)
                .contains(jobId);
        assertThat(modelInvocationJobRepository.markRunning(jobId, now.plusSeconds(1))).isTrue();
        assertThat(modelInvocationJobRepository.markRunning(jobId, now.plusSeconds(2))).isFalse();

        modelAccessRepository.saveInvocation(invocation("project-job-db", "wp5-test-design", now.plusSeconds(2), invocationId));
        modelInvocationJobRepository.markSucceeded(
                jobId,
                now.plusSeconds(3),
                response,
                objectMapper.writeValueAsString(response)
        );

        ModelInvocationJobRecord stored = modelInvocationJobRepository.job(jobId).orElseThrow();
        assertThat(stored.status()).isEqualTo(ModelInvocationJobStatus.SUCCEEDED);
        assertThat(stored.invocationId()).isEqualTo(invocationId);
        assertThat(stored.responseJson()).contains("local model response");
        assertThat(stored.principalRoles()).isEqualTo("SuperAdmin,Auditor");

        UUID runningJobId = UUID.randomUUID();
        modelInvocationJobRepository.save(new ModelInvocationJobRecord(
                runningJobId,
                ModelInvocationJobStatus.RUNNING,
                requestJson,
                "wp5-test-design",
                "db-user",
                "trc_db_running_job",
                now,
                now.plusSeconds(1),
                null,
                null,
                null,
                null,
                null
        ));
        assertThat(modelInvocationJobRepository.markRunningJobsFailed(
                now.plusSeconds(4),
                now.plusSeconds(2),
                "WORKER_RESTARTED",
                "recovered"
        )).isEqualTo(1);
        assertThat(modelInvocationJobRepository.job(runningJobId).orElseThrow().status())
                .isEqualTo(ModelInvocationJobStatus.FAILED);
    }

    @Test
    void assetRepositoryHonorsJdbcPagingConstraintsAndTransactionRollback() {
        String projectId = "project-asset-db-" + UUID.randomUUID();
        AssetRequirement first = requirement(projectId, "REQ-DB-1", "订单导入需求", "SRC-1", Instant.now().minusSeconds(10));
        AssetRequirement second = requirement(projectId, "REQ-DB-2", "订单导出需求", "SRC-2", Instant.now());

        assetRepository.saveRequirement(first);
        assetRepository.saveRequirement(second);

        AssetListQuery query = new AssetListQuery(projectId, "ACTIVE", null, "IMPORT", "订单", PageQuery.of(0, 1));
        assertThat(assetRepository.countRequirements(query)).isEqualTo(2);
        assertThat(assetRepository.requirements(query))
                .extracting(AssetRequirement::code)
                .containsExactly("REQ-DB-2");
        assertThat(assetRepository.requirementBySourceRef(projectId, "IMPORT", "SRC-1")).contains(first);
        assertThat(assetRepository.hasActiveRequirementSourceRefConflict(projectId, "IMPORT", "SRC-1", second.id())).isTrue();

        AssetRequirement rolledBack = requirement(projectId, "REQ-ROLLBACK", "回滚需求", "SRC-ROLLBACK", Instant.now());
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            assetRepository.saveRequirement(rolledBack);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(assetRepository.requirement(rolledBack.id())).isEmpty();
    }

    @Test
    void assetRepositoryFindsActiveTestCasesByRequirementThroughTraceLinks() {
        String projectId = "project-wp5-conflict-db-" + UUID.randomUUID();
        AssetRequirement requirement = requirement(projectId, "REQ-WP5-CONFLICT", "WP5 冲突需求", "SRC-WP5", Instant.now());
        assetRepository.saveRequirement(requirement);
        UUID caseId = UUID.randomUUID();
        TestCaseRecord testCase = testCase(projectId, caseId, "TC-WP5-CONFLICT", "验证WP5冲突需求核心冒烟流程");
        assetRepository.saveTestCase(testCase);
        assetRepository.saveTraceLink(new TraceLink(
                UUID.randomUUID(),
                requirement.id(),
                null,
                null,
                null,
                caseId,
                Instant.now()
        ));

        assertThat(assetRepository.testCasesByRequirement(projectId, requirement.id()))
                .extracting(TestCaseRecord::id)
                .containsExactly(caseId);
        assertThat(assetRepository.testCasesByRequirement(projectId + "-other", requirement.id())).isEmpty();
    }

    @Test
    void assetRepositoryRejectsDuplicateAiGeneratedTestCaseSourceRefThroughJdbc() {
        String projectId = "project-wp5-publish-idem-db-" + UUID.randomUUID();
        String sourceRef = "wp5:" + UUID.randomUUID();
        TestCaseRecord first = testCase(
                projectId,
                UUID.randomUUID(),
                "TC-WP5-PUBLISH-IDEM-1",
                "WP5 首次发布用例",
                sourceRef
        );
        TestCaseRecord duplicate = testCase(
                projectId,
                UUID.randomUUID(),
                "TC-WP5-PUBLISH-IDEM-2",
                "WP5 重复发布用例",
                sourceRef
        );

        assetRepository.saveTestCase(first);

        assertThatThrownBy(() -> assetRepository.saveTestCase(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assetRepository.testCaseBySourceRef(projectId, "AI_GENERATED", sourceRef))
                .get()
                .extracting(TestCaseRecord::id)
                .isEqualTo(first.id());
    }

    @Test
    void testDesignRepositoryFindsPublishCompensationCandidatesThroughJdbc() {
        String projectId = "project-wp5-publish-comp-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        AssetRequirement requirement = requirement(
                projectId,
                "REQ-WP5-PUB-COMP",
                "WP5 发布补偿 DB 需求",
                "SRC-WP5-PUB-COMP",
                now
        );
        assetRepository.saveRequirement(requirement);
        UUID taskId = UUID.randomUUID();
        testDesignRepository.saveTask(testDesignTask(
                taskId,
                projectId,
                TestDesignTaskStatus.SUCCEEDED,
                "DB 发布补偿任务",
                now
        ));
        TestCaseRecord testCase = testCase(
                projectId,
                UUID.randomUUID(),
                "TC-WP5-PUB-COMP",
                "WP5 发布补偿遗留用例",
                "wp5:" + UUID.randomUUID()
        );
        assetRepository.saveTestCase(testCase);
        UUID repairableCandidateId = UUID.randomUUID();
        TestDesignCandidate repairable = testDesignCandidate(
                repairableCandidateId,
                taskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.FAILED.name(),
                testCase.id(),
                now.minusSeconds(10)
        );
        TestDesignCandidate noAssetReference = testDesignCandidate(
                UUID.randomUUID(),
                taskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.FAILED.name(),
                null,
                now.minusSeconds(9)
        );
        TestDesignCandidate alreadySucceeded = testDesignCandidate(
                UUID.randomUUID(),
                taskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.FAILED.name(),
                testCase.id(),
                now.minusSeconds(8)
        );
        TestDesignCandidate alreadyAttempted = testDesignCandidate(
                UUID.randomUUID(),
                taskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.FAILED.name(),
                testCase.id(),
                now.minusSeconds(7)
        );
        testDesignRepository.saveCandidate(repairable);
        testDesignRepository.saveCandidate(noAssetReference);
        testDesignRepository.saveCandidate(alreadySucceeded);
        testDesignRepository.saveCandidate(alreadyAttempted);
        testDesignRepository.savePublishRecord(publishRecord(
                taskId,
                alreadySucceeded.id(),
                projectId,
                requirement.id(),
                testCase.id(),
                "RETRY_LINK_EXISTING",
                "SUCCEEDED",
                now
        ));
        testDesignRepository.savePublishRecord(publishRecord(
                taskId,
                alreadyAttempted.id(),
                projectId,
                requirement.id(),
                testCase.id(),
                "AUTO_COMPENSATE_LINK_EXISTING",
                "FAILED",
                now
        ));

        List<UUID> globalCompensationCandidates = testDesignRepository.publishCompensationCandidates(100).stream()
                .map(TestDesignCandidate::id)
                .toList();
        assertThat(globalCompensationCandidates)
                .contains(repairableCandidateId)
                .doesNotContain(noAssetReference.id(), alreadySucceeded.id(), alreadyAttempted.id());
        assertThat(testDesignRepository.publishCompensationCandidates(projectId, "wp5.case.generate", 10))
                .extracting(TestDesignCandidate::id)
                .containsExactly(repairableCandidateId);
        assertThat(testDesignRepository.countPublishCompensationCandidates(projectId, "wp5.case.generate")).isEqualTo(1);
        assertThatThrownBy(() -> testDesignRepository.savePublishRecord(publishRecord(
                taskId,
                alreadyAttempted.id(),
                projectId,
                requirement.id(),
                testCase.id(),
                "AUTO_COMPENSATE_LINK_EXISTING",
                "FAILED",
                now.plusSeconds(1)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testDesignRepositoryQueriesConflictOperationsThroughJdbc() {
        String projectId = "project-wp5-conflict-ops-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        AssetRequirement requirement = requirement(
                projectId,
                "REQ-WP5-CONFLICT-OPS",
                "WP5 冲突运营 DB 需求",
                "SRC-WP5-CONFLICT-OPS",
                now
        );
        assetRepository.saveRequirement(requirement);
        UUID taskId = UUID.randomUUID();
        testDesignRepository.saveTask(testDesignTask(
                taskId,
                projectId,
                TestDesignTaskStatus.SUCCEEDED,
                "DB 冲突运营任务",
                now
        ));
        TestCaseRecord testCase = testCase(
                projectId,
                UUID.randomUUID(),
                "TC-WP5-CONFLICT-OPS",
                "DB 冲突推荐用例",
                "manual:" + UUID.randomUUID()
        );
        assetRepository.saveTestCase(testCase);
        UUID candidateId = UUID.randomUUID();
        testDesignRepository.saveCandidate(testDesignCandidate(
                candidateId,
                taskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.CONFIRMED.name(),
                null,
                now.minusSeconds(10)
        ));
        testDesignRepository.savePublishRecord(publishRecord(
                taskId,
                candidateId,
                projectId,
                requirement.id(),
                testCase.id(),
                "DUPLICATE_REVIEW_REQUIRED",
                "CONFLICT",
                now.minusSeconds(8)
        ));

        TestDesignConflictOperationQuery openQuery = new TestDesignConflictOperationQuery(
                projectId,
                null,
                null,
                null,
                null,
                "OPEN",
                "冲突",
                PageQuery.of(0, 20)
        );

        assertThat(testDesignRepository.countConflictOperations(openQuery)).isEqualTo(1L);
        assertThat(testDesignRepository.conflictOperations(openQuery))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.candidateId()).isEqualTo(candidateId);
                    assertThat(record.taskTitle()).isEqualTo("DB 冲突运营任务");
                    assertThat(record.assetCaseId()).isEqualTo(testCase.id());
                    assertThat(record.resolved()).isFalse();
                });
        assertThat(testDesignRepository.conflictOperationSummary(openQuery.withoutResolutionStatus()))
                .satisfies(summary -> {
                    assertThat(summary.totalCount()).isEqualTo(1L);
                    assertThat(summary.openCount()).isEqualTo(1L);
                    assertThat(summary.resolvedCount()).isZero();
                    assertThat(summary.duplicateReviewCount()).isEqualTo(1L);
                });

        testDesignRepository.saveCandidate(testDesignCandidate(
                candidateId,
                taskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.PUBLISHED.name(),
                testCase.id(),
                now.minusSeconds(2)
        ));
        testDesignRepository.savePublishRecord(publishRecord(
                taskId,
                candidateId,
                projectId,
                requirement.id(),
                testCase.id(),
                "MANUAL_LINK_EXISTING",
                "SUCCEEDED",
                now.minusSeconds(1)
        ));
        TestDesignConflictOperationQuery resolvedQuery = new TestDesignConflictOperationQuery(
                projectId,
                null,
                null,
                null,
                null,
                "RESOLVED",
                null,
                PageQuery.of(0, 20)
        );

        assertThat(testDesignRepository.countConflictOperations(openQuery)).isZero();
        assertThat(testDesignRepository.conflictOperations(resolvedQuery))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.candidateId()).isEqualTo(candidateId);
                    assertThat(record.candidateStatus()).isEqualTo(TestDesignCandidateStatus.PUBLISHED.name());
                    assertThat(record.resolved()).isTrue();
                });
        assertThat(testDesignRepository.conflictOperationSummary(resolvedQuery.withoutResolutionStatus()))
                .satisfies(summary -> {
                    assertThat(summary.totalCount()).isEqualTo(1L);
                    assertThat(summary.openCount()).isZero();
                    assertThat(summary.resolvedCount()).isEqualTo(1L);
                });
    }

    @Test
    void reportingRepositoryPersistsLatestFailureDiagnosisThroughJdbc() {
        Instant now = Instant.now();
        UUID reportId = UUID.randomUUID();
        UUID executionRunId = UUID.randomUUID();
        ReportExecutionReport report = new ReportExecutionReport(
                reportId,
                "project-wp10-db-" + UUID.randomUUID(),
                executionRunId,
                "db-diagnosis-" + UUID.randomUUID(),
                "READY",
                "wp10-report-v1",
                "a".repeat(64),
                "{\"diagnosisStatus\":\"RULE_READY\"}",
                "{\"aggregateOnly\":true}",
                "db-test",
                now,
                null,
                null,
                "trc_wp10_db",
                null,
                now,
                now
        );
        ReportFailureDiagnosis first = failureDiagnosis(
                reportId,
                "ASSERTION_FAILED",
                new BigDecimal("0.7400"),
                now
        );
        ReportFailureDiagnosis replacement = failureDiagnosis(
                reportId,
                "TIMEOUT",
                new BigDecimal("0.7600"),
                "AI_FAILED",
                "REPORT_DIAGNOSIS_POLICY_BLOCKED",
                now.plusSeconds(1)
        );

        assertThat(reportingRepository.insertReportIfAbsent(report)).isTrue();
        reportingRepository.replaceLatestFailureDiagnosis(reportId, first);
        reportingRepository.replaceLatestFailureDiagnosis(reportId, replacement);

        assertThat(reportingRepository.latestFailureDiagnosis(reportId))
                .get()
                .satisfies(diagnosis -> {
                    assertThat(diagnosis.id()).isEqualTo(replacement.id());
                    assertThat(diagnosis.status()).isEqualTo("AI_FAILED");
                    assertThat(diagnosis.classificationJson()).contains("\"primaryCategory\": \"TIMEOUT\"");
                    assertThat(diagnosis.confidence()).isEqualByComparingTo("0.7600");
                    assertThat(diagnosis.manualReviewRequired()).isTrue();
                    assertThat(diagnosis.modelInvocationDigest()).isNull();
                    assertThat(diagnosis.diagnosisSummaryJson())
                            .contains("\"classificationOnly\": true")
                            .contains("\"contextDigest\": \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"")
                            .contains("\"rawPromptStored\": false");
                    assertThat(diagnosis.errorCode()).isEqualTo("REPORT_DIAGNOSIS_POLICY_BLOCKED");
                });
    }

    @Test
    void reportingRepositoryPersistsExportManifestThroughJdbc() {
        Instant now = Instant.now();
        UUID reportId = UUID.randomUUID();
        UUID executionRunId = UUID.randomUUID();
        ReportExecutionReport report = new ReportExecutionReport(
                reportId,
                "project-wp10-export-db-" + UUID.randomUUID(),
                executionRunId,
                "db-export-" + UUID.randomUUID(),
                "READY",
                "wp10-report-v1",
                "c".repeat(64),
                "{\"exportManifestCount\":0}",
                "{\"aggregateOnly\":true}",
                "db-test",
                now,
                null,
                null,
                "trc_wp10_export_db",
                null,
                now,
                now
        );
        ReportExportManifest jsonManifest = exportManifest(reportId, "JSON", "CREATED", "d".repeat(64), null, now);
        ReportExportManifest markdownManifest = exportManifest(
                reportId,
                "MARKDOWN",
                "BLOCKED",
                null,
                "REPORT_EXPORT_REDACTION_BLOCKED",
                now.plusSeconds(1)
        );

        assertThat(reportingRepository.insertReportIfAbsent(report)).isTrue();
        reportingRepository.insertExportManifest(jsonManifest);
        reportingRepository.insertExportManifest(markdownManifest);

        assertThat(reportingRepository.countExportManifests(reportId)).isEqualTo(2L);
        assertThat(reportingRepository.latestExportManifest(reportId, "MARKDOWN"))
                .get()
                .satisfies(manifest -> {
                    assertThat(manifest.id()).isEqualTo(markdownManifest.id());
                    assertThat(manifest.exportType()).isEqualTo("MARKDOWN");
                    assertThat(manifest.status()).isEqualTo("BLOCKED");
                    assertThat(manifest.contentDigest()).isNull();
                    assertThat(manifest.aggregateOnly()).isTrue();
                    assertThat(manifest.redactionPolicyJson())
                            .contains("\"aggregateOnly\": true")
                            .contains("\"contentStored\": false");
                    assertThat(manifest.blockReason()).isEqualTo("REPORT_EXPORT_REDACTION_BLOCKED");
                });
    }

    @Test
    void reportingRepositoryPersistsDefectDraftThroughJdbc() {
        Instant now = Instant.now();
        UUID reportId = UUID.randomUUID();
        UUID executionRunId = UUID.randomUUID();
        ReportExecutionReport report = new ReportExecutionReport(
                reportId,
                "project-wp10-draft-db-" + UUID.randomUUID(),
                executionRunId,
                "db-draft-" + UUID.randomUUID(),
                "READY",
                "wp10-report-v1",
                "e".repeat(64),
                "{\"defectDraftCount\":0}",
                "{\"aggregateOnly\":true}",
                "db-test",
                now,
                null,
                null,
                "trc_wp10_draft_db",
                null,
                now,
                now
        );
        ReportFailureDiagnosis diagnosis = failureDiagnosis(
                reportId,
                "ASSERTION_FAILED",
                new BigDecimal("0.7400"),
                now
        );
        ReportDefectDraft draft = defectDraft(reportId, diagnosis.id(), "DRAFT", now);
        ReportDefectDraft reviewed = new ReportDefectDraft(
                draft.id(),
                draft.reportId(),
                draft.diagnosisId(),
                "REVIEWED",
                draft.title(),
                draft.reproductionSummary(),
                draft.impactSummary(),
                draft.prioritySuggestion(),
                draft.evidenceRefsJson(),
                draft.payloadPreviewJson(),
                draft.createdBy(),
                "db-reviewer",
                draft.createdAt(),
                now.plusSeconds(1)
        );

        assertThat(reportingRepository.insertReportIfAbsent(report)).isTrue();
        reportingRepository.replaceLatestFailureDiagnosis(reportId, diagnosis);
        reportingRepository.insertDefectDraft(draft);
        reportingRepository.updateDefectDraft(reviewed);

        assertThat(reportingRepository.countDefectDrafts(reportId)).isEqualTo(1L);
        assertThat(reportingRepository.defectDrafts(reportId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(draft.id());
                    assertThat(item.diagnosisId()).isEqualTo(diagnosis.id());
                    assertThat(item.status()).isEqualTo("REVIEWED");
                    assertThat(item.prioritySuggestion()).isEqualTo("P1");
                    assertThat(item.evidenceRefsJson()).contains("wp9:execution_node");
                    assertThat(item.payloadPreviewJson())
                            .contains("\"masked\": true")
                            .contains("\"externalSystemWriteAttempted\": false")
                            .contains("\"rawEvidenceIncluded\": false");
                    assertThat(item.updatedBy()).isEqualTo("db-reviewer");
                });
        assertThat(reportingRepository.defectDraft(reportId, draft.id()))
                .get()
                .extracting(ReportDefectDraft::status)
                .isEqualTo("REVIEWED");
    }

    @Test
    void reportingRepositoryClaimsQueuedReportsAndScansStaleGeneratingThroughJdbc() {
        Instant now = Instant.now();
        UUID queuedReportId = UUID.randomUUID();
        ReportExecutionReport queued = report(
                queuedReportId,
                "project-wp10-worker-db-" + UUID.randomUUID(),
                "db-worker-queued-" + UUID.randomUUID(),
                "QUEUED",
                null,
                now.minusSeconds(30)
        );
        ReportExecutionReport staleGenerating = report(
                UUID.randomUUID(),
                queued.projectId(),
                "db-worker-stale-" + UUID.randomUUID(),
                "GENERATING",
                null,
                now.minusSeconds(90)
        );
        ReportExecutionReport freshGenerating = report(
                UUID.randomUUID(),
                queued.projectId(),
                "db-worker-fresh-" + UUID.randomUUID(),
                "GENERATING",
                null,
                now
        );

        assertThat(reportingRepository.insertReportIfAbsent(queued)).isTrue();
        assertThat(reportingRepository.insertReportIfAbsent(staleGenerating)).isTrue();
        assertThat(reportingRepository.insertReportIfAbsent(freshGenerating)).isTrue();

        assertThat(reportingRepository.queuedReports(10))
                .extracting(ReportExecutionReport::id)
                .contains(queuedReportId);
        ReportExecutionReport generating = new ReportExecutionReport(
                queued.id(),
                queued.projectId(),
                queued.executionRunId(),
                queued.requestKey(),
                "GENERATING",
                queued.schemaVersion(),
                queued.sourceRunDigest(),
                "{\"generationStatus\":\"GENERATING\"}",
                queued.redactionPolicyJson(),
                queued.generatedBy(),
                queued.generatedAt(),
                null,
                null,
                queued.traceId(),
                queued.archivedAt(),
                queued.createdAt(),
                now
        );
        assertThat(reportingRepository.updateReportIfStatus(generating, "QUEUED")).isTrue();
        assertThat(reportingRepository.updateReportIfStatus(queued, "QUEUED")).isFalse();
        assertThat(reportingRepository.report(queuedReportId))
                .get()
                .extracting(ReportExecutionReport::status)
                .isEqualTo("GENERATING");

        assertThat(reportingRepository.generatingReportsUpdatedBefore(now.minusSeconds(60), 10))
                .extracting(ReportExecutionReport::id)
                .contains(staleGenerating.id())
                .doesNotContain(freshGenerating.id());
    }

    @Test
    void testDesignRepositoryAggregatesCrossWpAuditChainThroughJdbc() {
        String projectId = "project-wp5-audit-chain-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        AssetRequirement requirement = requirement(
                projectId,
                "REQ-WP5-AUDIT-CHAIN",
                "WP5 跨 WP 审计链 DB 需求",
                "SRC-WP5-AUDIT-CHAIN",
                now
        );
        assetRepository.saveRequirement(requirement);
        UUID invocationId = UUID.randomUUID();
        modelAccessRepository.saveInvocation(invocation(projectId, "wp5-test-design", now.minusSeconds(8), invocationId));
        modelInvocationJobRepository.save(new ModelInvocationJobRecord(
                UUID.randomUUID(),
                ModelInvocationJobStatus.SUCCEEDED,
                "{\"projectId\":\"" + projectId + "\"}",
                "wp5-test-design",
                "db-user",
                "trc_wp5_audit_chain_db",
                now.minusSeconds(9),
                now.minusSeconds(8),
                now.minusSeconds(7),
                invocationId,
                null,
                null,
                "{}"
        ));
        UUID taskId = UUID.randomUUID();
        testDesignRepository.saveTask(new TestDesignTask(
                taskId,
                projectId,
                "DB 跨 WP 审计链任务",
                TestDesignTaskStatus.PUBLISHED.name(),
                requirement.id().toString(),
                "SMOKE",
                "wp5.case.generate",
                "v1",
                invocationId,
                "local-echo-primary",
                "test-local-model",
                1,
                1,
                1,
                1,
                null,
                "db-contract",
                null,
                null,
                "d".repeat(64),
                "{}",
                now.minusSeconds(10),
                now
        ));
        TestCaseRecord testCase = testCase(
                projectId,
                UUID.randomUUID(),
                "TC-WP5-AUDIT-CHAIN",
                "WP5 跨 WP 审计链发布用例",
                "wp5:" + UUID.randomUUID()
        );
        assetRepository.saveTestCase(testCase);
        UUID candidateId = UUID.randomUUID();
        testDesignRepository.saveCandidate(new TestDesignCandidate(
                candidateId,
                taskId,
                projectId,
                requirement.id(),
                null,
                "DB 跨 WP 审计链候选",
                "candidate body must not be aggregated",
                "SMOKE",
                "HIGH",
                TestDesignCandidateStatus.PUBLISHED.name(),
                "precondition",
                "[{\"action\":\"open\",\"expectedResult\":\"shown\"}]",
                "shown",
                "db,audit-chain",
                candidateId + ":SMOKE:db",
                0.95D,
                "wp5.case.generate",
                "v1",
                invocationId,
                "local-echo-primary",
                "test-local-model",
                testCase.id(),
                null,
                null,
                null,
                null,
                "db-reviewer",
                now.minusSeconds(6),
                1L,
                now.minusSeconds(7),
                now.minusSeconds(6)
        ));
        testDesignRepository.saveReviewRecord(new TestDesignReviewRecord(
                UUID.randomUUID(),
                candidateId,
                taskId,
                projectId,
                "CONFIRMED",
                "GENERATED",
                "CONFIRMED",
                "db-reviewer",
                "contains token=secret-value",
                "{}",
                now.minusSeconds(5)
        ));
        testDesignRepository.savePublishRecord(publishRecord(
                taskId,
                candidateId,
                projectId,
                requirement.id(),
                testCase.id(),
                "CREATE",
                "SUCCEEDED",
                now.minusSeconds(4)
        ));
        assetRepository.saveTraceLink(new TraceLink(
                UUID.randomUUID(),
                requirement.id(),
                null,
                null,
                null,
                testCase.id(),
                now.minusSeconds(3)
        ));
        jdbcTemplate.update("""
                insert into audit_log (actor_type, actor_service, action, resource_type, resource_id,
                    scope_type, scope_id, result, after_json)
                values ('SERVICE', 'test-design', 'CREATE', 'TEST_DESIGN_TASK', ?, 'PROJECT', null, 'SUCCESS',
                    cast(? as jsonb))
                """, taskId.toString(), "{\"taskId\":\"" + taskId + "\"}");
        jdbcTemplate.update("""
                insert into audit_log (actor_type, actor_service, action, resource_type, resource_id,
                    scope_type, scope_id, result, after_json)
                values ('SERVICE', 'test-design', 'EXPORT', 'TEST_DESIGN_TASK_REPORT', ?, 'PROJECT', null, 'SUCCESS',
                    cast(? as jsonb))
                """, UUID.randomUUID().toString(), "{\"taskId\":\"" + taskId + "\"}");
        jdbcTemplate.update("""
                insert into audit_outbox (event_payload_json, status)
                values (cast(? as jsonb), 'FAILED')
                """, "{\"record\":{\"resourceId\":\"" + taskId + "\"}}");
        UUID unrelatedOutboxResourceId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into audit_outbox (event_payload_json, status)
                values (cast(? as jsonb), 'FAILED')
                """, "{\"record\":{\"resourceId\":\"" + unrelatedOutboxResourceId + "\"}}");

        TestDesignAuditChainAggregate aggregate = testDesignRepository.auditChainAggregate(taskId);

        assertThat(aggregate.wp1AuditEventCount()).isEqualTo(2L);
        assertThat(aggregate.wp1TaskAuditEventCount()).isEqualTo(1L);
        assertThat(aggregate.wp1ReportExportAuditEventCount()).isEqualTo(1L);
        assertThat(aggregate.wp2InvocationCount()).isEqualTo(1L);
        assertThat(aggregate.wp2InvocationSucceededCount()).isEqualTo(1L);
        assertThat(aggregate.wp2TraceSignalCount()).isEqualTo(1L);
        assertThat(aggregate.wp3PublishedCaseCount()).isEqualTo(1L);
        assertThat(aggregate.wp3TraceLinkCount()).isEqualTo(1L);
        assertThat(aggregate.auditOutboxFailedCount()).isEqualTo(1L);

        TestDesignCrossWpOperationsAggregate crossWpAggregate =
                testDesignRepository.crossWpOperationsAggregate(projectId, "wp5.case.generate");
        assertThat(crossWpAggregate.taskCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.candidateCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.publishRecordCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.candidateScopeMismatchCount()).isZero();
        assertThat(crossWpAggregate.publishScopeMismatchCount()).isZero();
        assertThat(crossWpAggregate.modelInvocationReferenceCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.wp1AuditEventCount()).isEqualTo(2L);
        assertThat(crossWpAggregate.wp2InvocationCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.wp3PublishedCaseCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.auditOutboxFailedCount()).isEqualTo(1L);
        assertThat(crossWpAggregate.replayEligibleOutboxCount()).isEqualTo(1L);

        List<TestDesignModelObservationBucket> modelBuckets =
                testDesignRepository.modelObservationBuckets(projectId, "wp5.case.generate");
        assertThat(modelBuckets)
                .anySatisfy(bucket -> {
                    assertThat(bucket.dimension()).isEqualTo("STATUS");
                    assertThat(bucket.bucketKey()).isEqualTo("SUCCEEDED");
                    assertThat(bucket.invocationCount()).isEqualTo(1L);
                    assertThat(bucket.succeededCount()).isEqualTo(1L);
                    assertThat(bucket.traceSignalCount()).isEqualTo(1L);
                    assertThat(bucket.jobSignalCount()).isEqualTo(1L);
                })
                .anySatisfy(bucket -> {
                    assertThat(bucket.dimension()).isEqualTo("PROVIDER");
                    assertThat(bucket.bucketKey()).isEqualTo("local-echo-primary");
                    assertThat(bucket.totalCostText()).isNotBlank();
                });

        List<TestDesignCrossWpAuditDetailBucket> detailBuckets =
                testDesignRepository.crossWpAuditDetailBuckets(projectId, "wp5.case.generate");
        assertThat(detailBuckets)
                .anySatisfy(bucket -> {
                    assertThat(bucket.section()).isEqualTo("WP5_TASK");
                    assertThat(bucket.category()).isEqualTo("TASK_STATUS");
                    assertThat(bucket.status()).isEqualTo(TestDesignTaskStatus.PUBLISHED.name());
                    assertThat(bucket.eventCount()).isEqualTo(1L);
                })
                .anySatisfy(bucket -> {
                    assertThat(bucket.section()).isEqualTo("WP2_MODEL");
                    assertThat(bucket.category()).isEqualTo("MODEL_STATUS");
                    assertThat(bucket.status()).isEqualTo(InvocationStatus.SUCCEEDED.name());
                    assertThat(bucket.successCount()).isEqualTo(1L);
                })
                .anySatisfy(bucket -> {
                    assertThat(bucket.section()).isEqualTo("WP3_TRACE");
                    assertThat(bucket.category()).isEqualTo("TRACE_LINK");
                    assertThat(bucket.status()).isEqualTo("LINKED");
                    assertThat(bucket.successCount()).isEqualTo(1L);
                })
                .anySatisfy(bucket -> {
                    assertThat(bucket.section()).isEqualTo("WP1_OUTBOX");
                    assertThat(bucket.status()).isEqualTo("FAILED");
                    assertThat(bucket.failedCount()).isEqualTo(1L);
                });

        int requeued = testDesignRepository.requeueAuditOutbox(
                projectId,
                "FAILED",
                10,
                "db contract token=secret-value",
                "db-contract",
                now
        );
        assertThat(requeued).isEqualTo(1);
        Long scopedPending = jdbcTemplate.queryForObject("""
                select count(*)
                from audit_outbox
                where status = 'PENDING'
                  and event_payload_json #>> '{record,resourceId}' = ?
                """, Long.class, taskId.toString());
        Long unrelatedFailed = jdbcTemplate.queryForObject("""
                select count(*)
                from audit_outbox
                where status = 'FAILED'
                  and event_payload_json #>> '{record,resourceId}' = ?
                """, Long.class, unrelatedOutboxResourceId.toString());
        assertThat(scopedPending).isEqualTo(1L);
        assertThat(unrelatedFailed).isEqualTo(1L);
    }

    @Test
    void testDesignRepositoryPersistsQueueAlertOperationsThroughJdbc() {
        String projectId = "project-wp5-ops-alert-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        AssetRequirement requirement = requirement(
                projectId,
                "REQ-WP5-OPS-ALERT",
                "WP5 运营告警 DB 需求",
                "SRC-WP5-OPS-ALERT",
                now
        );
        assetRepository.saveRequirement(requirement);
        UUID queuedTaskId = UUID.randomUUID();
        testDesignRepository.saveTask(testDesignTask(
                queuedTaskId,
                projectId,
                TestDesignTaskStatus.QUEUED,
                "DB 队列告警任务",
                now.minusSeconds(180)
        ));
        UUID publishQueuedCandidateId = UUID.randomUUID();
        testDesignRepository.saveCandidate(testDesignCandidate(
                publishQueuedCandidateId,
                queuedTaskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.PUBLISH_QUEUED.name(),
                null,
                now.minusSeconds(90)
        ));
        TestCaseRecord existingCase = testCase(
                projectId,
                UUID.randomUUID(),
                "TC-WP5-OPS-ALERT",
                "WP5 运营补偿既有用例",
                "wp5:" + UUID.randomUUID()
        );
        assetRepository.saveTestCase(existingCase);
        UUID failedCandidateId = UUID.randomUUID();
        testDesignRepository.saveCandidate(testDesignCandidate(
                failedCandidateId,
                queuedTaskId,
                projectId,
                requirement.id(),
                TestDesignCandidateStatus.FAILED.name(),
                existingCase.id(),
                now.minusSeconds(60)
        ));

        assertThat(testDesignRepository.countTasksByStatus(
                projectId,
                "wp5.case.generate",
                TestDesignTaskStatus.QUEUED
        )).isEqualTo(1L);
        assertThat(testDesignRepository.oldestTaskUpdatedAtByStatus(
                projectId,
                "wp5.case.generate",
                TestDesignTaskStatus.QUEUED
        )).isPresent();
        assertThat(testDesignRepository.queuedTasksForReplay(projectId, "wp5.case.generate", 10))
                .singleElement()
                .extracting(TestDesignTask::id)
                .isEqualTo(queuedTaskId);
        assertThat(testDesignRepository.countCandidatesByStatus(
                projectId,
                "wp5.case.generate",
                TestDesignCandidateStatus.PUBLISH_QUEUED
        )).isEqualTo(1L);
        assertThat(testDesignRepository.publishQueuedCandidatesForReplay(projectId, "wp5.case.generate", 10))
                .singleElement()
                .extracting(TestDesignCandidate::id)
                .isEqualTo(publishQueuedCandidateId);
        assertThat(testDesignRepository.countPublishCompensationCandidates(projectId, "wp5.case.generate"))
                .isEqualTo(1L);
        assertThat(testDesignRepository.publishCompensationCandidates(projectId, "wp5.case.generate", 10))
                .singleElement()
                .extracting(TestDesignCandidate::id)
                .isEqualTo(failedCandidateId);

        UUID subscriptionId = UUID.randomUUID();
        TestDesignQueueAlertSubscription subscription = new TestDesignQueueAlertSubscription(
                subscriptionId,
                projectId,
                "wp5.case.generate",
                "GENERATION_QUEUE_LAG",
                "OPS_CONSOLE",
                "ops-console:wp5-db",
                120,
                true,
                "db-contract",
                "db-contract",
                now.minusSeconds(10),
                now.minusSeconds(10)
        );
        testDesignRepository.saveQueueAlertSubscription(subscription);
        assertThat(testDesignRepository.queueAlertSubscriptionByKey(
                projectId,
                "wp5.case.generate",
                "GENERATION_QUEUE_LAG",
                "OPS_CONSOLE",
                "ops-console:wp5-db"
        )).get().extracting(TestDesignQueueAlertSubscription::id).isEqualTo(subscriptionId);

        TestDesignQueueAlertSubscription disabledSubscription = new TestDesignQueueAlertSubscription(
                subscriptionId,
                projectId,
                "wp5.case.generate",
                "GENERATION_QUEUE_LAG",
                "OPS_CONSOLE",
                "ops-console:wp5-db",
                60,
                false,
                "db-contract",
                "db-reviewer",
                now.minusSeconds(10),
                now
        );
        testDesignRepository.saveQueueAlertSubscription(disabledSubscription);
        assertThat(testDesignRepository.queueAlertSubscriptions(projectId, "wp5.case.generate"))
                .singleElement()
                .satisfies(current -> {
                    assertThat(current.enabled()).isFalse();
                    assertThat(current.thresholdSeconds()).isEqualTo(60);
                });

        jdbcTemplate.update("""
                insert into audit_log (actor_type, actor_service, action, resource_type, resource_id, scope_type, result)
                values ('SERVICE', 'test-design', 'WP5_QUEUE_ALERT_SUBSCRIPTION_UPSERT',
                    'TEST_DESIGN_CROSS_WP_OPERATIONS', ?, 'PROJECT', 'SUCCESS')
                """, projectId);
        jdbcTemplate.update("""
                insert into audit_log (actor_type, actor_service, action, resource_type, resource_id, scope_type, result)
                values ('SERVICE', 'test-design', 'WP5_CROSS_WP_QUEUED_EVENT_REPLAY',
                    'TEST_DESIGN_CROSS_WP_OPERATIONS', ?, 'PROJECT', 'SUCCESS')
                """, projectId);
        jdbcTemplate.update("""
                insert into audit_log (actor_type, actor_service, action, resource_type, resource_id, scope_type, result)
                values ('SERVICE', 'test-design', 'WP5_PUBLISH_COMPENSATION_RUN',
                    'TEST_DESIGN_CROSS_WP_OPERATIONS', ?, 'PROJECT', 'FAILED')
                """, projectId);
        TestDesignOperationsAuditAggregate aggregate =
                testDesignRepository.operationsAuditAggregate(projectId, "wp5.case.generate");
        assertThat(aggregate.totalOperationCount()).isEqualTo(3L);
        assertThat(aggregate.successCount()).isEqualTo(2L);
        assertThat(aggregate.failedCount()).isEqualTo(1L);
        assertThat(aggregate.queueAlertSubscriptionMutationCount()).isEqualTo(1L);
        assertThat(aggregate.queuedEventReplayCount()).isEqualTo(1L);
        assertThat(aggregate.publishCompensationRunCount()).isEqualTo(1L);
    }

    @Test
    void auditLogWriterCommitsInIndependentTransactionWhenBusinessTransactionRollsBack() {
        String action = "db-contract-independent-audit-" + UUID.randomUUID();
        String resourceId = "audit-resource-" + UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            auditLogWriter.record(AuditLogWriter.success(
                    null,
                    action,
                    "db_contract",
                    resourceId,
                    "Independent audit contract"
            ));
            throw new IllegalStateException("force business rollback");
        })).isInstanceOf(IllegalStateException.class);

        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from audit_log
                where action = ?
                  and resource_type = 'db_contract'
                  and resource_id = ?
                  and result = 'SUCCESS'
                """, Long.class, action, resourceId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void testDesignRepositoryPersistsContextPolicyOverridesThroughJdbc() {
        String projectId = "project-wp5-policy-db-" + UUID.randomUUID();
        Instant now = Instant.now();
        UUID projectOverrideId = UUID.randomUUID();
        TestDesignContextPolicyOverride pendingProjectOverride = new TestDesignContextPolicyOverride(
                projectOverrideId,
                "PROJECT",
                projectId,
                null,
                "PENDING",
                7,
                null,
                null,
                null,
                null,
                null,
                "QUALITY_BASELINE",
                null,
                "WP5-CTX-DB-1",
                "DB project policy",
                "https://ticket.example/wp5/db-1",
                "OPEN",
                "linkedAssetsPerRequirement=7",
                "a524bf67490dce4fa9c8443af24ba5c39e7d24cc934c9133f126d88a7d6720aa",
                1,
                "raise linked assets",
                "request db note",
                null,
                "db-requester",
                null,
                null,
                now.minusSeconds(5),
                now.minusSeconds(5)
        );

        testDesignRepository.saveContextPolicyOverride(pendingProjectOverride);

        assertThat(testDesignRepository.contextPolicyOverride(projectOverrideId))
                .get()
                .satisfies(override -> {
                    assertThat(override.status()).isEqualTo("PENDING");
                    assertThat(override.contextLinkedAssetsPerRequirement()).isEqualTo(7);
                    assertThat(override.changeReasonCode()).isEqualTo("QUALITY_BASELINE");
                    assertThat(override.approvalReasonCode()).isNull();
                    assertThat(override.workOrderKey()).isEqualTo("WP5-CTX-DB-1");
                    assertThat(override.policyBodyVersion()).isEqualTo(1);
                });
        assertThat(testDesignRepository.contextPolicyOverrides(projectId, null))
                .singleElement()
                .extracting(TestDesignContextPolicyOverride::id)
                .isEqualTo(projectOverrideId);

        TestDesignContextPolicyOverride approvedProjectOverride = new TestDesignContextPolicyOverride(
                projectOverrideId,
                "PROJECT",
                projectId,
                null,
                "APPROVED",
                7,
                null,
                null,
                null,
                null,
                null,
                "QUALITY_BASELINE",
                "QUALITY_BASELINE",
                "WP5-CTX-DB-1",
                "DB project policy",
                "https://ticket.example/wp5/db-1",
                "APPROVED",
                "linkedAssetsPerRequirement=7",
                "a524bf67490dce4fa9c8443af24ba5c39e7d24cc934c9133f126d88a7d6720aa",
                1,
                "raise linked assets",
                "request db note",
                "approved db note",
                "db-requester",
                "db-approver",
                now,
                now.minusSeconds(5),
                now
        );
        testDesignRepository.saveContextPolicyOverride(approvedProjectOverride);

        assertThat(testDesignRepository.latestApprovedProjectContextPolicyOverride(projectId))
                .get()
                .satisfies(override -> {
                    assertThat(override.id()).isEqualTo(projectOverrideId);
                    assertThat(override.status()).isEqualTo("APPROVED");
                    assertThat(override.approvalReasonCode()).isEqualTo("QUALITY_BASELINE");
                    assertThat(override.reviewNote()).isEqualTo("approved db note");
                });

        UUID environmentOverrideId = UUID.randomUUID();
        TestDesignContextPolicyOverride approvedEnvironmentOverride = new TestDesignContextPolicyOverride(
                environmentOverrideId,
                "ENVIRONMENT",
                projectId,
                "qa",
                "APPROVED",
                null,
                null,
                null,
                null,
                null,
                90,
                "PROJECT_COMPLEXITY",
                "PROJECT_COMPLEXITY",
                "WP5-CTX-DB-2",
                "DB environment policy",
                null,
                "APPROVED",
                "linkedAssetSchemaChars=90",
                "45f09b4fe039f01c46d236875558bfda635821fbd4eadc3bd59417f2d64bf0d3",
                1,
                "lower schema chars",
                null,
                "approved qa note",
                "db-requester",
                "db-approver",
                now.plusSeconds(1),
                now.plusSeconds(1),
                now.plusSeconds(1)
        );
        testDesignRepository.saveContextPolicyOverride(approvedEnvironmentOverride);

        assertThat(testDesignRepository.contextPolicyOverrides(projectId, "qa"))
                .extracting(TestDesignContextPolicyOverride::id)
                .containsExactly(environmentOverrideId, projectOverrideId);
        assertThat(testDesignRepository.latestApprovedEnvironmentContextPolicyOverride(projectId, "qa"))
                .get()
                .satisfies(override -> {
                    assertThat(override.id()).isEqualTo(environmentOverrideId);
                    assertThat(override.contextAssetSchemaChars()).isEqualTo(90);
                    assertThat(override.changeReasonCode()).isEqualTo("PROJECT_COMPLEXITY");
                });
    }

    private UUID createEnabledUser(String namePrefix) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into iam_user (id, username, password_hash, display_name, email, status, auth_version)
                values (?, ?, ?, ?, ?, 'ENABLED', 1)
                """,
                userId,
                namePrefix + "-" + userId,
                "{bcrypt}$2a$10$abcdefghijklmnopqrstuv",
                "DB Contract User",
                namePrefix + "-" + userId + "@example.com");
        return userId;
    }

    private InvocationRecord invocation(String projectId, String actorService, Instant createdAt) {
        return invocation(projectId, actorService, createdAt, UUID.randomUUID());
    }

    private InvocationRecord invocation(String projectId, String actorService, Instant createdAt, UUID invocationId) {
        return invocation(projectId, actorService, createdAt, invocationId, null);
    }

    private InvocationRecord invocation(
            String projectId,
            String actorService,
            Instant createdAt,
            UUID invocationId,
            String roleScope
    ) {
        return new InvocationRecord(
                invocationId,
                projectId,
                "app-db",
                "env-db",
                "INTERNAL",
                "test-case-design",
                1,
                null,
                "local-echo-primary",
                "test-local-model",
                "db-contract-route",
                "default",
                "CHAT",
                InvocationStatus.SUCCEEDED,
                false,
                "digest-" + UUID.randomUUID().toString().replace("-", ""),
                "masked request",
                "masked response",
                100,
                50,
                new BigDecimal("0.01500000"),
                null,
                null,
                25,
                roleScope,
                actorService,
                "db-test-user",
                createdAt
        );
    }

    private TestDesignTask testDesignTask(
            UUID id,
            String projectId,
            TestDesignTaskStatus status,
            String title,
            Instant updatedAt
    ) {
        return new TestDesignTask(
                id,
                projectId,
                title,
                status.name(),
                UUID.randomUUID().toString(),
                "SMOKE",
                "wp5.case.generate",
                "v1",
                null,
                null,
                "RULE_TEMPLATE",
                1,
                0,
                0,
                0,
                null,
                "db-contract",
                null,
                null,
                "c".repeat(64),
                "{}",
                updatedAt.minusSeconds(5),
                updatedAt
        );
    }

    private static TestDesignCandidate testDesignCandidate(
            UUID id,
            UUID taskId,
            String projectId,
            UUID requirementId,
            String status,
            UUID assetCaseId,
            Instant updatedAt
    ) {
        return new TestDesignCandidate(
                id,
                taskId,
                projectId,
                requirementId,
                null,
                "DB 发布补偿候选",
                "DB publish compensation candidate",
                "SMOKE",
                "HIGH",
                status,
                "precondition",
                "[{\"action\":\"open\",\"expectedResult\":\"shown\"}]",
                "shown",
                "db,compensation",
                id + ":SMOKE:db",
                0.95D,
                "wp5.case.generate",
                "v1",
                null,
                null,
                "RULE_TEMPLATE",
                assetCaseId,
                null,
                null,
                null,
                status.equals(TestDesignCandidateStatus.FAILED.name()) ? "publish failed" : null,
                "db-reviewer",
                updatedAt.minusSeconds(5),
                2L,
                updatedAt.minusSeconds(6),
                updatedAt
        );
    }

    private static TestDesignPublishRecord publishRecord(
            UUID taskId,
            UUID candidateId,
            String projectId,
            UUID requirementId,
            UUID assetCaseId,
            String action,
            String result,
            Instant createdAt
    ) {
        return new TestDesignPublishRecord(
                UUID.randomUUID(),
                taskId,
                candidateId,
                projectId,
                requirementId,
                assetCaseId,
                false,
                action,
                result,
                null,
                "db-publisher",
                createdAt
        );
    }

    private static ReportExecutionReport report(
            UUID reportId,
            String projectId,
            String requestKey,
            String status,
            String sourceRunDigest,
            Instant now
    ) {
        return new ReportExecutionReport(
                reportId,
                projectId,
                UUID.randomUUID(),
                requestKey,
                status,
                "wp10-report-v1",
                sourceRunDigest,
                "{\"generationStatus\":\"" + status + "\"}",
                "{\"aggregateOnly\":true}",
                "db-test",
                "READY".equals(status) ? now : null,
                null,
                null,
                "trc_wp10_worker_db",
                null,
                now,
                now
        );
    }

    private static ReportFailureDiagnosis failureDiagnosis(
            UUID reportId,
            String primaryCategory,
            BigDecimal confidence,
            Instant now
    ) {
        return failureDiagnosis(reportId, primaryCategory, confidence, "RULE_READY", primaryCategory, now);
    }

    private static ReportFailureDiagnosis failureDiagnosis(
            UUID reportId,
            String primaryCategory,
            BigDecimal confidence,
            String status,
            String errorCode,
            Instant now
    ) {
        return new ReportFailureDiagnosis(
                UUID.randomUUID(),
                reportId,
                status,
                """
                        {"primaryCategory": "%s", "ruleVersion": "wp10-failure-classifier-v1"}
                        """.formatted(primaryCategory).trim(),
                null,
                confidence,
                true,
                """
                        {
                          "classificationOnly": true,
                          "rootCauseCandidates": [],
                          "diagnosisContext": {
                            "contextDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                            "contextStored": false,
                            "rawPromptStored": false
                          }
                        }
                        """.trim(),
                errorCode,
                now,
                now
        );
    }

    private static ReportExportManifest exportManifest(
            UUID reportId,
            String exportType,
            String status,
            String contentDigest,
            String blockReason,
            Instant now
    ) {
        return new ReportExportManifest(
                UUID.randomUUID(),
                reportId,
                exportType,
                status,
                "wp10-report-v1",
                "wp10-report-export-fields-v1",
                """
                        {"aggregateOnly": true, "contentStored": false}
                        """.trim(),
                contentDigest,
                true,
                "db-exporter",
                now,
                blockReason,
                now
        );
    }

    private static ReportDefectDraft defectDraft(
            UUID reportId,
            UUID diagnosisId,
            String status,
            Instant now
    ) {
        return new ReportDefectDraft(
                UUID.randomUUID(),
                reportId,
                diagnosisId,
                status,
                "[ASSERTION_FAILED] WP10 report requires review",
                "runStatus=FAILED; sourceRunDigest=" + "e".repeat(64),
                "failureBucketCounts={ASSERTION_FAILED=1}; nodeStatusCounts={FAILED=1}",
                "P1",
                "[\"wp9:execution_node:" + "f".repeat(64) + "\"]",
                """
                        {
                          "schemaVersion": "wp10-defect-preview-v1",
                          "masked": true,
                          "aggregateOnly": true,
                          "externalSystemWriteAttempted": false,
                          "redactionPolicy": {
                            "payloadPreviewMasked": true,
                            "rawEvidenceIncluded": false,
                            "rawPromptStored": false,
                            "rawResponseStored": false
                          }
                        }
                        """.trim(),
                "db-creator",
                "db-creator",
                now,
                now
        );
    }

    private AssetRequirement requirement(
            String projectId,
            String code,
            String title,
            String sourceRef,
            Instant createdAt
    ) {
        return new AssetRequirement(
                UUID.randomUUID(),
                code,
                title,
                "db profile contract requirement",
                "IMPORT",
                sourceRef,
                null,
                "must persist through JDBC mapper",
                "DRAFT",
                "HIGH",
                projectId,
                "db,contract",
                1,
                "ACTIVE",
                null,
                null,
                createdAt,
                createdAt
        );
    }

    private TestCaseRecord testCase(String projectId, UUID caseId, String code, String title) {
        return testCase(projectId, caseId, code, title, "wp5:" + caseId);
    }

    private TestCaseRecord testCase(String projectId, UUID caseId, String code, String title, String sourceRef) {
        Instant now = Instant.now();
        return new TestCaseRecord(
                caseId,
                code,
                title,
                "db profile contract test case",
                projectId,
                null,
                null,
                "AI_GENERATED",
                sourceRef,
                "DRAFT",
                "HIGH",
                "wp5,db-contract",
                List.of(new TestCaseStep(
                        UUID.randomUUID(),
                        caseId,
                        0,
                        "执行核心流程",
                        "核心流程通过"
                )),
                AssetVersion.initial(),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
    }
}
