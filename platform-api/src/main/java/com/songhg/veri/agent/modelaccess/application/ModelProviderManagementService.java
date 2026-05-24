package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelProviderManagementService {

    private static final String DEFAULT_ROUTING_GROUP = "default";
    private static final String DEFAULT_PROVIDER_CAPABILITIES = "CHAT,TEXT,JSON,REQUIREMENT_PARSE";

    private final ModelAccessRepository repository;
    private final List<ModelProviderClient> providerClients;
    private final SensitiveContentGuard contentGuard;
    private final ModelAccessProperties properties;
    private final ModelAccessMetrics metrics;
    private final ProviderResilienceManager providerResilienceManager;

    public ModelProviderManagementService(
            ModelAccessRepository repository,
            List<ModelProviderClient> providerClients,
            SensitiveContentGuard contentGuard,
            ModelAccessProperties properties,
            ModelAccessMetrics metrics,
            ProviderResilienceManager providerResilienceManager
    ) {
        this.repository = repository;
        this.providerClients = providerClients;
        this.contentGuard = contentGuard;
        this.properties = properties;
        this.metrics = metrics;
        this.providerResilienceManager = providerResilienceManager;
    }

    public List<ModelProviderConfig> providers() {
        return repository.providers();
    }

    public ModelProviderConfig createProvider(CreateProviderCommand request) {
        Instant now = Instant.now();
        ModelProviderConfig provider = new ModelProviderConfig(
                UUID.randomUUID(),
                request.name().trim(),
                request.providerType(),
                normalizeRoutingGroup(request.routingGroup()),
                normalizeCapabilities(request.capabilities()),
                trimToNull(request.baseUrl()),
                trimToNull(request.apiKeyRef()),
                ProviderStatus.ENABLED,
                request.priority() == null ? 100 : request.priority(),
                request.timeoutMs() == null ? 10000 : request.timeoutMs(),
                request.inputCostPer1kTokens() == null ? BigDecimal.ZERO : request.inputCostPer1kTokens(),
                request.outputCostPer1kTokens() == null ? BigDecimal.ZERO : request.outputCostPer1kTokens(),
                now,
                now
        );
        validateProviderConfig(provider);
        ensureProviderNameAvailable(provider.name(), provider.id());
        return repository.saveProvider(provider);
    }

    public ModelProviderConfig updateProvider(UUID id, UpdateProviderCommand request) {
        ModelProviderConfig existing = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        ModelProviderConfig updated = new ModelProviderConfig(
                existing.id(),
                StringUtils.hasText(request.name()) ? request.name().trim() : existing.name(),
                existing.providerType(),
                request.routingGroup() == null ? existing.routingGroup() : normalizeRoutingGroup(request.routingGroup()),
                request.capabilities() == null ? existing.capabilities() : normalizeCapabilities(request.capabilities()),
                request.baseUrl() == null ? existing.baseUrl() : trimToNull(request.baseUrl()),
                request.apiKeyRef() == null ? existing.apiKeyRef() : trimToNull(request.apiKeyRef()),
                existing.status(),
                request.priority() == null ? existing.priority() : request.priority(),
                request.timeoutMs() == null ? existing.timeoutMs() : request.timeoutMs(),
                request.inputCostPer1kTokens() == null ? existing.inputCostPer1kTokens() : request.inputCostPer1kTokens(),
                request.outputCostPer1kTokens() == null ? existing.outputCostPer1kTokens() : request.outputCostPer1kTokens(),
                existing.createdAt(),
                Instant.now()
        );
        validateProviderConfig(updated);
        ensureProviderNameAvailable(updated.name(), updated.id());
        return repository.saveProvider(updated);
    }

    public ModelProviderConfig setProviderStatus(UUID id, ProviderStatus status) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        return repository.saveProvider(new ModelProviderConfig(
                provider.id(),
                provider.name(),
                provider.providerType(),
                provider.routingGroup(),
                provider.capabilities(),
                provider.baseUrl(),
                provider.apiKeyRef(),
                status,
                provider.priority(),
                provider.timeoutMs(),
                provider.inputCostPer1kTokens(),
                provider.outputCostPer1kTokens(),
                provider.createdAt(),
                Instant.now()
        ));
    }

    public ProviderCheckResult checkProvider(UUID id) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        java.util.Optional<ProviderCheckResult> cached = providerResilienceManager.cachedProviderCheck(provider);
        if (cached.isPresent()) {
            return cached.get();
        }
        Instant startedAt = Instant.now();
        String modelName = properties.defaultModel();
        ProviderCheckResult response;
        try {
            ModelProviderClient client = clientFor(provider);
            client.call(provider, new ProviderCallRequest(
                    modelName,
                    "WP2 provider readiness check. Do not include secrets.",
                    "Return OK."
            ));
            response = new ProviderCheckResult(
                    provider.id(),
                    provider.name(),
                    provider.providerType(),
                    provider.status(),
                    "UP",
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    modelName,
                    null,
                    null,
                    false,
                    Instant.now()
            );
        } catch (RuntimeException exception) {
            response = new ProviderCheckResult(
                    provider.id(),
                    provider.name(),
                    provider.providerType(),
                    provider.status(),
                    "DOWN",
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    modelName,
                    providerCheckErrorCode(exception),
                    sanitizeProviderCheckError(exception.getMessage()),
                    false,
                    Instant.now()
            );
        }
        providerResilienceManager.cacheProviderCheck(provider, response);
        metrics.recordProviderCheck(response);
        return response;
    }

    public ProviderResilienceResult providerResilience(UUID id) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        ProviderResilienceManager.CircuitStateView circuitState = providerResilienceManager.circuitState(provider);
        return new ProviderResilienceResult(
                provider.id(),
                provider.name(),
                circuitState.open(),
                circuitState.consecutiveFailures(),
                circuitState.openUntil(),
                providerResilienceManager.rateLimitEnabled(),
                providerResilienceManager.rateLimitMaxRequests(),
                providerResilienceManager.rateLimitWindowSeconds(),
                providerResilienceManager.concurrencyLimitEnabled(),
                providerResilienceManager.maxConcurrentRequests(),
                providerResilienceManager.availablePermits(provider)
        );
    }

    public ProviderResilienceResult resetProviderCircuit(UUID id) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        providerResilienceManager.resetCircuit(provider);
        return providerResilience(id);
    }

    public int enabledProviderCount() {
        return (int) repository.providers().stream().filter(ModelProviderConfig::enabled).count();
    }

    public boolean providerRateLimitEnabled() {
        return providerResilienceManager.rateLimitEnabled();
    }

    public int providerRateLimitMaxRequests() {
        return providerResilienceManager.rateLimitMaxRequests();
    }

    public long providerRateLimitWindowSeconds() {
        return providerResilienceManager.rateLimitWindowSeconds();
    }

    public boolean providerConcurrencyLimitEnabled() {
        return providerResilienceManager.concurrencyLimitEnabled();
    }

    public int providerMaxConcurrentRequests() {
        return providerResilienceManager.maxConcurrentRequests();
    }

    public int openCircuitProviderCount() {
        return providerResilienceManager.openCircuitCount();
    }

    private ModelProviderClient clientFor(ModelProviderConfig provider) {
        return providerClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商适配器未配置"));
    }

    private String providerCheckErrorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return ErrorCode.MODEL_PROVIDER_UNAVAILABLE.name();
    }

    private String sanitizeProviderCheckError(String message) {
        if (!StringUtils.hasText(message)) {
            return "模型供应商检查失败";
        }
        String sanitized = contentGuard.mask(message);
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private String normalizeRoutingGroup(String routingGroup) {
        return normalizeRoutingText(
                StringUtils.hasText(routingGroup) ? routingGroup : DEFAULT_ROUTING_GROUP,
                "routingGroup",
                64
        );
    }

    private String normalizeCapabilities(String capabilities) {
        List<String> tokens = routeTokens(StringUtils.hasText(capabilities) ? capabilities : DEFAULT_PROVIDER_CAPABILITIES);
        if (tokens.isEmpty()) {
            return DEFAULT_PROVIDER_CAPABILITIES;
        }
        return String.join(",", tokens);
    }

    private String normalizeRoutingText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
        if (!normalized.matches("[A-Za-z0-9_.:-]+")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 仅支持字母、数字、点、下划线、冒号和短横线");
        }
        return normalized;
    }

    private List<String> routeTokens(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String item : normalized.split("[,;\\s]+")) {
            String token = normalizeRouteToken(item);
            if (StringUtils.hasText(token) && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String normalizeRouteToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if ("*".equals(value.trim())) {
            return "*";
        }
        return value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private void validateProviderConfig(ModelProviderConfig provider) {
        if (!StringUtils.hasText(provider.name())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型供应商名称不能为空");
        }
        if (provider.inputCostPer1kTokens().signum() < 0 || provider.outputCostPer1kTokens().signum() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型供应商 token 成本不能为负数");
        }
        if (provider.providerType() == ProviderType.OPENAI_COMPATIBLE) {
            if (!StringUtils.hasText(provider.baseUrl())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "OpenAI-compatible 供应商必须配置 baseUrl");
            }
            validateExternalProviderBaseUrl(provider.baseUrl());
            if (!StringUtils.hasText(provider.apiKeyRef()) || !provider.apiKeyRef().startsWith("env:")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "OpenAI-compatible 供应商 apiKeyRef 必须使用 env:VARIABLE_NAME");
            }
        }
    }

    private void validateExternalProviderBaseUrl(String baseUrl) {
        try {
            URI uri = new URI(baseUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "OpenAI-compatible 供应商 baseUrl 仅支持 http/https");
            }
            if (!StringUtils.hasText(uri.getHost()) || StringUtils.hasText(uri.getUserInfo())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "OpenAI-compatible 供应商 baseUrl 格式无效");
            }
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "OpenAI-compatible 供应商 baseUrl 格式无效");
        }
    }

    private void ensureProviderNameAvailable(String providerName, UUID currentProviderId) {
        boolean duplicated = repository.providers()
                .stream()
                .anyMatch(provider -> provider.name().equalsIgnoreCase(providerName)
                        && !provider.id().equals(currentProviderId));
        if (duplicated) {
            throw new BusinessException(ErrorCode.CONFLICT, "模型供应商名称已存在");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
