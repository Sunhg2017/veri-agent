package com.songhg.veri.agent.testdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.testdata.application.command.CreateTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.command.RetryTestDataTaskCommand;
import com.songhg.veri.agent.testdata.application.query.TestDataTaskPageRequest;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.domain.TestDataTask;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDataTaskServiceTest {

    @Test
    void createsCleanupTaskIdempotentlyWithoutRunningDestructiveCleanup() {
        TestDataTaskService service = service(true);

        var task = service.createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-run-001",
                "lease:run-001",
                Map.of("reason", "release")
        ));

        assertThat(task.status()).isEqualTo("PENDING");
        assertThat(task.policy()).containsEntry("destructiveCleanupTriggered", false);
        assertThat(task.resultSummary()).containsEntry("reason", "release");

        var duplicate = service.createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-run-001",
                "lease:run-001",
                Map.of("reason", "release")
        ));
        assertThat(duplicate.id()).isEqualTo(task.id());
        assertThat(service.tasks(taskListRequest()).total()).isEqualTo(1);

        assertThatThrownBy(() -> service.createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "ROLLBACK",
                "cleanup-run-001",
                "lease:run-001",
                Map.of()
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void rejectsRetryWhenTaskIsNotFailedOrCanceled() {
        TestDataTaskService service = service(true);
        var task = service.createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "ROLLBACK",
                "rollback-001",
                null,
                Map.of()
        ));

        assertThatThrownBy(() -> service.retryTask(task.id(), new RetryTestDataTaskCommand(null, Map.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void rejectsMissingRequestKeyAndSensitiveResultSummary() {
        TestDataTaskService service = service(true);

        assertThatThrownBy(() -> service.createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                null,
                "lease:run-001",
                Map.of()
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> service.createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-sensitive-001",
                "lease:run-001",
                Map.of("token", "secret-value")
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECRET_POLICY_VIOLATION));
    }

    @Test
    void retriesFailedTaskByResettingStatusAndAttempt() {
        ServiceFixture fixture = fixture(true);
        var task = fixture.service().createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-retry-001",
                "lease:retry-001",
                Map.of("reason", "cleanup failed")
        ));
        Instant failedAt = Instant.now();
        fixture.repository().updateDataTaskIfRequestKeyAvailable(new TestDataTask(
                task.id(),
                task.projectId(),
                task.dataSetId(),
                task.taskType(),
                "FAILED",
                task.requestKey(),
                task.targetRef(),
                task.attempt(),
                "{\"error\":\"timeout\"}",
                "CLEANUP_TIMEOUT",
                "cleanup adapter timeout",
                task.traceId(),
                "wp8-tester",
                failedAt.minusSeconds(30),
                failedAt,
                task.createdAt(),
                failedAt
        ));

        var retried = fixture.service().retryTask(task.id(), new RetryTestDataTaskCommand(
                "cleanup-retry-002",
                Map.of("retryReason", "manual confirmation")
        ));

        assertThat(retried.status()).isEqualTo("PENDING");
        assertThat(retried.attempt()).isEqualTo(2);
        assertThat(retried.requestKey()).isEqualTo("cleanup-retry-002");
        assertThat(retried.errorCode()).isNull();
        assertThat(retried.errorSummary()).isNull();
        assertThat(retried.startedAt()).isNull();
        assertThat(retried.finishedAt()).isNull();
        assertThat(retried.policy()).containsEntry("destructiveCleanupTriggered", false);
        assertThat(retried.resultSummary()).containsEntry("retryReason", "manual confirmation");

        assertThatThrownBy(() -> fixture.service().retryTask(task.id(), new RetryTestDataTaskCommand(
                "cleanup-retry-003",
                Map.of()
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void rejectsRetryWhenRequestKeyConflictsWithAnotherTask() {
        ServiceFixture fixture = fixture(true);
        fixture.service().createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-conflict-owner",
                "lease:owner",
                Map.of()
        ));
        var failed = fixture.service().createTask(new CreateTestDataTaskCommand(
                "project-alpha",
                null,
                "CLEANUP",
                "cleanup-conflict-retry",
                "lease:retry",
                Map.of()
        ));
        Instant failedAt = Instant.now();
        fixture.repository().updateDataTaskIfRequestKeyAvailable(new TestDataTask(
                failed.id(),
                failed.projectId(),
                failed.dataSetId(),
                failed.taskType(),
                "FAILED",
                failed.requestKey(),
                failed.targetRef(),
                failed.attempt(),
                "{}",
                "CLEANUP_FAILED",
                "cleanup failed",
                failed.traceId(),
                "wp8-tester",
                failedAt.minusSeconds(5),
                failedAt,
                failed.createdAt(),
                failedAt
        ));

        assertThatThrownBy(() -> fixture.service().retryTask(
                failed.id(),
                new RetryTestDataTaskCommand("cleanup-conflict-owner", Map.of())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void rejectsBusinessApisWhenControlPlaneDisabled() {
        TestDataTaskService service = service(false);

        assertThatThrownBy(() -> service.tasks(new TestDataTaskPageRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    private TestDataTaskService service(boolean enabled) {
        return fixture(enabled).service();
    }

    private ServiceFixture fixture(boolean enabled) {
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
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp8-tester");
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        return new ServiceFixture(new TestDataTaskService(
                repository,
                contextClient,
                actorResolver,
                new TestDataProperties(enabled, 10, 512, 60, 120, false, true),
                new ObjectMapper()
        ), repository);
    }

    private TestDataTaskPageRequest taskListRequest() {
        TestDataTaskPageRequest request = new TestDataTaskPageRequest();
        request.setProjectId("project-alpha");
        return request;
    }

    private record ServiceFixture(TestDataTaskService service, InMemoryTestDataRepository repository) {
    }
}
