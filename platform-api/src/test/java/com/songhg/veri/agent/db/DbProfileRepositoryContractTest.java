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
import com.songhg.veri.agent.modelaccess.infrastructure.JdbcModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.JdbcModelAccessRepository;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
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
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        transactionTemplate.executeWithoutResult(status -> {
            testDesignRepository.lockTaskIdempotencyKey(projectId, idempotencyKey);
            testDesignRepository.saveTask(new TestDesignTask(
                    taskId,
                    projectId,
                    "DB 幂等创建任务",
                    TestDesignTaskStatus.SUCCEEDED.name(),
                    UUID.randomUUID().toString(),
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
                    now,
                    now
            ));
        });

        assertThat(testDesignRepository.taskByIdempotencyKey(projectId, idempotencyKey))
                .get()
                .satisfies(task -> {
                    assertThat(task.id()).isEqualTo(taskId);
                    assertThat(task.requestDigest()).isEqualTo(requestDigest);
                });
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
        modelAccessRepository.saveInvocation(invocation(projectId, "wp4-document-input", now.minusSeconds(20)));
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
        assertThat(modelAccessRepository.distinctProjectIds(now.minusSeconds(60), now.plusSeconds(60)))
                .contains(projectId, projectId + "-other");
        assertThat(modelAccessRepository.distinctActorServices(now.minusSeconds(60), now.plusSeconds(60)))
                .contains("wp4-document-input", "wp5-test-design");
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
                actorService,
                "db-test-user",
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
                "wp5:" + caseId,
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
