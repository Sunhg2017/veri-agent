package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.testdata.application.command.AcquireTestAccountLeaseCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataSetCommand;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.application.view.TestDataWorkerTickResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDataWorkerServiceTest {

    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";

    @Test
    void runOnceProcessesPendingTasksRecoversExpiredLeaseAndRunsHealthCheck() {
        Fixture fixture = fixture(true);
        var pool = fixture.poolService().createAccountPool(new CreateTestAccountPoolCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "pool-alpha",
                "Pool alpha",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                60
        ));
        var account = fixture.poolService().addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of("applicationId", "app-alpha"),
                SECRET_REF,
                "HEALTHY",
                "manual smoke passed"
        ));
        var lease = fixture.leaseService().acquireLease(new AcquireTestAccountLeaseCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                pool.id(),
                List.of("ADMIN"),
                "EXECUTION_RUN",
                "run-001",
                60,
                "lease-run-001"
        ));
        TestAccountLease expiredLease = new TestAccountLease(
                lease.id(),
                lease.poolId(),
                lease.accountId(),
                lease.projectId(),
                "ACTIVE",
                lease.holderType(),
                lease.holderRef(),
                lease.requestKey(),
                "d".repeat(64),
                lease.leaseTokenDigest(),
                Instant.now().minusSeconds(30),
                null,
                null,
                "wp8-tester",
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(30)
        );
        fixture.repository().updateAccountLease(expiredLease);

        var dataSet = fixture.dataSetService().createDataSet(new CreateTestDataSetCommand(
                "project-alpha",
                "app-alpha",
                "env-staging",
                "dataset-alpha",
                "Dataset alpha",
                "READY",
                Map.of("fields", List.of(Map.of("name", "customerId", "type", "STRING"))),
                "INTERNAL",
                Map.of("mode", "MANUAL_CONFIRM"),
                "MANUAL",
                "b".repeat(64)
        ));
        fixture.taskService().createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                dataSet.id(),
                "PREPARE",
                "prepare-worker-001",
                "data-set:" + dataSet.code(),
                Map.of("reason", "worker prepare")
        ));
        fixture.taskService().createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-worker-001",
                "lease:run-001",
                Map.of("reason", "worker cleanup")
        ));

        TestDataWorkerTickResponse tick = fixture.workerService().runOnce();

        assertThat(tick.workerEnabled()).isTrue();
        assertThat(tick.workerId()).isEqualTo("wp8-test-worker");
        assertThat(tick.recoveredExpiredLeaseCount()).isEqualTo(1);
        assertThat(tick.claimedTaskCount()).isEqualTo(2);
        assertThat(tick.succeededTaskCount()).isEqualTo(1);
        assertThat(tick.failedTaskCount()).isEqualTo(1);
        assertThat(tick.lockedAccountCount()).isEqualTo(1);
        assertThat(tick.updatedAccountCount()).isEqualTo(1);
        assertThat(tick.noop()).isFalse();
        assertThat(fixture.repository().accountLease(lease.id())).get()
                .extracting(TestAccountLease::status)
                .isEqualTo("EXPIRED");
        assertThat(fixture.repository().pooledAccount(account.id())).get()
                .extracting(com.songhg.veri.agent.testdata.domain.TestPooledAccount::status)
                .isEqualTo("LOCKED");
        assertThat(fixture.repository().dataTaskByProjectAndRequestKey("project-alpha", "prepare-worker-001")).get()
                .extracting(com.songhg.veri.agent.testdata.domain.TestDataTask::status)
                .isEqualTo("SUCCEEDED");
        assertThat(fixture.repository().dataTaskByProjectAndRequestKey("project-alpha", "cleanup-worker-001")).get()
                .extracting(com.songhg.veri.agent.testdata.domain.TestDataTask::status)
                .isEqualTo("FAILED");
    }

    private Fixture fixture(boolean workerEnabled) {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        when(contextClient.projectContext("project-alpha")).thenReturn(new PlatformContext(
                "PROJECT",
                "project-alpha",
                "ACTIVE",
                "INTERNAL",
                false,
                List.of("apps", "environments", "configs"),
                Instant.now()
        ));
        doNothing().when(contextClient).writeAuditEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-tester");
        TestDataProperties properties = new TestDataProperties(
                true,
                workerEnabled,
                3_600_000,
                3_600_000,
                "wp8-test-worker",
                10,
                10,
                10,
                10,
                512,
                60,
                120,
                false,
                true
        );
        ObjectMapper objectMapper = new ObjectMapper();
        TestAccountPoolService poolService = new TestAccountPoolService(
                repository,
                contextClient,
                actorResolver,
                properties,
                objectMapper
        );
        TestAccountLeaseService leaseService = new TestAccountLeaseService(
                repository,
                contextClient,
                actorResolver,
                properties,
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
        TestAccountHealthCheckService healthCheckService = new TestAccountHealthCheckService(repository, contextClient);
        TestDataWorkerService workerService = new TestDataWorkerService(
                taskService,
                leaseService,
                healthCheckService,
                properties
        );
        return new Fixture(repository, poolService, leaseService, dataSetService, taskService, workerService);
    }

    private record Fixture(
            InMemoryTestDataRepository repository,
            TestAccountPoolService poolService,
            TestAccountLeaseService leaseService,
            TestDataSetService dataSetService,
            TestDataTaskService taskService,
            TestDataWorkerService workerService
    ) {
    }
}
