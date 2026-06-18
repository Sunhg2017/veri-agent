package com.songhg.veri.agent.uie2e.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.uie2e.application.UiE2eCrossWpReferenceService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UiE2eSceneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsScenesWithoutAuthenticationAndProjectScope() throws Exception {
        mockMvc.perform(get("/api/v1/ui-e2e/scenes"))
                .andExpect(status().isForbidden());

        String projectToken = userAccessToken(List.of("Developer@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectToken)
                        .param("projectId", "project-alpha"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSceneRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createsListsUpdatesAndArchivesScene() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSceneRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.projectId").value("project-alpha"))
                .andExpect(jsonPath("$.data.code").value("portal-role-admin-login"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.steps", hasSize(1)))
                .andReturn();

        UUID sceneId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("keyword", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(sceneId.toString()))
                .andExpect(jsonPath("$.data.items[0].stepCount").value(1));

        mockMvc.perform(get("/api/v1/ui-e2e/scenes/{id}", sceneId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policy.mutable").value(true))
                .andExpect(jsonPath("$.data.policy.executable").value(false));

        mockMvc.perform(patch("/api/v1/ui-e2e/scenes/{id}", sceneId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "后台管理员登录并校验首页",
                                "status", "APPROVED",
                                "riskLevel", "CRITICAL",
                                "tags", List.of("smoke", "critical"),
                                "steps", List.of(Map.of(
                                        "stepType", "ASSERT",
                                        "actionSummary", Map.of("action", "check"),
                                        "locatorStrategy", Map.of("preferred", "testId"),
                                        "assertionSummary", Map.of("expected", "dashboard"),
                                        "waitPolicy", Map.of("timeoutSeconds", 3)
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("后台管理员登录并校验首页"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.data.policy.executable").value(true))
                .andExpect(jsonPath("$.data.steps[0].stepType").value("ASSERT"));

        mockMvc.perform(post("/api/v1/ui-e2e/scenes/{id}/archive", sceneId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists());

        mockMvc.perform(patch("/api/v1/ui-e2e/scenes/{id}", sceneId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "should fail"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void rejectsInvalidStatusAndDuplicateCode() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "code", "portal-role-admin-login",
                                "name", "Bad scene",
                                "status", "ARCHIVED",
                                "steps", List.of(Map.of("stepType", "LOGIN"))
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSceneRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ui-e2e/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSceneRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private Map<String, Object> createSceneRequest() {
        return Map.of(
                "projectId", "project-alpha",
                "applicationId", "app-alpha",
                "environmentId", "env-staging",
                "code", "portal-role-admin-login",
                "name", "后台管理员登录并进入首页",
                "riskLevel", "HIGH",
                "tags", List.of("login", "rbac", "smoke"),
                "steps", List.of(Map.of(
                        "stepType", "LOGIN",
                        "actionSummary", Map.of("submitAction", "click"),
                        "locatorStrategy", Map.of("preferred", "testId"),
                        "assertionSummary", Map.of("successSignal", "url contains /dashboard"),
                        "waitPolicy", Map.of("timeoutSeconds", 5)
                ))
        );
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
