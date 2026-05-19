package com.songhg.veri.agent.documentinput.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.document-input.service-token=test-document-input-token"
})
@AutoConfigureMockMvc
class DocumentInputOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesWp4DocumentInputContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/sources'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/sources'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/sources/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/sources/{id}/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/field-mapping'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/field-mapping'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports/multipart'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports/{id}/candidates'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports/{id}/publish'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/imports/{id}/publish-records'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/candidates/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/candidates/{id}/confirm'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/candidates/{id}/ignore'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/candidates/batch-action'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/webhook-events'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/webhook-events/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/webhook-events/{id}/replay'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/document-input/webhooks/{sourceCode}'].post").exists());
    }
}
