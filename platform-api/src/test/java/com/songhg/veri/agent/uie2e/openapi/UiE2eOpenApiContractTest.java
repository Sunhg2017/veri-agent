package com.songhg.veri.agent.uie2e.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
class UiE2eOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsUiE2eControlPlanePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/scenes'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/scenes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/scenes/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/scenes/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/scenes/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles/{id}/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles/{id}/submit-review'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles/{id}/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/bundles/{id}/reject'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/runs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/runs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/runs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/runs/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/runs/{id}/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/flaky-marks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ui-e2e/flaky-marks'].get").exists());
    }
}
