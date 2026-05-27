package com.songhg.veri.agent.document.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.document.application.DocumentWebhookAutoRetryService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.document-input.service-token=test-document-input-token",
        "veri-agent.document-input.webhook-secret=local-document-input-webhook-secret",
        "veri-agent.asset.service-token=test-asset-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentInputControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private DocumentWebhookAutoRetryService autoRetryService;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/document-input/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("document-input"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.supportedSourceTypes").value(6))
                .andExpect(jsonPath("$.data.inputEnabled").value(true))
                .andExpect(jsonPath("$.data.webhookEnabled").value(true))
                .andExpect(jsonPath("$.data.modelParseEnabled").value(false))
                .andExpect(jsonPath("$.data.webhookMaxPayloadBytes").value(262144))
                .andExpect(jsonPath("$.data.importMaxContentBytes").value(16777216))
                .andExpect(jsonPath("$.data.documentBinaryMaxBytes").value(10485760))
                .andExpect(jsonPath("$.data.ocrConfigured").value(false))
                .andExpect(jsonPath("$.data.ocrTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.data.ocrMaxOutputChars").value(20000))
                .andExpect(jsonPath("$.data.ocrMaxConcurrentProcesses").value(2))
                .andExpect(jsonPath("$.data.ocrAvailablePermits").value(2))
                .andExpect(jsonPath("$.data.ocrWorkerMode").value("LOCAL_COMMAND"))
                .andExpect(jsonPath("$.data.ocrRemoteWorkerConfigured").value(false))
                .andExpect(jsonPath("$.data.ocrWorkerTokenConfigured").value(false))
                .andExpect(jsonPath("$.data.ocrLocalCommandFallbackEnabled").value(true))
                .andExpect(jsonPath("$.data.ocrLocalCommandExecutionAllowed").value(true))
                .andExpect(jsonPath("$.data.batchActionLimit").value(100))
                .andExpect(jsonPath("$.data.webhookIpAllowlistEnabled").value(false))
                .andExpect(jsonPath("$.data.webhookTrustedProxyCidrsConfigured").value(false))
                .andExpect(jsonPath("$.data.webhookRateLimitEnabled").value(false))
                .andExpect(jsonPath("$.data.webhookRateLimitMaxRequests").value(0))
                .andExpect(jsonPath("$.data.webhookRateLimitWindowSeconds").value(60))
                .andExpect(jsonPath("$.data.binaryMimeValidationEnabled").value(true))
                .andExpect(jsonPath("$.data.pdfMaxPages").value(0))
                .andExpect(jsonPath("$.data.pdfMaxParseMillis").value(0))
                .andExpect(jsonPath("$.data.malwareScanEnabled").value(false))
                .andExpect(jsonPath("$.data.malwareScanTimeoutSeconds").value(15))
                .andExpect(jsonPath("$.data.malwareScanMaxConcurrentProcesses").value(2))
                .andExpect(jsonPath("$.data.malwareScanAvailablePermits").value(2))
                .andExpect(jsonPath("$.data.webhookSecretCacheEnabled").value(true))
                .andExpect(jsonPath("$.data.webhookSecretCacheTtlSeconds").value(60))
                .andExpect(jsonPath("$.data.webhookSecretRotationOverlapSeconds").value(300))
                .andExpect(jsonPath("$.data.webhookSecretCacheSize").value(0))
                .andExpect(jsonPath("$.data.externalSecretProvider.providerType").value("VAULT_KMS"))
                .andExpect(jsonPath("$.data.externalSecretProvider.configured").value(false))
                .andExpect(jsonPath("$.data.externalSecretProvider.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.externalSecretProvider.lastErrorMessage", containsString("未启用")));

        mockMvc.perform(get("/actuator/metrics/veri.agent.document_input.secret_provider.health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("veri.agent.document_input.secret_provider.health"));
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
                                  "eventVersion": "1.0",
                                  "mappingVersion": "default",
                                  "description": "plain text input"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceCode").value("manual-text-v2"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.eventVersion").value("1.0"))
                .andExpect(jsonPath("$.data.mappingVersion").value("default"))
                .andExpect(jsonPath("$.data.dataFlowSupported").value(true));

        createSource("pdf-source", "PDF", null);
        createSource("confluence-backlog", "CONFLUENCE", null);

        mockMvc.perform(get("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .param("index", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(3)));

        mockMvc.perform(get("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .param("sourceType", "PDF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("ENABLED"))
                .andExpect(jsonPath("$.data.items[0].dataFlowSupported").value(true));

        mockMvc.perform(get("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .param("sourceType", "CONFLUENCE"))
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
                                  "sourceCode": "confluence-enabled",
                                  "name": "Confluence Enabled",
                                  "sourceType": "CONFLUENCE",
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
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andReturn();

        String importId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        awaitImport(importId, "SUCCEEDED", 1, 1);

        MvcResult candidates = awaitCandidates(importId, 1);
        assertThat(JsonPath.<String>read(candidates.getResponse().getContentAsString(), "$.data.items[0].status"))
                .isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(candidates.getResponse().getContentAsString(), "$.data.items[0].title"))
                .isEqualTo("登录需求");

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
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"))
                .andReturn();

        publish = awaitPublishedImport(importId, 1, 1);
        String requirementId = JsonPath.read(publish.getResponse().getContentAsString(),
                "$.data.createdRequirementIds[0]");

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("登录需求"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.source").value("IMPORT"))
                .andExpect(jsonPath("$.data.sourceRef").value("REQ-BATCH-1#1"))
                .andExpect(jsonPath("$.data.acceptanceCriteria").value("登录成功,失败提示"))
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
        MvcResult imported = mockMvc.perform(post("/api/v1/document-input/imports")
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
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        awaitImport(importId, "SUCCEEDED", 2, 2);

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
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andReturn();

        String importId = JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");
        awaitImport(importId, "SUCCEEDED", 2, 2);
        MvcResult candidates = awaitCandidates(importId, 2);
        String firstCandidateId = JsonPath.read(candidates.getResponse().getContentAsString(), "$.data.items[0].id");
        String secondCandidateId = JsonPath.read(candidates.getResponse().getContentAsString(), "$.data.items[1].id");

        mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                        .queryParam("status", "PENDING")
                        .queryParam("sourceRef", "md-batch-001")
                        .queryParam("keyword", "退出")
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("退出需求"));

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
                                  "candidates": [
                                    {"id": "%s", "version": 0},
                                    {"id": "%s", "version": 0}
                                  ]
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
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"))
                .andExpect(jsonPath("$.data.records", hasSize(2)));

        awaitPublishedImport(importId, 2, 2);

        mockMvc.perform(get("/api/v1/document-input/imports/{id}/publish-records", importId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].candidateStatus").value("PUBLISHED"));
    }

    @Test
    void previewsAndPublishesUpdatesForRepeatedExternalRequirementIds() throws Exception {
        MvcResult firstImport = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-diff-001",
                                  "sourceUrl": "https://docs.example.test/req/md-diff-001",
                                  "content": "## 登录需求\\\\n旧描述\\\\nPriority: MEDIUM\\\\nTags: auth\\\\nAcceptance Criteria:\\\\n- 旧标准"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String firstImportId = JsonPath.read(firstImport.getResponse().getContentAsString(), "$.data.id");
        String firstCandidateId = JsonPath.read(awaitCandidates(firstImportId, 1)
                .getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", firstCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", firstImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"));
        MvcResult firstPublish = awaitPublishedImport(firstImportId, 1, 1);
        String requirementId = JsonPath.read(firstPublish.getResponse().getContentAsString(),
                "$.data.createdRequirementIds[0]");

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].changeType").value("CREATE"))
                .andExpect(jsonPath("$.data[0].snapshot.description").value("旧描述"));

        MvcResult secondImport = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-diff-001",
                                  "sourceUrl": "https://docs.example.test/req/md-diff-001-v2",
                                  "content": "## 登录需求\\\\n新描述\\\\nPriority: HIGH\\\\nTags: auth, document-input, login\\\\nAcceptance Criteria:\\\\n- 新标准"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String secondImportId = JsonPath.read(secondImport.getResponse().getContentAsString(), "$.data.id");
        String secondCandidateId = JsonPath.read(awaitCandidates(secondImportId, 1)
                .getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", secondCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", secondImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": true,
                                  "candidateIds": ["%s"]
                                }
                                """.formatted(secondCandidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.plannedCreateCount").value(0))
                .andExpect(jsonPath("$.data.plannedUpdateCount").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.data.records[0].assetRequirementId").value(requirementId))
                .andExpect(jsonPath("$.data.records[0].existingRequirementId").value(requirementId))
                .andExpect(jsonPath("$.data.records[0].diffSummary", containsString("description")))
                .andExpect(jsonPath("$.data.records[0].diffSummary", containsString("acceptanceCriteria")));

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", secondImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"));
        assertThat(JsonPath.<String>read(
                awaitPublishedImport(secondImportId, 1, 1).getResponse().getContentAsString(),
                "$.data.createdRequirementIds[0]"
        )).isEqualTo(requirementId);

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("登录需求"))
                .andExpect(jsonPath("$.data.description").value("新描述"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.sourceRef").value("md-diff-001#1"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://docs.example.test/req/md-diff-001-v2"))
                .andExpect(jsonPath("$.data.acceptanceCriteria").value("新标准"))
                .andExpect(jsonPath("$.data.tags", containsString("auth")))
                .andExpect(jsonPath("$.data.tags", containsString("login")))
                .andExpect(jsonPath("$.data.tags", containsString("document-input")));

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("UPSERT"))
                .andExpect(jsonPath("$.data[0].changedFields", hasSize(5)))
                .andExpect(jsonPath("$.data[0].diff.description.after").value("新描述"))
                .andExpect(jsonPath("$.data[0].diff.acceptanceCriteria.after").value("新标准"))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].changeType").value("CREATE"));

        MvcResult thirdImport = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-diff-001",
                                  "sourceUrl": "https://docs.example.test/req/md-diff-001-v2",
                                  "content": "## 登录需求\\\\n新描述\\\\nPriority: HIGH\\\\nTags: auth, document-input, login\\\\nAcceptance Criteria:\\\\n- 新标准"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String thirdImportId = JsonPath.read(thirdImport.getResponse().getContentAsString(), "$.data.id");
        String thirdCandidateId = JsonPath.read(awaitCandidates(thirdImportId, 1)
                .getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", thirdCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", thirdImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": true,
                                  "candidateIds": ["%s"]
                                }
                                """.formatted(thirdCandidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.plannedUpdateCount").value(0))
                .andExpect(jsonPath("$.data.linkedExistingCount").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("LINK_EXISTING"))
                .andExpect(jsonPath("$.data.records[0].existingRequirementId").value(requirementId));

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", thirdImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"));
        assertThat(JsonPath.<String>read(
                awaitPublishedImport(thirdImportId, 1, 1).getResponse().getContentAsString(),
                "$.data.createdRequirementIds[0]"
        )).isEqualTo(requirementId);

        mockMvc.perform(get("/api/v1/asset/requirements/{id}/versions", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("UPSERT"));
    }

    @Test
    void blocksAutomaticUpdateWhenExistingImportedRequirementIsReviewed() throws Exception {
        MvcResult firstImport = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-review-conflict",
                                  "content": "## 审批需求\\\\n旧描述\\\\nPriority: MEDIUM\\\\nTags: review\\\\nAcceptance Criteria:\\\\n- 旧标准"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String firstImportId = JsonPath.read(firstImport.getResponse().getContentAsString(), "$.data.id");
        String firstCandidateId = JsonPath.read(awaitCandidates(firstImportId, 1)
                .getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", firstCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", firstImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"));
        MvcResult firstPublish = awaitPublishedImport(firstImportId, 1, 1);
        String requirementId = JsonPath.read(firstPublish.getResponse().getContentAsString(),
                "$.data.createdRequirementIds[0]");

        mockMvc.perform(put("/api/v1/asset/requirements/{id}", requirementId)
                        .headers(assetHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "审批需求",
                                  "description": "人工评审后的描述",
                                  "status": "APPROVED",
                                  "priority": "MEDIUM",
                                  "tags": "review,manual"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        MvcResult secondImport = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "MARKDOWN",
                                  "sourceRef": "md-review-conflict",
                                  "content": "## 审批需求\\\\n模型解析出的新描述\\\\nPriority: HIGH\\\\nTags: review, ai\\\\nAcceptance Criteria:\\\\n- 新标准"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String secondImportId = JsonPath.read(secondImport.getResponse().getContentAsString(), "$.data.id");
        String secondCandidateId = JsonPath.read(awaitCandidates(secondImportId, 1)
                .getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/document-input/candidates/{id}/confirm", secondCandidateId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", secondImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dryRun": true,
                                  "candidateIds": ["%s"]
                                }
                                """.formatted(secondCandidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.plannedUpdateCount").value(0))
                .andExpect(jsonPath("$.data.conflictCount").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("CONFLICT_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].result").value("CONFLICT"))
                .andExpect(jsonPath("$.data.records[0].existingRequirementId").value(requirementId))
                .andExpect(jsonPath("$.data.records[0].diffSummary", containsString("description")))
                .andExpect(jsonPath("$.data.records[0].errorMessage", containsString("APPROVED")));

        mockMvc.perform(post("/api/v1/document-input/imports/{id}/publish", secondImportId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISH_QUEUED"));
        awaitPublishRecordStatus(secondImportId, "PUBLISH_FAILED", "FAILED");

        mockMvc.perform(get("/api/v1/asset/requirements/{id}", requirementId)
                        .headers(assetHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.description").value("人工评审后的描述"))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.data.tags").value("review,manual"));
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
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalCreated").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andReturn();

        String importId = JsonPath.read(webhook.getResponse().getContentAsString(), "$.data.id");
        awaitImport(importId, "SUCCEEDED", 1, 1);

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

        MvcResult events = awaitWebhookEvent("custom-reqs", "PROCESSED");
        assertThat(JsonPath.<String>read(events.getResponse().getContentAsString(), "$.data.items[0].signatureStatus"))
                .isEqualTo("VALID");

        String eventRecordId = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[0].id");
        String eventSourceId = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[0].sourceId");
        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceId", eventSourceId)
                        .param("eventType", "requirement.created")
                        .param("status", "PROCESSED")
                        .param("receivedFrom", "2026-01-01T00:00:00Z")
                        .param("receivedTo", "2999-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(eventRecordId));

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
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("webhook 签名无效")))
                .andExpect(jsonPath("$.message", containsString("secretRef")));

        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-reqs")
                        .param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].signatureStatus").value("INVALID"))
                .andExpect(jsonPath("$.data.items[0].errorMessage", containsString("webhook 签名无效")))
                .andExpect(jsonPath("$.data.items[0].errorMessage", containsString("raw body")));
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"));

        MvcResult events = awaitWebhookEvent("custom-secure", "FAILED");
        assertThat(JsonPath.<String>read(events.getResponse().getContentAsString(), "$.data.items[0].eventVersion"))
                .isEqualTo("2.0");
    }

    @Test
    void rejectsWebhookUnknownDisabledAndExpiredSources() throws Exception {
        String payload = """
                {
                  "projectId": "project-wp4",
                  "eventType": "requirement.created",
                  "eventVersion": "1.0",
                  "id": "REQ-SECURITY",
                  "requirements": [{"title": "安全负例"}]
                }
                """;

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-missing")
                        .headers(webhookHeaders(payload, "evt-missing", "idem-missing"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        String disabledSourceId = createSource("custom-disabled", "CUSTOM_API", "project-wp4");
        mockMvc.perform(put("/api/v1/document-input/sources/{id}", disabledSourceId)
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "custom-disabled",
                                  "name": "custom-disabled source",
                                  "sourceType": "CUSTOM_API",
                                  "status": "DISABLED",
                                  "defaultProjectId": "project-wp4",
                                  "secretRef": "secret://wp4/custom-disabled",
                                  "eventVersion": "1.0",
                                  "mappingVersion": "default",
                                  "endpointUrl": "https://example.test/custom-disabled"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-disabled")
                        .headers(webhookHeaders(payload, "evt-disabled", "idem-disabled"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        createSource("custom-expired", "CUSTOM_API", "project-wp4");
        String expiredTimestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-expired")
                        .headers(webhookHeaders(payload, "evt-expired", "idem-expired", expiredTimestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.items[0].signatureStatus").value("EXPIRED"));
    }

    @Test
    void recordsWebhookPayloadFailuresForReplayTriage() throws Exception {
        createSource("custom-invalid-json", "CUSTOM_API", "project-wp4");
        String payload = "{not-json";

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-invalid-json")
                        .headers(webhookHeaders(payload, "evt-invalid-json", "idem-invalid-json"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"));

        MvcResult events = awaitWebhookEvent("custom-invalid-json", "FAILED");
        assertThat(JsonPath.<String>read(events.getResponse().getContentAsString(), "$.data.items[0].signatureStatus"))
                .isEqualTo("VALID");
        assertThat(JsonPath.<String>read(events.getResponse().getContentAsString(), "$.data.items[0].errorMessage"))
                .isEqualTo("webhook payload 不是合法 JSON");

        String eventRecordId = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[0].id");
        mockMvc.perform(post("/api/v1/document-input/webhook-events/{id}/replay", eventRecordId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        MvcResult replayed = awaitWebhookEventById(eventRecordId, "FAILED", 1);
        assertThat(JsonPath.<String>read(replayed.getResponse().getContentAsString(), "$.data.replayBy"))
                .isEqualTo("user-001");
        assertThat(JsonPath.<String>read(replayed.getResponse().getContentAsString(), "$.data.replayTraceId"))
                .startsWith("trc_");
    }

    @Test
    void autoRetriesValidFailedWebhookEventsUntilDeadLetter() throws Exception {
        createSource("custom-auto-retry", "CUSTOM_API", "project-wp4");
        String payload = "{not-json";

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-auto-retry")
                        .headers(webhookHeaders(payload, "evt-auto-retry", "idem-auto-retry"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"));

        MvcResult events = awaitWebhookEvent("custom-auto-retry", "FAILED");

        String eventRecordId = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[0].id");
        DocumentWebhookAutoRetryService.AutoRetryResult firstRetry = autoRetryService.retryNow();
        awaitWebhookEventById(eventRecordId, "FAILED", 1);
        DocumentWebhookAutoRetryService.AutoRetryResult secondRetry = autoRetryService.retryNow();
        awaitWebhookEventById(eventRecordId, "FAILED", 2);
        DocumentWebhookAutoRetryService.AutoRetryResult thirdRetry = autoRetryService.retryNow();
        awaitWebhookEventById(eventRecordId, "DEAD_LETTER", 3);
        DocumentWebhookAutoRetryService.AutoRetryResult fourthRetry = autoRetryService.retryNow();

        assertAutoRetryResult(firstRetry, 1, 1, 0);
        assertAutoRetryResult(secondRetry, 1, 1, 0);
        assertAutoRetryResult(thirdRetry, 1, 1, 0);
        assertAutoRetryResult(fourthRetry, 0, 0, 0);

        mockMvc.perform(get("/api/v1/document-input/webhook-events/{id}", eventRecordId)
                        .headers(documentInputHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.data.retryCount").value(3))
                .andExpect(jsonPath("$.data.replayBy").value("system"))
                .andExpect(jsonPath("$.data.replayTraceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.errorMessage").value("webhook payload 不是合法 JSON"));
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
                                  "secretRef": "secret://wp4/%s",
                                  "eventVersion": "1.0",
                                  "mappingVersion": "default",
                                  "endpointUrl": "https://example.test/%s"
                                }
                                """.formatted(sourceCode, sourceCode, sourceType, projectLine, sourceCode, sourceCode)))
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
        return webhookHeaders(payload, eventId, idempotencyKey, String.valueOf(Instant.now().getEpochSecond()));
    }

    private HttpHeaders webhookHeaders(String payload, String eventId, String idempotencyKey, String timestamp) throws Exception {
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

    private String userAccessToken() {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "admin_user",
                "平台管理员",
                "admin@example.com",
                "$2a$10$test",
                false,
                1,
                List.of("SuperAdmin")
        )).accessToken();
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

    private MvcResult awaitCandidates(String importId, int expectedTotal) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                                .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.total").value(expectedTotal))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("候选需求未生成") : lastError;
    }

    private MvcResult awaitPublishedImport(
            String importId,
            int expectedTotalCreated,
            long expectedPublishedCount
    ) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/imports/{id}", importId)
                                .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.totalCreated").value(expectedTotalCreated))
                        .andExpect(jsonPath("$.data.publishedCount").value(expectedPublishedCount))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("发布任务未完成") : lastError;
    }

    private MvcResult awaitWebhookEvent(
            String sourceCode,
            String expectedStatus
    ) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/webhook-events")
                                .headers(documentInputHeaders())
                                .param("sourceCode", sourceCode))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.items[0].status").value(expectedStatus))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("Webhook 事件未达到期望状态") : lastError;
    }

    private MvcResult awaitWebhookEventById(
            String eventId,
            String expectedStatus,
            int expectedRetryCount
    ) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/webhook-events/{id}", eventId)
                                .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value(expectedStatus))
                        .andExpect(jsonPath("$.data.retryCount").value(expectedRetryCount))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("Webhook 事件重试状态未达到期望值") : lastError;
    }

    private MvcResult awaitPublishRecordStatus(
            String importId,
            String candidateStatus,
            String result
    ) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                return mockMvc.perform(get("/api/v1/document-input/imports/{id}/publish-records", importId)
                                .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items[0].candidateStatus").value(candidateStatus))
                        .andExpect(jsonPath("$.data.items[0].result").value(result))
                        .andReturn();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("发布记录未达到期望状态") : lastError;
    }

    private void assertAutoRetryResult(
            DocumentWebhookAutoRetryService.AutoRetryResult result,
            int attempted,
            int succeeded,
            int failed
    ) {
        org.assertj.core.api.Assertions.assertThat(result.attempted()).isEqualTo(attempted);
        org.assertj.core.api.Assertions.assertThat(result.succeeded()).isEqualTo(succeeded);
        org.assertj.core.api.Assertions.assertThat(result.failed()).isEqualTo(failed);
    }
}
