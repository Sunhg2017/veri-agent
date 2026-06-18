package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.uie2e.application.view.UiE2eHealthResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UiE2eHealthService {

    private final UiE2eProperties properties;

    public UiE2eHealthService(UiE2eProperties properties) {
        this.properties = properties;
    }

    /**
     * Publishes the current WP7 M1 readiness and hard safety boundaries without exposing runner credentials,
     * allowlist host values or raw artifact content.
     */
    public UiE2eHealthResponse health() {
        int allowlistHostCount = properties.effectiveAllowlistHostCount();
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
                        Map.entry("credentialInjectionAdapterReady", false),
                        Map.entry("plaintextCredentialStored", false),
                        Map.entry("plaintextCredentialExported", false),
                        Map.entry("cookiePlaintextStored", false),
                        Map.entry("secretRefPlaintextExported", false)
                ),
                Map.ofEntries(
                        Map.entry("captureScreenshotEnabled", properties.captureScreenshotEnabled()),
                        Map.entry("captureVideoEnabled", properties.captureVideoEnabled()),
                        Map.entry("captureTraceEnabled", properties.captureTraceEnabled()),
                        Map.entry("maxArtifactCount", properties.effectiveMaxArtifactCount()),
                        Map.entry("maxArtifactSizeBytes", properties.effectiveMaxArtifactSizeBytes()),
                        Map.entry("redactionScanRequired", true),
                        Map.entry("rawArtifactDownloadReady", false),
                        Map.entry("rawDomSnapshotStored", false)
                ),
                Map.ofEntries(
                        Map.entry("foundationReady", true),
                        Map.entry("moduleSkeletonReady", true),
                        Map.entry("healthApiReady", true),
                        Map.entry("permissionSeedReady", true),
                        Map.entry("databaseSchemaReady", true),
                        Map.entry("sceneControlPlaneReady", false),
                        Map.entry("bundleSummaryReady", false),
                        Map.entry("bundleReviewReady", false),
                        Map.entry("runControlPlaneReady", false),
                        Map.entry("runnerPortReady", false),
                        Map.entry("artifactManifestReady", false),
                        Map.entry("flakyMarkReady", false),
                        Map.entry("wp3SourceSummaryPlanned", true),
                        Map.entry("wp5CaseSummaryPlanned", true),
                        Map.entry("wp8RunnerAccountContractReady", true),
                        Map.entry("wp9UiTestContractPlanned", true),
                        Map.entry("wp10EvidenceContractPlanned", true),
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
}
