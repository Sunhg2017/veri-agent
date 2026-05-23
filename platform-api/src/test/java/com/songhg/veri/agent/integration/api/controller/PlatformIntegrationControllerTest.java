package com.songhg.veri.agent.integration.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret",
        "veri-agent.integration.service-token=test-platform-service-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlatformIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesContextValidationForServiceCallers() throws Exception {
        mockMvc.perform(get("/api/v1/contexts/projects/project-001")
                        .header("Authorization", "Bearer test-platform-service-token")
                        .header("X-Caller-Service", "model-access")
                        .header("X-Delegated-User-Id", "user-001")
                        .param("include", "apps,environments,configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.resourceType").value("PROJECT"))
                .andExpect(jsonPath("$.data.resourceId").value("project-001"))
                .andExpect(jsonPath("$.data.sensitivityLevel").value("INTERNAL"))
                .andExpect(jsonPath("$.data.allowPublicModel").value(false))
                .andExpect(jsonPath("$.data.include[0]").value("apps"));
    }

    @Test
    void acceptsSanitizedAuditEventsFromServiceCallers() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
                        .header("Authorization", "Bearer test-platform-service-token")
                        .header("X-Caller-Service", "model-access")
                        .header("X-Delegated-User-Id", "user-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "MODEL_INVOKE",
                                  "resourceType": "MODEL_INVOCATION",
                                  "resourceId": "inv-001",
                                  "scopeType": "PROJECT",
                                  "scopeId": "project-001",
                                  "result": "SUCCEEDED",
                                  "reason": "WP2 model invocation",
                                  "afterJson": {
                                    "providerName": "local-echo-primary",
                                    "prompt_digest": "abc123"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsInvalidInternalServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/contexts/applications/app-001")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
