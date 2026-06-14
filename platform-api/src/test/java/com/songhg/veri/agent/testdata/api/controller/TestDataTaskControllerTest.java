package com.songhg.veri.agent.testdata.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
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
class TestDataTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsTaskListWithoutAuthenticationAndProjectScope() throws Exception {
        mockMvc.perform(get("/api/v1/test-data/data-tasks"))
                .andExpect(status().isForbidden());

        String projectToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/test-data/data-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createsAndListsCleanupTaskWithoutTriggeringWorker() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/test-data/data-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "taskType", "CLEANUP",
                                "requestKey", "cleanup-run-001",
                                "targetRef", "lease:run-001",
                                "resultSummary", Map.of("reason", "release")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.policy.destructiveCleanupTriggered").value(false))
                .andExpect(jsonPath("$.data.policy.workerReady").value(false))
                .andReturn();
        UUID taskId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/test-data/data-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "taskType", "CLEANUP",
                                "requestKey", "cleanup-run-001",
                                "targetRef", "lease:run-001",
                                "resultSummary", Map.of("reason", "release")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(taskId.toString()));

        mockMvc.perform(get("/api/v1/test-data/data-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("taskType", "CLEANUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/v1/test-data/data-tasks/{id}/retry", taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp8-task-user-" + UUID.randomUUID(),
                "WP8 Task User",
                "wp8-task-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
