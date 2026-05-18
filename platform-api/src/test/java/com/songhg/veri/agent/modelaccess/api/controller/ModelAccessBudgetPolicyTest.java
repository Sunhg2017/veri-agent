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
        "veri-agent.model-access.daily-project-cost-limit=0.000001",
        "veri-agent.model-access.budget-estimated-output-tokens=100",
        "veri-agent.model-access.budget-zone-id=UTC"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ModelAccessBudgetPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksInvocationBeforeProviderCallWhenProjectedProjectBudgetIsExceeded() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-budget-low",
                                  "messages": [
                                    {"role": "user", "content": "预算检查请求"}
                                  ],
                                  "allowPublicModel": false
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BUDGET_EXCEEDED"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-budget-low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.data.items[0].totalCost").value(0));

        mockMvc.perform(get("/api/v1/model-access/cost/alerts")
                        .headers(authHeaders())
                        .param("projectId", "project-budget-low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scope").value("PROJECT"))
                .andExpect(jsonPath("$.data[0].level").value("OK"));
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", "wp5-test-design");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }
}
