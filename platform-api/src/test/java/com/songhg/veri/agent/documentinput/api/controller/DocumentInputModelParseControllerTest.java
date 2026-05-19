package com.songhg.veri.agent.documentinput.api.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.document-input.service-token=test-document-input-token",
        "veri-agent.document-input.model-parse-enabled=true",
        "veri-agent.model-access.service-token=test-model-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentInputModelParseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importsWithWp2ModelAssistedCandidatesWhenEnabled() throws Exception {
        MvcResult imported = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4-ai",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "AI-PRD-1",
                                  "content": "## AI 登录需求\\\\nPriority: HIGH\\\\nTags: ai, login\\\\nAcceptance Criteria:\\\\n- 登录成功"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalParsed").value(1))
                .andExpect(jsonPath("$.data.requirements[0].parseSource").value("MODEL"))
                .andExpect(jsonPath("$.data.requirements[0].modelInvocationId").exists())
                .andExpect(jsonPath("$.data.requirements[0].modelProviderName").value("local-echo-primary"))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].parseSource").value("MODEL"))
                .andExpect(jsonPath("$.data.items[0].modelInvocationId").exists())
                .andExpect(jsonPath("$.data.items[0].modelProviderName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.items[0].confidence").value(0.86));

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(modelHeaders())
                        .param("projectId", "project-wp4-ai")
                        .param("actorService", "wp4-document-input"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].promptKey").value("wp4-document-requirement-parse"))
                .andExpect(jsonPath("$.data.items[0].promptDigest").exists());
    }

    @Test
    void fallsBackToRuleParserWhenWp2SensitiveGuardBlocksModelParsing() throws Exception {
        MvcResult imported = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4-ai-sensitive",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "AI-PRD-SENSITIVE",
                                  "content": "## 敏感需求\\\\npassword=PlainSecret123 需要被阻断模型调用"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalParsed").value(1))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].parseSource").value("RULE"))
                .andExpect(jsonPath("$.data.items[0].modelInvocationId").doesNotExist());

        mockMvc.perform(get("/api/v1/model-access/invocations")
                        .headers(modelHeaders())
                        .param("projectId", "project-wp4-ai-sensitive")
                        .param("actorService", "wp4-document-input"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("SENSITIVE_CONTENT_BLOCKED"))
                .andExpect(jsonPath("$.data.items[0].requestPreview").exists());
    }

    private HttpHeaders documentInputHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-document-input-token");
        headers.set("X-Caller-Service", "wp4-document-input");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }

    private HttpHeaders modelHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-model-token");
        headers.set("X-Caller-Service", "wp4-document-input");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }
}
