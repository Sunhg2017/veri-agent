package com.songhg.veri.agent.uie2e.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UiE2eBundleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsBundleApisWithoutReviewPermission() throws Exception {
        String readOnlyToken = userAccessToken(List.of("Developer@PROJECT:project-alpha"));

        mockMvc.perform(get("/api/v1/ui-e2e/bundles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken)
                        .param("projectId", "project-alpha"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ui-e2e/bundles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sceneId", UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsListsAndReviewsBundle() throws Exception {
        String ownerToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String reviewerToken = userAccessToken(List.of("Tester@PROJECT:project-alpha"));
        String auditorToken = userAccessToken(List.of("Auditor@PROJECT:project-alpha"));
        UUID sceneId = createScene(ownerToken);

        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/bundles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sceneId", sceneId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.sceneId").value(sceneId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.staticCheckStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.policy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.reviews", hasSize(0)))
                .andReturn();

        UUID bundleId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/ui-e2e/bundles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("projectId", "project-alpha")
                        .param("keyword", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(bundleId.toString()))
                .andExpect(jsonPath("$.data.items[0].sceneCode").value("portal-role-admin-login"));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/submit-review", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "ready"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/submit-review", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "ready"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.reviews[0].reviewStatus").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/reject", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/approve", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "approved"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").exists())
                .andExpect(jsonPath("$.data.reviews[0].reviewStatus").value("APPROVED"));

        mockMvc.perform(get("/api/v1/ui-e2e/bundles/{id}/export", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/ui-e2e/bundles/{id}/export", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion").value("wp7-bundle-export-v1"))
                .andExpect(jsonPath("$.data.bundle.id").value(bundleId.toString()))
                .andExpect(jsonPath("$.data.reviewSummary.reviewCount").value(2))
                .andExpect(jsonPath("$.data.reviewSummary.latestReview.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewSummary.latestReview.commentPresent").value(true))
                .andExpect(jsonPath("$.data.redactionPolicy.aggregateOnly").value(true))
                .andExpect(jsonPath("$.data.redactionPolicy.reviewCommentExported").value(false))
                .andExpect(content().string(not(containsString("\"reviewComment\":\"approved\""))));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/archive", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/ui-e2e/bundles/{id}/archive", bundleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists())
                .andExpect(jsonPath("$.data.policy.archivable").value(false));
    }

    @Test
    void returnsStaticCheckFailureForSensitiveBundle() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID sceneId = createSensitiveScene(token);

        mockMvc.perform(post("/api/v1/ui-e2e/bundles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sceneId", sceneId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("STATIC_CHECK_FAILED"))
                .andExpect(jsonPath("$.data.staticCheckStatus").value("SCRIPT_STATIC_CHECK_FAILED"))
                .andExpect(jsonPath("$.data.staticCheckSummary.findingCount").value(1));
    }

    private UUID createScene(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "applicationId", "app-alpha",
                                "environmentId", "env-staging",
                                "code", "portal-role-admin-login",
                                "name", "后台管理员登录并进入首页",
                                "status", "APPROVED",
                                "riskLevel", "HIGH",
                                "tags", List.of("login", "rbac", "smoke"),
                                "steps", List.of(Map.of(
                                        "stepType", "LOGIN",
                                        "actionSummary", Map.of("submitAction", "click"),
                                        "locatorStrategy", Map.of("preferred", "testId"),
                                        "assertionSummary", Map.of("successSignal", "url contains /dashboard"),
                                        "waitPolicy", Map.of("timeoutSeconds", 5)
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID createSensitiveScene(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "code", "portal-role-admin-secret",
                                "name", "后台管理员危险步骤",
                                "steps", List.of(Map.of(
                                        "stepType", "LOGIN",
                                        "actionSummary", Map.of("token", "Bearer real-token-value"),
                                        "locatorStrategy", Map.of("preferred", "testId"),
                                        "assertionSummary", Map.of("successSignal", "ok"),
                                        "waitPolicy", Map.of("timeoutSeconds", 5)
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp7_tester",
                "WP7 Tester",
                "wp7-tester@example.com",
                "$2a$10$test",
                false,
                1,
                roles
        )).accessToken();
    }
}
