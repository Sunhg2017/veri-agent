package com.songhg.veri.agent.testdesign.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class TestDesignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private TestDesignRepository testDesignRepository;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/test-design/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("test-design"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.generationEnabled").value(true))
                .andExpect(jsonPath("$.data.generationMode").value("RULE_TEMPLATE"))
                .andExpect(jsonPath("$.data.supportedCoverageTypes", hasSize(6)));
    }

    @Test
    void rejectsUnauthenticatedCalls() throws Exception {
        mockMvc.perform(get("/api/v1/test-design/tasks"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/test-design/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsReviewsDryRunsAndPublishesCandidatesToWp3Assets() throws Exception {
        String userToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(userToken, "WP5 登录需求", "登录成功后进入工作台", "project-wp5");

        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "登录需求用例生成",
                                  "requirementIds": ["%s"],
                                  "coverageTypes": ["SMOKE", "EXCEPTION"]
                                }
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(2))
                .andExpect(jsonPath("$.data.candidates", hasSize(2)))
                .andExpect(jsonPath("$.data.candidates[0].status").value("GENERATED"))
                .andExpect(jsonPath("$.data.candidates[0].steps", hasSize(3)))
                .andReturn();

        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        MvcResult updated = mockMvc.perform(put("/api/v1/test-design/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "编辑后的登录冒烟用例",
                                  "description": "人工补充候选用例",
                                  "coverageType": "SMOKE",
                                  "priority": "HIGH",
                                  "expectedResult": "登录成功",
                                  "tags": ["wp5", "review"],
                                  "version": %d,
                                  "steps": [
                                    {"action": "输入账号密码", "expectedResult": "可提交"},
                                    {"action": "点击登录", "expectedResult": "进入工作台"}
                                  ]
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EDITED"))
                .andExpect(jsonPath("$.data.title").value("编辑后的登录冒烟用例"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn();

        Integer updatedVersion = JsonPath.read(updated.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "comment": "可以发布"}
                                """.formatted(updatedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedBy").isString());

        MvcResult otherCandidates = mockMvc.perform(get("/api/v1/test-design/tasks/{id}/candidates", taskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        String secondCandidateId = JsonPath.read(otherCandidates.getResponse().getContentAsString(), "$.data.items[1].id");
        Integer secondVersion = JsonPath.read(otherCandidates.getResponse().getContentAsString(), "$.data.items[1].version");
        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", secondCandidateId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(secondVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(0)))
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"));

        mockMvc.perform(get("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + userToken)
                        .param("projectId", "project-wp5")
                        .param("source", "AI_GENERATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        MvcResult publish = mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCEEDED"))
                .andReturn();

        String caseId = JsonPath.read(publish.getResponse().getContentAsString(), "$.data.createdCaseIds[0]");
        mockMvc.perform(get("/api/v1/asset/test-cases/{id}", caseId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI_GENERATED"))
                .andExpect(jsonPath("$.data.sourceRef", startsWith("wp5:")))
                .andExpect(jsonPath("$.data.requirementId").value(requirementId))
                .andExpect(jsonPath("$.data.steps", hasSize(2)));

        mockMvc.perform(get("/api/v1/asset/links")
                        .header("Authorization", "Bearer " + userToken)
                        .param("requirementId", requirementId)
                        .param("caseId", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].caseId").value(caseId));
    }

    @Test
    void enforcesPublishPermissionAndProjectScope() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String developerToken = userAccessToken(List.of("Developer@PROJECT:project-wp5"));
        String deniedToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-other"));
        String requirementId = createRequirement(ownerToken, "权限需求", "权限验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/test-design/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + deniedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void linksExistingWp3CaseBySourceRefDuringPublish() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "幂等发布需求", "发布幂等验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/{id}/confirm", candidateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult existingCase = mockMvc.perform(post("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp5",
                                  "title": "已存在的 WP5 用例",
                                  "description": "模拟上次发布已创建资产",
                                  "source": "AI_GENERATED",
                                  "sourceRef": "wp5:%s",
                                  "status": "DRAFT",
                                  "priority": "HIGH",
                                  "steps": [
                                    {"action": "执行已有用例", "expectedResult": "已有用例可复用"}
                                  ]
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isCreated())
                .andReturn();
        String existingCaseId = JsonPath.read(existingCase.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish-dry-run", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.records[0].action").value("LINK_EXISTING"))
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.createdCaseIds", hasSize(0)))
                .andExpect(jsonPath("$.data.records[0].action").value("LINK_EXISTING"))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.records[0].assetCaseId").value(existingCaseId));

        mockMvc.perform(get("/api/v1/asset/test-cases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("projectId", "project-wp5")
                        .param("source", "AI_GENERATED")
                        .param("keyword", "wp5:" + candidateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void enforcesBatchReviewPermissionByCandidateProjectScope() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String deniedToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-other"));
        String requirementId = createRequirement(ownerToken, "批量评审权限需求", "批量评审验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String candidateId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].id");
        Integer version = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.candidates[0].version");

        mockMvc.perform(post("/api/v1/test-design/candidates/batch-action")
                        .header("Authorization", "Bearer " + deniedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CONFIRM","candidates":[{"id":"%s","version":%d}]}
                                """.formatted(candidateId, version)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/test-design/candidates/batch-action")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CONFIRM","candidates":[{"id":"%s","version":%d}]}
                                """.formatted(candidateId, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.items[0].candidate.status").value("CONFIRMED"));
    }

    @Test
    void retriesAndCancelsFailedGenerationTasks() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-wp5"));
        String requirementId = createRequirement(ownerToken, "任务状态需求", "状态流验收", "project-wp5");
        MvcResult taskResult = mockMvc.perform(post("/api/v1/test-design/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"project-wp5","requirementIds":["%s"],"coverageTypes":["SMOKE"]}
                                """.formatted(requirementId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.data.task.id");

        saveTaskStatus(taskId, TestDesignTaskStatus.FAILED, "模型输出非法");
        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/cancel", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.task.errorMessage").value("用户取消生成任务"));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/retry", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.task.generatedCount").value(1))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)));

        mockMvc.perform(post("/api/v1/test-design/tasks/{id}/retry", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void contractDoesNotExposeRawPromptOrSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(openApi, containsString("/api/v1/test-design/tasks"));
        MatcherAssert.assertThat(openApi, containsString("promptKey"));
        MatcherAssert.assertThat(openApi, not(containsString("promptPlaintext")));
        MatcherAssert.assertThat(openApi, not(containsString("secretValue")));
    }

    private String createRequirement(
            String userToken,
            String title,
            String acceptanceCriteria,
            String projectId
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/asset/requirements")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "WP5 测试需求",
                                  "priority": "HIGH",
                                  "projectId": "%s",
                                  "acceptanceCriteria": "%s"
                                }
                                """.formatted(title, projectId, acceptanceCriteria)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void saveTaskStatus(String taskId, TestDesignTaskStatus status, String errorMessage) {
        TestDesignTask task = testDesignRepository.task(UUID.fromString(taskId)).orElseThrow();
        testDesignRepository.saveTask(new TestDesignTask(
                task.id(),
                task.projectId(),
                task.title(),
                status.name(),
                task.requirementIds(),
                task.coverageTypes(),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                task.totalRequirements(),
                task.generatedCount(),
                task.confirmedCount(),
                task.publishedCount(),
                errorMessage,
                task.requestedBy(),
                task.createdAt(),
                Instant.now()
        ));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp5_user",
                "WP5 用户",
                "wp5@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
