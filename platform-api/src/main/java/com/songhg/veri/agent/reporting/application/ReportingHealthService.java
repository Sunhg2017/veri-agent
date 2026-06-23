package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.reporting.application.view.ReportingHealthResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportingHealthService {

    private final ReportingProperties properties;
    private final ReportingWebhookDispatcher webhookDispatcher;

    public ReportingHealthService(
            ReportingProperties properties,
            ReportingWebhookDispatcher webhookDispatcher
    ) {
        this.properties = properties;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * Publishes WP10 M1 readiness and safety boundaries without exposing provider, prompt or evidence details.
     */
    public ReportingHealthResponse health() {
        return new ReportingHealthResponse(
                "reporting",
                "UP",
                properties.enabled(),
                properties.generateEnabled(),
                properties.asyncGenerationEnabled(),
                properties.generationWorkerEnabled(),
                properties.effectiveGenerationWorkerIntervalMs(),
                properties.effectiveGenerationWorkerInitialDelayMs(),
                properties.effectiveGenerationWorkerId(),
                properties.effectiveGenerationWorkerBatchSize(),
                properties.effectiveGenerationRunningTimeoutSeconds(),
                properties.effectiveGenerationRecoveryBatchSize(),
                properties.diagnosisEnabled(),
                properties.defectDraftEnabled(),
                properties.exportEnabled(),
                properties.effectiveMaxEvidenceItems(),
                properties.effectiveMaxDiagnosisContextChars(),
                properties.effectiveMaxExportMarkdownChars(),
                properties.effectiveSchemaVersion(),
                properties.effectiveFieldSetVersion(),
                webhookDispatcher.health(),
                Map.ofEntries(
                        Map.entry("foundationReady", true),
                        Map.entry("permissionSeedReady", true),
                        Map.entry("databaseSchemaReady", true),
                        Map.entry("moduleSkeletonReady", true),
                        Map.entry("healthApiReady", true),
                        Map.entry("reportGenerationReady", true),
                        Map.entry("asyncGenerationRequestModeReady", true),
                        Map.entry("asyncGenerationWorkerReady", true),
                        Map.entry("asyncGenerationDefaultDisabled", !properties.asyncGenerationEnabled()),
                        Map.entry("generationWorkerUsesStatusClaim", true),
                        Map.entry("generationWorkerRecoversStaleGenerating", true),
                        Map.entry("reportQueryReady", true),
                        Map.entry("reportArchiveReady", true),
                        Map.entry("reportRetryReady", true),
                        Map.entry("wp9EvidenceManifestReady", true),
                        Map.entry("wp8EvidenceManifestReady", true),
                        Map.entry("wp3EvidenceManifestReady", true),
                        Map.entry("wp5EvidenceManifestReady", true),
                        Map.entry("evidenceAggregationReady", true),
                        Map.entry("failureClassifierReady", true),
                        Map.entry("diagnosisApiReady", true),
                        Map.entry("aiDiagnosisReady", true),
                        Map.entry("aiDiagnosisFallbackReady", true),
                        Map.entry("defectDraftReady", true),
                        Map.entry("exportSummaryReady", true),
                        Map.entry("reportWebhookDeliveryReady", true),
                        Map.entry("reportWebhookDeliveryDefaultDisabled", !properties.webhookDeliveryEnabled()),
                        Map.entry("reportWebhookDeliveryAggregateOnly", true),
                        Map.entry("reportWebhookDeliveryBlocksGeneration", false),
                        Map.entry("crossWpDirectTableReadAllowed", false),
                        Map.entry("rawRunnerArtifactStored", false),
                        Map.entry("requestResponseBodyStored", false),
                        Map.entry("rawPromptStored", false),
                        Map.entry("rawResponseStored", false),
                        Map.entry("secretPlaintextStored", false),
                        Map.entry("supportedReportStatuses", List.of(
                                "QUEUED",
                                "GENERATING",
                                "READY",
                                "FAILED",
                                "ARCHIVED"
                        )),
                        Map.entry("supportedDiagnosisStatuses", List.of(
                                "NOT_REQUESTED",
                                "RULE_READY",
                                "AI_RUNNING",
                                "AI_READY",
                                "AI_FAILED"
                        )),
                        Map.entry("supportedDefectDraftStatuses", List.of(
                                "DRAFT",
                                "REVIEWED",
                                "DISMISSED",
                                "EXPORTED"
                        )),
                        Map.entry("supportedExportTypes", List.of("JSON", "MARKDOWN", "HTML", "PDF", "WORD", "EXCEL"))
                )
        );
    }
}
