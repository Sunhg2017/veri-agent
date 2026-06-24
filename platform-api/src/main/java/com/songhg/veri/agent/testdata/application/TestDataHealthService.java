package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.testdata.application.port.TestAccountProvisioningAdapter;
import com.songhg.veri.agent.testdata.application.port.TestDataCleanupAdapter;
import com.songhg.veri.agent.testdata.application.view.TestDataHealthResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestDataHealthService {

    private final TestDataProperties properties;
    private final TestDataCleanupAdapter cleanupAdapter;
    private final TestAccountProvisioningAdapter provisioningAdapter;

    @Autowired
    public TestDataHealthService(
            TestDataProperties properties,
            TestDataCleanupAdapter cleanupAdapter,
            TestAccountProvisioningAdapter provisioningAdapter
    ) {
        this.properties = properties;
        this.cleanupAdapter = cleanupAdapter;
        this.provisioningAdapter = provisioningAdapter;
    }

    public TestDataHealthService(TestDataProperties properties) {
        this(properties, TestDataCleanupAdapter.disabled(), TestAccountProvisioningAdapter.disabled());
    }

    /**
     * Publishes WP8 readiness and safety boundaries without exposing account credentials or record payloads.
     */
    public TestDataHealthResponse health() {
        return new TestDataHealthResponse(
                "test-data",
                "UP",
                properties.enabled(),
                properties.workerEnabled(),
                properties.cleanupEnabled(),
                properties.exportEnabled(),
                properties.effectiveWorkerIntervalMs(),
                properties.effectiveWorkerInitialDelayMs(),
                properties.effectiveWorkerId(),
                properties.effectiveWorkerTaskBatchSize(),
                properties.effectiveLeaseRecoveryBatchSize(),
                properties.effectiveAccountHealthCheckBatchSize(),
                properties.effectiveRecordMaxCount(),
                properties.effectiveRecordSummaryMaxBytes(),
                properties.effectiveDefaultLeaseTtlSeconds(),
                properties.effectiveMaxLeaseTtlSeconds(),
                Map.ofEntries(
                        Map.entry("foundationReady", true),
                        Map.entry("permissionSeedReady", true),
                        Map.entry("dataSetSchemaReady", true),
                        Map.entry("accountPoolSchemaReady", true),
                        Map.entry("leaseSchemaReady", true),
                        Map.entry("cleanupTaskSchemaReady", true),
                        Map.entry("auditEventDictionaryReady", true),
                        Map.entry("crossWpReferenceSchemaReady", true),
                        Map.entry("businessCrudApiReady", true),
                        Map.entry("leaseApiReady", true),
                        Map.entry("cleanupTaskApiReady", true),
                        Map.entry("cleanupWorkerReady", true),
                        Map.entry("taskExecutionWorkerReady", true),
                        Map.entry("leaseRecoveryWorkerReady", true),
                        Map.entry("accountHealthCheckWorkerReady", true),
                        Map.entry("wp8FileDownloadReady", true),
                        Map.entry("destructiveCleanupAdapterReady", cleanupAdapter.ready()),
                        Map.entry("destructiveCleanupAdapterProvider", cleanupAdapter.provider()),
                        Map.entry("destructiveCleanupDefaultDisabled", !properties.cleanupEnabled()),
                        Map.entry("secretPlaintextStored", false),
                        Map.entry("secretRefPlaintextExported", false),
                        Map.entry("rawRecordPayloadStored", false),
                        Map.entry("productionDataCopyAllowed", false),
                        Map.entry("automaticBusinessAccountProvisioningEnabled", properties.accountProvisioningEnabled()),
                        Map.entry("automaticBusinessAccountProvisioningReady", properties.accountProvisioningEnabled()
                                && provisioningAdapter.ready()),
                        Map.entry("accountProvisioningAdapterProvider", provisioningAdapter.provider()),
                        Map.entry("supportedDataSetStatuses", List.of("DRAFT", "READY", "DISABLED", "ARCHIVED")),
                        Map.entry("supportedAccountStatuses", List.of(
                                "AVAILABLE",
                                "LEASED",
                                "LOCKED",
                                "EXPIRED",
                                "DISABLED",
                                "ARCHIVED"
                        )),
                        Map.entry("supportedLeaseStatuses", List.of("ACTIVE", "RELEASED", "EXPIRED", "REVOKED")),
                        Map.entry("supportedTaskTypes", List.of("PREPARE", "REFRESH", "CLEANUP", "ROLLBACK"))
                )
        );
    }
}
