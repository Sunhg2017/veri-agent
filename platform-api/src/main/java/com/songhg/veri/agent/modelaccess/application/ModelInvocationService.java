package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelInvocationService {

    private static final String DEFAULT_MODEL_CAPABILITY = "CHAT";

    private final ModelAccessRepository repository;
    private final List<ModelProviderClient> providerClients;
    private final PlatformContextClient platformContextClient;
    private final SensitiveContentGuard contentGuard;
    private final PromptRenderer promptRenderer;
    private final ModelAccessProperties properties;
    private final ModelAccessMetrics metrics;
    private final ProviderResilienceManager providerResilienceManager;

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
        this.repository = repository;
        this.providerClients = providerClients;
        this.platformContextClient = platformContextClient;
        this.contentGuard = contentGuard;
        this.promptRenderer = promptRenderer;
        this.properties = properties;
        this.metrics = metrics;
        this.providerResilienceManager = providerResilienceManager;
    }

    public InvokeModelResponse invoke(InvokeModelRequest request, ServicePrincipal principal) {
        Instant startedAt = Instant.now();
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
        try {
            contentGuard.assertSafe(fullPrompt);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.SENSITIVE_CONTENT_BLOCKED) {
                saveRecord(
                        request,
                        principal,
                        prompt,
                        null,
                        InvocationStatus.BLOCKED,
                        false,
                        contentGuard.digest(fullPrompt),
                        contentGuard.mask(fullPrompt),
                        null,
                        0,
                        0,
                        BigDecimal.ZERO,
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
        List<ModelProviderConfig> candidates = routingDecision.providers();
        RuntimeException lastFailure = null;
        boolean fallbackUsed = false;
        boolean fallbackOnBudgetOverrun = properties.fallbackOnBudgetOverrun();
        BudgetWindow budgetWindow = budgetCheckEnabled() ? currentBudgetWindow() : null;
        for (int index = 0; index < candidates.size(); index++) {
            ModelProviderConfig provider = candidates.get(index);
            if (providerResilienceManager.isCircuitOpen(provider)) {
                lastFailure = new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商熔断中");
                fallbackUsed = true;
                continue;
            }
            ModelProviderClient client = clientFor(provider);
            BudgetViolation budgetViolation = budgetViolation(request, principal, provider, fullPrompt, budgetWindow);
            if (budgetViolation != null) {
                if (fallbackOnBudgetOverrun && index < candidates.size() - 1) {
                    lastFailure = new BusinessException(ErrorCode.BUDGET_EXCEEDED, budgetViolation.message());
                    fallbackUsed = true;
                    continue;
                }
                saveRecord(
                        request,
                        principal,
                        prompt,
                        provider,
                        InvocationStatus.BLOCKED,
                        index > 0 || fallbackUsed,
                        contentGuard.digest(fullPrompt),
                        contentGuard.mask(fullPrompt),
                        null,
                        0,
                        0,
                        BigDecimal.ZERO,
                        ErrorCode.BUDGET_EXCEEDED.name(),
                        budgetViolation.message(),
                        routingDecision.ruleName(),
                        modelCapability,
                        effectiveSensitivityLevel,
                        startedAt
                );
                throw new BusinessException(ErrorCode.BUDGET_EXCEEDED, budgetViolation.message());
            }
            try {
                ProviderCallResult result = providerResilienceManager.callWithRetry(client, provider, new ProviderCallRequest(
                        StringUtils.hasText(request.modelName()) ? request.modelName().trim() : properties.defaultModel(),
                        renderedPrompt,
                        messageText
                ));
                providerResilienceManager.recordProviderSuccess(provider);
                BigDecimal totalCost = cost(provider, result.inputTokens(), result.outputTokens());
                InvocationRecord record = saveRecord(
                        request,
                        principal,
                        prompt,
                        provider,
                        InvocationStatus.SUCCEEDED,
                        index > 0 || fallbackUsed,
                        contentGuard.digest(fullPrompt),
                        contentGuard.mask(fullPrompt),
                        contentGuard.mask(result.content()),
                        result.inputTokens(),
                        result.outputTokens(),
                        totalCost,
                        null,
                        null,
                        routingDecision.ruleName(),
                        modelCapability,
                        effectiveSensitivityLevel,
                        startedAt
                );
                return new InvokeModelResponse(
                        record.id(),
                        provider.id(),
                        provider.name(),
                        record.modelName(),
                        record.fallbackUsed(),
                        result.content(),
                        result.inputTokens(),
                        result.outputTokens(),
                        totalCost
                );
            } catch (RuntimeException exception) {
                if (exception instanceof BusinessException businessException
                        && businessException.getErrorCode() == ErrorCode.BUDGET_EXCEEDED) {
                    saveRecord(
                            request,
                            principal,
                            prompt,
                            provider,
                            InvocationStatus.BLOCKED,
                            index > 0 || fallbackUsed,
                            contentGuard.digest(fullPrompt),
                            contentGuard.mask(fullPrompt),
                            null,
                            0,
                            0,
                            BigDecimal.ZERO,
                            ErrorCode.BUDGET_EXCEEDED.name(),
                            businessException.getMessage(),
                            routingDecision.ruleName(),
                            modelCapability,
                            effectiveSensitivityLevel,
                            startedAt
                    );
                    throw businessException;
                }
                lastFailure = exception;
                providerResilienceManager.recordProviderFailure(provider);
                fallbackUsed = true;
            }
        }

        InvocationRecord failed = saveRecord(
                request,
                principal,
                prompt,
                null,
                InvocationStatus.FAILED,
                fallbackUsed,
                contentGuard.digest(fullPrompt),
                contentGuard.mask(fullPrompt),
                null,
                0,
                0,
                BigDecimal.ZERO,
                ErrorCode.MODEL_PROVIDER_UNAVAILABLE.name(),
                lastFailure == null ? "无可用模型供应商" : lastFailure.getMessage(),
                routingDecision.ruleName(),
                modelCapability,
                effectiveSensitivityLevel,
                startedAt
        );
        throw new BusinessException(
                ErrorCode.MODEL_PROVIDER_UNAVAILABLE,
                "模型调用失败，invocationId=" + failed.id()
        );
    }

    private PromptTemplate resolvePrompt(String promptKey) {
        if (!StringUtils.hasText(promptKey)) {
            return null;
        }
        return repository.activePrompt(promptKey.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "未找到可用的 Prompt 活跃版本"));
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
            throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "没有符合策略的可用模型供应商");
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
                    .comparing((ModelProviderConfig provider) -> estimatedCost(provider, fullPrompt))
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

    private ModelProviderClient clientFor(ModelProviderConfig provider) {
        return providerClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商适配器未配置"));
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
        saveRecord(
                request,
                principal,
                prompt,
                provider,
                InvocationStatus.BLOCKED,
                false,
                contentGuard.digest(fullPrompt),
                contentGuard.mask(fullPrompt),
                null,
                0,
                0,
                BigDecimal.ZERO,
                ErrorCode.MODEL_POLICY_VIOLATION.name(),
                message,
                null,
                modelCapability,
                sensitivityLevel,
                startedAt
        );
        throw new BusinessException(ErrorCode.MODEL_POLICY_VIOLATION, message);
    }

    private InvocationRecord saveRecord(
            InvokeModelRequest request,
            ServicePrincipal principal,
            PromptTemplate prompt,
            ModelProviderConfig provider,
            InvocationStatus status,
            boolean fallbackUsed,
            String promptDigest,
            String requestPreview,
            String responsePreview,
            int inputTokens,
            int outputTokens,
            BigDecimal totalCost,
            String errorCode,
            String errorMessage,
            String routingRuleName,
            String modelCapability,
            String sensitivityLevel,
            Instant startedAt
    ) {
        InvocationRecord record = repository.saveInvocation(new InvocationRecord(
                UUID.randomUUID(),
                request.projectId(),
                request.applicationId(),
                request.environmentId(),
                sensitivityLevel,
                prompt == null ? null : prompt.promptKey(),
                prompt == null ? null : prompt.version(),
                provider == null ? null : provider.id(),
                provider == null ? null : provider.name(),
                StringUtils.hasText(request.modelName()) ? request.modelName().trim() : properties.defaultModel(),
                routingRuleName,
                provider == null ? null : provider.routingGroup(),
                modelCapability,
                status,
                fallbackUsed,
                promptDigest,
                requestPreview,
                responsePreview,
                inputTokens,
                outputTokens,
                totalCost,
                errorCode,
                errorMessage,
                Duration.between(startedAt, Instant.now()).toMillis(),
                principal.callerService(),
                principal.delegatedUserId(),
                Instant.now()
        ));
        metrics.recordInvocation(record, provider == null ? null : provider.providerType());
        platformContextClient.writeInvocationAudit(record);
        return record;
    }

    private BudgetViolation budgetViolation(
            InvokeModelRequest request,
            ServicePrincipal principal,
            ModelProviderConfig provider,
            String fullPrompt,
            BudgetWindow window
    ) {
        if (!budgetCheckEnabled()) {
            return null;
        }
        BigDecimal estimatedCost = estimatedCost(provider, fullPrompt);

        if (properties.hasDailyProjectCostLimit()) {
            BudgetViolation violation = budgetViolation(
                    "PROJECT",
                    properties.dailyProjectCostLimit(),
                    estimatedCost,
                    new InvocationQuery(
                            trimToNull(request.projectId()),
                            null,
                            null,
                            null,
                            null,
                            null,
                            window.startTime(),
                            window.endTime(),
                            PageQuery.of(0, 1)
                    )
            );
            if (violation != null) {
                return violation;
            }
        }

        if (properties.hasDailyCallerServiceCostLimit()) {
            String callerService = trimToNull(principal.callerService());
            if (callerService != null) {
                BudgetViolation violation = budgetViolation(
                        "CALLER_SERVICE",
                        properties.dailyCallerServiceCostLimit(),
                        estimatedCost,
                        new InvocationQuery(
                                null,
                                null,
                                null,
                                null,
                                null,
                                callerService,
                                window.startTime(),
                                window.endTime(),
                                PageQuery.of(0, 1)
                        )
                );
                if (violation != null) {
                    return violation;
                }
            }
        }

        if (properties.hasDailyPlatformCostLimit()) {
            return budgetViolation(
                    "PLATFORM",
                    properties.dailyPlatformCostLimit(),
                    estimatedCost,
                    new InvocationQuery(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            window.startTime(),
                            window.endTime(),
                            PageQuery.of(0, 1)
                    )
            );
        }
        return null;
    }

    private BudgetViolation budgetViolation(
            String scope,
            BigDecimal limit,
            BigDecimal estimatedCost,
            InvocationQuery query
    ) {
        BigDecimal spent = repository.invocationSummary(query).totalCost();
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }
        BigDecimal projected = spent.add(estimatedCost).setScale(8, RoundingMode.HALF_UP);
        if (projected.compareTo(limit) <= 0) {
            return null;
        }
        return new BudgetViolation(
                "模型调用超出" + scope + "日预算，limit=" + limit
                        + ", spent=" + spent.setScale(8, RoundingMode.HALF_UP)
                        + ", estimated=" + estimatedCost
        );
    }

    private boolean budgetCheckEnabled() {
        return properties.hasDailyPlatformCostLimit()
                || properties.hasDailyProjectCostLimit()
                || properties.hasDailyCallerServiceCostLimit();
    }

    private BudgetWindow currentBudgetWindow() {
        try {
            ZoneId zone = StringUtils.hasText(properties.budgetZoneId())
                    ? ZoneId.of(properties.budgetZoneId())
                    : ZoneId.of("Asia/Shanghai");
            LocalDate today = LocalDate.now(zone);
            return new BudgetWindow(
                    today.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant()
            );
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP2 预算时区配置无效");
        }
    }

    private BigDecimal estimatedCost(ModelProviderConfig provider, String fullPrompt) {
        return cost(
                provider,
                estimateTokens(fullPrompt),
                Math.max(0, properties.budgetEstimatedOutputTokens())
        );
    }

    private BigDecimal cost(ModelProviderConfig provider, int inputTokens, int outputTokens) {
        BigDecimal input = provider.inputCostPer1kTokens()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        BigDecimal output = provider.outputCostPer1kTokens()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        return input.add(output).setScale(8, RoundingMode.HALF_UP);
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String sensitivityLevel(String sensitivityLevel) {
        if (!StringUtils.hasText(sensitivityLevel)) {
            return "INTERNAL";
        }
        String normalized = sensitivityLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED" -> normalized;
            case "STRICT" -> "RESTRICTED";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "sensitivityLevel 仅支持 PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED");
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

    private record BudgetWindow(Instant startTime, Instant endTime) {
    }

    private record RoutingDecision(List<ModelProviderConfig> providers, String ruleName) {
    }

    private record BudgetViolation(String message) {
    }
}
