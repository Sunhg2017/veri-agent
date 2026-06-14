package com.songhg.veri.agent.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

        mockMvc.perform(get("/api/v1/api-automation/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/v1/execution/health"))
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
    void asyncDispatcherDoesNotBypassSecurityRules() throws Exception {
        mockMvc.perform(get("/api/v1/management/users").with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesExplicitCorsConfigurationForPortalOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/execution/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,X-Trace-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Trace-Id"));
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
