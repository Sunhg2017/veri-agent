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
class TestAccountLeaseControllerTest {

    private static final String SECRET_REF = "secret://wp8/accounts/admin-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsLeaseListWithoutAuthenticationAndProjectScope() throws Exception {
        mockMvc.perform(get("/api/v1/test-data/leases"))
                .andExpect(status().isForbidden());

        String projectToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void acquiresRenewsReleasesAndListsLeaseWithoutSecretEcho() throws Exception {
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID poolId = createPoolAndAccount(token);

        MvcResult acquired = mockMvc.perform(post("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaseRequest(poolId, "run-001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.leaseTokenDigest").isString())
                .andExpect(jsonPath("$.data.policy.leaseTokenPlaintextReturned").value(false))
                .andExpect(jsonPath("$.data.account.status").value("LEASED"))
                .andExpect(content().string(not(containsString(SECRET_REF))))
                .andExpect(content().string(not(containsString("secret://"))))
                .andReturn();
        UUID leaseId = UUID.fromString(JsonPath.read(acquired.getResponse().getContentAsString(), "$.data.id"));

        String readOnlyToken = userAccessToken(List.of("Tester@PROJECT:project-alpha"));
        mockMvc.perform(get("/api/v1/test-data/leases/{id}/export", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnlyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/test-data/leases/{id}/export", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion").value("wp8-account-lease-export-v1"))
                .andExpect(jsonPath("$.data.lease.id").value(leaseId.toString()))
                .andExpect(jsonPath("$.data.lease.leaseTokenDigest").isString())
                .andExpect(jsonPath("$.data.pool.id").value(poolId.toString()))
                .andExpect(jsonPath("$.data.account.secretRefDigest").isString())
                .andExpect(jsonPath("$.data.account.scopeSummaryKeys", hasSize(1)))
                .andExpect(jsonPath("$.data.redactionPolicy.secretRefPlaintextExported").value(false))
                .andExpect(jsonPath("$.data.redactionPolicy.leaseTokenPlaintextExported").value(false))
                .andExpect(content().string(not(containsString(SECRET_REF))))
                .andExpect(content().string(not(containsString("secret://"))));

        mockMvc.perform(post("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaseRequest(poolId, "run-001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(leaseId.toString()));

        mockMvc.perform(post("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaseRequest(poolId, "run-002"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/test-data/leases/{id}/renew", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlSeconds", 90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("projectId", "project-alpha")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/v1/test-data/leases/{id}/release", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "releaseReason", "run finished",
                                "accountStatus", "AVAILABLE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"))
                .andExpect(jsonPath("$.data.account.status").value("AVAILABLE"));

        mockMvc.perform(get("/api/v1/test-data/leases/{id}/export", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleSummary.releaseReasonPresent").value(true))
                .andExpect(jsonPath("$.data.lease.releaseReasonDigest").isString())
                .andExpect(jsonPath("$.data.redactionPolicy.freeTextValuesExported").value(false))
                .andExpect(content().string(not(containsString("run finished"))))
                .andExpect(content().string(not(containsString(SECRET_REF))))
                .andExpect(content().string(not(containsString("secret://"))));
    }

    @Test
    void rejectsCrossProjectLeaseExportAccess() throws Exception {
        String alphaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        String betaToken = userAccessToken(List.of("ProjectOwner@PROJECT:project-beta"));
        UUID poolId = createPoolAndAccount(alphaToken);

        MvcResult acquired = mockMvc.perform(post("/api/v1/test-data/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaseRequest(poolId, "run-001"))))
                .andExpect(status().isOk())
                .andReturn();
        UUID leaseId = UUID.fromString(JsonPath.read(acquired.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/test-data/leases/{id}/export", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private UUID createPoolAndAccount(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/test-data/account-pools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poolRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID poolId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        mockMvc.perform(post("/api/v1/test-data/account-pools/{id}/accounts", poolId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest())))
                .andExpect(status().isOk());
        return poolId;
    }

    private Map<String, Object> poolRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", "project-alpha");
        request.put("applicationId", "app-alpha");
        request.put("environmentId", "env-staging");
        request.put("code", "pool-alpha");
        request.put("name", "Lease pool");
        request.put("status", "READY");
        request.put("defaultTtlSeconds", 60);
        return request;
    }

    private Map<String, Object> accountRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("accountKey", "admin-01");
        request.put("displayName", "Admin 01");
        request.put("status", "AVAILABLE");
        request.put("roleTags", List.of("ADMIN"));
        request.put("scopeSummary", Map.of("applicationId", "app-alpha"));
        request.put("secretRef", SECRET_REF);
        return request;
    }

    private Map<String, Object> leaseRequest(UUID poolId, String holderRef) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", "project-alpha");
        request.put("applicationId", "app-alpha");
        request.put("environmentId", "env-staging");
        request.put("poolId", poolId.toString());
        request.put("roleTags", List.of("ADMIN"));
        request.put("holderType", "EXECUTION_RUN");
        request.put("holderRef", holderRef);
        request.put("ttlSeconds", 60);
        request.put("requestKey", "lease-" + holderRef);
        return request;
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp8-lease-user-" + UUID.randomUUID(),
                "WP8 Lease User",
                "wp8-lease-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }
}
