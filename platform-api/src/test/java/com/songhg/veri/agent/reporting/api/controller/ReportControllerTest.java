package com.songhg.veri.agent.reporting.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.asset.application.AssetCrossWpReportEvidenceService;
import com.songhg.veri.agent.asset.application.command.AssetReportEvidenceQuery;
import com.songhg.veri.agent.asset.application.view.AssetReportEvidenceResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.ExecutionRunService;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunExportResponse;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.command.TestDataReportEvidenceQuery;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataReportEvidenceResponse;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpReportEvidenceService;
import com.songhg.veri.agent.testdesign.application.command.TestDesignReportEvidenceQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignReportEvidenceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.reporting.schema-version=wp10-test-report-v1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExecutionRunService executionRunService;

    @MockitoBean
    private TestDataCrossWpReferenceService testDataCrossWpReferenceService;

    @MockitoBean
    private AssetCrossWpReportEvidenceService assetCrossWpReportEvidenceService;

    @MockitoBean
    private TestDesignCrossWpReportEvidenceService testDesignCrossWpReportEvidenceService;

    @MockitoBean
    private ModelInvocationService modelInvocationService;

    @Test
    void generatesListsDetailsAndArchivesReportFromSanitizedRunExport() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID accountLeaseRef = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId))
                .thenReturn(runExport(runId, "project-alpha", "FAILED", true, accountLeaseRef));
        when(testDataCrossWpReferenceService.reportEvidence(any(TestDataReportEvidenceQuery.class)))
                .thenReturn(wp8Evidence(accountLeaseRef));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "release-report-1",
                                "reason", "release gate"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.projectId").value("project-alpha"))
                .andExpect(jsonPath("$.data.executionRunId").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.schemaVersion").value("wp10-test-report-v1"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.summary.runStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.summary.reportHandoffReady").value(true))
                .andExpect(jsonPath("$.data.summary.rawReportStored").value(false))
                .andExpect(jsonPath("$.data.summary.nodeStatusCounts.FAILED").value(1))
                .andExpect(jsonPath("$.data.summary.failureBucketCounts.ASSERTION_FAILED").value(1))
                .andExpect(jsonPath("$.data.summary.evidenceManifestCount").value(3))
                .andExpect(jsonPath("$.data.summary.evidenceManifestTruncated").value(false))
                .andExpect(jsonPath("$.data.summary.wp8EvidenceReferenceCount").value(1))
                .andExpect(jsonPath("$.data.summary.wp8EvidenceManifestCount").value(1))
                .andExpect(jsonPath("$.data.summary.wp8EvidenceReferenceTruncated").value(false))
                .andExpect(jsonPath("$.data.summary.diagnosisStatus").value("RULE_READY"))
                .andExpect(jsonPath("$.data.summary.diagnosisRuleVersion").value("wp10-failure-classifier-v1"))
                .andExpect(jsonPath("$.data.summary.diagnosisPrimaryCategory").value("ASSERTION_FAILED"))
                .andExpect(jsonPath("$.data.summary.diagnosisManualReviewRequired").value(true))
                .andExpect(jsonPath("$.data.redactionPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.redactionPolicy.crossWpDirectTableReadAllowed").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests.length()").value(3))
                .andExpect(jsonPath("$.data.evidenceManifests[0].sourceWp").value("WP9"))
                .andExpect(jsonPath("$.data.evidenceManifests[0].sourceType").value("EXECUTION_NODE"))
                .andExpect(jsonPath("$.data.evidenceManifests[0].sourceRefDigest").isString())
                .andExpect(jsonPath("$.data.evidenceManifests[0].summaryKeys[0]").value("accountLeaseRef"))
                .andExpect(jsonPath("$.data.evidenceManifests[0].summaryKeys[1]").value("sanitized"))
                .andExpect(jsonPath("$.data.evidenceManifests[0].redactionFlags.summaryValuesStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[0].redactionFlags.unsafeSummaryKeysFiltered").value(true))
                .andExpect(jsonPath("$.data.evidenceManifests[0].evidenceSummary.nodeKey").value("api-smoke"))
                .andExpect(jsonPath("$.data.evidenceManifests[0].evidenceSummary.resultSummaryKeyCount").value(4))
                .andExpect(jsonPath("$.data.evidenceManifests[2].sourceWp").value("WP8"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].sourceType").value("ACCOUNT_LEASE"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].schemaVersion").value("wp8-report-evidence-v1"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].summaryKeys[0]").value("accountLeaseRefDigest"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].redactionFlags.sourceWp8ReportEvidenceSanitized").value(true))
                .andExpect(jsonPath("$.data.evidenceManifests[2].redactionFlags.secretRefPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[2].redactionFlags.leaseTokenStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[2].evidenceSummary.accountLeaseRefDigest").isString())
                .andExpect(jsonPath("$.data.evidenceManifests[2].evidenceSummary.holderRefDigest").isString())
                .andExpect(jsonPath("$.data.evidenceManifests[2].evidenceSummary.accountScopeSummaryKeys[0]")
                        .value("applicationId"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].evidenceSummary.secretRefDigest").value("a".repeat(64)))
                .andExpect(jsonPath("$.data.latestDiagnosis.status").value("RULE_READY"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.primaryCategory")
                        .value("ASSERTION_FAILED"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.secondaryCategory")
                        .value("TEST_DATA_ACCOUNT"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.ruleVersion")
                        .value("wp10-failure-classifier-v1"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.failedNodeCount").value(1))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.accountIssueCount").value(1))
                .andExpect(jsonPath("$.data.latestDiagnosis.rootCauseCandidates.length()").value(2))
                .andExpect(jsonPath("$.data.latestDiagnosis.rootCauseCandidates[0].category")
                        .value("ASSERTION_FAILED"))
                .andExpect(jsonPath("$.data.latestDiagnosis.rootCauseCandidates[0].evidenceRefs[0]",
                        startsWith("wp9:execution_node:")))
                .andExpect(jsonPath("$.data.latestDiagnosis.rootCauseCandidates[1].category")
                        .value("TEST_DATA_ACCOUNT"))
                .andExpect(jsonPath("$.data.latestDiagnosis.rootCauseCandidates[1].evidenceRefs[0]",
                        startsWith("wp8:account_lease:")))
                .andExpect(jsonPath("$.data.latestDiagnosis.confidence").value(0.7400))
                .andExpect(jsonPath("$.data.latestDiagnosis.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.data.latestDiagnosis.modelInvocationDigest").doesNotExist())
                .andExpect(jsonPath("$.data.latestDiagnosis.aiDiagnosisReady").value(false))
                .andExpect(jsonPath("$.data.latestDiagnosis.modelInvoked").value(false))
                .andExpect(jsonPath("$.data.latestDiagnosis.redactionPolicy.aggregateOnly").value(true))
                .andExpect(content().string(not(containsString("Bearer"))))
                .andExpect(content().string(not(containsString("Authorization"))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andExpect(content().string(not(containsString(accountLeaseRef.toString()))))
                .andExpect(content().string(not(containsString("lease-token-plain"))))
                .andExpect(content().string(not(containsString("execution-run-secret-holder"))))
                .andExpect(content().string(not(containsString("account-key-secret"))))
                .andExpect(content().string(not(containsString("Staging Admin"))))
                .andReturn();

        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "release-report-1",
                                "reason", "client retry"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.evidenceManifests.length()").value(3))
                .andExpect(jsonPath("$.data.latestDiagnosis.status").value("RULE_READY"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.primaryCategory")
                        .value("ASSERTION_FAILED"));

        verify(executionRunService).runProjectScopeId(runId);
        verify(executionRunService).exportRun(runId);
        verifyNoMoreInteractions(executionRunService);
        verify(testDataCrossWpReferenceService).reportEvidence(argThat(query ->
                "project-alpha".equals(query.projectId())
                        && List.of(accountLeaseRef).equals(query.accountLeaseRefs())
                        && query.dataSetRefs().isEmpty()
                        && query.cleanupTaskRefs().isEmpty()
        ));
        verifyNoMoreInteractions(testDataCrossWpReferenceService);

        mockMvc.perform(get("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("projectId", "project-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.items[0].summary.nodeCount").value(2));

        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.redactionPolicy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[1].evidenceSummary.nodeKey").value("report"))
                .andExpect(jsonPath("$.data.latestDiagnosis.status").value("RULE_READY"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.primaryCategory")
                        .value("ASSERTION_FAILED"));

        ArgumentCaptor<ModelInvocationCommand> commandCaptor = ArgumentCaptor.forClass(ModelInvocationCommand.class);
        when(modelInvocationService.invoke(commandCaptor.capture(), any(ServicePrincipal.class)))
                .thenReturn(modelResult());

        mockMvc.perform(post("/api/v1/reports/{id}/diagnoses", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("AI_READY"))
                .andExpect(jsonPath("$.data.errorCode").doesNotExist())
                .andExpect(jsonPath("$.data.classification.primaryCategory").value("ASSERTION_FAILED"))
                .andExpect(jsonPath("$.data.rootCauseCandidates.length()").value(2))
                .andExpect(jsonPath("$.data.aiDiagnosisReady").value(true))
                .andExpect(jsonPath("$.data.modelInvoked").value(true))
                .andExpect(jsonPath("$.data.classificationOnly").value(false))
                .andExpect(jsonPath("$.data.modelInvocationDigest").isString())
                .andExpect(jsonPath("$.data.diagnosisContext.contextDigest").isString())
                .andExpect(jsonPath("$.data.diagnosisContext.contextStored").value(false))
                .andExpect(jsonPath("$.data.diagnosisContext.rawPromptStored").value(false))
                .andExpect(jsonPath("$.data.diagnosisContext.rawResponseStored").value(false))
                .andExpect(jsonPath("$.data.redactionPolicy.contextDigestOnly").value(true))
                .andExpect(content().string(not(containsString("Authorization"))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andExpect(content().string(not(containsString(accountLeaseRef.toString()))))
                .andExpect(content().string(not(containsString("lease-token-plain"))))
                .andExpect(content().string(not(containsString("execution-run-secret-holder"))))
                .andExpect(content().string(not(containsString("account-key-secret"))))
                .andExpect(content().string(not(containsString("Staging Admin"))));

        ModelInvocationCommand command = commandCaptor.getValue();
        String modelContext = command.messages().getFirst().content();
        assertThat(command.projectId()).isEqualTo("project-alpha");
        assertThat(command.promptKey()).isNull();
        assertThat(command.allowPublicModel()).isFalse();
        assertThat(command.sensitivityLevel()).isEqualTo("INTERNAL");
        assertThat(command.capability()).isEqualTo("JSON");
        assertThat(modelContext).contains("WP10_FAILURE_DIAGNOSIS_V1");
        assertThat(modelContext).contains("ASSERTION_FAILED");
        assertThat(modelContext).doesNotContain("Authorization");
        assertThat(modelContext).doesNotContain("Bearer");
        assertThat(modelContext).doesNotContain("secret://");
        assertThat(modelContext).doesNotContain(accountLeaseRef.toString());
        assertThat(modelContext).doesNotContain("lease-token-plain");
        assertThat(modelContext).doesNotContain("execution-run-secret-holder");
        assertThat(modelContext).doesNotContain("account-key-secret");
        assertThat(modelContext).doesNotContain("Staging Admin");

        mockMvc.perform(get("/api/v1/reports/{id}/diagnoses/latest", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("AI_READY"))
                .andExpect(jsonPath("$.data.errorCode").doesNotExist())
                .andExpect(jsonPath("$.data.modelInvocationDigest").isString())
                .andExpect(jsonPath("$.data.diagnosisContext.contextDigest").isString());

        mockMvc.perform(get("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("projectId", "project-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].summary.diagnosisStatus").value("AI_READY"))
                .andExpect(jsonPath("$.data.items[0].summary.diagnosisPrimaryCategory").value("ASSERTION_FAILED"));

        MvcResult defectDraft = mockMvc.perform(post("/api/v1/reports/{id}/defect-drafts", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.diagnosisId").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title", containsString("ASSERTION_FAILED")))
                .andExpect(jsonPath("$.data.reproductionSummary", containsString("runStatus=FAILED")))
                .andExpect(jsonPath("$.data.impactSummary", containsString("ASSERTION_FAILED")))
                .andExpect(jsonPath("$.data.prioritySuggestion").value("P1"))
                .andExpect(jsonPath("$.data.evidenceRefs[0]", startsWith("wp9:execution_node:")))
                .andExpect(jsonPath("$.data.payloadPreview.schemaVersion").value("wp10-defect-preview-v1"))
                .andExpect(jsonPath("$.data.payloadPreview.externalSystem").value("MANUAL_COPY_ONLY"))
                .andExpect(jsonPath("$.data.payloadPreview.fieldMappingVersion")
                        .value("wp10-defect-preview-fields-v1"))
                .andExpect(jsonPath("$.data.payloadPreview.masked").value(true))
                .andExpect(jsonPath("$.data.payloadPreview.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.payloadPreview.externalSystemWriteAttempted").value(false))
                .andExpect(jsonPath("$.data.payloadPreview.fields.primaryCategory").value("ASSERTION_FAILED"))
                .andExpect(jsonPath("$.data.payloadPreview.redactionPolicy.payloadPreviewMasked").value(true))
                .andExpect(jsonPath("$.data.payloadPreview.redactionPolicy.rawEvidenceIncluded").value(false))
                .andExpect(content().string(not(containsString("Bearer"))))
                .andExpect(content().string(not(containsString("Authorization"))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andExpect(content().string(not(containsString(accountLeaseRef.toString()))))
                .andExpect(content().string(not(containsString("lease-token-plain"))))
                .andExpect(content().string(not(containsString("execution-run-secret-holder"))))
                .andExpect(content().string(not(containsString("account-key-secret"))))
                .andExpect(content().string(not(containsString("Staging Admin"))))
                .andExpect(content().string(not(containsString("raw prompt"))))
                .andExpect(content().string(not(containsString("raw response"))))
                .andReturn();

        UUID draftId = UUID.fromString(JsonPath.read(defectDraft.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(patch("/api/v1/reports/{id}/defect-drafts/{draftId}", reportId, draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REVIEWED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(draftId.toString()))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.payloadPreview.externalSystemWriteAttempted").value(false));

        mockMvc.perform(patch("/api/v1/reports/{id}/defect-drafts/{draftId}", reportId, draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISMISSED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("REPORT_DEFECT_DRAFT_INVALID_STATE"));

        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.defectDraftCount").value(1))
                .andExpect(jsonPath("$.data.defectDrafts.length()").value(1))
                .andExpect(jsonPath("$.data.defectDrafts[0].id").value(draftId.toString()))
                .andExpect(jsonPath("$.data.defectDrafts[0].status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.defectDrafts[0].payloadPreview.externalSystemWriteAttempted")
                        .value(false))
                .andExpect(content().string(not(containsString("Authorization"))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andExpect(content().string(not(containsString(accountLeaseRef.toString()))));

        mockMvc.perform(get("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("projectId", "project-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].summary.defectDraftCount").value(1));

        mockMvc.perform(get("/api/v1/reports/{id}/export", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("exportType", "JSON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.exportType").value("JSON"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.schemaVersion").value("wp10-test-report-v1"))
                .andExpect(jsonPath("$.data.fieldSetVersion").value("wp10-report-export-fields-v1"))
                .andExpect(jsonPath("$.data.contentDigest").isString())
                .andExpect(jsonPath("$.data.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.redactionPolicy.contentStored").value(false))
                .andExpect(jsonPath("$.data.redactionPolicy.externalDefectWriteAttempted").value(false))
                .andExpect(jsonPath("$.data.manifest.contentDigest").isString())
                .andExpect(jsonPath("$.data.content.report.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.content.summary.runStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.content.evidenceManifests.length()").value(3))
                .andExpect(jsonPath("$.data.content.evidenceManifests[0].summaryKeys[0]").value("accountLeaseRef"))
                .andExpect(jsonPath("$.data.content.evidenceManifests[0].summaryKeys[1]").value("sanitized"))
                .andExpect(jsonPath("$.data.content.evidenceManifests[0].evidenceSummary.nodeKey").value("api-smoke"))
                .andExpect(jsonPath("$.data.content.evidenceManifests[2].sourceWp").value("WP8"))
                .andExpect(jsonPath("$.data.content.evidenceManifests[2].evidenceSummary.accountLeaseRefDigest")
                        .isString())
                .andExpect(jsonPath("$.data.content.latestDiagnosis.status").value("AI_READY"))
                .andExpect(jsonPath("$.data.content.latestDiagnosis.modelInvocationDigest").isString())
                .andExpect(content().string(not(containsString("Bearer"))))
                .andExpect(content().string(not(containsString("Authorization"))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andExpect(content().string(not(containsString(accountLeaseRef.toString()))))
                .andExpect(content().string(not(containsString("lease-token-plain"))))
                .andExpect(content().string(not(containsString("execution-run-secret-holder"))))
                .andExpect(content().string(not(containsString("account-key-secret"))))
                .andExpect(content().string(not(containsString("Staging Admin"))))
                .andExpect(content().string(not(containsString("raw prompt"))))
                .andExpect(content().string(not(containsString("raw response"))));

        mockMvc.perform(get("/api/v1/reports/{id}/export", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("exportType", "MARKDOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.exportType").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.contentDigest").isString())
                .andExpect(jsonPath("$.data.content", containsString("WP10 Report Export")))
                .andExpect(jsonPath("$.data.content", containsString("ASSERTION_FAILED")))
                .andExpect(content().string(not(containsString("Bearer"))))
                .andExpect(content().string(not(containsString("Authorization"))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andExpect(content().string(not(containsString(accountLeaseRef.toString()))))
                .andExpect(content().string(not(containsString("Staging Admin"))));

        mockMvc.perform(get("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("projectId", "project-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].summary.exportManifestCount").value(2));

        mockMvc.perform(post("/api/v1/reports/{id}/archive", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists());
    }

    @Test
    void generatesWp3AndWp5AggregateEvidenceManifestsFromSanitizedRunRefs() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID requirementRef = UUID.randomUUID();
        UUID testCaseRef = UUID.randomUUID();
        UUID taskRef = UUID.randomUUID();
        UUID candidateRef = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(crossWpRunExport(
                runId,
                "project-alpha",
                requirementRef,
                testCaseRef,
                taskRef,
                candidateRef
        ));
        when(assetCrossWpReportEvidenceService.reportEvidence(any(AssetReportEvidenceQuery.class)))
                .thenReturn(wp3Evidence(requirementRef, testCaseRef));
        when(testDesignCrossWpReportEvidenceService.reportEvidence(any(TestDesignReportEvidenceQuery.class)))
                .thenReturn(wp5Evidence(taskRef, candidateRef, requirementRef, testCaseRef));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "wp3-wp5-evidence-report"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary.evidenceManifestCount").value(6))
                .andExpect(jsonPath("$.data.summary.wp3EvidenceReferenceCount").value(2))
                .andExpect(jsonPath("$.data.summary.wp3EvidenceManifestCount").value(2))
                .andExpect(jsonPath("$.data.summary.wp3EvidenceReferenceTruncated").value(false))
                .andExpect(jsonPath("$.data.summary.wp5EvidenceReferenceCount").value(2))
                .andExpect(jsonPath("$.data.summary.wp5EvidenceManifestCount").value(2))
                .andExpect(jsonPath("$.data.summary.wp5EvidenceReferenceTruncated").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[2].sourceWp").value("WP3"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].sourceType").value("REQUIREMENT"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].schemaVersion").value("wp3-report-evidence-v1"))
                .andExpect(jsonPath("$.data.evidenceManifests[2].redactionFlags.sourceWp3ReportEvidenceSanitized")
                        .value(true))
                .andExpect(jsonPath("$.data.evidenceManifests[2].redactionFlags.assetBodyStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[2].redactionFlags.traceIdentifierListStored")
                        .value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[2].evidenceSummary.requirementRefDigest")
                        .isString())
                .andExpect(jsonPath("$.data.evidenceManifests[3].sourceWp").value("WP3"))
                .andExpect(jsonPath("$.data.evidenceManifests[3].sourceType").value("TEST_CASE"))
                .andExpect(jsonPath("$.data.evidenceManifests[4].sourceWp").value("WP5"))
                .andExpect(jsonPath("$.data.evidenceManifests[4].sourceType").value("TEST_DESIGN_TASK"))
                .andExpect(jsonPath("$.data.evidenceManifests[4].schemaVersion").value("wp5-report-evidence-v1"))
                .andExpect(jsonPath("$.data.evidenceManifests[4].redactionFlags.sourceWp5ReportEvidenceSanitized")
                        .value(true))
                .andExpect(jsonPath("$.data.evidenceManifests[4].redactionFlags.promptStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[4].redactionFlags.modelPayloadStored").value(false))
                .andExpect(jsonPath("$.data.evidenceManifests[5].sourceWp").value("WP5"))
                .andExpect(jsonPath("$.data.evidenceManifests[5].sourceType").value("TEST_DESIGN_CANDIDATE"))
                .andExpect(content().string(not(containsString(requirementRef.toString()))))
                .andExpect(content().string(not(containsString(testCaseRef.toString()))))
                .andExpect(content().string(not(containsString(taskRef.toString()))))
                .andExpect(content().string(not(containsString(candidateRef.toString()))))
                .andExpect(content().string(not(containsString("Requirement raw body should not leak"))))
                .andExpect(content().string(not(containsString("Candidate generated step body should not leak"))))
                .andExpect(content().string(not(containsString("raw prompt"))))
                .andExpect(content().string(not(containsString("raw response"))));

        verify(assetCrossWpReportEvidenceService).reportEvidence(argThat(query ->
                "project-alpha".equals(query.projectId())
                        && List.of(requirementRef).equals(query.requirementRefs())
                        && List.of(testCaseRef).equals(query.testCaseRefs())
                        && query.apiRefs().isEmpty()
                        && query.pageRefs().isEmpty()
                        && query.businessFlowRefs().isEmpty()
        ));
        verify(testDesignCrossWpReportEvidenceService).reportEvidence(argThat(query ->
                "project-alpha".equals(query.projectId())
                        && List.of(taskRef).equals(query.taskRefs())
                        && List.of(candidateRef).equals(query.candidateRefs())
        ));
    }

    @Test
    void rejectsUnsupportedReportExportType() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(successRunExport(runId, "project-alpha"));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "unsupported-export-type"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/reports/{id}/export", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("exportType", "PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("REPORT_EXPORT_TYPE_INVALID"));
    }

    @Test
    void downgradesDiagnosisWhenWp2BudgetBlocksInvocation() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(runExport(runId, "project-alpha", "FAILED", true));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "budget-blocked-report"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        when(modelInvocationService.invoke(any(ModelInvocationCommand.class), any(ServicePrincipal.class)))
                .thenThrow(new BusinessException(ErrorCode.BUDGET_EXCEEDED, "budget exhausted"));

        mockMvc.perform(post("/api/v1/reports/{id}/diagnoses", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("AI_FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("REPORT_DIAGNOSIS_POLICY_BLOCKED"))
                .andExpect(jsonPath("$.data.classification.primaryCategory").value("ASSERTION_FAILED"))
                .andExpect(jsonPath("$.data.aiDiagnosisReady").value(false))
                .andExpect(jsonPath("$.data.modelInvoked").value(false))
                .andExpect(jsonPath("$.data.classificationOnly").value(true))
                .andExpect(jsonPath("$.data.modelInvocationDigest").doesNotExist())
                .andExpect(jsonPath("$.data.diagnosisContext.contextDigest").isString())
                .andExpect(jsonPath("$.data.diagnosisContext.contextStored").value(false))
                .andExpect(jsonPath("$.data.redactionPolicy.contextDigestOnly").value(true));
    }

    @Test
    void rejectsSourceRunWithoutReadyReportHandoff() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(runExport(runId, "project-alpha", "FAILED", false));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "missing-handoff"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("REPORT_SOURCE_RUN_NOT_READY"));
    }

    @Test
    void rejectsCrossProjectSourceRunBeforeExportingIt() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-other");
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "cross-project"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("REPORT_SOURCE_RUN_NOT_FOUND"));

        verify(executionRunService).runProjectScopeId(runId);
        verify(executionRunService, never()).exportRun(runId);
    }

    @Test
    void classifiesSuccessfulReportAsNoFailureWithoutManualReview() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(successRunExport(runId, "project-alpha"));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "success-report"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.summary.diagnosisStatus").value("RULE_READY"))
                .andExpect(jsonPath("$.data.summary.diagnosisPrimaryCategory").value("NO_FAILURE"))
                .andExpect(jsonPath("$.data.summary.diagnosisManualReviewRequired").value(false))
                .andExpect(jsonPath("$.data.latestDiagnosis.status").value("RULE_READY"))
                .andExpect(jsonPath("$.data.latestDiagnosis.classification.primaryCategory").value("NO_FAILURE"))
                .andExpect(jsonPath("$.data.latestDiagnosis.rootCauseCandidates.length()").value(0))
                .andExpect(jsonPath("$.data.latestDiagnosis.confidence").value(0.9900))
                .andExpect(jsonPath("$.data.latestDiagnosis.manualReviewRequired").value(false));

        verifyNoMoreInteractions(testDataCrossWpReferenceService);
    }

    @Test
    void protectsReportDetailByProjectScope() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId)).thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(runExport(runId, "project-alpha", "SUCCEEDED", true));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String otherToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-other"));

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "scope-report"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    private ExecutionRunExportResponse runExport(
            UUID runId,
            String projectId,
            String status,
            boolean handoffReady
    ) {
        return runExport(runId, projectId, status, handoffReady, null);
    }

    private ExecutionRunExportResponse runExport(
            UUID runId,
            String projectId,
            String status,
            boolean handoffReady,
            UUID accountLeaseRef
    ) {
        Instant startedAt = Instant.parse("2026-06-16T10:00:00Z");
        Instant finishedAt = Instant.parse("2026-06-16T10:01:35Z");
        List<ExecutionNodeRunResponse> nodes = List.of(
                new ExecutionNodeRunResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "api-smoke",
                        "API_TEST",
                        "FAILED",
                        1,
                        "WP6_API",
                        "wp6-run-1",
                        "ASSERTION_FAILED",
                        "assertion failed",
                        apiNodeSummary(accountLeaseRef),
                        null,
                        startedAt,
                        startedAt,
                        finishedAt,
                        startedAt,
                        finishedAt
                ),
                new ExecutionNodeRunResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "report",
                        "REPORT_HANDOFF",
                        handoffReady ? "SUCCEEDED" : "PENDING",
                        1,
                        "REPORT",
                        null,
                        null,
                        null,
                        handoffReady
                                ? Map.of(
                                        "schedulerManaged", true,
                                        "reportHandoffReady", true,
                                        "rawReportStored", false
                                )
                                : Map.of("schedulerManaged", false),
                        null,
                        startedAt,
                        startedAt,
                        handoffReady ? finishedAt : null,
                        startedAt,
                        finishedAt
                )
        );
        ExecutionRunDetailResponse run = new ExecutionRunDetailResponse(
                runId,
                UUID.randomUUID(),
                projectId,
                status,
                "MANUAL",
                "run-request",
                null,
                1,
                "trc_wp9run",
                Map.of("runnerDispatched", false),
                "ASSERTION_FAILED",
                "assertion failed",
                nodes,
                false,
                "tester",
                startedAt,
                finishedAt,
                startedAt,
                finishedAt
        );
        return new ExecutionRunExportResponse(
                "wp9-run-export-v1",
                Instant.parse("2026-06-16T10:02:00Z"),
                run,
                Map.of("FAILED", 1, handoffReady ? "SUCCEEDED" : "PENDING", 1),
                Map.of(
                        "rawOutputExported", false,
                        "rawRequestResponseExported", false,
                        "secretRefsExported", false,
                        "claimTokenExported", false
                )
        );
    }

    private ExecutionRunExportResponse successRunExport(UUID runId, String projectId) {
        Instant startedAt = Instant.parse("2026-06-16T10:00:00Z");
        Instant finishedAt = Instant.parse("2026-06-16T10:00:25Z");
        List<ExecutionNodeRunResponse> nodes = List.of(
                new ExecutionNodeRunResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "api-smoke",
                        "API_TEST",
                        "SUCCEEDED",
                        1,
                        "WP6_API",
                        "wp6-run-2",
                        null,
                        null,
                        Map.of("sanitized", true),
                        null,
                        startedAt,
                        startedAt,
                        finishedAt,
                        startedAt,
                        finishedAt
                ),
                new ExecutionNodeRunResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "report",
                        "REPORT_HANDOFF",
                        "SUCCEEDED",
                        1,
                        "REPORT",
                        null,
                        null,
                        null,
                        Map.of(
                                "schedulerManaged", true,
                                "reportHandoffReady", true,
                                "rawReportStored", false
                        ),
                        null,
                        startedAt,
                        startedAt,
                        finishedAt,
                        startedAt,
                        finishedAt
                )
        );
        ExecutionRunDetailResponse run = new ExecutionRunDetailResponse(
                runId,
                UUID.randomUUID(),
                projectId,
                "SUCCEEDED",
                "MANUAL",
                "run-request",
                null,
                1,
                "trc_wp9run",
                Map.of("runnerDispatched", false),
                null,
                null,
                nodes,
                false,
                "tester",
                startedAt,
                finishedAt,
                startedAt,
                finishedAt
        );
        return new ExecutionRunExportResponse(
                "wp9-run-export-v1",
                Instant.parse("2026-06-16T10:02:00Z"),
                run,
                Map.of("SUCCEEDED", 2),
                Map.of(
                        "rawOutputExported", false,
                        "rawRequestResponseExported", false,
                        "secretRefsExported", false,
                        "claimTokenExported", false
                )
        );
    }

    private ExecutionRunExportResponse crossWpRunExport(
            UUID runId,
            String projectId,
            UUID requirementRef,
            UUID testCaseRef,
            UUID taskRef,
            UUID candidateRef
    ) {
        Instant startedAt = Instant.parse("2026-06-16T10:00:00Z");
        Instant finishedAt = Instant.parse("2026-06-16T10:00:45Z");
        List<ExecutionNodeRunResponse> nodes = List.of(
                new ExecutionNodeRunResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "cross-wp-context",
                        "API_TEST",
                        "SUCCEEDED",
                        1,
                        "WP6_API",
                        "wp6-run-cross-wp",
                        null,
                        null,
                        Map.of(
                                "sanitized", true,
                                "wp3RequirementRef", requirementRef.toString(),
                                "wp3TestCaseRef", testCaseRef.toString(),
                                "wp5TaskRef", taskRef.toString(),
                                "wp5CandidateRef", candidateRef.toString(),
                                "rawPrompt", "raw prompt should be filtered as an unsafe key"
                        ),
                        null,
                        startedAt,
                        startedAt,
                        finishedAt,
                        startedAt,
                        finishedAt
                ),
                new ExecutionNodeRunResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "report",
                        "REPORT_HANDOFF",
                        "SUCCEEDED",
                        1,
                        "REPORT",
                        null,
                        null,
                        null,
                        Map.of(
                                "schedulerManaged", true,
                                "reportHandoffReady", true,
                                "rawReportStored", false
                        ),
                        null,
                        startedAt,
                        startedAt,
                        finishedAt,
                        startedAt,
                        finishedAt
                )
        );
        ExecutionRunDetailResponse run = new ExecutionRunDetailResponse(
                runId,
                UUID.randomUUID(),
                projectId,
                "SUCCEEDED",
                "MANUAL",
                "run-request",
                null,
                1,
                "trc_wp9run",
                Map.of("runnerDispatched", false),
                null,
                null,
                nodes,
                false,
                "tester",
                startedAt,
                finishedAt,
                startedAt,
                finishedAt
        );
        return new ExecutionRunExportResponse(
                "wp9-run-export-v1",
                Instant.parse("2026-06-16T10:02:00Z"),
                run,
                Map.of("SUCCEEDED", 2),
                Map.of(
                        "rawOutputExported", false,
                        "rawRequestResponseExported", false,
                        "secretRefsExported", false,
                        "claimTokenExported", false
                )
        );
    }

    private Map<String, Object> apiNodeSummary(UUID accountLeaseRef) {
        if (accountLeaseRef == null) {
            return Map.of(
                    "sanitized", true,
                    "Authorization", "Bearer abcdefghijklmnop",
                    "secretToken", "secret://wp10/raw"
            );
        }
        return Map.of(
                "sanitized", true,
                "accountLeaseRef", accountLeaseRef.toString(),
                "Authorization", "Bearer abcdefghijklmnop",
                "secretToken", "secret://wp10/raw"
        );
    }

    private TestDataReportEvidenceResponse wp8Evidence(UUID accountLeaseRef) {
        return new TestDataReportEvidenceResponse(
                "project-alpha",
                "report-ref",
                List.of(),
                List.of(new TestDataReportEvidenceResponse.AccountLeaseEvidence(
                        accountLeaseRef,
                        "RELEASED",
                        "EXECUTION_RUN",
                        "execution-run-secret-holder",
                        Instant.parse("2026-06-16T10:10:00Z"),
                        Instant.parse("2026-06-16T10:02:30Z"),
                        new TestDataCrossWpAccountSummary(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "project-alpha",
                                "account-key-secret",
                                "Staging Admin",
                                "LOCKED",
                                List.of("ADMIN"),
                                Map.of(
                                        "applicationId", "app-alpha",
                                        "passwordHint", "do-not-store"
                                ),
                                "a".repeat(64),
                                "HEALTHY"
                        )
                )),
                List.of(),
                Map.of(
                        "secretPlaintextReturned", false,
                        "secretRefPlaintextReturned", false,
                        "leaseTokenPlaintextReturned", false
                )
        );
    }

    private AssetReportEvidenceResponse wp3Evidence(UUID requirementRef, UUID testCaseRef) {
        return new AssetReportEvidenceResponse(
                "project-alpha",
                "report-ref",
                List.of(new AssetReportEvidenceResponse.RequirementEvidence(
                        requirementRef,
                        "APPROVED",
                        "P1",
                        3,
                        "ACTIVE",
                        2,
                        4,
                        1,
                        0,
                        0,
                        2,
                        Instant.parse("2026-06-16T09:30:00Z")
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(new AssetReportEvidenceResponse.TestCaseEvidence(
                        testCaseRef,
                        "APPROVED",
                        "P1",
                        5,
                        "ACTIVE",
                        3,
                        6,
                        requirementRef,
                        null,
                        2,
                        Instant.parse("2026-06-16T09:45:00Z")
                )),
                Map.of(
                        "aggregateOnly", true,
                        "assetBodyReturned", false,
                        "traceIdentifierListReturned", false
                )
        );
    }

    private TestDesignReportEvidenceResponse wp5Evidence(
            UUID taskRef,
            UUID candidateRef,
            UUID requirementRef,
            UUID testCaseRef
    ) {
        return new TestDesignReportEvidenceResponse(
                "project-alpha",
                "report-ref",
                List.of(new TestDesignReportEvidenceResponse.TaskEvidence(
                        taskRef,
                        "COMPLETED",
                        1,
                        2,
                        1,
                        3,
                        2,
                        1,
                        true,
                        "b".repeat(64),
                        "c".repeat(64),
                        5,
                        3,
                        Map.of("CONFIRMED", 2L, "PUBLISHED", 1L),
                        1,
                        1,
                        "COMPLETE",
                        "d".repeat(64),
                        "wp5-task-report-v1",
                        Instant.parse("2026-06-16T09:55:00Z")
                )),
                List.of(new TestDesignReportEvidenceResponse.CandidateEvidence(
                        candidateRef,
                        taskRef,
                        requirementRef,
                        null,
                        testCaseRef,
                        "PUBLISHED",
                        "API_REGRESSION",
                        "P1",
                        0.87,
                        true,
                        true,
                        4,
                        Instant.parse("2026-06-16T09:58:00Z")
                )),
                Map.of(
                        "aggregateOnly", true,
                        "candidateBodyReturned", false,
                        "promptReturned", false,
                        "modelPayloadReturned", false
                )
        );
    }

    private ModelInvocationResult modelResult() {
        return new ModelInvocationResult(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "local-echo-primary",
                "local-echo",
                null,
                false,
                "{\"schemaVersion\":\"wp10-diagnosis-result-v1\",\"summary\":\"ok\"}",
                128,
                32,
                BigDecimal.ZERO
        );
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp10-report-user-" + UUID.randomUUID(),
                "WP10 Report User",
                "wp10-report-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
