package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.scheduling.config.XxlJobProperties;
import com.songhg.veri.agent.testdata.application.port.TestAccountProvisioningAdapter;
import com.songhg.veri.agent.testdata.application.port.TestDataCleanupAdapter;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataHealthServiceTest {

    @Test
    void marksCleanupAndProvisioningReadyOnlyWhenXxlJobAndHttpAdaptersAreReady() {
        TestDataHealthService service = new TestDataHealthService(
                properties(true, "HTTP", "http://cleanup.example.test/cleanup", true, "HTTP",
                        "http://provision.example.test/accounts"),
                readyCleanupAdapter("HTTP"),
                readyProvisioningAdapter("HTTP"),
                xxlJobProperties(true)
        );

        Map<String, Object> policy = service.health().policy();

        assertThat(policy)
                .containsEntry("workerManagedByXxlJob", true)
                .containsEntry("workerRuntimeReady", true)
                .containsEntry("cleanupWorkerReady", true)
                .containsEntry("cleanupExecutionReady", true)
                .containsEntry("destructiveCleanupAdapterReady", true)
                .containsEntry("automaticBusinessAccountProvisioningReady", true)
                .containsEntry("accountProvisioningRealAdapterRequired", true)
                .containsEntry("accountProvisioningLocalSecretRefMode", false)
                .containsEntry("accountProvisioningAdapterProvider", "HTTP");
    }

    @Test
    void localSecretRefModeDoesNotReportRealBusinessProvisioningReady() {
        TestDataHealthService service = new TestDataHealthService(
                properties(false, "DISABLED", "", true, "LOCAL_SECRET_REF", ""),
                TestDataCleanupAdapter.disabled(),
                readyProvisioningAdapter("LOCAL_SECRET_REF"),
                xxlJobProperties(true)
        );

        Map<String, Object> policy = service.health().policy();

        assertThat(policy)
                .containsEntry("workerRuntimeReady", true)
                .containsEntry("cleanupExecutionReady", false)
                .containsEntry("automaticBusinessAccountProvisioningReady", false)
                .containsEntry("accountProvisioningLocalSecretRefMode", true)
                .containsEntry("accountProvisioningAdapterProvider", "LOCAL_SECRET_REF");
    }

    private TestDataProperties properties(
            boolean cleanupEnabled,
            String cleanupAdapterMode,
            String cleanupAdapterUrl,
            boolean accountProvisioningEnabled,
            String accountProvisioningAdapterMode,
            String accountProvisioningAdapterUrl
    ) {
        return new TestDataProperties(
                true,
                true,
                5_000,
                30_000,
                "wp8-health-test-worker",
                10,
                50,
                100,
                10_000,
                2_048,
                1_800,
                14_400,
                cleanupEnabled,
                cleanupAdapterMode,
                cleanupAdapterUrl,
                "",
                5_000,
                accountProvisioningEnabled,
                accountProvisioningAdapterMode,
                accountProvisioningAdapterUrl,
                "",
                5_000,
                20,
                true
        );
    }

    private XxlJobProperties xxlJobProperties(boolean enabled) {
        return new XxlJobProperties(
                enabled,
                "http://xxl-job-admin.example.test",
                "",
                3,
                new XxlJobProperties.Executor("platform-api", "", "", 0, "", 30)
        );
    }

    private TestDataCleanupAdapter readyCleanupAdapter(String provider) {
        return new TestDataCleanupAdapter() {
            @Override
            public boolean ready() {
                return true;
            }

            @Override
            public String provider() {
                return provider;
            }

            @Override
            public CleanupResult cleanup(CleanupRequest request) {
                return CleanupResult.success("cleanup-health-test", 1, Map.of("status", "ready"));
            }
        };
    }

    private TestAccountProvisioningAdapter readyProvisioningAdapter(String provider) {
        return new TestAccountProvisioningAdapter() {
            @Override
            public boolean ready() {
                return true;
            }

            @Override
            public String provider() {
                return provider;
            }

            @Override
            public ProvisionedAccount provision(ProvisioningRequest request) {
                return new ProvisionedAccount(
                        request.accountKey(),
                        request.displayName(),
                        List.of("ADMIN"),
                        Map.of("environmentId", "env-staging"),
                        "secret://wp8/health-test/account",
                        "HEALTHY",
                        "health test account",
                        Map.of("requestedAt", Instant.now().toString())
                );
            }
        };
    }
}
