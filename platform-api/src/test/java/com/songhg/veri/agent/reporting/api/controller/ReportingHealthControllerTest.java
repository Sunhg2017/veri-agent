package com.songhg.veri.agent.reporting.api.controller;

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
        "veri-agent.reporting.max-evidence-items=3",
        "veri-agent.reporting.max-diagnosis-context-chars=256",
        "veri-agent.reporting.max-export-markdown-chars=512",
        "veri-agent.reporting.schema-version=wp10-test-report-v1",
        "veri-agent.reporting.field-set-version=wp10-test-fields-v1"
})
@AutoConfigureMockMvc
class ReportingHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesM1ControlPlaneHealthWithoutSensitiveValues() throws Exception {
        mockMvc.perform(get("/api/v1/reports/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("reporting"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.generateEnabled").value(true))
                .andExpect(jsonPath("$.data.diagnosisEnabled").value(true))
                .andExpect(jsonPath("$.data.defectDraftEnabled").value(true))
                .andExpect(jsonPath("$.data.exportEnabled").value(true))
                .andExpect(jsonPath("$.data.maxEvidenceItems").value(3))
                .andExpect(jsonPath("$.data.maxDiagnosisContextChars").value(256))
                .andExpect(jsonPath("$.data.maxExportMarkdownChars").value(512))
                .andExpect(jsonPath("$.data.schemaVersion").value("wp10-test-report-v1"))
                .andExpect(jsonPath("$.data.fieldSetVersion").value("wp10-test-fields-v1"))
                .andExpect(jsonPath("$.data.policy.foundationReady").value(true))
                .andExpect(jsonPath("$.data.policy.permissionSeedReady").value(true))
                .andExpect(jsonPath("$.data.policy.databaseSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.moduleSkeletonReady").value(true))
                .andExpect(jsonPath("$.data.policy.healthApiReady").value(true))
                .andExpect(jsonPath("$.data.policy.reportGenerationReady").value(true))
                .andExpect(jsonPath("$.data.policy.reportQueryReady").value(true))
                .andExpect(jsonPath("$.data.policy.reportArchiveReady").value(true))
                .andExpect(jsonPath("$.data.policy.reportRetryReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp9EvidenceManifestReady").value(true))
                .andExpect(jsonPath("$.data.policy.wp8EvidenceManifestReady").value(true))
                .andExpect(jsonPath("$.data.policy.evidenceAggregationReady").value(false))
                .andExpect(jsonPath("$.data.policy.failureClassifierReady").value(true))
                .andExpect(jsonPath("$.data.policy.diagnosisApiReady").value(true))
                .andExpect(jsonPath("$.data.policy.aiDiagnosisReady").value(true))
                .andExpect(jsonPath("$.data.policy.aiDiagnosisFallbackReady").value(true))
                .andExpect(jsonPath("$.data.policy.defectDraftReady").value(true))
                .andExpect(jsonPath("$.data.policy.exportSummaryReady").value(true))
                .andExpect(jsonPath("$.data.policy.crossWpDirectTableReadAllowed").value(false))
                .andExpect(jsonPath("$.data.policy.rawRunnerArtifactStored").value(false))
                .andExpect(jsonPath("$.data.policy.rawPromptStored").value(false))
                .andExpect(jsonPath("$.data.policy.rawResponseStored").value(false))
                .andExpect(jsonPath("$.data.policy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.policy.supportedReportStatuses[2]").value("READY"))
                .andExpect(jsonPath("$.data.policy.supportedDiagnosisStatuses[3]").value("AI_READY"))
                .andExpect(jsonPath("$.data.policy.supportedDefectDraftStatuses[0]").value("DRAFT"))
                .andExpect(jsonPath("$.data.policy.supportedExportTypes[1]").value("MARKDOWN"));
    }
}
