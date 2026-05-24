package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelInvocationService {

    private static final String DEFAULT_MODEL_CAPABILITY = "CHAT";

    private final ModelAccessRepository repository;
    private final PlatformContextClient platformContextClient;
    private final SensitiveContentGuard contentGuard;
    private final PromptRenderer promptRenderer;
    private final ModelAccessProperties properties;
    private final ModelProviderInvocationService providerInvocationService;

    public ModelInvocationService(
            ModelAccessRepository repository,
            List<ModelProviderClient> providerClients,
            PlatformContextClient platformContextClient,
            SensitiveContentGuard contentGuard,
            PromptRenderer promptRenderer,
            ModelAccessProperties properties,
            ModelAccessMetrics metrics,
            ProviderResilienceManager providerResilienceManager
    ) {
        this(
                repository,
                platformContextClient,
                contentGuard,
                promptRenderer,
                properties,
                new ModelProviderInvocationService(
                        repository,
                        providerClients,
                        platformContextClient,
                        contentGuard,
                        properties,
                        metrics,
                        providerResilienceManager
                )
        );
    }

    @Autowired
    public ModelInvocationService(
            ModelAccessRepository repository,
            PlatformContextClient platformContextClient,
            SensitiveContentGuard contentGuard,
            PromptRenderer promptRenderer,
            ModelAccessProperties properties,
            ModelProviderInvocationService providerInvocationService
    ) {
        this.repository = repository;
        this.platformContextClient = platformContextClient;
        this.contentGuard = contentGuard;
        this.promptRenderer = promptRenderer;
        this.properties = properties;
        this.providerInvocationService = providerInvocationService;
    }

    /**
     * Executes one model invocation through policy validation, routing, fallback and audit persistence.
     */
    public InvokeModelResponse invoke(InvokeModelRequest request, ServicePrincipal principal) {
        Instant startedAt = Instant.now();
        ModelInvocationExecutionPlan plan = prepareInvocationPlan(request, principal, startedAt);
        return providerInvocationService.invoke(request, principal, plan);
    }

    /**
     * Builds immutable invocation context before provider attempts start.
     */
    private ModelInvocationExecutionPlan prepareInvocationPlan(
            InvokeModelRequest request,
            ServicePrincipal principal,
            Instant startedAt
    ) {
        PromptTemplate prompt = resolvePrompt(request.promptKey());
        String renderedPrompt = prompt == null
                ? ""
                : promptRenderer.render(prompt.content(), request.promptVariables());
        String messageText = joinMessages(request.messages());
        String fullPrompt = StringUtils.hasText(renderedPrompt) ? renderedPrompt + "\n" + messageText : messageText;

        if (fullPrompt.length() > properties.maxPromptChars()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Prompt 超出 WP2 最大长度限制");
        }
        String requestSensitivityLevel = sensitivityLevel(request.sensitivityLevel());
        String modelCapability = modelCapability(request);
        assertPromptSafe(request, principal, prompt, fullPrompt, modelCapability, requestSensitivityLevel, startedAt);
        PlatformInvocationPolicy platformPolicy = platformContextClient.verifyInvocationContext(request, principal);
        String effectiveSensitivityLevel = stricterSensitivityLevel(
                requestSensitivityLevel,
                platformPolicy.sensitivityLevel()
        );
        enforceModelPolicy(
                request,
                principal,
                prompt,
                fullPrompt,
                platformPolicy,
                effectiveSensitivityLevel,
                modelCapability,
                startedAt
        );

        Boolean effectiveAllowPublicModel = Boolean.TRUE.equals(request.allowPublicModel())
                && platformPolicy.allowPublicModel();
        RoutingDecision routingDecision = candidateProviders(
                request,
                principal,
                effectiveAllowPublicModel,
                effectiveSensitivityLevel,
                modelCapability,
                fullPrompt
        );
        return new ModelInvocationExecutionPlan(
                startedAt,
                prompt,
                renderedPrompt,
                messageText,
                fullPrompt,
                effectiveSensitivityLevel,
                modelCapability,
                routingDecision.providers(),
                routingDecision.ruleName()
        );
    }

    private void assertPromptSafe(
            InvokeModelRequest request,
            ServicePrincipal principal,
            PromptTemplate prompt,
            String fullPrompt,
            String modelCapability,
            String requestSensitivityLevel,
            Instant startedAt
    ) {
        try {
            contentGuard.assertSafe(fullPrompt);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.SENSITIVE_CONTENT_BLOCKED) {
                providerInvocationService.recordBlocked(
                        request,
                        principal,
                        prompt,
                        null,
                        false,
                        fullPrompt,
                        exception.getErrorCode().name(),
                        exception.getMessage(),
                        null,
                        modelCapability,
                        requestSensitivityLevel,
                        startedAt
                );
            }
            throw exception;
        }
    }

    private PromptTemplate resolvePrompt(String promptKey) {
        if (!StringUtils.hasText(promptKey)) {
            return null;
        }
        return repository.activePrompt(promptKey.trim())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "未找到可用的 Prompt 活跃版本"
                ));
    }

    private RoutingDecision candidateProviders(
            InvokeModelRequest request,
            ServicePrincipal principal,
            Boolean allowPublicModel,
            String sensitivityLevel,
            String capability,
            String fullPrompt
    ) {
        List<ModelProviderConfig> baseCandidates = repository.providers()
                .stream()
                .filter(ModelProviderConfig::enabled)
                .filter(provider -> request.providerId() == null || provider.id().equals(request.providerId()))
                .filter(provider -> Boolean.TRUE.equals(allowPublicModel) || localProvider(provider))
                .filter(provider -> providerSupportsCapability(provider, capability))
                .toList();

        ModelAccessProperties.RoutingRule rule = request.providerId() == null
                ? matchingRoutingRule(request, principal, sensitivityLevel, capability)
                : null;
        List<ModelProviderConfig> candidates = applyRoutingRule(baseCandidates, rule)
                .stream()
                .sorted(providerComparator(rule, fullPrompt))
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MODEL_PROVIDER_UNAVAILABLE,
                    "没有符合策略的可用模型供应商"
            );
        }
        String ruleName = request.providerId() != null
                ? "explicit-provider"
                : routingRuleName(rule);
        return new RoutingDecision(candidates, ruleName);
    }

    private ModelAccessProperties.RoutingRule matchingRoutingRule(
            InvokeModelRequest request,
            ServicePrincipal principal,
            String sensitivityLevel,
            String capability
    ) {
        return properties.safeRoutingRules()
                .stream()
                .filter(rule -> routeFieldMatches(request.projectId(), rule.projectIds(), false))
                .filter(rule -> routeFieldMatches(sensitivityLevel, rule.sensitivityLevels(), true))
                .filter(rule -> routeFieldMatches(principal.callerService(), rule.callerServices(), false))
                .filter(rule -> routeFieldMatches(capability, rule.capabilities(), true))
                .findFirst()
                .orElse(null);
    }

    private List<ModelProviderConfig> applyRoutingRule(
            List<ModelProviderConfig> candidates,
            ModelAccessProperties.RoutingRule rule
    ) {
        if (rule == null || rule.providerGroups() == null || rule.providerGroups().isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(provider -> routeFieldMatches(provider.routingGroup(), rule.providerGroups(), false))
                .toList();
    }

    private Comparator<ModelProviderConfig> providerComparator(
            ModelAccessProperties.RoutingRule rule,
            String fullPrompt
    ) {
        Comparator<ModelProviderConfig> priorityComparator = Comparator
                .comparingInt(ModelProviderConfig::priority)
                .thenComparing(ModelProviderConfig::createdAt, Comparator.reverseOrder())
                .thenComparing(ModelProviderConfig::name);
        if (rule != null && "LOWEST_COST".equals(normalizeRouteToken(rule.costPreference()))) {
            return Comparator
                    .comparing((ModelProviderConfig provider) -> {
                        return providerInvocationService.estimatedCost(provider, fullPrompt);
                    })
                    .thenComparing(priorityComparator);
        }
        return priorityComparator;
    }

    private String routingRuleName(ModelAccessProperties.RoutingRule rule) {
        if (rule == null || !StringUtils.hasText(rule.name())) {
            return "default-priority";
        }
        return normalizeRoutingText(rule.name(), "routing rule", 128);
    }

    private boolean routeFieldMatches(String value, List<String> allowedValues, boolean normalizeToken) {
        if (allowedValues == null || allowedValues.isEmpty()) {
            return true;
        }
        String normalizedValue = normalizeToken ? normalizeRouteToken(value) : value == null ? "" : value.trim();
        return allowedValues.stream()
                .filter(StringUtils::hasText)
                .map(item -> normalizeToken ? normalizeRouteToken(item) : item.trim())
                .anyMatch(item -> "*".equals(item) || item.equalsIgnoreCase(normalizedValue));
    }

    private boolean providerSupportsCapability(ModelProviderConfig provider, String capability) {
        List<String> capabilities = routeTokens(provider.capabilities());
        return capabilities.contains("*") || capabilities.contains(capability);
    }

    private String modelCapability(InvokeModelRequest request) {
        String capability = normalizeRouteToken(request.capability());
        return StringUtils.hasText(capability) ? capability : DEFAULT_MODEL_CAPABILITY;
    }

    private void enforceModelPolicy(
            InvokeModelRequest request,
            ServicePrincipal principal,
            PromptTemplate prompt,
            String fullPrompt,
            PlatformInvocationPolicy platformPolicy,
            String sensitivityLevel,
            String modelCapability,
            Instant startedAt
    ) {
        if (!platformPolicy.allowPublicModel() && Boolean.TRUE.equals(request.allowPublicModel())) {
            blockBySensitivityPolicy(
                    request,
                    principal,
                    prompt,
                    null,
                    fullPrompt,
                    sensitivityLevel,
                    "WP1 平台策略不允许该资源开启公开模型路由",
                    modelCapability,
                    startedAt
            );
        }
        if (highSensitivity(sensitivityLevel) && Boolean.TRUE.equals(request.allowPublicModel())) {
            blockBySensitivityPolicy(
                    request,
                    principal,
                    prompt,
                    null,
                    fullPrompt,
                    sensitivityLevel,
                    "高敏感级别 " + sensitivityLevel + " 不允许开启公开模型路由",
                    modelCapability,
                    startedAt
            );
        }
        if (request.providerId() != null) {
            repository.provider(request.providerId())
                    .filter(provider -> !localProvider(provider)
                            && (highSensitivity(sensitivityLevel) || !platformPolicy.allowPublicModel()))
                    .ifPresent(provider -> blockBySensitivityPolicy(
                            request,
                            principal,
                            prompt,
                            provider,
                            fullPrompt,
                            sensitivityLevel,
                            externalProviderBlockedMessage(sensitivityLevel, platformPolicy),
                            modelCapability,
                            startedAt
                    ));
        }
    }

    private String externalProviderBlockedMessage(
            String sensitivityLevel,
            PlatformInvocationPolicy platformPolicy
    ) {
        if (!platformPolicy.allowPublicModel()) {
            return "WP1 平台策略不允许该资源指定外部模型供应商";
        }
        return "高敏感级别 " + sensitivityLevel + " 不允许指定外部模型供应商";
    }

    private void blockBySensitivityPolicy(
            InvokeModelRequest request,
            ServicePrincipal principal,
            PromptTemplate prompt,
            ModelProviderConfig provider,
            String fullPrompt,
            String sensitivityLevel,
            String message,
            String modelCapability,
            Instant startedAt
    ) {
        providerInvocationService.recordBlocked(
                request,
                principal,
                prompt,
                provider,
                false,
                fullPrompt,
                ErrorCode.MODEL_POLICY_VIOLATION.name(),
                message,
                null,
                modelCapability,
                sensitivityLevel,
                startedAt
        );
        throw new BusinessException(ErrorCode.MODEL_POLICY_VIOLATION, message);
    }

    private String sensitivityLevel(String sensitivityLevel) {
        if (!StringUtils.hasText(sensitivityLevel)) {
            return "INTERNAL";
        }
        String normalized = sensitivityLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED" -> normalized;
            case "STRICT" -> "RESTRICTED";
            default -> throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "sensitivityLevel 仅支持 PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED"
            );
        };
    }

    private String stricterSensitivityLevel(String requestSensitivityLevel, String platformSensitivityLevel) {
        String platformNormalized = sensitivityLevel(platformSensitivityLevel);
        return sensitivityRank(platformNormalized) > sensitivityRank(requestSensitivityLevel)
                ? platformNormalized
                : requestSensitivityLevel;
    }

    private int sensitivityRank(String sensitivityLevel) {
        return switch (sensitivityLevel) {
            case "PUBLIC" -> 0;
            case "INTERNAL" -> 1;
            case "CONFIDENTIAL" -> 2;
            case "RESTRICTED" -> 3;
            default -> 1;
        };
    }

    private boolean highSensitivity(String sensitivityLevel) {
        return "CONFIDENTIAL".equals(sensitivityLevel) || "RESTRICTED".equals(sensitivityLevel);
    }

    private boolean localProvider(ModelProviderConfig provider) {
        return provider.providerType().name().startsWith("LOCAL")
                || provider.providerType() == ProviderType.MOCK_FAILURE;
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
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    fieldName + " 仅支持字母、数字、点、下划线、冒号和短横线"
            );
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

    private String joinMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> message.role() + ": " + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record RoutingDecision(List<ModelProviderConfig> providers, String ruleName) {
    }
}
