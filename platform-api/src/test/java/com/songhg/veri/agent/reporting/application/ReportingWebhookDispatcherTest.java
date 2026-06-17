package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.integration.application.PlatformIntegrationService;
import com.songhg.veri.agent.reporting.application.view.ReportingWebhookDeliveryHealthResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import com.songhg.veri.agent.reporting.infrastructure.InMemoryReportingRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingWebhookDispatcherTest {

    private static final String SIGNING_SECRET = "wp10-signing-secret-32-byte-minimum";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void dispatchesAggregateOnlyWebhookWithSignatureHeaders() throws Exception {
        ConcurrentLinkedQueue<CapturedRequest> capturedRequests = new ConcurrentLinkedQueue<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/callbacks/report-ready", exchange -> handle(exchange, capturedRequests));
        server.start();

        UUID reportId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-06-18T08:00:00Z");
        InMemoryReportingRepository repository = new InMemoryReportingRepository();
        repository.insertReportIfAbsent(new ReportExecutionReport(
                reportId,
                "project-alpha",
                runId,
                "report-webhook-ready",
                "READY",
                "wp10-test-report-v1",
                "sha256:source-run",
                objectMapper.writeValueAsString(Map.of(
                        "generationStatus", "READY",
                        "runStatus", "FAILED",
                        "evidenceManifestCount", 2,
                        "failureSummaryStored", false
                )),
                objectMapper.writeValueAsString(Map.of("aggregateOnly", true)),
                "report-user",
                generatedAt,
                null,
                null,
                "trc_report_webhook_ready",
                null,
                generatedAt.minusSeconds(5),
                generatedAt
        ));
        repository.replaceLatestFailureDiagnosis(reportId, new ReportFailureDiagnosis(
                UUID.randomUUID(),
                reportId,
                "AI_READY",
                objectMapper.writeValueAsString(Map.of(
                        "primaryCategory", "ASSERTION_FAILED",
                        "ruleVersion", "wp10-failure-classifier-v1"
                )),
                "sha256:model-invocation",
                new BigDecimal("0.8100"),
                true,
                objectMapper.writeValueAsString(Map.of("aiDiagnosisReady", true)),
                null,
                generatedAt,
                generatedAt
        ));

        ReportingWebhookDispatcher dispatcher = new ReportingWebhookDispatcher(
                repository,
                new ReportingPlatformContextClient(new PlatformIntegrationService(Optional.empty(), objectMapper)),
                webhookProperties(callbackUrl()),
                objectMapper,
                List.of(new StaticSecretProvider()),
                HttpClient.newBuilder().build()
        );

        dispatcher.dispatch(reportId, "READY");

        CapturedRequest request = awaitRequest(capturedRequests);
        assertThat(request.path()).isEqualTo("/callbacks/report-ready");
        assertThat(request.headers().get("X-Trace-Id")).isNotBlank();
        assertThat(request.headers().get("X-VA-Event-Id")).isEqualTo(reportId.toString());
        assertThat(request.headers().get("X-VA-Signature-Algorithm")).isEqualTo("HMAC-SHA256");
        assertThat(request.headers().get("X-VA-Signature")).isEqualTo(hmacSha256(String.join(".",
                request.headers().get("X-VA-Timestamp"),
                reportId.toString(),
                request.body()
        )));

        Map<String, Object> payload = objectMapper.readValue(request.body(), Map.class);
        assertThat(payload.get("eventType")).isEqualTo("REPORT_GENERATION_COMPLETED");
        assertThat(payload.get("reportId")).isEqualTo(reportId.toString());
        assertThat(payload.get("projectId")).isEqualTo("project-alpha");
        assertThat(payload.get("executionRunId")).isEqualTo(runId.toString());
        assertThat(payload.get("status")).isEqualTo("READY");
        assertThat(payload.get("terminalStatus")).isEqualTo("READY");
        assertThat(payload.get("schemaVersion")).isEqualTo("wp10-test-report-v1");
        assertThat(payload.get("sourceRunDigest")).isEqualTo("sha256:source-run");
        assertThat(payload.get("generatedAt").toString()).isEqualTo("2026-06-18T08:00:00Z");
        assertThat(payload).doesNotContainKeys("failedCode", "failureSummary");
        assertThat(request.body()).doesNotContain("Bearer");
        assertThat(request.body()).doesNotContain("Authorization");
        assertThat(request.body()).doesNotContain("secret://");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        assertThat(summary.get("runStatus")).isEqualTo("FAILED");
        assertThat(summary.get("evidenceManifestCount")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> latestDiagnosis = (Map<String, Object>) payload.get("latestDiagnosis");
        assertThat(latestDiagnosis.get("status")).isEqualTo("AI_READY");
        assertThat(latestDiagnosis.get("primaryCategory")).isEqualTo("ASSERTION_FAILED");
        assertThat(latestDiagnosis.get("aiDiagnosisReady")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> redactionPolicy = (Map<String, Object>) payload.get("redactionPolicy");
        assertThat(redactionPolicy.get("aggregateOnly")).isEqualTo(true);
        assertThat(redactionPolicy.get("rawEvidenceIncluded")).isEqualTo(false);
    }

    @Test
    void healthMasksInvalidOrSensitiveWebhookDisplayFields() {
        ReportingWebhookDispatcher dispatcher = new ReportingWebhookDispatcher(
                new InMemoryReportingRepository(),
                new ReportingPlatformContextClient(new PlatformIntegrationService(Optional.empty(), objectMapper)),
                webhookProperties("ftp://notify.example.test/private?token=abc"),
                objectMapper,
                List.of(),
                HttpClient.newBuilder().build()
        );

        ReportingWebhookDeliveryHealthResponse health = dispatcher.health();
        assertThat(health.enabled()).isTrue();
        assertThat(health.urlConfigured()).isTrue();
        assertThat(health.callbackUrl()).isNull();
        assertThat(health.signatureEnabled()).isTrue();
        assertThat(health.secretRefConfigured()).isTrue();
        assertThat(health.secretRefDigest()).startsWith("sha256:");
        assertThat(health.timeoutMs()).isEqualTo(7000);
    }

    private ReportingProperties webhookProperties(String callbackUrl) {
        return new ReportingProperties(
                true,
                true,
                false,
                true,
                5000,
                30000,
                "wp10-report-worker",
                4,
                1800,
                50,
                true,
                true,
                true,
                true,
                callbackUrl,
                true,
                "secret://wp10/report-webhook-signature",
                7000,
                200,
                12000,
                30000,
                "wp10-test-report-v1",
                "wp10-report-export-fields-v1"
        );
    }

    private String callbackUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/callbacks/report-ready?token=masked";
    }

    private void handle(HttpExchange exchange, ConcurrentLinkedQueue<CapturedRequest> capturedRequests) throws IOException {
        String body;
        try (InputStream stream = exchange.getRequestBody()) {
            body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Trace-Id", exchange.getRequestHeaders().getFirst("X-Trace-Id"));
        headers.put("X-VA-Timestamp", exchange.getRequestHeaders().getFirst("X-VA-Timestamp"));
        headers.put("X-VA-Event-Id", exchange.getRequestHeaders().getFirst("X-VA-Event-Id"));
        headers.put("X-VA-Signature-Algorithm", exchange.getRequestHeaders().getFirst("X-VA-Signature-Algorithm"));
        headers.put("X-VA-Signature", exchange.getRequestHeaders().getFirst("X-VA-Signature"));
        capturedRequests.add(new CapturedRequest(exchange.getRequestURI().getPath(), headers, body));
        byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private CapturedRequest awaitRequest(ConcurrentLinkedQueue<CapturedRequest> requests) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            CapturedRequest request = requests.poll();
            if (request != null) {
                return request;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected webhook callback request");
    }

    private String hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record CapturedRequest(String path, Map<String, String> headers, String body) {
    }

    private static final class StaticSecretProvider implements SecretProvider {
        @Override
        public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
            return Optional.of(new ResolvedSecret(secretRef, SIGNING_SECRET, "STATIC", "v1"));
        }
    }
}
