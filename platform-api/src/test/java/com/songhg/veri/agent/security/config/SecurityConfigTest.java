package com.songhg.veri.agent.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.asset.service-token=test-asset-token",
        "veri-agent.model-access.service-token=test-model-token",
        "veri-agent.document-input.service-token=test-document-token"
})
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsDocumentedPublicEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/asset/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void protectsManagementEndpointsByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/management/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsTrustedServiceTokenOnInternalAssetEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/asset/requirements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-asset-token")
                        .header("X-Caller-Service", "asset-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", startsWith("trc_")));
    }
}
