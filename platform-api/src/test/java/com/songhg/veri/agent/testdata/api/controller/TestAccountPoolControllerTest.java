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
        "veri-agent.test-data.default-lease-ttl-seconds=60",
        "veri-agent.test-data.max-lease-ttl-seconds=120"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TestAccountPoolControllerTest {

    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";
    private static final String SECRET_REF_V2 = "secret://wp8/accounts/admin-01-v2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsAccountPoolsWithoutAuthenticationAndProjectScope() throws Exception {
        mockMvc.perform(get("/api/v1/test-data/account-pools"))
                .andExpect(status().isForbidden());

        String projectToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createsListsUpdatesDisablesArchivesAndMaintainsAccountsWithoutSecretRefEcho() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        MvcResult created = mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPoolRequest("pool-alpha"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.projectId").value("project-alpha"))
                .andExpect(jsonPath("$.data.applicationId").value("app-alpha"))
                .andExpect(jsonPath("$.data.environmentId").value("env-staging"))
                .andExpect(jsonPath("$.data.code").value("pool-alpha"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.defaultTtlSeconds").value(90))
                .andExpect(jsonPath("$.data.accounts", hasSize(0)))
                .andExpect(jsonPath("$.data.policy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.policy.secretRefPlaintextReturned").value(false))
                .andReturn();

        UUID poolId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("applicationId", "app-alpha")
                        .param("environmentId", "env-staging")
                        .param("keyword", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(poolId.toString()))
                .andExpect(jsonPath("$.data.items[0].accountCount").value(0));

        MvcResult accountCreated = mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest("admin-01", SECRET_REF))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountKey").value("admin-01"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.roleTags[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.secretRefDigest").isString())
                .andExpect(content().string(not(containsString(SECRET_REF))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andReturn();
        UUID accountId = UUID.fromString(JsonPath.read(accountCreated.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/test-data/account-pools/{id}", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts", hasSize(1)))
                .andExpect(jsonPath("$.data.accounts[0].secretRefDigest").isString())
                .andExpect(content().string(not(containsString(SECRET_REF))));

        mockMvc.perform(patch("/api/v1/test-data/accounts/{id}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "Admin 01 rotated",
                                "status", "LOCKED",
                                "roleTags", List.of("ADMIN", "LOCKED"),
                                "scopeSummary", Map.of("review", true),
                                "secretRef", SECRET_REF_V2,
                                "lastHealthStatus", "LOCKED",
                                "lastHealthSummary", "manual lock"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Admin 01 rotated"))
                .andExpect(jsonPath("$.data.status").value("LOCKED"))
                .andExpect(content().string(not(containsString(SECRET_REF_V2))))
                .andExpect(content().string(not(containsString("secret://"))));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/disable", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest("admin-02", "secret://wp8/accounts/admin-02"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/archive", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").exists());

        mockMvc.perform(patch("/api/v1/test-data/account-pools/{id}", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "should fail"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void rejectsInvalidAndDuplicateAccountPoolInputs() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));

        mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-alpha",
                                "code", "pool-alpha",
                                "name", "Pool alpha",
                                "status", "ARCHIVED"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        MvcResult created = mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPoolRequest("pool-alpha"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID poolId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPoolRequest("pool-alpha"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest("bad key space", SECRET_REF))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest("admin-01", "env:WP8_ACCOUNT_SECRET"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest("admin-01", SECRET_REF))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest("admin-01", SECRET_REF))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void rejectsCrossProjectAccountPoolAccess() throws Exception {
        String alphaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String betaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-beta"));

        MvcResult created = mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPoolRequest("pool-alpha"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID poolId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/test-data/account-pools/{id}", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "project-beta",
                                "code", "cross-project-pool",
                                "name", "Cross project pool"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private Map<String, Object> createPoolRequest(String code) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", "project-alpha");
        request.put("applicationId", "app-alpha");
        request.put("environmentId", "env-staging");
        request.put("code", code);
        request.put("name", "Checkout account pool");
        request.put("status", "READY");
        request.put("leasePolicy", Map.of("sharing", "EXCLUSIVE", "cleanup", "MANUAL_CONFIRM"));
        request.put("defaultTtlSeconds", 90);
        return request;
    }

    private Map<String, Object> accountRequest(String accountKey, String secretRef) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("accountKey", accountKey);
        request.put("displayName", "Admin 01");
        request.put("status", "AVAILABLE");
        request.put("roleTags", List.of("admin", "approver"));
        request.put("scopeSummary", Map.of("applicationId", "app-alpha", "environmentId", "env-staging"));
        request.put("secretRef", secretRef);
        request.put("lastHealthStatus", "HEALTHY");
        request.put("lastHealthSummary", "manual smoke passed");
        return request;
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp8-account-user-" + UUID.randomUUID(),
                "WP8 Account User",
                "wp8-account-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
