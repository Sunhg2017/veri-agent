package com.songhg.veri.agent.testdesign.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.util.List;
import java.util.UUID;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token",
        "veri-agent.test-design.service-token=test-design-token",
        "veri-agent.test-design.async-generation-enabled=false",
        "veri-agent.test-design.async-publish-enabled=false",
        "veri-agent.test-design.publish-event-recovery-enabled=false",
        "veri-agent.test-design.generation-mode=MODEL",
        "veri-agent.model-access.default-model=test-local-model"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestDesignModelGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Test
    void generatesCandidatesThroughWp2ModelInvocationAndKeepsObservationMetadata() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken, "模型生成登录需求", "登录成功后进入工作台");

        MvcResult result = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "模型生成任务",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE", "EXCEPTION"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(2))
                .andExpect(jsonPath("$.data.task.modelInvocationId").isString())
                .andExpect(jsonPath("$.data.task.modelProviderName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.task.modelName").value("test-local-model"))
                .andExpect(jsonPath("$.data.task.modelObservation.available").value(true))
                .andExpect(jsonPath("$.data.task.modelObservation.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.modelObservation.modelCapability").value("JSON"))
                .andExpect(jsonPath("$.data.candidates", hasSize(2)))
                .andExpect(jsonPath("$.data.candidates[0].modelInvocationId").isString())
                .andExpect(jsonPath("$.data.candidates[0].modelProviderName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.candidates[0].modelName").value("test-local-model"))
                .andExpect(jsonPath("$.data.candidates[0].title", startsWith("模型验证模型生成登录需求")))
                .andExpect(jsonPath("$.data.candidates[0].steps", hasSize(3)))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(json, not(containsString("local model response:")));
        MatcherAssert.assertThat(json, not(containsString("requestPreview")));
        MatcherAssert.assertThat(json, not(containsString("responsePreview")));
    }

    private String createRequirement(String userToken, String title, String acceptanceCriteria) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "WP5 模型生成测试需求",
                                  "priority": "HIGH",
                                  "projectId": "project-wp5",
                                  "acceptanceCriteria": "%s"
                                }
                                """.formatted(title, acceptanceCriteria)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp5_model_user",
                "WP5 Model User",
                "wp5-model@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
