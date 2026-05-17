package com.songhg.veri.agent.modelaccess.api;

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
        "veri-agent.model-access.platform-audit-enabled=true",
        "veri-agent.model-access.platform-api-service-token=test-platform-token",
        "veri-agent.model-access.platform-api-base-url=http://127.0.0.1:9"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ModelAccessAuditMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void auditWriteFailuresDoNotBlockInvocationAndAreCounted() throws Exception {
        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "project_id": "project-audit-metrics",
                                  "messages": [
                                    {"role": "user", "content": "生成审计失败指标验证请求"}
                                  ],
                                  "allow_public_model": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider_name").value("local-echo-primary"));

        mockMvc.perform(get("/actuator/metrics/veri.agent.model_access.platform.audit.events")
                        .headers(authHeaders())
                        .param("tag", "result:failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("veri.agent.model_access.platform.audit.events"))
                .andExpect(jsonPath("$.measurements[0].value").exists());
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", "wp5-test-design");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }
}
