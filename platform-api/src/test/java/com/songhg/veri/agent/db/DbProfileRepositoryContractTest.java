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
