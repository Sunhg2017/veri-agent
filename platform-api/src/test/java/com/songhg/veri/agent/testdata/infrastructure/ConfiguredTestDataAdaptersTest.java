package com.songhg.veri.agent.testdata.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.testdata.application.port.TestAccountProvisioningAdapter;
import com.songhg.veri.agent.testdata.application.port.TestDataCleanupAdapter;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredTestDataAdaptersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void cleanupAdapterPostsReviewedDestructiveRequestAndFiltersSensitiveSummary() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = startServer(
                authorization,
                body,
                200,
                """
                        {
                          "success": true,
                          "externalCleanupId": "cleanup-ext-001",
                          "affectedResourceCount": 4,
                          "summary": {
                            "deletedRows": 4,
                            "adapter": "sandbox-cleaner",
                            "token": "raw-token",
                            "nested": {"secret": "value"}
                          }
                        }
                        """
        );
        try {
            ConfiguredTestDataCleanupAdapter adapter = new ConfiguredTestDataCleanupAdapter(
                    cleanupProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/cleanup"),
                    objectMapper,
                    HttpClient.newHttpClient()
            );

            TestDataCleanupAdapter.CleanupResult result = adapter.cleanup(new TestDataCleanupAdapter.CleanupRequest(
                    UUID.fromString("00000000-0000-0000-0000-000000000101"),
                    "project-alpha",
                    UUID.fromString("00000000-0000-0000-0000-000000000201"),
                    "dataset-alpha",
                    "READY",
                    3,
                    List.of("ttlSeconds", "mode"),
                    "CLEANUP",
                    "cleanup-run-001",
                    "data-set:dataset-alpha",
                    1,
                    "wp8-worker-a",
                    Instant.parse("2026-06-26T00:00:00Z")
            ));

            assertThat(authorization.get()).isEqualTo("Bearer cleanup-token");
            assertThat(body.get().path("taskId").asText()).isEqualTo("00000000-0000-0000-0000-000000000101");
            assertThat(body.get().path("cleanupPolicyKeys").size()).isEqualTo(2);
            assertThat(body.get().toString()).doesNotContain("cleanup-token");
            assertThat(result.success()).isTrue();
            assertThat(result.externalCleanupId()).isEqualTo("cleanup-ext-001");
            assertThat(result.affectedResourceCount()).isEqualTo(4);
            assertThat(result.summary())
                    .containsEntry("deletedRows", 4)
                    .containsEntry("adapter", "sandbox-cleaner")
                    .doesNotContainKey("token")
                    .containsEntry("nested", "[REDACTED_COMPLEX_VALUE]");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void provisioningAdapterPostsHttpRequestAndKeepsSecretRefOutOfSummary() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = startServer(
                authorization,
                body,
                200,
                """
                        {
                          "accountKey": "auto-admin-0001",
                          "displayName": "Auto admin 0001",
                          "roleTags": ["ADMIN"],
                          "scopeSummary": {
                            "tenant": "alpha",
                            "password": "raw-password"
                          },
                          "secretRef": "secret://wp8/provisioned/admin/0001",
                          "healthStatus": "HEALTHY",
                          "healthSummary": "business account opened",
                          "summary": {
                            "externalAccountId": "biz-0001",
                            "token": "raw-token"
                          }
                        }
                        """
        );
        try {
            ConfiguredTestAccountProvisioningAdapter adapter = new ConfiguredTestAccountProvisioningAdapter(
                    provisioningProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/provision"),
                    objectMapper,
                    HttpClient.newHttpClient()
            );

            TestAccountProvisioningAdapter.ProvisionedAccount account =
                    adapter.provision(new TestAccountProvisioningAdapter.ProvisioningRequest(
                            UUID.fromString("00000000-0000-0000-0000-000000000301"),
                            "project-alpha",
                            "app-alpha",
                            "env-staging",
                            "pool-alpha",
                            "auto-admin-0001",
                            "Auto admin 0001",
                            List.of("ADMIN"),
                            Map.of("tenant", "alpha"),
                            "secret://wp8/provisioned/admin/0001",
                            "wp8-worker-a",
                            Instant.parse("2026-06-26T00:00:00Z")
                    ));

            assertThat(authorization.get()).isEqualTo("Bearer provisioning-token");
            assertThat(body.get().path("accountKey").asText()).isEqualTo("auto-admin-0001");
            assertThat(body.get().path("secretRef").asText()).isEqualTo("secret://wp8/provisioned/admin/0001");
            assertThat(body.get().toString()).doesNotContain("provisioning-token");
            assertThat(account.accountKey()).isEqualTo("auto-admin-0001");
            assertThat(account.roleTags()).containsExactly("ADMIN");
            assertThat(account.scopeSummary()).containsEntry("tenant", "alpha").doesNotContainKey("password");
            assertThat(account.secretRef()).isEqualTo("secret://wp8/provisioned/admin/0001");
            assertThat(account.summary()).containsEntry("externalAccountId", "biz-0001").doesNotContainKey("token");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(
            AtomicReference<String> authorization,
            AtomicReference<JsonNode> body,
            int status,
            String responseBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private TestDataProperties cleanupProperties(String url) {
        return properties(true, "HTTP", url, "cleanup-token", false, "DISABLED", "", "");
    }

    private TestDataProperties provisioningProperties(String url) {
        return properties(false, "DISABLED", "", "", true, "HTTP", url, "provisioning-token");
    }

    private TestDataProperties properties(
            boolean cleanupEnabled,
            String cleanupAdapterMode,
            String cleanupAdapterUrl,
            String cleanupAdapterToken,
            boolean accountProvisioningEnabled,
            String accountProvisioningAdapterMode,
            String accountProvisioningAdapterUrl,
            String accountProvisioningAdapterToken
    ) {
        return new TestDataProperties(
                true,
                true,
                5_000,
                30_000,
                "wp8-worker",
                10,
                50,
                100,
                10,
                2_048,
                60,
                120,
                cleanupEnabled,
                cleanupAdapterMode,
                cleanupAdapterUrl,
                cleanupAdapterToken,
                5_000,
                accountProvisioningEnabled,
                accountProvisioningAdapterMode,
                accountProvisioningAdapterUrl,
                accountProvisioningAdapterToken,
                5_000,
                10,
                true
        );
    }
}
