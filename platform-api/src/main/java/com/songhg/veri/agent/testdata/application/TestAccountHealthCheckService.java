package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.testdata.application.port.TestDataRepository;
import com.songhg.veri.agent.testdata.domain.TestAccountLease;
import com.songhg.veri.agent.testdata.domain.TestPooledAccount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestAccountHealthCheckService {

    private static final int MAX_HEALTH_SUMMARY_LENGTH = 512;

    private final TestDataRepository repository;
    private final TestDataPlatformContextClient contextClient;

    public TestAccountHealthCheckService(
            TestDataRepository repository,
            TestDataPlatformContextClient contextClient
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
    }

    /**
     * Runs bounded control-plane consistency checks without logging into external systems or touching credentials.
     *
     * <p>The first worker slice only reconciles account lifecycle drift against the WP8 lease state machine:
     * stale `LEASED` accounts without an active lease are quarantined as `LOCKED`, expired recovered accounts move from
     * `EXPIRED` to `LOCKED`, and `AVAILABLE` accounts with an active lease are corrected back to `LEASED`.</p>
     */
    @Transactional
    public AccountHealthCheckResult runManagedChecks(Instant now, int limit, String workerId) {
        int scannedCount = 0;
        int updatedCount = 0;
        int lockedCount = 0;
        int leasedCount = 0;
        for (TestPooledAccount account : repository.pooledAccountsForHealthCheck(Math.max(1, limit))) {
            scannedCount++;
            Optional<HealthAdjustment> adjustment = adjustment(account, now, workerId);
            if (adjustment.isEmpty()) {
                continue;
            }
            HealthAdjustment next = adjustment.get();
            repository.updatePooledAccount(next.account());
            auditAdjustment(next);
            updatedCount++;
            if ("LOCKED".equals(next.account().status())) {
                lockedCount++;
            }
            if ("LEASED".equals(next.account().status())) {
                leasedCount++;
            }
        }
        return new AccountHealthCheckResult(scannedCount, updatedCount, lockedCount, leasedCount);
    }

    private Optional<HealthAdjustment> adjustment(TestPooledAccount account, Instant now, String workerId) {
        Optional<TestAccountLease> activeLease = repository.activeLeaseByAccount(account.id())
                .filter(lease -> lease.expiresAt() != null && lease.expiresAt().isAfter(now));
        if ("EXPIRED".equals(account.status()) && activeLease.isEmpty()) {
            return Optional.of(lockAdjustment(
                    account,
                    now,
                    workerId,
                    "lease expired and requires operator review",
                    "expired_account_locked"
            ));
        }
        if ("LEASED".equals(account.status()) && activeLease.isEmpty()) {
            return Optional.of(lockAdjustment(
                    account,
                    now,
                    workerId,
                    "account lease drift detected without active lease",
                    "lease_drift_locked"
            ));
        }
        if ("AVAILABLE".equals(account.status()) && activeLease.isPresent()) {
            return Optional.of(leasedAdjustment(account, now, workerId, activeLease.get()));
        }
        return Optional.empty();
    }

    private HealthAdjustment lockAdjustment(
            TestPooledAccount account,
            Instant now,
            String workerId,
            String summary,
            String operation
    ) {
        TestPooledAccount locked = new TestPooledAccount(
                account.id(),
                account.poolId(),
                account.projectId(),
                account.accountKey(),
                account.displayName(),
                "LOCKED",
                account.roleTagsJson(),
                account.scopeSummaryJson(),
                account.secretRefDigest(),
                "LOCKED",
                sanitizedSummary(summary),
                account.createdBy(),
                effectiveWorkerActor(workerId),
                account.archivedAt(),
                account.createdAt(),
                now
        );
        return new HealthAdjustment(locked, operation, null);
    }

    private HealthAdjustment leasedAdjustment(
            TestPooledAccount account,
            Instant now,
            String workerId,
            TestAccountLease lease
    ) {
        TestPooledAccount leased = new TestPooledAccount(
                account.id(),
                account.poolId(),
                account.projectId(),
                account.accountKey(),
                account.displayName(),
                "LEASED",
                account.roleTagsJson(),
                account.scopeSummaryJson(),
                account.secretRefDigest(),
                account.lastHealthStatus(),
                account.lastHealthSummary(),
                account.createdBy(),
                effectiveWorkerActor(workerId),
                account.archivedAt(),
                account.createdAt(),
                now
        );
        return new HealthAdjustment(leased, "active_lease_reconciled", lease);
    }

    private void auditAdjustment(HealthAdjustment adjustment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", adjustment.operation());
        payload.put("poolId", adjustment.account().poolId().toString());
        payload.put("status", adjustment.account().status());
        payload.put("lastHealthStatus", adjustment.account().lastHealthStatus());
        payload.put("secretRefDigest", adjustment.account().secretRefDigest());
        if (adjustment.lease() != null) {
            payload.put("leaseId", adjustment.lease().id().toString());
            payload.put("leaseExpiresAt", adjustment.lease().expiresAt() == null ? null : adjustment.lease().expiresAt().toString());
        }
        contextClient.writeAuditEvent(
                "test_data.account.updated",
                "TEST_POOLED_ACCOUNT",
                adjustment.account().id().toString(),
                adjustment.account().projectId(),
                "SUCCESS",
                payload
        );
    }

    private String effectiveWorkerActor(String workerId) {
        return workerId == null || workerId.isBlank() ? "wp8-health-check-worker" : workerId.trim();
    }

    private String sanitizedSummary(String summary) {
        return SensitiveTextSanitizer.sanitizedEvidenceText(summary, MAX_HEALTH_SUMMARY_LENGTH);
    }

    public record AccountHealthCheckResult(
            int scannedAccountCount,
            int updatedAccountCount,
            int lockedAccountCount,
            int leasedAccountCount
    ) {
    }

    private record HealthAdjustment(TestPooledAccount account, String operation, TestAccountLease lease) {
    }
}
