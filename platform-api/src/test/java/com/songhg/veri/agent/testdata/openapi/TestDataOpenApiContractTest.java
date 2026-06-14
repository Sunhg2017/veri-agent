package com.songhg.veri.agent.testdata.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
class TestDataOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsTestDataControlPlanePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets/{id}/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-sets/{id}/records'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools/{id}/disable'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/account-pools/{id}/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/accounts/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/leases'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/leases'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/leases/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/leases/{id}/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/leases/{id}/renew'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/leases/{id}/release'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-tasks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-tasks/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/data-tasks/{id}/retry'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-data/health'].get").exists());
    }
}
