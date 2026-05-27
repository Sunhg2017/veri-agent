package com.songhg.veri.agent.testdesign.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.testdesign.application.TestDesignService;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token",
        "veri-agent.test-design.service-token=test-design-token",
        "veri-agent.test-design.event-recovery-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestDesignAsyncGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private TestDesignRepository testDesignRepository;

    @Autowired
    private TestDesignService testDesignService;

    @Test
    void createsQueuedTaskAndConsumesGenerationEventIdempotently() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken);

        MvcResult accepted = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "异步生成任务",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE", "EXCEPTION"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(0))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)))
                .andReturn();

        String taskId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.data.task.id");
        waitForGeneratedTask(userToken, taskId, 2);

        UUID taskUuid = UUID.fromString(taskId);
        assertThat(testDesignRepository.candidatesByTask(taskUuid)).hasSize(2);

        testDesignService.processQueuedTask(taskUuid);
        assertThat(testDesignRepository.candidatesByTask(taskUuid)).hasSize(2);
    }

    private void waitForGeneratedTask(String userToken, String taskId, int expectedCandidates) throws Exception {
        AssertionError lastFailure = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                                .header("Authorization", "Bearer " + userToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                        .andExpect(jsonPath("$.data.task.generatedCount").value(expectedCandidates))
                        .andExpect(jsonPath("$.data.candidates", hasSize(expectedCandidates)));
                return;
            } catch (AssertionError failure) {
                lastFailure = failure;
                Thread.sleep(100L);
            }
        }
        throw lastFailure == null ? new AssertionError("WP5 async generation did not finish") : lastFailure;
    }

    private String createRequirement(String userToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "异步生成需求",
                                  "description": "WP5 异步生成测试需求",
                                  "priority": "HIGH",
                                  "projectId": "project-wp5",
                                  "acceptanceCriteria": "后台事件生成候选后可评审"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp5_async_user",
                "WP5 Async User",
                "wp5-async@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
