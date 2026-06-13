package com.songhg.veri.agent.execution.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository apiAutomationRepository;

    @Test
    void createsManualRunAndReplaysRequestKeyIdempotently() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");

        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestKey", "release-2026-06-13-smoke",
                                "reason", "release gate",
                                "variables", Map.of("buildId", "20260613.1")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.planId").value(planId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.data.requestKey").value("release-2026-06-13-smoke"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.resultSummary.nodeCount").value(2))
                .andExpect(jsonPath("$.data.resultSummary.runnerDispatched").value(false))
                .andExpect(jsonPath("$.data.nodes[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.data.nodes[0].runnerType").value("WP6_API"))
                .andExpect(jsonPath("$.data.nodes[1].status").value("PENDING"))
                .andReturn();

        UUID runId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestKey", "release-2026-06-13-smoke",
                                "reason", "client retry"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.nodes.length()").value(2));

        mockMvc.perform(get("/api/v1/execution/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("planId", planId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(runId.toString()))
                .andExpect(jsonPath("$.data.items[0].nodeCount").value(2));

        mockMvc.perform(get("/api/v1/execution/runs/{id}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.nodes.length()").value(2));
    }

    @Test
    void rejectsManualRunWhenPlanIsNotReady() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "DRAFT");

        mockMvc.perform(post("/api/v1/execution/plans/{id}/runs", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestKey", "draft-plan"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("EXECUTION_PLAN_NOT_READY"));
    }

    private UUID createPlan(UUID bundleId, String token, String status) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(bundleId, status))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID approvedBundle(String projectId) {
        UUID bundleId = UUID.randomUUID();
        Instant now = Instant.now();
        apiAutomationRepository.insertScriptBundle(new ApiAutomationScriptBundle(
                bundleId,
                projectId,
                UUID.randomUUID(),
                "APPROVED",
                "bundle-digest-" + bundleId,
                2,
                "{}",
                "{}",
                "PASSED",
                "{}",
                "approved",
                "submitter",
                "approver",
                now,
                now,
                null,
                "tester",
                "tester",
                now,
                now
        ));
        return bundleId;
    }

    private Map<String, Object> planRequest(UUID bundleId, String status) {
        return Map.of(
                "projectId", "project-alpha",
                "name", "Release smoke",
                "environmentKey", "staging",
                "status", status,
                "dag", Map.of("nodes", List.of(
                        Map.of(
                                "key", "api-smoke",
                                "type", "API_TEST",
                                "dependencies", List.of(),
                                "input", Map.of("apiAutomationBundleId", bundleId.toString()),
                                "timeoutSeconds", 180,
                                "failurePolicy", "FAIL_FAST",
                                "retryPolicy", Map.of("maxAttempts", 1)
                        ),
                        Map.of(
                                "key", "report",
                                "type", "REPORT_HANDOFF",
                                "dependencies", List.of("api-smoke"),
                                "input", Map.of("summaryOnly", true),
                                "timeoutSeconds", 60,
                                "failurePolicy", "CONTINUE",
                                "retryPolicy", Map.of()
                        )
                ))
        );
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp9-run-user-" + UUID.randomUUID(),
                "WP9 Run User",
                "wp9-run-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
