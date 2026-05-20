package com.songhg.veri.agent.modelaccess.openapi;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.model-access.service-token=test-model-token",
        "veri-agent.model-access.default-model=test-local-model"
})
@AutoConfigureMockMvc
class ModelAccessOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesWp2ModelAccessContractWithBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Veri Agent WP1 Platform API"))
                .andExpect(jsonPath("$.info.version").value("0.1.0"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/model-access/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/providers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/providers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/providers/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/providers/{id}/check'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/providers/{id}/resilience'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/providers/{id}/circuit/reset'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/prompts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/prompts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/invocations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/invocations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/invocations/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/invocations/summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/cost/alerts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/model-access/cost/report'].get").exists());
    }

    @Test
    void generatedContractDoesNotExposeSecretPlaintextFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(openApi, containsString("apiKeyRef"));
        MatcherAssert.assertThat(openApi, not(containsString("apiKeyValue")));
        MatcherAssert.assertThat(openApi, not(containsString("secretValue")));
        MatcherAssert.assertThat(openApi, not(containsString("promptPlaintext")));
    }

    @Test
    void generatedContractDoesNotReintroduceTenantScope() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(openApi, not(containsString("tenant_id")));
        MatcherAssert.assertThat(openApi, not(containsString("tenantId")));
        MatcherAssert.assertThat(openApi, not(containsString("TENANT")));
    }

    @Test
    void generatedContractKeepsInvocationAuditFiltersAndCsvExportVisible() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(openApi, containsString("sensitivityLevel"));
        MatcherAssert.assertThat(openApi, containsString("actorService"));
        MatcherAssert.assertThat(openApi, containsString("/api/v1/model-access/invocations/export"));
        MatcherAssert.assertThat(openApi, containsString("/api/v1/model-access/cost/alerts"));
        MatcherAssert.assertThat(openApi, containsString("/api/v1/model-access/cost/report"));
        MatcherAssert.assertThat(openApi, containsString("ProviderResilienceResponse"));
        MatcherAssert.assertThat(openApi, containsString("text/csv"));
    }
}
