package com.songhg.veri.agent.modelaccess.infrastructure;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleModelProviderClientTest {

    @Test
    void parsesOpenAiCompatibleChatCompletionResponse() throws Exception {
        HttpServer server = startOpenAiCompatibleServer();
        try {
            OpenAiCompatibleModelProviderClient client = new OpenAiCompatibleModelProviderClient(RestClient.builder()) {
                @Override
                protected String resolveApiKey(String apiKeyRef) {
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
