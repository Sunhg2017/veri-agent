package com.songhg.veri.agent.documentinput.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.document-input.service-token=test-document-input-token",
        "veri-agent.document-input.input-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentInputFeatureFlagTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesDisabledHealthAndRejectsProtectedInputOperations() throws Exception {
        mockMvc.perform(get("/api/v1/document-input/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.inputEnabled").value(false));

        mockMvc.perform(post("/api/v1/document-input/imports")
                        .header("Authorization", "Bearer test-document-input-token")
                        .header("X-Caller-Service", "wp4-document-input")
                        .header("X-Delegated-User-Id", "user-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "content": "## 登录需求"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("WP4 文档输入已关闭"));
    }
}
