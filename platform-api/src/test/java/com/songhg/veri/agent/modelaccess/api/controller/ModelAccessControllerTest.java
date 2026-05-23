package com.songhg.veri.agent.modelaccess.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.model-access.service-token=test-model-token",
        "veri-agent.model-access.default-model=test-local-model",
        "veri-agent.model-access.provider-circuit-failure-threshold=1",
        "veri-agent.model-access.provider-rate-limit-max-requests=0",
        "veri-agent.model-access.provider-max-concurrent-requests=2",
        "veri-agent.model-access.async-job-worker-threads=1",
        "veri-agent.model-access.async-job-dispatch-delay-ms=100",
        "veri-agent.model-access.routing-rules[0].name=wp4-private-low-cost",
        "veri-agent.model-access.routing-rules[0].project-ids[0]=project-routing",
        "veri-agent.model-access.routing-rules[0].sensitivity-levels[0]=INTERNAL",
        "veri-agent.model-access.routing-rules[0].caller-services[0]=wp4-document-input",
        "veri-agent.model-access.routing-rules[0].capabilities[0]=REQUIREMENT_PARSE",
        "veri-agent.model-access.routing-rules[0].provider-groups[0]=private",
        "veri-agent.model-access.routing-rules[0].cost-preference=LOWEST_COST"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ModelAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/model-access/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("model-access"))
                .andExpect(jsonPath("$.data.enabledProviders").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.providerRateLimitEnabled").value(false))
                .andExpect(jsonPath("$.data.providerConcurrencyLimitEnabled").value(true))
                .andExpect(jsonPath("$.data.providerMaxConcurrentRequests").value(2))
                .andExpect(jsonPath("$.data.openCircuitProviders").value(0));
    }

    @Test
    void rejectsCallsWithoutServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/model-access/providers"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUntrustedCallerServiceHeaderEvenWithValidServiceToken() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invoke")
                        .headers(authHeaders("untrusted-service"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-alpha",
                                  "taskType": "requirement-summary",
                                  "messageText": "summarize"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("服务调用方不可信"));
    }

    @Test
    void allowsUserBearerTokenForModelAccessManagementRequests() throws Exception {
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/model-access/providers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("local-echo-primary"));

        MvcResult promptResult = mockMvc.perform(post("/api/v1/model-access/prompts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "promptKey": "console-prompt",
                                  "name": "Console prompt",
                                  "content": "Return concise test suggestions.",
                                  "changeNote": "WP2-A console activation",
                                  "activate": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String promptId = JsonPath.read(promptResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/activate", promptId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void enforcesModelAccessPermissionsForUserBearerTokens() throws Exception {
        String developerToken = tokenForRole("Developer");
        String auditorToken = tokenForRole("Auditor");

        mockMvc.perform(get("/api/v1/model-access/providers")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/model-access/invocations/export")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv")));

        mockMvc.perform(post("/api/v1/model-access/providers/{id}/check", "00000000-0000-0000-0000-000000000201")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void managesPromptVersionsAndInvokesModelWithAuditLog() throws Exception {
        String promptId = createPromptVersion("v2", true);

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/activate", promptId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-001",
                                  "applicationId": "app-001",
                                  "promptKey": "case-design",
                                  "promptVariables": {"context": "登录流程"},
                                  "messages": [
                                    {"role": "user", "content": "生成 3 条冒烟测试点"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.modelName").value("test-local-model"))
                .andExpect(jsonPath("$.data.totalCost").exists())
                .andExpect(jsonPath("$.data.content", startsWith("local model response:")));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].promptKey").value("case-design"))
                .andExpect(jsonPath("$.data.items[0].sensitivityLevel").value("INTERNAL"))
                .andExpect(jsonPath("$.data.items[0].actorService").value("wp5-test-design"))
                .andExpect(jsonPath("$.data.items[0].requestPreview").exists())
                .andExpect(jsonPath("$.data.items[0].promptDigest").exists());

        mockMvc.perform(get("/api/v1/model-access/invocations/summary")
                        .headers(authHeaders())
                        .param("projectId", "project-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.totalCost").exists());
    }

    @Test
    void streamsInvocationResponseAsSseAndPersistsInvocationLog() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/model-access/invocations/stream")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "projectId": "project-stream",
                                  "messages": [
                                    {"role": "user", "content": "生成流式响应验证文本"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().string(containsString("event: metadata")))
                .andExpect(content().string(containsString("\"providerName\":\"local-echo-primary\"")))
                .andExpect(content().string(containsString("event: delta")))
                .andExpect(content().string(containsString("local model response: user: 生成流式响应验证文本")))
                .andExpect(content().string(containsString("event: done")))
                .andExpect(content().string(containsString("\"finishReason\":\"stop\"")));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-stream"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].responsePreview", containsString("local model response")))
                .andExpect(jsonPath("$.data.items[0].actorService").value("wp5-test-design"));
    }

    @Test
    void rejectsStreamingInvocationWithoutAuthenticationBeforeAsyncDispatch() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "projectId": "project-stream-anonymous",
                                  "messages": [
                                    {"role": "user", "content": "匿名流式调用不应执行"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-stream-anonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rejectsStreamingInvocationForUserWithoutManagePermissionBeforeAsyncDispatch() throws Exception {
        String developerToken = tokenForRole("Developer");

        mockMvc.perform(post("/api/v1/model-access/invocations/stream")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "projectId": "project-stream-forbidden",
                                  "messages": [
                                    {"role": "user", "content": "低权限流式调用不应执行"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-stream-forbidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void submitsAsyncInvocationJobAndPersistsInvocationLog() throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/api/v1/model-access/invocations/jobs")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-async",
                                  "messages": [
                                    {"role": "user", "content": "生成异步调用验证文本"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.traceId", startsWith("trc_")))
                .andReturn();
        String jobId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.data.jobId");

        waitForAsyncJob(jobId, "SUCCEEDED")
                .andExpect(jsonPath("$.data.invocationId").exists())
                .andExpect(jsonPath("$.data.response.content", startsWith("local model response:")))
                .andExpect(jsonPath("$.data.response.providerName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.errorCode").doesNotExist());

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-async"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].responsePreview", containsString("local model response")))
                .andExpect(jsonPath("$.data.items[0].actorService").value("wp5-test-design"));
    }

    @Test
    void cancelsQueuedAsyncInvocationJobWithoutWritingInvocationLog() throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/api/v1/model-access/invocations/jobs")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-async-cancel",
                                  "messages": [
                                    {"role": "user", "content": "这条排队任务应被取消"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();
        String jobId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.data.jobId");

        mockMvc.perform(post("/api/v1/model-access/invocations/jobs/{jobId}/cancel", jobId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.errorCode").value("CANCELLED"))
                .andExpect(jsonPath("$.data.finishedAt").exists());

        mockMvc.perform(get("/api/v1/model-access/invocations/jobs/{jobId}", jobId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        Thread.sleep(150);
        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-async-cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rejectsAsyncInvocationJobsWithoutAuthenticationOrManagePermission() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-async-anonymous",
                                  "messages": [
                                    {"role": "user", "content": "匿名异步调用不应执行"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isForbidden());

        String developerToken = tokenForRole("Developer");
        mockMvc.perform(post("/api/v1/model-access/invocations/jobs")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-async-forbidden",
                                  "messages": [
                                    {"role": "user", "content": "低权限异步调用不应执行"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/model-access/invocations/jobs/{jobId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForMissingAsyncInvocationJob() throws Exception {
        mockMvc.perform(get("/api/v1/model-access/invocations/jobs/{jobId}", UUID.randomUUID())
                        .headers(authHeaders()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void requiresApprovalBeforeActivatingHighRiskPrompt() throws Exception {
        String token = superAdminToken();

        MvcResult createResult = mockMvc.perform(post("/api/v1/model-access/prompts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "promptKey": "risk-prompt",
                                  "name": "高风险 Prompt",
                                  "content": "请按受控策略生成生产变更建议",
                                  "changeNote": "新增高风险生产变更 Prompt",
                                  "highRisk": true,
                                  "activate": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.highRisk").value(true))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andReturn();
        String promptId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/activate", promptId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/approve", promptId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "审批通过，保留版本说明"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("admin_user"))
                .andExpect(jsonPath("$.data.approvalNote").value("审批通过，保留版本说明"))
                .andExpect(jsonPath("$.data.approvedAt").exists());

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/activate", promptId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    @Test
    void rejectedHighRiskPromptCanBeReviewedAgainBeforeActivation() throws Exception {
        String token = superAdminToken();

        MvcResult createResult = mockMvc.perform(post("/api/v1/model-access/prompts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "promptKey": "risk-retry-prompt",
                                  "name": "高风险重审 Prompt",
                                  "content": "请生成生产数据修复建议",
                                  "highRisk": true,
                                  "activate": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andReturn();
        String promptId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/reject", promptId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "需要补充安全边界"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.approvedBy").value("admin_user"))
                .andExpect(jsonPath("$.data.approvalNote").value("需要补充安全边界"))
                .andExpect(jsonPath("$.data.approvedAt").exists());

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/activate", promptId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/model-access/prompts/{id}/approve", promptId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "补充安全边界后通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvalNote").value("补充安全边界后通过"));
    }

    @Test
    void blocksSensitivePromptContentBeforeProviderCall() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-001",
                                  "messages": [
                                    {"role": "user", "content": "password=PlainSecret123 请直接使用"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SENSITIVE_CONTENT_BLOCKED"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("SENSITIVE_CONTENT_BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].requestPreview").value("user: password=*** 请直接使用"));
    }

    @Test
    void blocksHighSensitivityInvocationFromPublicModelRouting() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-sensitive-policy",
                                  "messages": [
                                    {"role": "user", "content": "生成内部系统测试建议"}
                                  ],
                                  "allowPublicModel": true,
                                  "sensitivityLevel": "RESTRICTED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_POLICY_VIOLATION"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-sensitive-policy")
                        .param("sensitivityLevel", "RESTRICTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].sensitivityLevel").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("MODEL_POLICY_VIOLATION"));

        mockMvc.perform(get("/api/v1/model-access/invocations/summary")
                        .headers(authHeaders())
                        .param("projectId", "project-sensitive-policy")
                        .param("sensitivityLevel", "RESTRICTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.blocked").value(1));

        mockMvc.perform(get("/api/v1/model-access/invocations/summary")
                        .headers(authHeaders())
                        .param("projectId", "project-sensitive-policy")
                        .param("sensitivityLevel", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/model-access/invocations/export")
                        .headers(authHeaders())
                        .param("projectId", "project-sensitive-policy")
                        .param("sensitivityLevel", "STRICT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("project-sensitive-policy")))
                .andExpect(content().string(containsString("RESTRICTED")));
    }

    @Test
    void blocksHighSensitivityInvocationWhenExternalProviderIsExplicitlySelected() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "public-model-provider",
                                  "providerType": "OPENAI_COMPATIBLE",
                                  "baseUrl": "https://api.example.com",
                                  "apiKeyRef": "env:MODEL_API_KEY",
                                  "priority": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String providerId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-external-sensitive-policy",
                                  "messages": [
                                    {"role": "user", "content": "生成内部系统测试建议"}
                                  ],
                                  "providerId": "%s",
                                  "allowPublicModel": false,
                                  "sensitivityLevel": "CONFIDENTIAL"
                                }
                                """.formatted(providerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_POLICY_VIOLATION"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-external-sensitive-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].providerName").value("public-model-provider"))
                .andExpect(jsonPath("$.data.items[0].sensitivityLevel").value("CONFIDENTIAL"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("MODEL_POLICY_VIOLATION"));
    }

    @Test
    void fallsBackToNextProviderAndRecordsFallback() throws Exception {
        MvcResult failingProviderResult = mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "failing-primary",
                                  "providerType": "MOCK_FAILURE",
                                  "priority": 1,
                                  "timeoutMs": 1000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("failing-primary"))
                .andReturn();
        String failingProviderId = JsonPath.read(failingProviderResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-001",
                                  "messages": [
                                    {"role": "user", "content": "检查降级链路"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(true));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fallbackUsed").value(true));

        mockMvc.perform(get("/api/v1/model-access/providers/{id}/resilience", failingProviderId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("failing-primary"))
                .andExpect(jsonPath("$.data.circuitOpen").value(true))
                .andExpect(jsonPath("$.data.consecutiveFailures").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.concurrencyLimitEnabled").value(true))
                .andExpect(jsonPath("$.data.maxConcurrentRequests").value(2));

        mockMvc.perform(post("/api/v1/model-access/providers/{id}/circuit/reset", failingProviderId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("failing-primary"))
                .andExpect(jsonPath("$.data.circuitOpen").value(false))
                .andExpect(jsonPath("$.data.consecutiveFailures").value(0));
    }

    @Test
    void opensCircuitAfterProviderFailureAndSkipsExplicitProviderTemporarily() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "circuit-failing-provider",
                                  "providerType": "MOCK_FAILURE",
                                  "priority": 1,
                                  "timeoutMs": 1000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String providerId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-circuit",
                                  "providerId": "%s",
                                  "messages": [
                                    {"role": "user", "content": "触发熔断"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """.formatted(providerId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MODEL_PROVIDER_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-circuit",
                                  "providerId": "%s",
                                  "messages": [
                                    {"role": "user", "content": "熔断期再次调用"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """.formatted(providerId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message", containsString("invocationId")));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-circuit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].errorMessage").value("模型供应商熔断中"));
    }

    @Test
    void checksProviderReadinessWithoutWritingInvocationLog() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/providers/{id}/check", "00000000-0000-0000-0000-000000000201")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.providerType").value("LOCAL_ECHO"))
                .andExpect(jsonPath("$.data.providerStatus").value("ENABLED"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.latencyMs").exists())
                .andExpect(jsonPath("$.data.errorCode").doesNotExist());

        mockMvc.perform(post("/api/v1/model-access/providers/{id}/check", "00000000-0000-0000-0000-000000000201")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.cached").value(true));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void exposesOperationalMetricsForProviderChecksAndInvocations() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/providers/{id}/check", "00000000-0000-0000-0000-000000000201")
                        .headers(authHeaders()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-metrics",
                                  "messages": [
                                    {"role": "user", "content": "生成指标验证请求"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/veri.agent.model_access.provider.checks")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("veri.agent.model_access.provider.checks"))
                .andExpect(jsonPath("$.measurements[0].value").exists());

        mockMvc.perform(get("/actuator/metrics/veri.agent.model_access.invocations")
                        .headers(authHeaders())
                        .param("tag", "status:SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("veri.agent.model_access.invocations"))
                .andExpect(jsonPath("$.measurements[0].value").exists());
    }

    @Test
    void reportsProviderCheckFailureWithSanitizedError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "check-failing-provider",
                                  "providerType": "MOCK_FAILURE",
                                  "priority": 1,
                                  "timeoutMs": 1000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String providerId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/model-access/providers/{id}/check", providerId)
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("check-failing-provider"))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.errorCode").value("MODEL_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.errorMessage").value("模拟模型供应商不可用"));
    }

    @Test
    void exportsSanitizedInvocationLogsAsCsv() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-export",
                                  "messages": [
                                    {"role": "user", "content": "生成导出验证请求"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/model-access/invocations/export")
                        .headers(authHeaders())
                        .param("projectId", "project-export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"wp2-invocations.csv\""))
                .andExpect(content().string(startsWith("invocationId,createdAt,projectId")))
                .andExpect(content().string(containsString("sensitivityLevel")))
                .andExpect(content().string(containsString("project-export")))
                .andExpect(content().string(containsString("INTERNAL")))
                .andExpect(content().string(containsString("local-echo-primary")))
                .andExpect(content().string(not(containsString("\"code\""))))
                .andExpect(content().string(not(containsString("promptPlaintext"))));
    }

    @Test
    void exposesCostAlertsAndDailyReport() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-cost",
                                  "applicationId": "app-cost",
                                  "messages": [
                                    {"role": "user", "content": "生成成本报表验证请求"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/model-access/cost/report")
                        .headers(authHeaders())
                        .param("projectId", "project-cost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].projectId").value("project-cost"))
                .andExpect(jsonPath("$.data.rows[0].applicationId").value("app-cost"))
                .andExpect(jsonPath("$.data.rows[0].succeeded").value(1))
                .andExpect(jsonPath("$.data.rows[0].totalCost").exists());

        mockMvc.perform(get("/api/v1/model-access/cost/alerts")
                        .headers(authHeaders())
                        .param("projectId", "project-cost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void updatesProviderRoutingAndCostConfiguration() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "local-adjustable",
                                  "providerType": "LOCAL_ECHO",
                                  "priority": 30,
                                  "timeoutMs": 1000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String providerId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/v1/model-access/providers/{id}", providerId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "local-adjustable-v2",
                                  "priority": 5,
                                  "timeoutMs": 2500,
                                  "inputCostPer1kTokens": 0.02,
                                  "outputCostPer1kTokens": 0.04
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("local-adjustable-v2"))
                .andExpect(jsonPath("$.data.priority").value(5))
                .andExpect(jsonPath("$.data.timeoutMs").value(2500))
                .andExpect(jsonPath("$.data.inputCostPer1kTokens").value(0.02))
                .andExpect(jsonPath("$.data.outputCostPer1kTokens").value(0.04));
    }

    @Test
    void validatesOpenAiCompatibleProviderConfigurationAtCreateTime() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "public-provider-without-secret-ref",
                                  "providerType": "OPENAI_COMPATIBLE",
                                  "baseUrl": "https://api.example.com",
                                  "apiKeyRef": "plain-secret"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "public-provider-invalid-url",
                                  "providerType": "OPENAI_COMPATIBLE",
                                  "baseUrl": "file:///tmp/model",
                                  "apiKeyRef": "env:MODEL_API_KEY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void routesByProjectCallerSensitivityCapabilityGroupAndLowestCost() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "private-expensive",
                                  "providerType": "LOCAL_ECHO",
                                  "routingGroup": "private",
                                  "capabilities": "CHAT,REQUIREMENT_PARSE",
                                  "priority": 1,
                                  "timeoutMs": 1000,
                                  "inputCostPer1kTokens": 0.05,
                                  "outputCostPer1kTokens": 0.20
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.routingGroup").value("private"))
                .andExpect(jsonPath("$.data.capabilities").value("CHAT,REQUIREMENT_PARSE"));

        mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "private-cheap",
                                  "providerType": "LOCAL_ECHO",
                                  "routingGroup": "private",
                                  "capabilities": "CHAT,REQUIREMENT_PARSE",
                                  "priority": 20,
                                  "timeoutMs": 1000,
                                  "inputCostPer1kTokens": 0.001,
                                  "outputCostPer1kTokens": 0.002
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders("wp4-document-input"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-routing",
                                  "messages": [
                                    {"role": "user", "content": "抽取需求候选"}
                                  ],
                                  "allowPublicModel": false,
                                  "sensitivityLevel": "INTERNAL",
                                  "capability": "REQUIREMENT_PARSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("private-cheap"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-routing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].providerName").value("private-cheap"))
                .andExpect(jsonPath("$.data.items[0].routingRuleName").value("wp4-private-low-cost"))
                .andExpect(jsonPath("$.data.items[0].routingGroup").value("private"))
                .andExpect(jsonPath("$.data.items[0].modelCapability").value("REQUIREMENT_PARSE"));
    }

    private String createPromptVersion(String changeNote, boolean activate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/model-access/prompts")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "promptKey": "case-design",
                                  "name": "用例设计",
                                  "content": "请基于 {{context}} 输出测试点",
                                  "changeNote": "%s",
                                  "activate": %s
                                }
                                """.formatted(changeNote, activate)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private org.springframework.test.web.servlet.ResultActions waitForAsyncJob(String jobId, String expectedStatus) throws Exception {
        org.springframework.test.web.servlet.ResultActions lastResult = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            lastResult = mockMvc.perform(get("/api/v1/model-access/invocations/jobs/{jobId}", jobId)
                    .headers(authHeaders()));
            MvcResult mvcResult = lastResult
                    .andExpect(status().isOk())
                    .andReturn();
            String status = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.status");
            if (expectedStatus.equals(status)) {
                return lastResult;
            }
            Thread.sleep(50);
        }
        return lastResult.andExpect(jsonPath("$.data.status").value(expectedStatus));
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        return authHeaders("wp5-test-design");
    }

    private org.springframework.http.HttpHeaders authHeaders(String callerService) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", callerService);
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }

    private String superAdminToken() {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "admin_user",
                "平台管理员",
                "admin@example.com",
                "$2a$10$test",
                false,
                1,
                List.of("SuperAdmin")
        )).accessToken();
    }

    private String tokenForRole(String role) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                role.toLowerCase() + "_user",
                role + " 用户",
                role.toLowerCase() + "@example.com",
                "{noop}unused",
                false,
                1,
                List.of(role)
        )).accessToken();
    }
}
