package com.songhg.veri.agent.common.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesWp1ControlPlaneContractWithBearerSecurity() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Veri Agent WP1 Platform API"))
                .andExpect(jsonPath("$.info.version").value("0.1.0"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/bootstrap/super-admin']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/change-password'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}/enable'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}/disable'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}/reset-password'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}/roles'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/users/{username}/roles/unassign'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/roles'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/roles'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/roles/{code}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/roles/{code}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/roles/{code}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/permissions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/departments'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/departments/{key}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/departments/{key}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/departments/{key}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects/{key}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects/{key}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects/{key}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects/{key}/members'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects/{key}/members'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/projects/{key}/members/{username}/remove'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/applications/{key}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/applications/{key}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/applications/{key}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/applications/{key}/owners'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/applications/{key}/owners'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/applications/{key}/owners/{username}/remove'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}/connectivity-check'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}/connectivity-check'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/environments/{key}/users/{username}/remove'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/integrations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/integrations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/integrations/{key}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/integrations/{key}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/integrations/{key}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/settings'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/settings'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/settings/{key}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/settings/{key}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/settings/{key}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/secrets'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/secrets'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/secrets/rotate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/secrets/disable'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/audit-logs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/audit-logs/export'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/audit-outbox'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contexts/projects/{projectId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contexts/applications/{applicationId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit/events'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/requirements/{id}/versions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/apis'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/apis'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/pages'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/pages'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/business-flows'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/business-flows'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/test-cases/{id}/versions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/links'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/asset/links'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/tasks/{id}/publish'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/test-design/candidates/{id}/confirm'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs/{id}/parse'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs/{id}/diff'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs/{id}/sync-preview'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/specs/{id}/sync'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/generation-tasks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/generation-tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/generation-tasks/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/generation-tasks/{id}/script-bundles'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/script-bundles/{id}/submit-review'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/script-bundles/{id}/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/script-bundles/{id}/reject'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/runs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/runs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/runs/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/api-automation/runs/{id}/export'].get").exists())
                .andReturn();

        Path output = Path.of("..", "build", "openapi", "wp1-v1.json").normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.getResponse().getContentAsString(), StandardCharsets.UTF_8);
    }

    @Test
    void generatedContractDoesNotReintroduceLegacyInstanceBoundary() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("/tenants")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("tenant_id")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("base_tenant")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("TenantAdmin")));
    }

    @Test
    void generatedContractDoesNotExposeSecretPlaintextFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        org.hamcrest.MatcherAssert.assertThat(openApi, containsString("/api/v1/management/secrets"));
        org.hamcrest.MatcherAssert.assertThat(openApi, containsString("apiKeyRef"));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("apiKeyValue")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("secretValue")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("secret_value")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("plainValue")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("plaintext")));
        org.hamcrest.MatcherAssert.assertThat(openApi, not(containsString("promptPlaintext")));
    }

    @Test
    void generatedContractIncludesApiVersionPolicyAndOperationLifecycleMetadata() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info['x-api-version-policy'].current").value("v1"))
                .andExpect(jsonPath("$.info['x-api-version-policy'].pathPrefix").value("/api/v1"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post['x-api-version']").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post['x-api-lifecycle']").value("STABLE"))
                .andExpect(jsonPath("$.paths['/api/v1/contexts/projects/{projectId}'].get['x-api-lifecycle']").value("INTERNAL"))
                .andReturn();

        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            if (!pathEntry.getKey().startsWith("/api/v1")) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> operationEntries = pathEntry.getValue().fields();
            while (operationEntries.hasNext()) {
                Map.Entry<String, JsonNode> operationEntry = operationEntries.next();
                if (!HTTP_METHODS.contains(operationEntry.getKey())) {
                    continue;
                }
                JsonNode operation = operationEntry.getValue();
                assertThat(operation.path("x-api-version").asText())
                        .as("%s %s declares API version", operationEntry.getKey(), pathEntry.getKey())
                        .isEqualTo("v1");
                assertThat(operation.path("x-api-lifecycle").asText())
                        .as("%s %s declares API lifecycle", operationEntry.getKey(), pathEntry.getKey())
                        .isIn("STABLE", "INTERNAL", "DEPRECATED");
            }
        }
    }

    @Test
    void generatedContractDocumentsEveryApiOperation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            if (!pathEntry.getKey().startsWith("/api/v1")) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> operationEntries = pathEntry.getValue().fields();
            while (operationEntries.hasNext()) {
                Map.Entry<String, JsonNode> operationEntry = operationEntries.next();
                if (!HTTP_METHODS.contains(operationEntry.getKey())) {
                    continue;
                }
                String label = operationEntry.getKey().toUpperCase() + " " + pathEntry.getKey();
                JsonNode operation = operationEntry.getValue();
                assertThat(operation.path("operationId").asText())
                        .as(label + " declares operationId")
                        .isNotBlank();
                assertThat(operation.path("summary").asText())
                        .as(label + " declares summary")
                        .isNotBlank();
                assertThat(operation.path("tags").isArray())
                        .as(label + " declares tags")
                        .isTrue();
                assertThat(operation.path("tags").size())
                        .as(label + " declares at least one tag")
                        .isPositive();

                JsonNode responses = operation.path("responses");
                assertThat(responses.path("400").path("description").asText())
                        .as(label + " documents validation errors")
                        .isNotBlank();
                assertThat(responses.path("401").path("description").asText())
                        .as(label + " documents authentication errors")
                        .isNotBlank();
                assertThat(responses.path("403").path("description").asText())
                        .as(label + " documents authorization errors")
                        .isNotBlank();
                assertThat(responses.path("500").path("description").asText())
                        .as(label + " documents server errors")
                        .contains("traceId");
                responses.fields().forEachRemaining(responseEntry ->
                        assertThat(responseEntry.getValue().path("description").asText())
                                .as(label + " response " + responseEntry.getKey() + " has description")
                                .isNotBlank()
                );
            }
        }
    }

    @Test
    void successfulPagedResponsesUseStandardEnvelopeAndPageShape() throws Exception {
        mockMvc.perform(get("/api/v1/examples/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.index").isNumber())
                .andExpect(jsonPath("$.data.size").isNumber())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void validationErrorsUseStandardEnvelopeAndFieldErrorShape() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求字段校验失败"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.data.fieldErrors").isArray())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").isString())
                .andExpect(jsonPath("$.data.fieldErrors[0].reason").isString());
    }

    private static final Set<String> HTTP_METHODS = Set.of(
            "get",
            "put",
            "post",
            "delete",
            "options",
            "head",
            "patch",
            "trace"
    );
}
