package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestAccountHealthCheckServiceTest {

    @Test
    void locksLeaseDriftAndReconcilesMissingLeasedStatus() {
        InMemoryTestDataRepository repository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient contextClient = mock(TestDataPlatformContextClient.class);
        TestAccountHealthCheckService service = new TestAccountHealthCheckService(repository, contextClient);
        Instant now = Instant.now();

        TestPooledAccount staleLeased = account(UUID.randomUUID(), "LEASED", "HEALTHY", "stale lease", now.minusSeconds(30));
        TestPooledAccount activeAvailable = account(UUID.randomUUID(), "AVAILABLE", "HEALTHY", "available", now.minusSeconds(20));
        repository.insertPooledAccount(staleLeased);
        repository.insertPooledAccount(activeAvailable);
        repository.insertAccountLeaseIfAbsent(new TestAccountLease(
                UUID.randomUUID(),
                activeAvailable.poolId(),
                activeAvailable.id(),
                activeAvailable.projectId(),
                "ACTIVE",
                "EXECUTION_RUN",
                "run-001",
                "lease-run-001",
                "d".repeat(64),
                "e".repeat(64),
                now.plusSeconds(300),
                null,
                null,
                "wp8-tester",
                now.minusSeconds(10),
                now.minusSeconds(10)
        ));

        TestAccountHealthCheckService.AccountHealthCheckResult result = service.runManagedChecks(
                now,
                10,
                "wp8-health-worker"
        );

        assertThat(result.scannedAccountCount()).isEqualTo(2);
        assertThat(result.updatedAccountCount()).isEqualTo(2);
        assertThat(result.lockedAccountCount()).isEqualTo(1);
        assertThat(result.leasedAccountCount()).isEqualTo(1);
        assertThat(repository.pooledAccount(staleLeased.id())).get()
                .extracting(TestPooledAccount::status, TestPooledAccount::lastHealthStatus)
                .containsExactly("LOCKED", "LOCKED");
        assertThat(repository.pooledAccount(activeAvailable.id())).get()
                .extracting(TestPooledAccount::status)
                .isEqualTo("LEASED");
        verify(contextClient, times(2)).writeAuditEvent(
                eq("test_data.account.updated"),
                eq("TEST_POOLED_ACCOUNT"),
                anyString(),
                eq("project-alpha"),
                eq("SUCCESS"),
                anyMap()
        );
    }

    private TestPooledAccount account(UUID id, String status, String lastHealthStatus, String summary, Instant updatedAt) {
        return new TestPooledAccount(
                id,
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                "project-alpha",
                "account-" + id.toString().substring(0, 8),
                "Account " + id.toString().substring(0, 8),
                status,
                "[\"ADMIN\"]",
                "{\"applicationId\":\"app-alpha\"}",
                "a".repeat(64),
                lastHealthStatus,
                summary,
                "wp8-tester",
                "wp8-tester",
                null,
                updatedAt.minusSeconds(60),
                updatedAt
        );
    }
}
