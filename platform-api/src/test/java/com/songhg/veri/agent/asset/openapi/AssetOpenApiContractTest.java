package com.songhg.veri.agent.asset.openapi;

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
        "veri-agent.asset.service-token=test-asset-token"
})
@AutoConfigureMockMvc
class AssetOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesWp3AssetContractWithStandardPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Veri Agent WP1 Platform API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/asset/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements/{id}/versions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/apis'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/apis'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/apis/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/apis/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/pages'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/pages'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/business-flows'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/business-flows'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases/{id}/versions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases/{id}/steps'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/links'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/links'].post").exists());
    }

    @Test
    void generatedContractKeepsWp3FieldsAndNoTenantScope() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        MatcherAssert.assertThat(openApi, containsString("projectId"));
        MatcherAssert.assertThat(openApi, containsString("sourceRef"));
        MatcherAssert.assertThat(openApi, containsString("acceptanceCriteria"));
        MatcherAssert.assertThat(openApi, containsString("changedFields"));
        MatcherAssert.assertThat(openApi, containsString("snapshot"));
        MatcherAssert.assertThat(openApi, not(containsString("tenant_id")));
        MatcherAssert.assertThat(openApi, not(containsString("tenantId")));
    }
}
