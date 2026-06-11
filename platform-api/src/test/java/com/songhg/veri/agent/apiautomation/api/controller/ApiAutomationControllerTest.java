package com.songhg.veri.agent.apiautomation.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.api-automation.spec-max-bytes=65536",
        "veri-agent.api-automation.endpoint-max-count=10"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiAutomationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository repository;

    @Test
    void exposesHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/api-automation/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("api-automation"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.supportedOpenApiVersions[0]").value("3.x"))
                .andExpect(jsonPath("$.data.runnerEnabled").value(false))
                .andExpect(jsonPath("$.data.policy.rawRequestResponseStored").value(false))
                .andExpect(jsonPath("$.data.policy.urlFetchEnabled").value(false));
    }

    @Test
    void protectsSpecsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/api-automation/specs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void importsListsDetailsAndReparsesOpenApiSpec() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        MvcResult created = mockMvc.perform(post("/api/v1/api-automation/specs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "sourceType", "TEXT",
                                "name", "billing-openapi",
                                "versionLabel", "2026.06",
                                "sourceRef", "https://example.test/openapi.yaml?token=must-not-store",
                                "content", openApiJson()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.spec.projectId").value("project-alpha"))
                .andExpect(jsonPath("$.data.spec.status").value("PARSED"))
                .andExpect(jsonPath("$.data.spec.endpointCount").value(2))
                .andExpect(jsonPath("$.data.spec.sourceRef").value("https://example.test/openapi.yaml"))
                .andExpect(jsonPath("$.data.parseSummary.endpointCount").value(2))
                .andExpect(jsonPath("$.data.endpoints", hasSize(2)))
                .andExpect(jsonPath("$.data.endpoints[0].httpMethod").value("GET"))
                .andExpect(content().string(not(containsString("real-token-value"))))
                .andReturn();

        UUID specId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.spec.id"));
        assertThat(repository.spec(specId).orElseThrow().sanitizedSpecJson())
                .doesNotContain("real-token-value", "plain-secret")
                .contains("***MASKED***");

        mockMvc.perform(get("/api/v1/api-automation/specs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("keyword", "billing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(specId.toString()));

        mockMvc.perform(get("/api/v1/api-automation/specs/{id}", specId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spec.id").value(specId.toString()))
                .andExpect(jsonPath("$.data.endpoints[1].path").value("/v1/payments"));

        mockMvc.perform(post("/api/v1/api-automation/specs/{id}/parse", specId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spec.status").value("PARSED"))
                .andExpect(jsonPath("$.data.spec.endpointCount").value(2));
    }

    @Test
    void returnsStableValidationErrorForInvalidOpenApi() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        mockMvc.perform(post("/api/v1/api-automation/specs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "sourceType", "TEXT",
                                "name", "invalid-openapi",
                                "content", "{\"info\":{\"title\":\"missing version\"}}"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("OPENAPI_PARSE_FAILED")));
    }

    @Test
    void enforcesProjectScopeForSpecDetail() throws Exception {
        String adminToken = userAccessToken(List.of("SuperAdmin"));
        MvcResult created = mockMvc.perform(post("/api/v1/api-automation/specs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-beta",
                                "sourceType", "TEXT",
                                "name", "beta-openapi",
                                "content", openApiJson()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID specId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.spec.id"));
        String projectAlphaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/api-automation/specs/{id}", specId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectAlphaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "api_tester",
                "API Tester",
                "api-tester@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }

    private String openApiJson() {
        return """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Billing API", "version": "1.0.0"},
                  "paths": {
                    "/v1/customers/{id}": {
                      "parameters": [{"name": "id", "in": "path", "required": true, "schema": {"type": "string"}}],
                      "get": {
                        "operationId": "getCustomer",
                        "tags": ["customers"],
                        "summary": "Get customer",
                        "parameters": [{"name": "Authorization", "in": "header", "schema": {"type": "string"}, "example": "Bearer real-token-value"}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    },
                    "/v1/payments": {
                      "post": {
                        "operationId": "createPayment",
                        "tags": ["payments"],
                        "summary": "Create payment",
                        "requestBody": {"content": {"application/json": {"schema": {"type": "object", "properties": {"secret": {"type": "string", "example": "plain-secret"}}}}}},
                        "responses": {"201": {"description": "created"}, "400": {"description": "bad request"}}
                      }
                    }
                  }
                }
                """;
    }
}
