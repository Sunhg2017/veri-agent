package com.songhg.veri.agent.execution.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.auth.application.AuthTokenService;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
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
        "veri-agent.execution.webhook-enabled=true",
        "veri-agent.execution.cron-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionTriggerControllerTest {

    private static final String WEBHOOK_SECRET_REF = "secret://wp9/webhook";
    private static final String WEBHOOK_SECRET = "wp9-webhook-signing-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiAutomationRepository apiAutomationRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private SecretProviderTestConfig.CountingSecretProvider countingSecretProvider;

    @Test
    void createsListsUpdatesAndDryRunsTriggerWithoutLeakingSecretRef() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");

        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans/{id}/triggers", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "triggerType", "WEBHOOK",
                                "status", "DISABLED",
                                "secretRef", WEBHOOK_SECRET_REF,
                                "config", Map.of(
                                        "source", "github-actions",
                                        "eventType", "deployment",
                                        "payload", "must-reject"
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECRET_POLICY_VIOLATION"))
                .andReturn();

        created = mockMvc.perform(post("/api/v1/execution/plans/{id}/triggers", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "triggerType", "WEBHOOK",
                                "status", "DISABLED",
                                "secretRef", WEBHOOK_SECRET_REF,
                                "config", Map.of(
                                        "source", "github-actions",
                                        "eventType", "deployment"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.planId").value(planId.toString()))
                .andExpect(jsonPath("$.data.triggerType").value("WEBHOOK"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.secretRefConfigured").value(true))
                .andExpect(jsonPath("$.data.secretRefDigest", startsWith("")))
                .andExpect(jsonPath("$.data.configSummary.source").value("github-actions"))
                .andExpect(jsonPath("$.data.configSummary.rawPayloadStored").value(false))
                .andExpect(content().string(not(containsString(WEBHOOK_SECRET_REF))))
                .andReturn();
        UUID triggerId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(get("/api/v1/execution/plans/{id}/triggers", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("triggerType", "WEBHOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(triggerId.toString()))
                .andExpect(content().string(not(containsString(WEBHOOK_SECRET_REF))));

        mockMvc.perform(patch("/api/v1/execution/triggers/{id}", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ENABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(content().string(not(containsString(WEBHOOK_SECRET_REF))));

        mockMvc.perform(post("/api/v1/execution/triggers/{id}/dry-run", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.globalEnabled").value(true))
                .andExpect(jsonPath("$.data.runCreated").value(false))
                .andExpect(jsonPath("$.data.policy.webhookSignatureRequired").value(true));

        mockMvc.perform(get("/api/v1/execution/triggers/{id}/events", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void refusesToEnableWebhookTriggerWithoutSecretRef() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");

        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans/{id}/triggers", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "triggerType", "WEBHOOK",
                                "status", "DISABLED",
                                "config", Map.of("source", "github-actions")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.secretRefConfigured").value(false))
                .andReturn();
        UUID triggerId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));

        mockMvc.perform(patch("/api/v1/execution/triggers/{id}", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ENABLED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECRET_REQUIRED"))
                .andExpect(jsonPath("$.message").value("EXECUTION_TRIGGER_SECRET_REQUIRED"));
    }

    @Test
    void rejectsWebhookWhenTriggerDisabledAndRecordsRejectedEvent() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID triggerId = createTrigger(planId, token, "DISABLED");
        String payload = "{\"build\":\"20260613.1\"}";

        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(payload, "evt-disabled"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EXECUTION_TRIGGER_DISABLED"));

        mockMvc.perform(get("/api/v1/execution/triggers/{id}/events", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("EXECUTION_TRIGGER_DISABLED"))
                .andExpect(jsonPath("$.data.items[0].requestDigest").exists())
                .andExpect(content().string(not(containsString(payload))));

        org.assertj.core.api.Assertions.assertThat(executionRepository.runs(
                new com.songhg.veri.agent.execution.application.query.ExecutionRunQuery(
                        "project-alpha",
                        planId,
                        null,
                        20,
                        0
                )
        )).isEmpty();
    }

    @Test
    void rejectsInvalidSignatureAndAcceptsSignedWebhookIdempotently() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID triggerId = createTrigger(planId, token, "DISABLED");
        mockMvc.perform(patch("/api/v1/execution/triggers/{id}", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ENABLED"))))
                .andExpect(status().isOk());

        String badPayload = "{\"build\":\"bad\"}";
        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .header("X-VA-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-VA-Event-Id", "evt-bad-signature")
                        .header("X-VA-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("EXECUTION_TRIGGER_SIGNATURE_INVALID"));

        mockMvc.perform(get("/api/v1/execution/triggers/{id}/events", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("EXECUTION_TRIGGER_SIGNATURE_INVALID"));

        String payload = "{\"build\":\"20260613.2\",\"token\":\"not-stored\"}";
        MvcResult accepted = mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(payload, "evt-release-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.event.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.event.sourceEventId").value("evt-release-1"))
                .andExpect(jsonPath("$.data.runId").exists())
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(content().string(not(containsString("not-stored"))))
                .andReturn();
        String runId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.data.runId");

        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(payload, "evt-release-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.event.status").value("DUPLICATE"));

        mockMvc.perform(get("/api/v1/execution/runs/{id}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triggerType").value("WEBHOOK"))
                .andExpect(jsonPath("$.data.sourceEventId").value("evt-release-1"))
                .andExpect(jsonPath("$.data.resultSummary.webhookPayloadStored").value(false))
                .andExpect(jsonPath("$.data.nodes.length()").value(2));
    }

    @Test
    void rejectsUnsupportedWebhookMediaTypeBeforeSignatureProcessing() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID triggerId = createTrigger(planId, token, "ENABLED");
        String payload = "<build token=\"not-stored\"/>";

        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(payload, "evt-xml-rejected"))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(payload))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void cachesResolvedWebhookSecretWithinConfiguredTtl() throws Exception {
        countingSecretProvider.reset();
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID triggerId = createTrigger(planId, token, "ENABLED");
        String firstPayload = "{\"build\":\"cache-1\"}";
        String secondPayload = "{\"build\":\"cache-2\"}";

        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(firstPayload, "evt-cache-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(secondPayload, "evt-cache-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isAccepted());

        org.assertj.core.api.Assertions.assertThat(countingSecretProvider.resolveCount()).isEqualTo(1);
    }

    @Test
    void signedWebhookRetryCanRecoverAfterRejectedSignatureAttempt() throws Exception {
        UUID bundleId = approvedBundle("project-alpha");
        String token = userAccessToken(List.of("ProjectOwner@PROJECT:project-alpha"));
        UUID planId = createPlan(bundleId, token, "READY");
        UUID triggerId = createTrigger(planId, token, "DISABLED");
        mockMvc.perform(patch("/api/v1/execution/triggers/{id}", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ENABLED"))))
                .andExpect(status().isOk());

        String payload = "{\"build\":\"retry-after-bad-signature\"}";
        mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .header("X-VA-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-VA-Event-Id", "evt-retry-after-bad-signature")
                        .header("X-VA-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("EXECUTION_TRIGGER_SIGNATURE_INVALID"));

        MvcResult retry = mockMvc.perform(post("/api/v1/execution/webhooks/{id}", triggerId)
                        .headers(webhookHeaders(payload, "evt-retry-after-bad-signature"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.event.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.event.errorCode").doesNotExist())
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andReturn();
        String runId = JsonPath.read(retry.getResponse().getContentAsString(), "$.data.runId");

        mockMvc.perform(get("/api/v1/execution/triggers/{id}/events", triggerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].runId").value(runId))
                .andExpect(jsonPath("$.data.items[0].status").value("ACCEPTED"));
    }

    private UUID createPlan(UUID bundleId, String token, String status) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(bundleId, status))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID createTrigger(UUID planId, String token, String status) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/execution/plans/{id}/triggers", planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "triggerType", "WEBHOOK",
                                "status", status,
                                "secretRef", WEBHOOK_SECRET_REF,
                                "config", Map.of("source", "github-actions")
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
    }

    private HttpHeaders webhookHeaders(String payload, String eventId) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-VA-Timestamp", timestamp);
        headers.set("X-VA-Event-Id", eventId);
        headers.set("X-VA-Signature", hmacSha256(String.join(".", timestamp, eventId, payload)));
        return headers;
    }

    private String hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private UUID approvedBundle(String projectId) {
        UUID bundleId = UUID.randomUUID();
        Instant now = Instant.now();
        apiAutomationRepository.insertScriptBundle(new ApiAutomationScriptBundle(
                bundleId,
                projectId,
                UUID.randomUUID(),
                "APPROVED",
                "bundle-digest-" + bundleId,
                2,
                "{}",
                "{}",
                "PASSED",
                "{}",
                "approved",
                "submitter",
                "approver",
                now,
                now,
                null,
                "tester",
                "tester",
                now,
                now
        ));
        return bundleId;
    }

    private Map<String, Object> planRequest(UUID bundleId, String status) {
        return Map.of(
                "projectId", "project-alpha",
                "name", "Release smoke",
                "environmentKey", "staging",
                "status", status,
                "dag", Map.of("nodes", List.of(
                        Map.of(
                                "key", "api-smoke",
                                "type", "API_TEST",
                                "dependencies", List.of(),
                                "input", Map.of("apiAutomationBundleId", bundleId.toString()),
                                "timeoutSeconds", 180,
                                "failurePolicy", "FAIL_FAST",
                                "retryPolicy", Map.of("maxAttempts", 1)
                        ),
                        Map.of(
                                "key", "report",
                                "type", "REPORT_HANDOFF",
                                "dependencies", List.of("api-smoke"),
                                "input", Map.of("summaryOnly", true),
                                "timeoutSeconds", 60,
                                "failurePolicy", "CONTINUE",
                                "retryPolicy", Map.of()
                        )
                ))
        );
    }

    private String userAccessToken(List<String> roles) {
        return tokenService.issue(new AuthUserRecord(
                UUID.randomUUID(),
                "wp9-trigger-user-" + UUID.randomUUID(),
                "WP9 Trigger User",
                "wp9-trigger-user@example.test",
                "{noop}password",
                false,
                1,
                roles
        )).accessToken();
    }

    @TestConfiguration
    static class SecretProviderTestConfig {
        @Bean
        CountingSecretProvider wp9TestSecretProvider() {
            return new CountingSecretProvider();
        }

        static class CountingSecretProvider implements SecretProvider {
            private final AtomicInteger resolveCount = new AtomicInteger();

            @Override
            public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
                resolveCount.incrementAndGet();
                if (WEBHOOK_SECRET_REF.equals(secretRef)) {
                    return Optional.of(new ResolvedSecret(secretRef, WEBHOOK_SECRET, "test", "v1"));
                }
                return Optional.empty();
            }

            void reset() {
                resolveCount.set(0);
            }

            int resolveCount() {
                return resolveCount.get();
            }
        }
    }
}
