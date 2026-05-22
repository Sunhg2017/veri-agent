package com.songhg.veri.agent.modelaccess.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.model-access.service-token=test-model-token",
        "veri-agent.model-access.default-model=test-local-model",
        "veri-agent.model-access.daily-caller-service-cost-limit=0.001",
        "veri-agent.model-access.budget-estimated-output-tokens=100",
        "veri-agent.model-access.budget-overrun-action=FALLBACK",
        "veri-agent.model-access.budget-zone-id=UTC"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ModelAccessBudgetFallbackPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fallsBackToLowerCostProviderWhenBudgetOverrunActionAllowsDegrade() throws Exception {
        createProvider("budget-expensive", 1, "1.0", "1.0");
        createProvider("budget-cheap", 2, "0.0001", "0.0001");

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders("wp2-budget-fallback"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-budget-fallback",
                                  "messages": [
                                    {"role": "user", "content": "预算降级验证请求"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("budget-cheap"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(true));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders("wp2-budget-fallback"))
                        .param("actorService", "wp2-budget-fallback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].providerName").value("budget-cheap"))
                .andExpect(jsonPath("$.data.items[0].fallbackUsed").value(true));

        mockMvc.perform(get("/api/v1/model-access/cost/alerts")
                        .headers(authHeaders("wp2-budget-fallback"))
                        .param("actorService", "wp2-budget-fallback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scope").value("CALLER_SERVICE"))
                .andExpect(jsonPath("$.data[0].actorService").value("wp2-budget-fallback"))
                .andExpect(jsonPath("$.data[0].spentCost").exists());
    }

    private void createProvider(String name, int priority, String inputCost, String outputCost) throws Exception {
        mockMvc.perform(post("/api/v1/model-access/providers")
                        .headers(authHeaders("wp2-budget-fallback"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "providerType": "LOCAL_ECHO",
                                  "priority": %d,
                                  "timeoutMs": 1000,
                                  "inputCostPer1kTokens": %s,
                                  "outputCostPer1kTokens": %s
                                }
                                """.formatted(name, priority, inputCost, outputCost)))
                .andExpect(status().isCreated());
    }

    private org.springframework.http.HttpHeaders authHeaders(String callerService) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", callerService);
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }
}
