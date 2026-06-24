package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.execution.application.view.ExecutionHealthResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.scheduling.config.XxlJobProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionHealthServiceTest {

    @Test
    void marksSchedulerAndCronRuntimeReadyOnlyWhenXxlJobCarrierIsEnabled() {
        ExecutionHealthService service = new ExecutionHealthService(
                new ExecutionProperties(
                        true,
                        false,
                        true,
                        300,
                        60,
                        5_000,
                        30_000,
                        "wp9-active-01",
                        4,
                        2,
                        4,
                        180,
                        1_800,
                        50
                ),
                new XxlJobProperties(
                        true,
                        "http://xxl-job-admin:8080/xxl-job-admin",
                        "shared-token",
                        3,
                        new XxlJobProperties.Executor(
                                "platform-api",
                                null,
                                null,
                                0,
                                null,
                                30
                        )
                )
        );

        ExecutionHealthResponse response = service.health();

        assertThat(response.schedulerEnabled()).isTrue();
        assertThat(response.cronEnabled()).isTrue();
        assertThat(response.schedulerLeaderLockEnabled()).isTrue();
        assertThat(response.schedulerLeaderLockName()).isEqualTo("wp9:execution:scheduler:leader");
        assertThat(response.schedulerLeaderLockProvider()).isEqualTo("LOCAL_JVM");
        assertThat(response.schedulerLeaderLockDistributed()).isFalse();
        assertThat(response.schedulerLeaderLockReady()).isTrue();
        assertThat(response.policy())
                .containsEntry("schedulerManagedByXxlJob", true)
                .containsEntry("schedulerRuntimeReady", true)
                .containsEntry("cronRuntimeReady", true)
                .containsEntry("schedulerLeaderLockEnabled", true)
                .containsEntry("schedulerLeaderLockProvider", "LOCAL_JVM")
                .containsEntry("schedulerLeaderLockDistributed", false)
                .containsEntry("schedulerLeaderLockReady", true)
                .containsEntry("schedulerMultiActiveReady", false);
    }

    @Test
    void keepsCronRuntimeNotReadyWhenCarrierIsMissingEvenIfCronFlagIsEnabled() {
        ExecutionHealthService service = new ExecutionHealthService(
                new ExecutionProperties(
                        true,
                        false,
                        true,
                        300,
                        60,
                        5_000,
                        30_000,
                        "wp9-active-01",
                        4,
                        2,
                        4,
                        180,
                        1_800,
                        50
                ),
                new XxlJobProperties(
                        false,
                        null,
                        null,
                        3,
                        new XxlJobProperties.Executor(
                                "platform-api",
                                null,
                                null,
                                0,
                                null,
                                30
                        )
                )
        );

        ExecutionHealthResponse response = service.health();

        assertThat(response.policy())
                .containsEntry("schedulerManagedByXxlJob", false)
                .containsEntry("schedulerRuntimeReady", false)
                .containsEntry("cronRuntimeReady", false)
                .containsEntry("schedulerLeaderLockEnabled", true)
                .containsEntry("schedulerLeaderLockProvider", "LOCAL_JVM")
                .containsEntry("schedulerMultiActiveReady", false);
    }
}
