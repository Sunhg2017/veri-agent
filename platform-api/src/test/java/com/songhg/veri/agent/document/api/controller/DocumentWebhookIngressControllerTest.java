package com.songhg.veri.agent.document.api.controller;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.document-input.service-token=test-document-input-token",
        "veri-agent.document-input.webhook-secret=local-document-input-webhook-secret",
        "veri-agent.document-input.webhook-allowed-cidrs=127.0.0.1/32,203.0.113.0/24",
        "veri-agent.document-input.webhook-trusted-proxy-cidrs=127.0.0.1/32",
        "veri-agent.document-input.webhook-rate-limit-max-requests=1",
        "veri-agent.document-input.webhook-rate-limit-window-seconds=60"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentWebhookIngressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsWebhookOutsideAllowlistBeforeSignatureAndRecordsEvent() throws Exception {
        createSource("custom-ip-guard");
        String payload = payload("REQ-IP-GUARD", "IP 白名单拒绝");

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-ip-guard")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.9");
                            return request;
                        })
                        .header("X-VA-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-VA-Signature", "bad-signature-never-needed")
                        .header("X-VA-Event-Id", "evt-ip-guard")
                        .header("X-VA-Idempotency-Key", "idem-ip-guard")
                        .header("X-VA-Event-Version", "1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-ip-guard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.items[0].signatureStatus").value("INVALID"))
                .andExpect(jsonPath("$.data.items[0].errorMessage", startsWith("webhook 来源 IP 不在白名单")));
    }

    @Test
    void rejectsWebhookOverRateLimitBeforeBusinessParsingAndRecordsEvent() throws Exception {
        createSource("custom-rate-guard");
        String firstPayload = payload("REQ-RATE-1", "首次 webhook");
        String secondPayload = payload("REQ-RATE-2", "限流 webhook");

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-rate-guard")
                        .headers(webhookHeaders(firstPayload, "evt-rate-1", "idem-rate-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceCode").value("custom-rate-guard"));

        mockMvc.perform(post("/api/v1/document-input/webhooks/{sourceCode}", "custom-rate-guard")
                        .headers(webhookHeaders(secondPayload, "evt-rate-2", "idem-rate-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BUDGET_EXCEEDED"));

        MvcResult events = mockMvc.perform(get("/api/v1/document-input/webhook-events")
                        .headers(documentInputHeaders())
                        .param("sourceCode", "custom-rate-guard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        List<String> statuses = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[*].status");
        List<String> errors = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[*].errorMessage");

        assertThat(statuses).contains("PROCESSED", "REJECTED");
        assertThat(errors).anySatisfy(error -> assertThat(error).startsWith("webhook 请求过于频繁"));
    }

    private void createSource(String sourceCode) throws Exception {
        mockMvc.perform(post("/api/v1/document-input/sources")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "%s",
                                  "name": "%s source",
                                  "sourceType": "CUSTOM_API",
                                  "defaultProjectId": "project-wp4",
                                  "secretRef": "secret://wp4/%s",
                                  "eventVersion": "1.0",
                                  "mappingVersion": "default",
                                  "endpointUrl": "https://example.test/%s"
                                }
                                """.formatted(sourceCode, sourceCode, sourceCode, sourceCode)))
                .andExpect(status().isCreated());
    }

    private String payload(String sourceRef, String title) {
        return """
                {
                  "projectId": "project-wp4",
                  "eventType": "requirement.created",
                  "eventVersion": "1.0",
                  "id": "%s",
                  "requirements": [{"title": "%s"}]
                }
                """.formatted(sourceRef, title);
    }

    private HttpHeaders documentInputHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-document-input-token");
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
}
