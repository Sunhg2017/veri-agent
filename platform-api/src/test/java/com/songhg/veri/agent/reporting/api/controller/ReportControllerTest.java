package com.songhg.veri.agent.reporting.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.execution.application.ExecutionRunService;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunExportResponse;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.command.TestDataReportEvidenceQuery;
import com.songhg.veri.agent.testdata.application.view.TestDataCrossWpAccountSummary;
import com.songhg.veri.agent.testdata.application.view.TestDataReportEvidenceResponse;
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

        mockMvc.perform(post("/api/v1/reports/{id}/archive", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists());
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
