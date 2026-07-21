package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleModelProviderClientTest {

    @Test
    void parsesOpenAiCompatibleChatCompletionResponse() throws Exception {
        HttpServer server = startOpenAiCompatibleServer();
        try {
            OpenAiCompatibleModelProviderClient client = new OpenAiCompatibleModelProviderClient(RestClient.builder()) {
                @Override
                protected String resolveApiKey(ModelProviderConfig provider) {
                    return "test-api-key";
                }
            };
            ProviderCallResult result = client.call(provider(server), new ProviderCallRequest(
                    "gpt-test",
                    "system prompt",
                    "user prompt"
            ));

            assertThat(result.content()).isEqualTo("contract response");
            assertThat(result.inputTokens()).isEqualTo(12);
            assertThat(result.outputTokens()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reusesRestClientForSameProviderEndpointAndTimeout() throws Exception {
        HttpServer server = startOpenAiCompatibleServer();
        try {
            OpenAiCompatibleModelProviderClient client = new OpenAiCompatibleModelProviderClient(RestClient.builder());
            ModelProviderConfig provider = provider(server);

            assertThat(client.restClient(provider)).isSameAs(client.restClient(provider));
            assertThat(client.restClient(providerWithTimeout(server, 2000))).isNotSameAs(client.restClient(provider));
        } finally {
            server.stop(0);
        }
    }


    @Test
    void resolvesSecretRefThroughSecretProviderWithProviderScope() {
        AtomicReference<SecretResolveContext> captured = new AtomicReference<>();
        OpenAiCompatibleModelProviderClient client = new OpenAiCompatibleModelProviderClient(
                RestClient.builder(),
                List.of((secretRef, context) -> {
                    captured.set(context);
                    return "secret://model/api-key".equals(secretRef)
                            ? Optional.of(new ResolvedSecret(secretRef, "sk-live-key", "local", "v1"))
                            : Optional.empty();
                })
        );
        ModelProviderConfig provider = providerWithApiKeyRef("secret://model/api-key");

        assertThat(client.resolveApiKey(provider)).isEqualTo("sk-live-key");
        assertThat(captured.get().purpose()).isEqualTo("MODEL_API_KEY");
        assertThat(captured.get().callerService()).isEqualTo("wp2-model-access");
        assertThat(captured.get().scopeType()).isEqualTo("CONFIG");
        assertThat(captured.get().scopeId()).isEqualTo(provider.id().toString());
    }

    @Test
    void rejectsSecretRefWhenNoSecretProviderResolves() {
        OpenAiCompatibleModelProviderClient client = new OpenAiCompatibleModelProviderClient(RestClient.builder());

        assertThatThrownBy(() -> client.resolveApiKey(providerWithApiKeyRef("secret://model/missing")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("apiKeyRef 指向的密钥未维护");
    }

    @Test
    void rejectsUnsupportedApiKeyRefPrefix() {
        OpenAiCompatibleModelProviderClient client = new OpenAiCompatibleModelProviderClient(RestClient.builder());

        assertThatThrownBy(() -> client.resolveApiKey(providerWithApiKeyRef("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("env:VARIABLE_NAME 或 secret://");
    }

    private ModelProviderConfig providerWithApiKeyRef(String apiKeyRef) {
        Instant now = Instant.now();
        return new ModelProviderConfig(
                UUID.randomUUID(),
                "secret-ref-provider",
                ProviderType.OPENAI_COMPATIBLE,
                "default",
                "CHAT",
                "http://127.0.0.1:9",
                apiKeyRef,
                ProviderStatus.ENABLED,
                1,
                1000,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                now,
                now
        );
    }

    private HttpServer startOpenAiCompatibleServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = """
                    {
                      "choices": [
                        {"message": {"content": "contract response"}}
                      ],
                      "usage": {
                        "prompt_tokens": 12,
                        "completion_tokens": 3
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private ModelProviderConfig provider(HttpServer server) {
        return providerWithTimeout(server, 1000);
    }

    private ModelProviderConfig providerWithTimeout(HttpServer server, int timeoutMs) {
        Instant now = Instant.now();
        return new ModelProviderConfig(
                UUID.randomUUID(),
                "contract-provider",
                ProviderType.OPENAI_COMPATIBLE,
                "default",
                "CHAT,TEXT,JSON",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "env:TEST_MODEL_API_KEY",
                ProviderStatus.ENABLED,
                1,
                timeoutMs,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                now,
                now
        );
    }
}
