package com.songhg.veri.agent.testdata.application;

import com.songhg.veri.agent.testdata.application.view.TestDataHealthResponse;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TestDataHealthService {

    private final TestDataProperties properties;

    public TestDataHealthService(TestDataProperties properties) {
        this.properties = properties;
    }

    /**
     * Publishes WP8 readiness and safety boundaries without exposing account credentials or record payloads.
     */
    public TestDataHealthResponse health() {
        return new TestDataHealthResponse(
                "test-data",
                "UP",
                properties.enabled(),
                properties.cleanupEnabled(),
                properties.exportEnabled(),
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
                        Map.entry("businessCrudApiReady", false),
                        Map.entry("leaseApiReady", false),
                        Map.entry("cleanupWorkerReady", false),
                        Map.entry("destructiveCleanupDefaultDisabled", !properties.cleanupEnabled()),
                        Map.entry("secretPlaintextStored", false),
                        Map.entry("secretRefPlaintextExported", false),
                        Map.entry("rawRecordPayloadStored", false),
                        Map.entry("productionDataCopyAllowed", false),
                        Map.entry("automaticBusinessAccountProvisioningReady", false),
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
