package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.asset.application.AssetCrossWpReportEvidenceService;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.ExecutionRunService;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunExportResponse;
import com.songhg.veri.agent.modelaccess.application.ModelInvocationService;
import com.songhg.veri.agent.reporting.application.view.ReportGenerationWorkerTickResponse;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.infrastructure.InMemoryReportingRepository;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdesign.application.TestDesignCrossWpReportEvidenceService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.reporting.async-generation-enabled=true",
        "veri-agent.reporting.generation-worker-enabled=true",
        "veri-agent.reporting.generation-worker-id=wp10-test-worker",
        "veri-agent.reporting.generation-worker-batch-size=2",
        "veri-agent.reporting.generation-worker-interval-ms=3600000",
        "veri-agent.reporting.generation-worker-initial-delay-ms=3600000",
        "veri-agent.reporting.generation-running-timeout-seconds=1",
        "veri-agent.reporting.schema-version=wp10-test-report-v1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ReportGenerationWorkerServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportGenerationWorkerService workerService;

    @Autowired
    private InMemoryReportingRepository reportingRepository;

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
    void asyncCreateQueuesReportAndWorkerGeneratesReadySnapshot() throws Exception {
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
                                "requestKey", "async-report-ready",
                                "reason", "release gate"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.generatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.summary.generationStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.summary.asyncGeneration").value(true))
                .andExpect(jsonPath("$.data.summary.generationReasonPresent").value(true))
                .andReturn();
        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        ReportGenerationWorkerTickResponse tick = workerService.runOnce();

        assertThat(tick.workerEnabled()).isTrue();
        assertThat(tick.workerId()).isEqualTo("wp10-test-worker");
        assertThat(tick.claimedReportCount()).isEqualTo(1);
        assertThat(tick.readyReportCount()).isEqualTo(1);
        assertThat(tick.failedReportCount()).isZero();
        assertThat(tick.noop()).isFalse();
        assertThat(tick.traceId()).startsWith("trc_");

        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.generatedAt").exists())
                .andExpect(jsonPath("$.data.summary.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.summary.diagnosisStatus").value("RULE_READY"))
                .andExpect(jsonPath("$.data.summary.diagnosisPrimaryCategory").value("NO_FAILURE"))
                .andExpect(jsonPath("$.data.evidenceManifests.length()").value(2))
                .andExpect(jsonPath("$.data.latestDiagnosis.status").value("RULE_READY"));
        verify(executionRunService).runProjectScopeId(runId);
        verify(executionRunService).exportRun(runId);
    }

    @Test
    void workerMarksGenerationFailureWithoutLeakingSensitiveErrorText() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND,
                        "missing secret://wp10/raw Authorization: Bearer abcdefghijklmnop"));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "async-report-failed"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        ReportGenerationWorkerTickResponse tick = workerService.runOnce();

        assertThat(tick.claimedReportCount()).isEqualTo(1);
        assertThat(tick.readyReportCount()).isZero();
        assertThat(tick.failedReportCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failedCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.failureSummary").value(
                        "missing [REDACTED_SECRET_REF] [REDACTED]"))
                .andExpect(jsonPath("$.data.summary.generationStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.summary.failureSummaryStored").value(true));
    }

    @Test
    void asyncRetryRequeuesFailedReportAndClearsPreviousFailureSummary() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionRunService.runProjectScopeId(runId))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "missing secret://wp10/raw"))
                .thenReturn("project-alpha");
        when(executionRunService.exportRun(runId)).thenReturn(successRunExport(runId, "project-alpha"));
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "executionRunId", runId,
                                "requestKey", "async-report-retry"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        UUID reportId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        ReportGenerationWorkerTickResponse failedTick = workerService.runOnce();

        assertThat(failedTick.failedReportCount()).isEqualTo(1);
        mockMvc.perform(post("/api/v1/reports/{id}/retry", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.failedCode").doesNotExist())
                .andExpect(jsonPath("$.data.failureSummary").doesNotExist())
                .andExpect(jsonPath("$.data.summary.generationStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.summary.retryQueued").value(true))
                .andExpect(jsonPath("$.data.summary.failureCode").doesNotExist())
                .andExpect(jsonPath("$.data.summary.failureSummaryStored").doesNotExist());

        ReportGenerationWorkerTickResponse readyTick = workerService.runOnce();

        assertThat(readyTick.readyReportCount()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.failedCode").doesNotExist())
                .andExpect(jsonPath("$.data.failureSummary").doesNotExist())
                .andExpect(jsonPath("$.data.summary.runStatus").value("SUCCEEDED"));
    }

    @Test
    void workerRecoversStaleGeneratingReportsToFailed() {
        Instant old = Instant.now().minusSeconds(5);
        UUID reportId = UUID.randomUUID();
        reportingRepository.insertReportIfAbsent(new ReportExecutionReport(
                reportId,
                "project-alpha",
                UUID.randomUUID(),
                "stale-generating",
                "GENERATING",
                "wp10-test-report-v1",
                null,
                "{\"generationStatus\":\"GENERATING\"}",
                "{\"aggregateOnly\":true}",
                "worker-test",
                null,
                null,
                null,
                "trc_stale_generating",
                null,
                old,
                old
        ));

        ReportGenerationWorkerTickResponse tick = workerService.runOnce();

        assertThat(tick.recoveredStaleCount()).isEqualTo(1);
        assertThat(tick.claimedReportCount()).isZero();
        assertThat(reportingRepository.report(reportId))
                .get()
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo("FAILED");
                    assertThat(report.failedCode()).isEqualTo("REPORT_GENERATION_TIMEOUT");
                    assertThat(report.failureSummary()).contains("timed out");
                });
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp10-worker-user-" + UUID.randomUUID(),
                "WP10 Worker User",
                "wp10-worker-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
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
}
