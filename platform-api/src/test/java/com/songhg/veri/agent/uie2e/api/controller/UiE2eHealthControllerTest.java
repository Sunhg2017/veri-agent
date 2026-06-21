package com.songhg.veri.agent.uie2e.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.ui-e2e.runner-enabled=false",
        "veri-agent.ui-e2e.runner-mode=managed",
        "veri-agent.ui-e2e.default-timeout-seconds=120",
        "veri-agent.ui-e2e.max-timeout-seconds=600",
        "veri-agent.ui-e2e.max-scenes-per-run=2",
        "veri-agent.ui-e2e.max-artifact-size-bytes=4096",
        "veri-agent.ui-e2e.max-artifact-count=3",
        "veri-agent.ui-e2e.max-concurrency=4",
        "veri-agent.ui-e2e.allowlist-base-urls[0]=https://portal.example.test",
        "veri-agent.ui-e2e.allowlist-base-urls[1]=https://admin.example.test",
        "veri-agent.ui-e2e.capture-video-enabled=true",
        "veri-agent.ui-e2e.capture-har-enabled=true",
        "veri-agent.ui-e2e.capture-junit-xml-enabled=true"
})
@AutoConfigureMockMvc
class UiE2eHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesWp7M1HealthWithoutSensitiveValues() throws Exception {
        mockMvc.perform(get("/api/v1/ui-e2e/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("ui-e2e"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.runnerEnabled").value(false))
                .andExpect(jsonPath("$.data.runnerMode").value("managed"))
                .andExpect(jsonPath("$.data.defaultTimeoutSeconds").value(120))
                .andExpect(jsonPath("$.data.maxTimeoutSeconds").value(600))
                .andExpect(jsonPath("$.data.maxScenesPerRun").value(2))
                .andExpect(jsonPath("$.data.maxConcurrency").value(4))
                .andExpect(jsonPath("$.data.allowlistEnabled").value(true))
                .andExpect(jsonPath("$.data.allowlistHostCount").value(2))
                .andExpect(jsonPath("$.data.exportEnabled").value(true))
                .andExpect(jsonPath("$.data.supportedNodeTypes[0]").value("UI_TEST"))
                .andExpect(jsonPath("$.data.credentialPolicy.accountLeaseRefRequired").value(true))
                .andExpect(jsonPath("$.data.credentialPolicy.runnerAccountContractReady").value(true))
                .andExpect(jsonPath("$.data.credentialPolicy.secretRefDigestReturned").value(true))
                .andExpect(jsonPath("$.data.credentialPolicy.credentialInjectionAdapterReady").value(false))
                .andExpect(jsonPath("$.data.credentialPolicy.credentialInjectionPreviewOnly").value(false))
                .andExpect(jsonPath("$.data.credentialPolicy.plaintextCredentialStored").value(false))
                .andExpect(jsonPath("$.data.credentialPolicy.plaintextCredentialExported").value(false))
                .andExpect(jsonPath("$.data.artifactPolicy.captureScreenshotEnabled").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.captureVideoEnabled").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.captureHarEnabled").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.captureTraceEnabled").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.captureJunitXmlEnabled").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.maxArtifactCount").value(3))
                .andExpect(jsonPath("$.data.artifactPolicy.maxArtifactSizeBytes").value(4096))
                .andExpect(jsonPath("$.data.artifactPolicy.redactionScanRequired").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.rawArtifactDownloadReady").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.artifactCleanupEnabled").value(false))
                .andExpect(jsonPath("$.data.artifactPolicy.artifactCleanupSupported").value(true))
                .andExpect(jsonPath("$.data.artifactPolicy.artifactCleanupRetentionHours").value(168))
                .andExpect(jsonPath("$.data.artifactPolicy.artifactCleanupBatchSize").value(100))
                .andExpect(jsonPath("$.data.runnerCapacity.maxConcurrency").value(4))
                .andExpect(jsonPath("$.data.runnerCapacity.activeWorkers").value(0))
                .andExpect(jsonPath("$.data.runnerCapacity.availableWorkers").value(4))
                .andExpect(jsonPath("$.data.runnerCapacity.queuedTasks").value(0))
                .andExpect(jsonPath("$.data.runnerCapacity.sharedBrowserPoolReady").value(true))
                .andExpect(jsonPath("$.data.runnerCapacity.batchRunReady").value(true))
                .andExpect(jsonPath("$.data.runnerCapacity.summaryBackfillReady").value(true))
                .andExpect(jsonPath("$.data.policy.foundationReady").value(true))
                .andExpect(jsonPath("$.data.policy.moduleSkeletonReady").value(true))
                .andExpect(jsonPath("$.data.policy.healthApiReady").value(true))
                .andExpect(jsonPath("$.data.policy.permissionSeedReady").value(true))
                .andExpect(jsonPath("$.data.policy.databaseSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.bundleSummaryReady").value(true))
                .andExpect(jsonPath("$.data.policy.bundleReviewReady").value(true))
                .andExpect(jsonPath("$.data.policy.runControlPlaneReady").value(true))
                .andExpect(jsonPath("$.data.policy.runnerPortReady").value(true))
                .andExpect(jsonPath("$.data.policy.artifactManifestReady").value(true))
                .andExpect(jsonPath("$.data.policy.flakyMarkReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp8RunnerAccountContractReady").value(true))
                .andExpect(jsonPath("$.data.policy.sharedBrowserPoolReady").value(true))
                .andExpect(jsonPath("$.data.policy.batchRunReady").value(true))
                .andExpect(jsonPath("$.data.policy.summaryBackfillReady").value(true))
                .andExpect(jsonPath("$.data.policy.artifactCleanupReady").value(true))
                .andExpect(jsonPath("$.data.policy.managedPreviewRunnerReady").value(false))
                .andExpect(jsonPath("$.data.policy.realBrowserRunnerReady").value(false))
                .andExpect(jsonPath("$.data.policy.runnerDefaultDisabled").value(true))
                .andExpect(jsonPath("$.data.policy.videoCaptureDefaultDisabled").value(false))
                .andExpect(jsonPath("$.data.policy.allowlistHostValuesExposed").value(false))
                .andExpect(jsonPath("$.data.policy.supportedSceneStatuses[2]").value("APPROVED"))
                .andExpect(jsonPath("$.data.policy.supportedBundleStatuses[1]").value("STATIC_CHECK_FAILED"))
                .andExpect(jsonPath("$.data.policy.supportedRunStatuses[3]").value("FAILED"))
                .andExpect(jsonPath("$.data.policy.supportedFlakyStatuses[1]").value("FLAKY_CANDIDATE"));
    }
}
