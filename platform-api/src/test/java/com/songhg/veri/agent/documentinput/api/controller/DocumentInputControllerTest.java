package com.songhg.veri.agent.documentinput.api.controller;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.bootstrap.token=init-token",
        "veri-agent.auth.token-secret=test-auth-secret",
        "veri-agent.document-input.service-token=test-document-input-token",
        "veri-agent.asset.service-token=test-asset-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentInputControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/document-input/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("document-input"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.supportedSourceTypes").value(3))
                .andExpect(jsonPath("$.data.inputEnabled").value(true))
                .andExpect(jsonPath("$.data.webhookEnabled").value(true))
                .andExpect(jsonPath("$.data.modelParseEnabled").value(false))
                .andExpect(jsonPath("$.data.webhookMaxPayloadBytes").value(262144))
                .andExpect(jsonPath("$.data.batchActionLimit").value(100));
    }

    @Test
    void rejectsUnauthenticatedCalls() throws Exception {
        mockMvc.perform(get("/api/v1/document-input/sources"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/document-input/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsUserBearerTokenForConsoleRequests() throws Exception {
        String userToken = userAccessToken();

        mockMvc.perform(get("/api/v1/document-input/sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("index", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/api/v1/document-input/sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "console-text",
                                  "name": "Console Text",
                                  "sourceType": "TEXT",
                                  "endpointUrl": "https://example.test/console-text"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceCode").value("console-text"))
                .andExpect(jsonPath("$.data.name").value("Console Text"));
    }

    @Test
    void managesSourcesAndKeepsUnsupportedTypesPlanned() throws Exception {
        String sourceId = createSource("manual-text", "TEXT", null);

        mockMvc.perform(get("/api/v1/document-input/sources/{id}/health", sourceId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceCode").value("manual-text"))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.message").value("READY"));

        mockMvc.perform(put("/api/v1/document-input/sources/{id}", sourceId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "manual-text-v2",
                                  "name": "Manual Text V2",
                                  "sourceType": "TEXT",
                                  "status": "ENABLED",
                                  "defaultProjectId": "project-wp4",
                                  "description": "plain text input"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceCode").value("manual-text-v2"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.dataFlowSupported").value(true));

        createSource("pdf-backlog", "PDF", null);

        mockMvc.perform(get("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .param("index", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        mockMvc.perform(get("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .param("sourceType", "PDF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.data.items[0].dataFlowSupported").value(false));

        mockMvc.perform(get("/api/v1/document-input/sources/{id}/health", sourceId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataFlowSupported").value(true));
    }

    @Test
    void rejectsEnabledUnsupportedSourceTypes() throws Exception {
        mockMvc.perform(post("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "pdf-enabled",
                                  "name": "PDF Enabled",
                                  "sourceType": "PDF",
                                  "status": "ENABLED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void updatesFieldMappingAndImportsJsonRequirementsIntoAssets() throws Exception {
        mockMvc.perform(put("/api/v1/document-input/field-mapping")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Custom API mapping",
                                  "itemPath": "items",
                                  "titlePath": "name",
                                  "descriptionPath": "detail",
                                  "priorityPath": "level",
                                  "acceptanceCriteriaPath": "checks",
                                  "tagsPath": "labels"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemPath").value("items"))
                .andExpect(jsonPath("$.data.titlePath").value("name"));

        MvcResult result = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "CUSTOM_API",
                                  "title": "Custom import",
                                  "sourceRef": "REQ-BATCH-1",
                                  "content": "{\\"items\\":[{\\"name\\":\\"登录需求\\",\\"detail\\":\\"支持账号密码登录\\",\\"level\\":\\"P1\\",\\"checks\\":[\\"登录成功\\",\\"失败提示\\"],\\"labels\\":[\\"auth\\",\\"login\\"]}]}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalParsed").value(1))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.requirements[0].title").value("登录需求"))
                .andExpect(jsonPath("$.data.requirements[0].priority").value("HIGH"))
                .andReturn();

        String importId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        MvcResult candidates = mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].title").value("登录需求"))
                .andReturn();

        String candidateId = JsonPath.read(candidates.getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(put("/api/v1/document-input/candidates/{id}", candidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "登录需求",
                                  "description": "支持账号密码登录",
                                  "priority": "HIGH",
                                  "acceptanceCriteria": "登录成功,失败提示",
                                  "tags": ["auth", "login"],
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", candidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedBy").value("user-001"));

        MvcResult publish = mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCreated").value(1))
                .andExpect(jsonPath("$.data.publishedCount").value(1))
                .andExpect(jsonPath("$.data.createdRequirementIds", hasSize(1)))
                .andReturn();

        String requirementId = JsonPath.read(publish.getResponse().getContentAsString(),
                "$.data.createdRequirementIds[0]");

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("登录需求"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.tags").value("auth,login,document-input"));

        mockMvc.perform(get("/api/v1/document-input/imports/{id}", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCreated").value(1))
                .andExpect(jsonPath("$.data.publishedCount").value(1))
                .andExpect(jsonPath("$.data.createdRequirementIds", hasSize(1)));
    }

    @Test
    void importsMarkdownAndListsImportRecords() throws Exception {
        mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-001",
                                  "content": "## 支付成功通知\\\\nPriority: CRITICAL\\\\nAcceptance Criteria:\\\\n- 支付成功后发送通知\\\\n\\\\n## 支付失败提示\\\\nPriority: LOW"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalParsed").value(2))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.requirements[0].priority").value("CRITICAL"));

        mockMvc.perform(get("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .param("projectId", "project-wp4")
                        .param("index", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"));
    }

    @Test
    void supportsBatchCandidateActionPublishDryRunAndPublishRecords() throws Exception {
        MvcResult imported = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-batch-001",
                                  "content": "## 登录需求\\\\nPriority: HIGH\\\\n\\\\n## 退出需求\\\\nPriority: LOW"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        MvcResult candidates = mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        String firstCandidateId = JsonPath.read(candidates.getResponse().getContentAsString(), "$.data.items[0].id");
        String secondCandidateId = JsonPath.read(candidates.getResponse().getContentAsString(), "$.data.items[1].id");

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", firstCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/ignore", secondCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/document-input/candidates/batch-action")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CONFIRM",
                                  "candidateIds": ["%s", "%s"]
                                }
                                """.formatted(firstCandidateId, secondCandidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("CONFIRM"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeededCount").value(2))
                .andExpect(jsonPath("$.data.items[0].candidate.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", importId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": true,
                                  "candidateIds": ["%s", "%s"]
                                }
                                """.formatted(firstCandidateId, secondCandidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.plannedCreateCount").value(2))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].result").value("PLANNED"));

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", importId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.totalCreated").value(2))
                .andExpect(jsonPath("$.data.publishedCount").value(2))
                .andExpect(jsonPath("$.data.createdRequirementIds", hasSize(2)))
                .andExpect(jsonPath("$.data.records", hasSize(2)));

        mockMvc.perform(get("/api/v1/document-input/imports/{id}/publish-records", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].candidateStatus").value("PUBLISHED"));
    }

    @Test
    void handlesCustomApiWebhookIncrementalSync() throws Exception {
        createSource("custom-reqs", "CUSTOM_API", "project-wp4");
        String payload = """
                {
                  "projectId": "project-wp4",
                  "eventType": "requirement.created",
                  "eventVersion": "1.0",
                  "id": "REQ-9",
                  "title": "Webhook batch",
                  "requirements": [
                    {
                      "title": "Webhook 需求",
                      "description": "来自自研需求平台",
                      "priority": "LOW",
                      "tags": ["external", "sync"]
                    }
                  ]
                }
                """;
        HttpHeaders webhookHeaders = webhookHeaders(payload, "evt-001", "idem-001");

        MvcResult webhook = mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-reqs")
                        .headers(webhookHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceCode").value("custom-reqs"))
                .andExpect(jsonPath("$.data.sourceRef").value("REQ-9"))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andReturn();

        String importId = JsonPath.read(webhook.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-reqs")
                        .headers(webhookHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(importId));

        String changedPayload = payload.replace("Webhook 需求", "Webhook 需求 v2");
        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-reqs")
                        .headers(webhookHeaders(changedPayload, "evt-001", "idem-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changedPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-reqs")
                        .header("X-VA-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-VA-Signature", "bad-signature")
                        .header("X-VA-Event-Id", "evt-001")
                        .header("X-VA-Idempotency-Key", "idem-001")
                        .header("X-VA-Event-Version", "1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult events = mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-reqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.items[0].signatureStatus").value("VALID"))
                .andReturn();

        String eventRecordId = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[0].id");
        mockMvc.perform(get("/api/v1/document-input/webhook-events/{id}", eventRecordId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventType").value("requirement.created"));

        mockMvc.perform(post("/api/v1/document-input/webhook-events/{id}/replay", eventRecordId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-reqs")
                        .header("X-VA-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-VA-Signature", "bad-signature")
                        .header("X-VA-Event-Id", "evt-bad")
                        .header("X-VA-Idempotency-Key", "idem-bad")
                        .header("X-VA-Event-Version", "1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsWebhookMissingRequiredHeadersAndUnsupportedVersions() throws Exception {
        createSource("custom-secure", "CUSTOM_API", "project-wp4");
        String payload = """
                {
                  "projectId": "project-wp4",
                  "eventType": "requirement.created",
                  "id": "REQ-SECURE",
                  "requirements": [{"title": "安全头校验"}]
                }
                """;

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-secure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        HttpHeaders unsupportedVersionHeaders = webhookHeaders(payload, "evt-unsupported-version", "idem-unsupported-version");
        unsupportedVersionHeaders.set("X-VA-Event-Version", "2.0");
        String timestamp = unsupportedVersionHeaders.getFirst("X-VA-Timestamp");
        unsupportedVersionHeaders.set("X-VA-Signature", hmacSha256(String.join(
                ".",
                timestamp,
                "evt-unsupported-version",
                "idem-unsupported-version",
                payload
        )));

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-secure")
                        .headers(unsupportedVersionHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-secure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].eventVersion").value("2.0"));
    }

    @Test
    void recordsWebhookPayloadFailuresForReplayTriage() throws Exception {
        createSource("custom-invalid-json", "CUSTOM_API", "project-wp4");
        String payload = "{not-json";

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-invalid-json")
                        .headers(webhookHeaders(payload, "evt-invalid-json", "idem-invalid-json"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-invalid-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].signatureStatus").value("VALID"))
                .andExpect(jsonPath("$.data.items[0].errorMessage").value("webhook payload 不是合法 JSON"));
    }

    @Test
    void rejectsOversizedWebhookPayloadBeforeParsing() throws Exception {
        createSource("custom-large-payload", "CUSTOM_API", "project-wp4");
        String payload = """
                {
                  "projectId": "project-wp4",
                  "eventType": "requirement.created",
                  "eventVersion": "1.0",
                  "id": "REQ-LARGE",
                  "requirements": [
                    {
                      "title": "%s"
                    }
                  ]
                }
                """.formatted("A".repeat(270_000));

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-large-payload")
                        .headers(webhookHeaders(payload, "evt-large", "idem-large"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-large-payload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.items[0].signatureStatus").value("VALID"))
                .andExpect(jsonPath("$.data.items[0].errorMessage", startsWith("webhook payload 超过上限")));
    }

    private String createSource(String sourceCode, String sourceType, String defaultProjectId) throws Exception {
        String projectLine = defaultProjectId == null ? "" : "\"defaultProjectId\": \"%s\",".formatted(defaultProjectId);
        MvcResult result = mockMvc.perform(post("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "%s",
                                  "name": "%s source",
                                  "sourceType": "%s",
                                  %s
                                  "endpointUrl": "https://example.test/%s"
                                }
                                """.formatted(sourceCode, sourceCode, sourceType, projectLine, sourceCode)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private HttpHeaders documentInputHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-document-input-token");
        headers.set("X-Caller-Service", "wp4-document-input");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }

    private HttpHeaders assetHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-asset-token");
        headers.set("X-Caller-Service", "wp4-document-input");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
    }

    private HttpHeaders webhookHeaders(String payload, String eventId, String idempotencyKey) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-VA-Timestamp", timestamp);
        headers.set("X-VA-Signature", hmacSha256(String.join(".", timestamp, eventId, idempotencyKey, payload)));
        headers.set("X-VA-Event-Id", eventId);
        headers.set("X-VA-Idempotency-Key", idempotencyKey);
        headers.set("X-VA-Event-Version", "1.0");
        return headers;
    }

    private String hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("local-document-input-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String userAccessToken() throws Exception {
        bootstrapSuperAdmin();
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin_user",
                                  "password": "PlainPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private void bootstrapSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap/super-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bootstrapToken": "init-token",
                                  "username": "admin_user",
                                  "password": "PlainPassword123",
                                  "displayName": "平台管理员",
                                  "email": "admin@example.com"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
