package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.ProviderCallResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiCompatibleModelProviderClient implements ModelProviderClient {

    private final RestClient.Builder restClientBuilder;

    public OpenAiCompatibleModelProviderClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public boolean supports(ModelProviderConfig provider) {
        return provider.providerType() == ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request) {
        if (!StringUtils.hasText(provider.baseUrl())) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "OpenAI-compatible 供应商缺少 baseUrl");
        }
        String apiKey = resolveApiKey(provider.apiKeyRef());
        Map<String, Object> payload = Map.of(
                "model", request.modelName(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.prompt() == null ? "" : request.prompt()),
                        Map.of("role", "user", "content", request.messageText() == null ? "" : request.messageText())
                )
        );
        OpenAiChatCompletionResponse response;
        try {
            response = restClientBuilder
                    .baseUrl(provider.baseUrl())
                    .requestFactory(requestFactory(provider.timeoutMs()))
                    .build()
                    .post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, providerHttpFailureMessage(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "OpenAI-compatible 供应商网络不可达或调用超时");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "OpenAI-compatible 供应商返回为空");
        }
        String content = response.choices().getFirst().message().content();
        int inputTokens = response.usage() == null ? estimateTokens(request.prompt() + request.messageText()) : response.usage().promptTokens();
        int outputTokens = response.usage() == null ? estimateTokens(content) : response.usage().completionTokens();
        return new ProviderCallResult(content, inputTokens, outputTokens);
    }

    private SimpleClientHttpRequestFactory requestFactory(int timeoutMs) {
        int safeTimeoutMs = Math.max(100, timeoutMs);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(safeTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(safeTimeoutMs));
        return requestFactory;
    }

    protected String resolveApiKey(String apiKeyRef) {
        if (!StringUtils.hasText(apiKeyRef) || !apiKeyRef.startsWith("env:")) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "apiKeyRef 必须使用 env:VARIABLE_NAME 引用");
        }
        String envName = apiKeyRef.substring("env:".length());
        String apiKey = System.getenv(envName);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "apiKeyRef 指向的环境变量不存在");
        }
        return apiKey;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String providerHttpFailureMessage(HttpStatusCode statusCode) {
        if (statusCode.value() == 401 || statusCode.value() == 403) {
            return "OpenAI-compatible 供应商认证失败";
        }
        if (statusCode.value() == 429) {
            return "OpenAI-compatible 供应商限流";
        }
        if (statusCode.is5xxServerError()) {
            return "OpenAI-compatible 供应商服务端错误";
        }
        if (statusCode.is4xxClientError()) {
            return "OpenAI-compatible 供应商请求被拒绝";
        }
        return "OpenAI-compatible 供应商调用失败";
    }

    private record OpenAiChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    private record Choice(Message message) {
    }

    private record Message(String content) {
    }

    private record Usage(
            @JsonProperty("prompt_tokens")
            int promptTokens,
            @JsonProperty("completion_tokens")
            int completionTokens
    ) {
    }
}
