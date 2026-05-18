package com.songhg.veri.agent.modelaccess.api.controller;

import com.songhg.veri.agent.modelaccess.application.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.PlatformInvocationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.model-access.service-token=test-model-token",
        "veri-agent.model-access.default-model=test-local-model"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ModelAccessPlatformPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformContextClient platformContextClient;

    @Test
    void blocksPublicRoutingWhenPlatformPolicyDisallowsIt() throws Exception {
        when(platformContextClient.verifyInvocationContext(any(), any()))
                .thenReturn(new PlatformInvocationPolicy("INTERNAL", false));

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp1-policy",
                                  "messages": [
                                    {"role": "user", "content": "生成平台策略验证点"}
                                  ],
                                  "allowPublicModel": true,
                                  "sensitivityLevel": "PUBLIC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_POLICY_VIOLATION"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-wp1-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].sensitivityLevel").value("INTERNAL"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("MODEL_POLICY_VIOLATION"));
    }

    @Test
    void recordsStricterSensitivityFromPlatformPolicy() throws Exception {
        when(platformContextClient.verifyInvocationContext(any(), any()))
                .thenReturn(new PlatformInvocationPolicy("RESTRICTED", false));

        mockMvc.perform(post("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp1-sensitivity",
                                  "messages": [
                                    {"role": "user", "content": "生成平台敏感级别验证点"}
                                  ],
                                  "allowPublicModel": false,
                                  "sensitivityLevel": "INTERNAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("local-echo-primary"));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(authHeaders())
                        .param("projectId", "project-wp1-sensitivity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].sensitivityLevel").value("RESTRICTED"));
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", "wp5-test-design");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }
}
