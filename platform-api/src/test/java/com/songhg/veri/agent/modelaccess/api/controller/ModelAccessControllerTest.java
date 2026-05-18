package com.songhg.veri.agent.modelaccess.api.controller;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.model-access.service-token=test-model-token",
        "veri-agent.model-access.default-model=test-local-model",
        "veri-agent.model-access.provider-circuit-failure-threshold=1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ModelAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/model-access/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("model-access"))
                .andExpect(jsonPath("$.data.enabledProviders").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void rejectsCallsWithoutServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/model-access/providers"))
                .andExpect(status().isForbidden());
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
        mockMvc.perform(post("/api/v1/model-access/providers")
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
                .andExpect(jsonPath("$.data.name").value("failing-primary"));

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-001",
                                  "messages": [
                                    {"role": "user", "content": "检查降级链路"}
                                  ],
                                  "allowPublicModel": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(true));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fallbackUsed").value(true));
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
                                  "allowPublicModel": true
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
                                  "allowPublicModel": true
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

    private org.springframework.http.HttpHeaders authHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", "wp5-test-design");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }
}
