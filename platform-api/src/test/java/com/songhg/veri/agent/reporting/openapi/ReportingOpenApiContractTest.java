package com.songhg.veri.agent.reporting.openapi;

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
class ReportingOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsReportingControlPlanePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/reports/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/compare'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/retry'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/diagnoses'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/diagnoses/latest'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/defect-drafts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/defect-drafts/{draftId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/{id}/exports/{exportId}/download'].get").exists());
    }
}
