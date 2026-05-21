package com.songhg.veri.agent.common.secret;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalSecretProviderTest {

    @Test
    void resolvesVaultSecretThroughConfiguredEndpoint() {
        String scopeId = UUID.randomUUID().toString();
        CapturingHttpClient httpClient = new CapturingHttpClient(200, """
                {"value":"vault-secret","provider":"vault-prod","version":"v7"}
                """);
        ExternalSecretProvider provider = provider(row(scopeId), httpClient, "external-token");

        Optional<ResolvedSecret> resolved = provider.resolve("vault://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                scopeId
        ));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().value()).isEqualTo("vault-secret");
        assertThat(resolved.get().provider()).isEqualTo("vault-prod");
        assertThat(resolved.get().version()).isEqualTo("v7");
        assertThat(httpClient.lastRequest.uri()).isEqualTo(URI.create("https://vault.example.test/resolve"));
        assertThat(httpClient.lastRequest.headers().firstValue("Authorization")).contains("Bearer external-token");
        assertThat(httpClient.lastBody)
                .contains("\"secretRef\":\"vault://wp4/source-a\"")
                .contains("\"providerType\":\"VAULT\"")
                .doesNotContain("vault-secret");
    }

    @Test
    void rejectsExternalSecretWhenScopeDoesNotMatch() {
        ExternalSecretProvider provider = provider(row(UUID.randomUUID().toString()), new CapturingHttpClient(200, "{}"), "token");

        assertThatThrownBy(() -> provider.resolve("vault://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                UUID.randomUUID().toString()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥作用域不匹配");
    }

    @Test
    void requiresExternalResolveEndpointForVaultOrKms() {
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(UUID.randomUUID().toString())),
                new SecretProviderProperties("", "v1", "", "token", 3, 1, "", "", ""),
                new ObjectMapper(),
                new CapturingHttpClient(200, "{}")
        );

        assertThatThrownBy(() -> provider.resolve("vault://wp4/source-a", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("resolve endpoint 未配置");
    }

    @Test
    void reportsDisabledHealthWhenExternalEndpointIsMissing() {
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(UUID.randomUUID().toString())),
                new SecretProviderProperties("", "v1", "", "token", 3, 1, "", "", ""),
                new ObjectMapper(),
                new CapturingHttpClient(200, "{}")
        );

        SecretProviderHealth health = provider.health();

        assertThat(health.providerType()).isEqualTo("VAULT_KMS");
        assertThat(health.configured()).isFalse();
        assertThat(health.status()).isEqualTo("DISABLED");
        assertThat(health.lastErrorMessage()).contains("未启用");
    }

    @Test
    void reportsUnknownHealthWhenHealthEndpointIsMissing() {
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(UUID.randomUUID().toString())),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve", "token", 3, 1, "", "", ""),
                new ObjectMapper(),
                new CapturingHttpClient(200, "{}")
        );

        SecretProviderHealth health = provider.health();

        assertThat(health.configured()).isTrue();
        assertThat(health.status()).isEqualTo("UNKNOWN");
        assertThat(health.timeoutSeconds()).isEqualTo(3);
        assertThat(health.maxAttempts()).isEqualTo(2);
        assertThat(health.lastErrorMessage()).contains("健康检查端点未配置");
    }

    @Test
    void reportsUpHealthThroughConfiguredHealthEndpoint() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, "{\"status\":\"UP\"}");
        ExternalSecretProvider provider = provider(row(UUID.randomUUID().toString()), httpClient, "external-token");

        SecretProviderHealth health = provider.health();

        assertThat(health.configured()).isTrue();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.lastErrorMessage()).isNull();
        assertThat(httpClient.lastRequest.uri()).isEqualTo(URI.create("https://vault.example.test/health"));
        assertThat(httpClient.lastRequest.headers().firstValue("Authorization")).contains("Bearer external-token");
    }

    @Test
    void reportsDownHealthWithoutLeakingEndpointOrToken() {
        CapturingHttpClient httpClient = new CapturingHttpClient(new IllegalStateException(
                "failed https://vault.example.test/health token external-token"
        ));
        ExternalSecretProvider provider = provider(row(UUID.randomUUID().toString()), httpClient, "external-token");

        SecretProviderHealth health = provider.health();

        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.lastErrorMessage())
                .contains("<external-secret-health>")
                .doesNotContain("external-token")
                .doesNotContain("https://vault.example.test/health");
    }

    @Test
    void retriesResolveOnTransientServerErrors() {
        String scopeId = UUID.randomUUID().toString();
        CapturingHttpClient httpClient = new CapturingHttpClient(List.of(
                response(503, "{}"),
                response(200, "{\"value\":\"vault-secret\"}")
        ));
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(scopeId)),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve", "token", 3, 1,
                        "https://vault.example.test/health", "", ""),
                new ObjectMapper(),
                httpClient
        );

        Optional<ResolvedSecret> resolved = provider.resolve("vault://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                scopeId
        ));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().value()).isEqualTo("vault-secret");
        assertThat(httpClient.sendCount).isEqualTo(2);
    }

    @Test
    void signsResolveRequestsWhenSigningSecretIsConfigured() throws Exception {
        String scopeId = UUID.randomUUID().toString();
        CapturingHttpClient httpClient = new CapturingHttpClient(200, "{\"value\":\"vault-secret\"}");
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(scopeId)),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve?env=prod",
                        "external-token", 3, 1, "https://vault.example.test/health",
                        "vault-key-1", "external-signing-secret"),
                new ObjectMapper(),
                httpClient
        );

        provider.resolve("vault://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                scopeId
        ));

        HttpHeaders headers = httpClient.lastRequest.headers();
        String timestamp = headers.firstValue("X-VA-Secret-Timestamp").orElseThrow();
        String nonce = headers.firstValue("X-VA-Secret-Nonce").orElseThrow();
        String canonical = String.join("\n",
                "POST",
                "/resolve?env=prod",
                timestamp,
                nonce,
                sha256Hex(httpClient.lastBody)
        );

        assertThat(headers.firstValue("Authorization")).contains("Bearer external-token");
        assertThat(headers.firstValue("X-VA-Secret-Signature-Algorithm")).contains("HMAC-SHA256");
        assertThat(headers.firstValue("X-VA-Secret-Key-Id")).contains("vault-key-1");
        assertThat(headers.firstValue("X-VA-Secret-Signature")).contains(hmacSha256("external-signing-secret", canonical));
        assertThat(httpClient.lastBody).doesNotContain("external-signing-secret");
    }

    @Test
    void signsHealthRequestsWhenSigningSecretIsConfigured() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, "{\"status\":\"UP\"}");
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(UUID.randomUUID().toString())),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve",
                        "", 3, 1, "https://vault.example.test/health",
                        "vault-key-1", "external-signing-secret"),
                new ObjectMapper(),
                httpClient
        );

        SecretProviderHealth health = provider.health();

        HttpHeaders headers = httpClient.lastRequest.headers();
        String timestamp = headers.firstValue("X-VA-Secret-Timestamp").orElseThrow();
        String nonce = headers.firstValue("X-VA-Secret-Nonce").orElseThrow();
        String canonical = String.join("\n",
                "GET",
                "/health",
                timestamp,
                nonce,
                sha256Hex("")
        );

        assertThat(health.status()).isEqualTo("UP");
        assertThat(headers.firstValue("X-VA-Secret-Key-Id")).contains("vault-key-1");
        assertThat(headers.firstValue("X-VA-Secret-Signature")).contains(hmacSha256("external-signing-secret", canonical));
    }

    @Test
    void reportsDownHealthWithoutLeakingSigningSecret() {
        CapturingHttpClient httpClient = new CapturingHttpClient(new IllegalStateException(
                "auth failed external-signing-secret https://vault.example.test/health"
        ));
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(UUID.randomUUID().toString())),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve",
                        "", 3, 1, "https://vault.example.test/health",
                        "vault-key-1", "external-signing-secret"),
                new ObjectMapper(),
                httpClient
        );

        SecretProviderHealth health = provider.health();

        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.lastErrorMessage())
                .doesNotContain("external-signing-secret")
                .doesNotContain("https://vault.example.test/health")
                .contains("<external-secret-health>");
    }

    @Test
    void authenticationFailureDoesNotRevealSecretRefOrSigningSecret() {
        String scopeId = UUID.randomUUID().toString();
        CapturingHttpClient httpClient = new CapturingHttpClient(401, "{\"error\":\"vault://wp4/source-a denied\"}");
        ExternalSecretProvider provider = new ExternalSecretProvider(
                jdbcTemplateReturning(row(scopeId)),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve",
                        "", 3, 1, "https://vault.example.test/health",
                        "vault-key-1", "external-signing-secret"),
                new ObjectMapper(),
                httpClient
        );

        assertThatThrownBy(() -> provider.resolve("vault://wp4/source-a", new SecretResolveContext(
                "WEBHOOK_SIGNING",
                "wp4-document-input",
                "CONFIG",
                scopeId
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("异常状态: 401")
                .hasMessageNotContaining("vault://wp4/source-a")
                .hasMessageNotContaining("external-signing-secret")
                .hasMessageNotContaining("https://vault.example.test/resolve");
    }

    private ExternalSecretProvider provider(ExternalSecretDbRow row, CapturingHttpClient httpClient, String token) {
        return new ExternalSecretProvider(
                jdbcTemplateReturning(row),
                new SecretProviderProperties("", "v1", "https://vault.example.test/resolve", token, 3, 1,
                        "https://vault.example.test/health", "", ""),
                new ObjectMapper(),
                httpClient
        );
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacSha256(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static CapturingHttpClient.ResponseSpec response(int statusCode, String body) {
        return new CapturingHttpClient.ResponseSpec(statusCode, body);
    }

    private JdbcTemplate jdbcTemplateReturning(ExternalSecretDbRow row) {
        return new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                try {
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("secret_ref")).thenReturn(row.secretRef());
                    when(resultSet.getString("purpose")).thenReturn(row.purpose());
                    when(resultSet.getString("scope_type")).thenReturn(row.scopeType());
                    when(resultSet.getString("scope_id")).thenReturn(row.scopeId());
                    when(resultSet.getString("secret_version")).thenReturn(row.secretVersion());
                    when(resultSet.getString("provider_code")).thenReturn(row.providerCode());
                    when(resultSet.getString("provider_type")).thenReturn(row.providerType());
                    return List.of(rowMapper.mapRow(resultSet, 0));
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    private ExternalSecretDbRow row(String scopeId) {
        return new ExternalSecretDbRow(
                "vault://wp4/source-a",
                "WEBHOOK_SIGNING",
                "CONFIG",
                scopeId,
                "v1",
                "vault-prod",
                "VAULT"
        );
    }

    private record ExternalSecretDbRow(
            String secretRef,
            String purpose,
            String scopeType,
            String scopeId,
            String secretVersion,
            String providerCode,
            String providerType
    ) {
    }

    private static final class CapturingHttpClient extends HttpClient {
        private final List<ResponseSpec> responses;
        private final RuntimeException failure;
        private HttpRequest lastRequest;
        private String lastBody;
        private int sendCount;

        private CapturingHttpClient(int statusCode, String body) {
            this(List.of(new ResponseSpec(statusCode, body)));
        }

        private CapturingHttpClient(List<ResponseSpec> responses) {
            this.responses = List.copyOf(responses);
            this.failure = null;
        }

        private CapturingHttpClient(RuntimeException failure) {
            this.responses = List.of();
            this.failure = failure;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(3));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            sendCount++;
            this.lastRequest = request;
            this.lastBody = readPublisher(request);
            if (failure != null) {
                throw failure;
            }
            ResponseSpec response = responses.get(Math.min(sendCount - 1, responses.size() - 1));
            @SuppressWarnings("unchecked")
            T typedBody = (T) response.body();
            return new SimpleHttpResponse<>(request, response.statusCode(), typedBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }

        private String readPublisher(HttpRequest request) {
            if (request.bodyPublisher().isEmpty()) {
                return "";
            }
            BodySubscriber subscriber = new BodySubscriber();
            request.bodyPublisher().orElseThrow().subscribe(subscriber);
            return subscriber.body();
        }

        private record ResponseSpec(int statusCode, String body) {
        }
    }

    private record SimpleHttpResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {
        private final StringBuilder body = new StringBuilder();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            body.append(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return body.toString();
        }
    }
}
