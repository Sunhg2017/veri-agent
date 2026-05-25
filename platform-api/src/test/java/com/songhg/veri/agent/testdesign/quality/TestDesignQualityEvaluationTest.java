package com.songhg.veri.agent.testdesign.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token",
        "veri-agent.test-design.service-token=test-design-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestDesignQualityEvaluationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void candidateSchemaDocumentsEnterpriseQualityContract() throws Exception {
        JsonNode schema = objectMapper.readTree(new ClassPathResource(
                "schemas/testdesign/wp5-test-design-candidate.schema.json"
        ).getInputStream());

        List<String> required = StreamSupport.stream(schema.path("required").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(required)
                .contains("title", "coverageType", "priority", "steps", "expectedResult", "duplicateKey", "confidence");
        assertThat(schema.at("/properties/steps/minItems").asInt()).isEqualTo(2);
        assertThat(schema.at("/properties/steps/maxItems").asInt()).isEqualTo(12);
        assertThat(schema.at("/properties/confidence/minimum").asDouble()).isZero();
        assertThat(schema.at("/properties/confidence/maximum").asDouble()).isEqualTo(1D);
        assertThat(StreamSupport.stream(schema.at("/properties/coverageType/enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList())
                .contains("SMOKE", "EXCEPTION", "PERMISSION", "REGRESSION");
    }

    @Test
    void generatedCandidatesMeetBaselineQualityAndRedactObviousSecrets() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(
                userToken,
                "登录密钥回显保护",
                "登录成功后进入工作台，错误提示不得回显 apiKey=sk_live_1234567890 或 token=wp5_secret_987654321"
        );

        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "WP5 quality eval",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE", "EXCEPTION", "PERMISSION"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.task.generatedCount").value(3))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andReturn();

        String body = taskResult.getResponse().getContentAsString();
        List<String> titles = JsonPath.read(body, "$.data.candidates[*].title");
        List<String> expectedResults = JsonPath.read(body, "$.data.candidates[*].steps[*].expectedResult");
        List<String> coverageTypes = JsonPath.read(body, "$.data.candidates[*].coverageType");
        List<String> duplicateKeys = JsonPath.read(body, "$.data.candidates[*].duplicateKey");
        List<Double> confidenceScores = JsonPath.read(body, "$.data.candidates[*].confidence");
        List<List<Map<String, Object>>> steps = JsonPath.read(body, "$.data.candidates[*].steps");

        assertThat(new HashSet<>(titles)).hasSameSizeAs(titles);
        assertThat(new HashSet<>(duplicateKeys)).hasSameSizeAs(duplicateKeys);
        assertThat(confidenceScores).allSatisfy(score -> assertThat(score).isBetween(0D, 1D));
        assertThat(steps).allSatisfy(item -> assertThat(item).hasSizeGreaterThanOrEqualTo(2));
        assertThat(expectedResults).allSatisfy(expectedResult -> assertThat(expectedResult).isNotBlank());
        assertThat(coverageTypes).containsExactlyInAnyOrder("SMOKE", "EXCEPTION", "PERMISSION");
        assertThat(body)
                .contains("[REDACTED]")
                .doesNotContain("sk_live_1234567890", "wp5_secret_987654321", "apiKey=sk_live");
    }

    @Test
    void candidateQualityGateRejectsSensitiveManualUpdateBeforePersistence() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken, "登录质量门禁", "登录成功后进入工作台");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "WP5 quality gate update",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();

        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(put("/api/v1/test-design/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "登录质量门禁编辑用例",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "expectedResult": "不得保存 token=wp5_secret_123456789",
                                  "version": %d,
                                  "steps": [
                                    {"action": "输入账号密码", "expectedResult": "可提交"},
                                    {"action": "点击登录", "expectedResult": "进入工作台"}
                                  ]
                                }
                                """.formatted(version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("质量门禁")));

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].version").value(0))
                .andExpect(jsonPath("$.data.candidates[0].title").value("验证登录质量门禁核心冒烟流程"));
    }

    private String createRequirement(String userToken, String title, String acceptanceCriteria) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "WP5 golden corpus requirement",
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
                "wp5_quality_user",
                "WP5 Quality User",
                "wp5-quality@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
