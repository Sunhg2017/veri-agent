package com.songhg.veri.agent.testdata.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.test-data.record-max-count=2",
        "veri-agent.test-data.record-summary-max-bytes=512"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestDataSetControllerTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);
    private static final String DIGEST_D = "d".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsDataSetsWithoutAuthenticationAndProjectScope() throws Exception {
        mockMvc.perform(get("/api/v1/test-data/data-sets"))
                .andExpect(status().isForbidden());

        String projectToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createsListsImportsUpdatesAndArchivesDataSetWithoutRawPayload() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDataSetRequest("dataset-alpha"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.projectId").value("project-alpha"))
                .andExpect(jsonPath("$.data.applicationId").value("app-alpha"))
                .andExpect(jsonPath("$.data.environmentId").value("env-staging"))
                .andExpect(jsonPath("$.data.code").value("dataset-alpha"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.schema.fields[0].name").value("customerId"))
                .andExpect(jsonPath("$.data.policy.rawRecordPayloadStored").value(false))
                .andExpect(jsonPath("$.data.policy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.records", hasSize(0)))
                .andExpect(content().string(not(containsString("secret://wp8/raw-source"))))
                .andExpect(content().string(not(containsString("raw-ssn-1234"))))
                .andReturn();

        UUID dataSetId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("keyword", "customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(dataSetId.toString()))
                .andExpect(jsonPath("$.data.items[0].recordCount").value(0));

        mockMvc.perform(post("/api/v1/test-data/data-sets/{id}/records", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRecordsRequest(
                                record("customer:001", DIGEST_A, DIGEST_B),
                                record("customer:002", DIGEST_C, null)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].recordDigest").value(DIGEST_A))
                .andExpect(jsonPath("$.data.records[0].maskedSummary.customerEmail").value("c***@example.test"))
                .andExpect(jsonPath("$.data.policy.rawRecordPayloadStored").value(false))
                .andExpect(content().string(not(containsString("secret://wp8/raw-source"))))
                .andExpect(content().string(not(containsString("raw-ssn-1234"))));

        String readOnlyToken = userAccessToken(List.of("Tester@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/test-data/data-sets/{id}/export", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/test-data/data-sets/{id}/export", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion").value("wp8-data-set-export-v1"))
                .andExpect(jsonPath("$.data.dataSet.id").value(dataSetId.toString()))
                .andExpect(jsonPath("$.data.dataSet.sourceRefDigest").value(DIGEST_B))
                .andExpect(jsonPath("$.data.recordCount").value(2))
                .andExpect(jsonPath("$.data.schemaFieldCount").value(2))
                .andExpect(jsonPath("$.data.sensitiveFieldCount").value(1))
                .andExpect(jsonPath("$.data.records[0].recordDigest").value(DIGEST_A))
                .andExpect(jsonPath("$.data.records[0].maskedSummaryKeys", hasSize(2)))
                .andExpect(jsonPath("$.data.redactionPolicy.rawRecordPayloadExported").value(false))
                .andExpect(jsonPath("$.data.redactionPolicy.maskedSummaryValuesExported").value(false))
                .andExpect(content().string(not(containsString("secret://wp8/raw-source"))))
                .andExpect(content().string(not(containsString("raw-ssn-1234"))))
                .andExpect(content().string(not(containsString("c***@example.test"))));

        mockMvc.perform(post("/api/v1/test-data/data-sets/{id}/records", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRecordsRequest(
                                record("customer:001", DIGEST_D, null)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.records[0].recordDigest").value(DIGEST_D));

        mockMvc.perform(post("/api/v1/test-data/data-sets/{id}/records", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRecordsRequest(
                                record("customer:003", DIGEST_B, null)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/test-data/data-sets/{id}", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sanitized customers ready",
                                "status", "READY"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Sanitized customers ready"))
                .andExpect(jsonPath("$.data.status").value("READY"));

        mockMvc.perform(post("/api/v1/test-data/data-sets/{id}/archive", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists());

        mockMvc.perform(patch("/api/v1/test-data/data-sets/{id}", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "should fail"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/test-data/data-sets/{id}/records", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRecordsRequest(
                                record("customer:001", DIGEST_A, null)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void rejectsInvalidSchemaDuplicateCodeAndArchiveStatusPatch() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "code", "bad-schema",
                                "name", "Bad schema",
                                "schema", Map.of("fields", List.of(Map.of("name", "1bad", "type", "STRING")))
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDataSetRequest("dataset-alpha"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDataSetRequest("dataset-alpha"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "code", "archived-on-create",
                                "name", "Archived on create",
                                "status", "ARCHIVED"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void rejectsCrossProjectDataSetAccess() throws Exception {
        String alphaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String betaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-beta"));

        MvcResult created = mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDataSetRequest("dataset-alpha"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID dataSetId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/test-data/data-sets/{id}", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/test-data/data-sets/{id}/export", dataSetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/test-data/data-sets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-beta",
                                "code", "cross-project",
                                "name", "Cross project"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private Map<String, Object> createDataSetRequest(String code) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", "project-alpha");
        request.put("applicationId", "app-alpha");
        request.put("environmentId", "env-staging");
        request.put("code", code);
        request.put("name", "Sanitized customers");
        request.put("status", "DRAFT");
        request.put("schema", Map.of("fields", List.of(
                        Map.of("name", "customerId", "type", "STRING", "required", true),
                        Map.of("name", "riskScore", "type", "NUMBER", "required", false, "sensitive", true)
        )));
        request.put("sensitivityLevel", "CONFIDENTIAL");
        request.put("cleanupPolicy", Map.of(
                        "mode", "MANUAL_CONFIRM",
                        "ttlDays", 7,
                        "rollbackSummary", "snapshot only; no destructive cleanup in M2"
        ));
        request.put("sourceType", "EXTERNAL_REF");
        request.put("sourceRefDigest", DIGEST_B);
        return request;
    }

    private Map<String, Object> importRecordsRequest(Map<String, Object>... records) {
        return Map.of("records", List.of(records));
    }

    private Map<String, Object> record(String recordKey, String recordDigest, String externalRefDigest) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("recordKey", recordKey);
        item.put("recordDigest", recordDigest);
        item.put("maskedSummary", Map.of(
                "customerEmail", "c***@example.test",
                "riskBand", "LOW"
        ));
        if (externalRefDigest != null) {
            item.put("externalRefDigest", externalRefDigest);
        }
        item.put("tags", List.of("sanitized", "smoke"));
        return item;
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp8-user-" + UUID.randomUUID(),
                "WP8 User",
                "wp8-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
