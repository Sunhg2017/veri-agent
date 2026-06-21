package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.uie2e.application.view.UiE2eHealthResponse;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UiE2eHealthService {

    private final UiE2eProperties properties;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;
    private final UiE2eArtifactStorage artifactStorage;
    private final UiE2eRunnerExecutionPool executionPool;

    @Autowired
    public UiE2eHealthService(
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            UiE2eArtifactStorage artifactStorage,
            UiE2eRunnerExecutionPool executionPool
    ) {
        this.properties = properties;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
        this.artifactStorage = artifactStorage;
        this.executionPool = executionPool;
    }

    UiE2eHealthService(UiE2eProperties properties) {
        this(properties, null, null, null);
    }

    UiE2eHealthService(UiE2eProperties properties, TestDataCrossWpReferenceService testDataCrossWpReferenceService) {
        this(properties, testDataCrossWpReferenceService, null, null);
    }

    UiE2eHealthService(
            UiE2eProperties properties,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService,
            UiE2eArtifactStorage artifactStorage
    ) {
        this(properties, testDataCrossWpReferenceService, artifactStorage, null);
    }

    /**
     * Publishes the current WP7 M1 readiness and hard safety boundaries without exposing runner credentials,
     * allowlist host values or raw artifact content.
     */
    public UiE2eHealthResponse health() {
        int allowlistHostCount = properties.effectiveAllowlistHostCount();
        boolean credentialInjectionReady = testDataCrossWpReferenceService != null
                && testDataCrossWpReferenceService.runnerCredentialInjectionReady();
        boolean previewRunner = properties.runnerEnabled() && "managed".equals(properties.effectiveRunnerMode());
        boolean httpWorkerRunner = properties.runnerEnabled() && "http-adapter".equals(properties.effectiveRunnerMode());
        boolean realBrowserRunner = properties.runnerEnabled()
                && Set.of("playwright-subprocess", "real-browser").contains(properties.effectiveRunnerMode());
        boolean cleanupSupported = artifactStorage != null && artifactStorage.supportsDestructiveCleanup();
        return new UiE2eHealthResponse(
                "ui-e2e",
                "UP",
                properties.enabled(),
                properties.runnerEnabled(),
                properties.effectiveRunnerMode(),
                properties.effectiveDefaultTimeoutSeconds(),
                properties.effectiveMaxTimeoutSeconds(),
                properties.effectiveMaxScenesPerRun(),
                properties.effectiveMaxConcurrency(),
                allowlistHostCount > 0,
                allowlistHostCount,
                properties.exportEnabled(),
                List.of("UI_TEST"),
                Map.ofEntries(
                        Map.entry("accountLeaseRefRequired", true),
                        Map.entry("runnerAccountContractReady", true),
                        Map.entry("secretRefDigestReturned", true),
                        Map.entry("credentialInjectionAdapterReady", credentialInjectionReady),
                        Map.entry("credentialInjectionPreviewOnly", previewRunner && !credentialInjectionReady),
                        Map.entry("plaintextCredentialStored", false),
                        Map.entry("plaintextCredentialExported", false),
                        Map.entry("cookiePlaintextStored", false),
                        Map.entry("secretRefPlaintextExported", false)
                ),
                Map.ofEntries(
                        Map.entry("captureScreenshotEnabled", properties.captureScreenshotEnabled()),
                        Map.entry("captureVideoEnabled", properties.captureVideoEnabled()),
                        Map.entry("captureHarEnabled", properties.captureHarEnabled()),
                        Map.entry("captureTraceEnabled", properties.captureTraceEnabled()),
                        Map.entry("captureJunitXmlEnabled", properties.captureJunitXmlEnabled()),
                        Map.entry("maxArtifactCount", properties.effectiveMaxArtifactCount()),
                        Map.entry("maxArtifactSizeBytes", properties.effectiveMaxArtifactSizeBytes()),
                        Map.entry("redactionScanRequired", true),
                        Map.entry("rawArtifactDownloadReady", artifactStorage != null),
                        Map.entry("artifactCleanupEnabled", properties.artifactCleanupEnabled()),
                        Map.entry("artifactCleanupSupported", cleanupSupported),
                        Map.entry("artifactCleanupRetentionHours", properties.effectiveArtifactCleanupRetentionHours()),
                        Map.entry("artifactCleanupBatchSize", properties.effectiveArtifactCleanupBatchSize()),
                        Map.entry("rawDomSnapshotStored", false)
                ),
                runnerCapacity(),
                Map.ofEntries(
                        Map.entry("foundationReady", true),
                        Map.entry("moduleSkeletonReady", true),
                        Map.entry("healthApiReady", true),
                        Map.entry("permissionSeedReady", true),
                        Map.entry("databaseSchemaReady", true),
                        Map.entry("sceneControlPlaneReady", true),
                        Map.entry("bundleSummaryReady", true),
                        Map.entry("bundleReviewReady", true),
                        Map.entry("runControlPlaneReady", true),
                        Map.entry("runnerPortReady", true),
                        Map.entry("artifactManifestReady", true),
                        Map.entry("flakyMarkReady", true),
                        Map.entry("wp3SourceSummaryPlanned", true),
                        Map.entry("wp5CaseSummaryPlanned", true),
                        Map.entry("wp8RunnerAccountContractReady", true),
                        Map.entry("wp9UiTestContractPlanned", true),
                        Map.entry("wp10EvidenceContractPlanned", true),
                        Map.entry("sharedBrowserPoolReady", true),
                        Map.entry("batchRunReady", true),
                        Map.entry("summaryBackfillReady", true),
                        Map.entry("artifactCleanupReady", cleanupSupported),
                        Map.entry("externalWorkerConfigured", properties.runnerWorkerConfigured()),
                        Map.entry("externalWorkerTokenConfigured", properties.runnerWorkerTokenConfigured()),
                        Map.entry("externalWorkerCancelConfigured", properties.runnerWorkerCancelConfigured()),
                        Map.entry("httpWorkerRunnerReady", httpWorkerRunner && properties.runnerWorkerConfigured()),
                        Map.entry("managedPreviewRunnerReady", previewRunner),
                        Map.entry("realBrowserRunnerReady", realBrowserRunner),
                        Map.entry("runnerDefaultDisabled", !properties.runnerEnabled()),
                        Map.entry("videoCaptureDefaultDisabled", !properties.captureVideoEnabled()),
                        Map.entry("allowlistHostValuesExposed", false),
                        Map.entry("supportedSceneStatuses", List.of(
                                "DRAFT",
                                "REVIEWING",
                                "APPROVED",
                                "DISABLED",
                                "ARCHIVED"
                        )),
                        Map.entry("supportedBundleStatuses", List.of(
                                "DRAFT",
                                "STATIC_CHECK_FAILED",
                                "REVIEWING",
                                "APPROVED",
                                "REJECTED",
                                "ARCHIVED"
                        )),
                        Map.entry("supportedRunStatuses", List.of(
                                "QUEUED",
                                "RUNNING",
                                "SUCCEEDED",
                                "FAILED",
                                "TIMEOUT",
                                "CANCELED",
                                "BLOCKED"
                        )),
                        Map.entry("supportedFlakyStatuses", List.of(
                                "NONE",
                                "FLAKY_CANDIDATE",
                                "CONFIRMED_FLAKY",
                                "WAIVED"
                        ))
                )
        );
    }

    private Map<String, Object> runnerCapacity() {
        UiE2eRunnerExecutionPool.CapacitySnapshot snapshot = executionPool == null
                ? new UiE2eRunnerExecutionPool.CapacitySnapshot(
                properties.effectiveMaxConcurrency(),
                0,
                0,
                0,
                0L,
                false
        )
                : executionPool.snapshot();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("maxConcurrency", snapshot.maxConcurrency());
        summary.put("activeWorkers", snapshot.activeWorkers());
        summary.put("availableWorkers", snapshot.availableWorkers());
        summary.put("queuedTasks", snapshot.queuedTasks());
        summary.put("poolSize", snapshot.poolSize());
        summary.put("completedTaskCount", snapshot.completedTaskCount());
        summary.put("saturated", snapshot.saturated());
        summary.put("sharedBrowserPoolReady", true);
        summary.put("batchRunReady", true);
        summary.put("summaryBackfillReady", true);
        return Map.copyOf(summary);
    }
}
