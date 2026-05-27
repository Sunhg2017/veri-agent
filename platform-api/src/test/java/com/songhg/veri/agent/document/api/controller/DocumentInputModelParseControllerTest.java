package com.songhg.veri.agent.document.api.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        awaitImport(importId, "SUCCEEDED", 1, 1);
        MvcResult candidates = awaitCandidates(importId, "MODEL");
        mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].modelInvocationId").exists())
                .andExpect(jsonPath("$.data.items[0].modelProviderName").value("local-echo-primary"))
                .andExpect(jsonPath("$.data.items[0].confidence").value(0.86));

        String candidateId = JsonPath.read(candidates.getResponse().getContentAsString(), "$.data.items[0].id");
        mockMvc.perform(put("/api/v1/document-input/candidates/{id}", candidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "AI 登录需求",
                                  "description": "人工补充账号密码登录边界",
                                  "priority": "MEDIUM",
                                  "acceptanceCriteria": "登录成功；password=PlainSecret123；通知 user@example.com",
                                  "tags": ["ai", "login", "manual-review"],
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/v1/document-input/feedback-samples")
                        .headers(documentInputHeaders())
                        .param("candidateId", candidateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].candidateId").value(candidateId))
                .andExpect(jsonPath("$.data.items[0].parseSource").value("MODEL"))
                .andExpect(jsonPath("$.data.items[0].correctionType").value("MANUAL_EDIT"))
                .andExpect(jsonPath("$.data.items[0].curationStatus").value("READY_FOR_CORPUS"))
                .andExpect(jsonPath("$.data.items[0].changedFields", containsString("description")))
                .andExpect(jsonPath("$.data.items[0].changedFields", containsString("priority")))
                .andExpect(jsonPath("$.data.items[0].afterSnapshot.acceptanceCriteria", containsString("[SECRET]")))
                .andExpect(jsonPath("$.data.items[0].afterSnapshot.acceptanceCriteria", containsString("[EMAIL]")))
                .andExpect(jsonPath("$.data.items[0].afterSnapshot.acceptanceCriteria", not(containsString("PlainSecret123"))));

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
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        awaitImport(importId, "SUCCEEDED", 1, 1);
        awaitCandidates(importId, "RULE");
        mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
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

    private MvcResult awaitImport(
            String importId,
            String expectedStatus,
            int expectedTotalParsed,
            long expectedPendingCount
    ) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/imports/{id}", importId)
                                .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value(expectedStatus))
                        .andExpect(jsonPath("$.data.totalParsed").value(expectedTotalParsed))
                        .andExpect(jsonPath("$.data.pendingCount").value(expectedPendingCount))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("导入任务未达到期望状态") : lastError;
    }

    private MvcResult awaitCandidates(String importId, String expectedParseSource) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.items[0].parseSource").value(expectedParseSource))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("候选需求未生成") : lastError;
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
