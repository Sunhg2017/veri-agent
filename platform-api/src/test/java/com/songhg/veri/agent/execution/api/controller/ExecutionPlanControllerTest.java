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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository apiAutomationRepository;

    @Test
    void protectsPlansWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/execution/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsListsDryRunsUpdatesAndArchivesPlan() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(bundleId, "DRAFT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.projectId").value("project-alpha"))
                .andExpect(jsonPath("$.data.name").value("Release smoke"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.nodes", hasSize(2)))
                .andExpect(jsonPath("$.data.nodes[0].inputSummary.secretRefs.masked").value(true))
                .andExpect(content().string(not(containsString("secret://wp6/token"))))
                .andReturn();

        UUID planId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("keyword", "Release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(planId.toString()))
                .andExpect(jsonPath("$.data.items[0].nodeCount").value(2));

        mockMvc.perform(post("/api/v1/execution/plans/{id}/dry-run", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.policy.runCreated").value(false))
                .andExpect(jsonPath("$.data.policy.runnerDispatched").value(false))
                .andExpect(jsonPath("$.data.nodes[0].runnerType").value("WP6_API"));

        mockMvc.perform(patch("/api/v1/execution/plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Release smoke ready",
                                "status", "READY"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.name").value("Release smoke ready"));

        mockMvc.perform(post("/api/v1/execution/plans/{id}/archive", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists());

        mockMvc.perform(patch("/api/v1/execution/plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "should fail"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void rejectsInvalidDagAndCrossProjectBundle() throws Exception {
        UUID otherProjectBundle = approvedBundle("project-other");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(otherProjectBundle, "DRAFT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("EXECUTION_DAG_INVALID")));

        mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "name", "bad dag",
                                "environmentKey", "staging",
                                "dag", Map.of("nodes", List.of(
                                        Map.of("key", "a", "type", "REPORT_HANDOFF", "dependencies", List.of("b")),
                                        Map.of("key", "b", "type", "REPORT_HANDOFF", "dependencies", List.of("a"))
                                ))
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("EXECUTION_DAG_INVALID")));
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
                "description", "release gate",
                "status", status,
                "triggerPolicy", Map.of("manualEnabled", true, "webhookEnabled", false, "cronEnabled", false),
                "dag", Map.of("nodes", List.of(
                        Map.of(
                                "key", "api-smoke",
                                "type", "API_TEST",
                                "dependencies", List.of(),
                                "input", Map.of(
                                        "apiAutomationBundleId", bundleId.toString(),
                                        "baseUrlRef", "env:STAGING_BASE_URL",
                                        "secretRefs", List.of("secret://wp6/token")
                                ),
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
                "wp9-user-" + UUID.randomUUID(),
                "WP9 User",
                "wp9-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
