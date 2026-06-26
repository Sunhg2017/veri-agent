package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.testdata.application.port.TestAccountProvisioningAdapter;
import com.songhg.veri.agent.testdata.application.port.TestDataCleanupAdapter;
import com.songhg.veri.agent.testdata.application.view.TestDataHealthResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.scheduling.config.XxlJobProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestDataHealthService {

    private final TestDataProperties properties;
    private final TestDataCleanupAdapter cleanupAdapter;
    private final TestAccountProvisioningAdapter provisioningAdapter;
    private final XxlJobProperties xxlJobProperties;

    @Autowired
    public TestDataHealthService(
            TestDataProperties properties,
            TestDataCleanupAdapter cleanupAdapter,
            TestAccountProvisioningAdapter provisioningAdapter,
            XxlJobProperties xxlJobProperties
    ) {
        this.properties = properties;
        this.cleanupAdapter = cleanupAdapter;
        this.provisioningAdapter = provisioningAdapter;
        this.xxlJobProperties = xxlJobProperties;
    }

    public TestDataHealthService(TestDataProperties properties) {
        this(properties, TestDataCleanupAdapter.disabled(), TestAccountProvisioningAdapter.disabled(), null);
    }

    /**
     * Publishes WP8 readiness and safety boundaries without exposing account credentials or record payloads.
     */
    public TestDataHealthResponse health() {
        boolean workerManagedByXxlJob = xxlJobProperties != null && xxlJobProperties.enabled();
        boolean workerRuntimeReady = properties.workerEnabled() && workerManagedByXxlJob;
        boolean cleanupExecutionReady = workerRuntimeReady && properties.cleanupEnabled() && cleanupAdapter.ready();
        boolean accountProvisioningReady = workerRuntimeReady
                && properties.accountProvisioningRealAdapterReady()
                && provisioningAdapter.ready();
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
                        Map.entry("workerManagedByXxlJob", workerManagedByXxlJob),
                        Map.entry("workerRuntimeReady", workerRuntimeReady),
                        Map.entry("cleanupWorkerReady", workerRuntimeReady),
                        Map.entry("cleanupExecutionReady", cleanupExecutionReady),
                        Map.entry("taskExecutionWorkerReady", workerRuntimeReady),
                        Map.entry("leaseRecoveryWorkerReady", workerRuntimeReady),
                        Map.entry("accountHealthCheckWorkerReady", workerRuntimeReady),
                        Map.entry("wp8FileDownloadReady", true),
                        Map.entry("destructiveCleanupAdapterReady", cleanupAdapter.ready()),
                        Map.entry("destructiveCleanupAdapterProvider", cleanupAdapter.provider()),
                        Map.entry("destructiveCleanupDefaultDisabled", !properties.cleanupEnabled()),
                        Map.entry("secretPlaintextStored", false),
                        Map.entry("secretRefPlaintextExported", false),
                        Map.entry("rawRecordPayloadStored", false),
                        Map.entry("productionDataCopyAllowed", false),
                        Map.entry("automaticBusinessAccountProvisioningEnabled", properties.accountProvisioningEnabled()),
                        Map.entry("automaticBusinessAccountProvisioningReady", accountProvisioningReady),
                        Map.entry("accountProvisioningRealAdapterRequired", true),
                        Map.entry("accountProvisioningLocalSecretRefMode", "LOCAL_SECRET_REF".equals(
                                properties.effectiveAccountProvisioningAdapterMode())),
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
