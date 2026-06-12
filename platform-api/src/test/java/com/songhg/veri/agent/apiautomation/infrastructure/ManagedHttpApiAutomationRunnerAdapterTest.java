package com.songhg.veri.agent.apiautomation.infrastructure;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedHttpApiAutomationRunnerAdapterTest {

    private final ManagedHttpApiAutomationRunnerAdapter adapter = new ManagedHttpApiAutomationRunnerAdapter();

    @Test
    void validatesApprovedBundleMetadata() {
        assertThat(adapter.validateBundle(scriptBundle("APPROVED", "PASSED", 1, "digest")).accepted()).isTrue();
        assertThat(adapter.validateBundle(scriptBundle("APPROVED", "FAILED", 1, "digest")).errorCode())
                .isEqualTo("SCRIPT_STATIC_CHECK_FAILED");
        assertThat(adapter.validateBundle(scriptBundle("APPROVED", "PASSED", 0, "digest")).errorCode())
                .isEqualTo("RUNNER_FAILED");
    }

    @Test
    void executesCaseAndKeepsOnlyAggregateAssertionSummary() throws Exception {
        HttpServer server = startServer("/v1/items/1", 200, "{\"token\":\"secret-response\"}", 0, null);
        try {
            ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(
                    server,
                    List.of(automationCase("GET", "/v1/items/{id}", 200)),
                    5
            ));

            assertThat(result.status()).isEqualTo("PASSED");
            assertThat(result.runnerMode()).isEqualTo("MANAGED");
            assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
                assertThat(caseResult.status()).isEqualTo("PASSED");
                assertThat(caseResult.assertionSummaryJson())
                        .contains("\"aggregateOnly\":true")
                        .contains("\"rawRequestResponseStored\":false")
                        .contains("\"expectedStatus\":200")
                        .contains("\"actualStatus\":200")
                        .doesNotContain("secret-response");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsFailedWhenStatusAssertionDoesNotMatch() throws Exception {
        HttpServer server = startServer("/v1/payments", 500, "{\"apiKey\":\"raw-secret\"}", 0, null);
        try {
            ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(
                    server,
                    List.of(automationCase("POST", "/v1/payments", 200)),
                    5
            ));

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.errorCode()).isEqualTo("RUNNER_FAILED");
            assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
                assertThat(caseResult.status()).isEqualTo("FAILED");
                assertThat(caseResult.errorCode()).isEqualTo("ASSERTION_FAILED");
                assertThat(caseResult.assertionSummaryJson())
                        .contains("\"actualStatus\":500")
                        .doesNotContain("raw-secret");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replacesOpenApiPathTemplateBeforeDispatch() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        HttpServer server = startServer("/v1/users/1/orders/1", 204, "", 0, requestedPath);
        try {
            ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(
                    server,
                    List.of(automationCase("DELETE", "/v1/users/{userId}/orders/{orderId}", 204)),
                    5
            ));

            assertThat(result.status()).isEqualTo("PASSED");
            assertThat(requestedPath.get()).isEqualTo("/v1/users/1/orders/1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsTimeoutWithoutLeakingRawTarget() throws Exception {
        HttpServer server = startServer("/v1/slow", 200, "late response", 1_500, null);
        try {
            ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(runRequest(
                    server,
                    List.of(automationCase("GET", "/v1/slow", 200)),
                    1
            ));

            assertThat(result.status()).isEqualTo("TIMEOUT");
            assertThat(result.errorCode()).isEqualTo("RUNNER_TIMEOUT");
            assertThat(result.errorSummary()).isEqualTo("managed runner timed out");
            assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
                assertThat(caseResult.status()).isEqualTo("TIMEOUT");
                assertThat(caseResult.errorCode()).isEqualTo("RUNNER_TIMEOUT");
                assertThat(caseResult.errorSummary()).isEqualTo("HTTP request timed out");
                assertThat(caseResult.assertionSummaryJson()).doesNotContain("127.0.0.1", "/v1/slow", "late response");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsafeCasePathBeforeNetworkDispatch() {
        ApiAutomationRunnerPort.RunnerRunResult result = adapter.run(new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle("APPROVED", "PASSED", 1, "digest"),
                List.of(automationCase("GET", "https://attacker.example/v1", 200)),
                "https://api.example.test",
                5,
                List.of()
        ));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.status()).isEqualTo("ERROR");
            assertThat(caseResult.errorCode()).isEqualTo("RUNNER_FAILED");
            assertThat(caseResult.errorSummary()).isEqualTo("invalid runner request");
        });
    }

    private static HttpServer startServer(
            String path,
            int status,
            String responseBody,
            int delayMillis,
            AtomicReference<String> requestedPath
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            if (requestedPath != null) {
                requestedPath.set(exchange.getRequestURI().getPath());
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            if (status == 204 || status == 304) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static ApiAutomationRunnerPort.RunnerRunRequest runRequest(
            HttpServer server,
            List<ApiAutomationCase> cases,
            int timeoutSeconds
    ) {
        return new ApiAutomationRunnerPort.RunnerRunRequest(
                UUID.randomUUID(),
                scriptBundle("APPROVED", "PASSED", 1, "digest"),
                cases,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                timeoutSeconds,
                List.of()
        );
    }

    private static ApiAutomationCase automationCase(String method, String path, int expectedStatus) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationCase(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "project-alpha",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                method + " " + path,
                method,
                path,
                "SMOKE",
                expectedStatus,
                "{}",
                "{}",
                "GENERATED",
                "READY",
                now,
                now
        );
    }

    private static ApiAutomationScriptBundle scriptBundle(
            String status,
            String staticCheckStatus,
            int fileCount,
            String digest
    ) {
        Instant now = Instant.EPOCH;
        return new ApiAutomationScriptBundle(
                UUID.randomUUID(),
                "project-alpha",
                UUID.randomUUID(),
                status,
                digest,
                fileCount,
                "{}",
                "{}",
                staticCheckStatus,
                "{}",
                null,
                "tester",
                "tester",
                now,
                now,
                null,
                "tester",
                "tester",
                now,
                now
        );
    }
}
