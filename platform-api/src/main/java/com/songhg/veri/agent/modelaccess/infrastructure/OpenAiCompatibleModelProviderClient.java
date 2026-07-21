package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.port.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.ResolvedSecret;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** 模型供应商密钥用途约定：密钥管理页创建 MODEL_API_KEY 用途的 secret:// 引用后可被此处解析 */
    static final String API_KEY_SECRET_PURPOSE = "MODEL_API_KEY";
    static final String API_KEY_CALLER_SERVICE = "wp2-model-access";
    static final String API_KEY_SCOPE_TYPE = "CONFIG";
    static final String SECRET_REF_PREFIX = "secret://";
    static final String ENV_REF_PREFIX = "env:";

    private final RestClient.Builder restClientBuilder;
    private final List<SecretProvider> secretProviders;
    private final ConcurrentMap<ClientKey, RestClient> clients = new ConcurrentHashMap<>();

    @Autowired
    public OpenAiCompatibleModelProviderClient(RestClient.Builder restClientBuilder, ObjectProvider<SecretProvider> secretProviders) {
        this.restClientBuilder = restClientBuilder;
        this.secretProviders = secretProviders == null ? List.of() : secretProviders.orderedStream().toList();
    }

    /** 测试用便捷构造：无密钥提供方时 secret:// 引用解析会明确报错 */
    OpenAiCompatibleModelProviderClient(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, (ObjectProvider<SecretProvider>) null);
    }

    /** 测试用便捷构造：直接注入密钥提供方列表 */
    OpenAiCompatibleModelProviderClient(RestClient.Builder restClientBuilder, List<SecretProvider> secretProviders) {
        this.restClientBuilder = restClientBuilder;
        this.secretProviders = secretProviders == null ? List.of() : List.copyOf(secretProviders);
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
        String apiKey = resolveApiKey(provider);
        Map<String, Object> payload = Map.of(
                "model", request.modelName(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.prompt() == null ? "" : request.prompt()),
                        Map.of("role", "user", "content", request.messageText() == null ? "" : request.messageText())
                )
        );
        OpenAiChatCompletionResponse response;
        try {
            response = restClient(provider)
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

    RestClient restClient(ModelProviderConfig provider) {
        String baseUrl = provider.baseUrl().trim();
        int timeoutMs = Math.max(100, provider.timeoutMs());
        return clients.computeIfAbsent(new ClientKey(baseUrl, timeoutMs), key -> restClientBuilder
                .baseUrl(key.baseUrl())
                .requestFactory(requestFactory(key.timeoutMs()))
                .build());
    }

    /**
     * 解析供应商 API Key：env: 前缀走环境变量；secret:// 前缀委托密钥提供方解密，
     * 作用域固定为 CONFIG + 供应商 ID，保证密钥与供应商一一绑定。
     */
    protected String resolveApiKey(ModelProviderConfig provider) {
        String apiKeyRef = provider.apiKeyRef();
        if (!StringUtils.hasText(apiKeyRef)) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "apiKeyRef 必须使用 env:VARIABLE_NAME 或 secret:// 引用");
        }
        if (apiKeyRef.startsWith(SECRET_REF_PREFIX)) {
            return resolveApiKeyFromSecretStore(provider, apiKeyRef);
        }
        if (!apiKeyRef.startsWith(ENV_REF_PREFIX)) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "apiKeyRef 必须使用 env:VARIABLE_NAME 或 secret:// 引用");
        }
        String envName = apiKeyRef.substring(ENV_REF_PREFIX.length());
        String apiKey = System.getenv(envName);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "apiKeyRef 指向的环境变量不存在");
        }
        return apiKey;
    }

    private String resolveApiKeyFromSecretStore(ModelProviderConfig provider, String apiKeyRef) {
        SecretResolveContext context = new SecretResolveContext(
                API_KEY_SECRET_PURPOSE,
                API_KEY_CALLER_SERVICE,
                API_KEY_SCOPE_TYPE,
                provider.id() == null ? null : provider.id().toString()
        );
        for (SecretProvider secretProvider : secretProviders) {
            Optional<ResolvedSecret> resolved = secretProvider.resolve(apiKeyRef, context);
            if (resolved.isPresent() && StringUtils.hasText(resolved.get().value())) {
                return resolved.get().value();
            }
        }
        // 未命中时不回退任何明文配置，避免绕过密钥库审计
        throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "apiKeyRef 指向的密钥未维护、已撤销或作用域不匹配");
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

    private record ClientKey(String baseUrl, int timeoutMs) {
    }
}
