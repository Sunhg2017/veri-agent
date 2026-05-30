package com.songhg.veri.agent.testdesign.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.test-design.service-token=test-design-token"
})
@AutoConfigureMockMvc
class TestDesignOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesWp5TestDesignContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/retry'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/candidates'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/publish-dry-run'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/publish'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/publish-records'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/review-records'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/review-records/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/review-records/export'].get.responses['200'].content['text/csv']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/quality/summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/quality/prompt-trend'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/report/audit-summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/report/audit-chain'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/report/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/report/export'].get.responses['200'].content['text/csv']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/export'].get.responses['200'].content['text/csv']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/{id}/confirm'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/{id}/reject'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/{id}/ignore'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/{id}/resolve-conflict'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/batch-resolve-conflicts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/batch-action'].post").exists());
    }

    @Test
    void generatedWp5ContractKeepsReferencesOnly() throws Exception {
        String openApi = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.hamcrest.MatcherAssert.assertThat(openApi, containsString("promptKey"));
        org.hamcrest.MatcherAssert.assertThat(openApi, containsString("modelInvocationId"));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("rawPrompt")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("promptPlaintext")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("secretValue")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("tenantId")));
    }
}
