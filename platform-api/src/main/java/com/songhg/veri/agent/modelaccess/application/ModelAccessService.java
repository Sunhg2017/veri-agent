package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.request.CreatePromptRequest;
import com.songhg.veri.agent.modelaccess.api.request.CreateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.response.CostAlertResponse;
import com.songhg.veri.agent.modelaccess.api.response.CostReportResponse;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.api.response.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.api.response.ProviderCheckResponse;
import com.songhg.veri.agent.modelaccess.api.request.UpdateProviderRequest;
import com.songhg.veri.agent.modelaccess.api.response.ProviderResilienceResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelAccessService {

    private static final String DEFAULT_ROUTING_GROUP = "default";
    private static final String DEFAULT_MODEL_CAPABILITY = "CHAT";
    private static final String DEFAULT_PROVIDER_CAPABILITIES = "CHAT,TEXT,JSON,REQUIREMENT_PARSE";

    private final ModelAccessRepository repository;
    private final List<ModelProviderClient> providerClients;
    private final PlatformContextClient platformContextClient;
    private final SensitiveContentGuard contentGuard;
    private final PromptRenderer promptRenderer;
    private final ModelAccessProperties properties;
    private final ModelAccessMetrics metrics;
    private final ProviderResilienceManager providerResilienceManager;

    public ModelAccessService(
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

    public List<ModelProviderConfig> providers() {
        return repository.providers();
    }

    public ModelProviderConfig createProvider(CreateProviderRequest request) {
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

    public ModelProviderConfig updateProvider(UUID id, UpdateProviderRequest request) {
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

    public ProviderCheckResponse checkProvider(UUID id) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        java.util.Optional<ProviderCheckResponse> cached = providerResilienceManager.cachedProviderCheck(provider);
        if (cached.isPresent()) {
            return cached.get();
        }
        Instant startedAt = Instant.now();
        String modelName = properties.defaultModel();
        ProviderCheckResponse response;
        try {
            ModelProviderClient client = clientFor(provider);
            client.call(provider, new ProviderCallRequest(
                    modelName,
                    "WP2 provider readiness check. Do not include secrets.",
                    "Return OK."
            ));
            response = new ProviderCheckResponse(
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
            response = new ProviderCheckResponse(
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

    public ProviderResilienceResponse providerResilience(UUID id) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        ProviderResilienceManager.CircuitStateView circuitState = providerResilienceManager.circuitState(provider);
        return new ProviderResilienceResponse(
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

    public ProviderResilienceResponse resetProviderCircuit(UUID id) {
        ModelProviderConfig provider = repository.provider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在"));
        providerResilienceManager.resetCircuit(provider);
        return providerResilience(id);
    }

    public List<PromptTemplate> prompts(String promptKey) {
        return repository.prompts(trimToNull(promptKey));
    }

    public PromptTemplate createPrompt(CreatePromptRequest request) {
        String promptKey = request.promptKey().trim();
        int nextVersion = repository.prompts(promptKey)
                .stream()
                .map(PromptTemplate::version)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        PromptStatus status = Boolean.TRUE.equals(request.activate()) ? PromptStatus.ACTIVE : PromptStatus.DRAFT;
        if (status == PromptStatus.ACTIVE) {
            repository.deactivateActivePrompts(promptKey);
        }
        Instant now = Instant.now();
        return repository.savePrompt(new PromptTemplate(
                UUID.randomUUID(),
                promptKey,
                request.name().trim(),
                nextVersion,
                request.content(),
                status,
                trimToNull(request.changeNote()),
                now,
                now
        ));
    }

    public PromptTemplate activatePrompt(UUID id) {
        PromptTemplate prompt = repository.prompt(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prompt 版本不存在"));
        repository.deactivateActivePrompts(prompt.promptKey());
        return repository.savePrompt(new PromptTemplate(
                prompt.id(),
                prompt.promptKey(),
                prompt.name(),
                prompt.version(),
                prompt.content(),
                PromptStatus.ACTIVE,
                prompt.changeNote(),
                prompt.createdAt(),
                Instant.now()
        ));
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
        for (int index = 0; index < candidates.size(); index++) {
            ModelProviderConfig provider = candidates.get(index);
            if (providerResilienceManager.isCircuitOpen(provider)) {
                lastFailure = new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商熔断中");
                fallbackUsed = true;
                continue;
            }
            ModelProviderClient client = clientFor(provider);
            BudgetViolation budgetViolation = budgetViolation(request, provider, fullPrompt);
            if (budgetViolation != null) {
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

    public List<InvocationRecord> invocations() {
        return repository.invocations(new InvocationQuery(null, null, null, null, null, null, null, null, PageQuery.of(0, 200)));
    }

    public PageResponse<InvocationRecord> invocations(InvocationQuery query) {
        InvocationQuery normalized = normalizeQuery(query);
        List<InvocationRecord> items = repository.invocations(normalized);
        long total = repository.countInvocations(normalized);
        return PageResponse.of(items, normalized.index(), normalized.size(), total);
    }

    public InvocationSummaryResponse invocationSummary(InvocationQuery query) {
        return repository.invocationSummary(normalizeQuery(query));
    }

    public List<CostAlertResponse> costAlerts(String projectId) {
        BudgetWindow window = currentBudgetWindow();
        List<CostAlertResponse> alerts = new ArrayList<>();
        if (properties.hasDailyPlatformCostLimit()) {
            alerts.add(costAlert(
                    "PLATFORM",
                    null,
                    properties.dailyPlatformCostLimit(),
                    new InvocationQuery(null, null, null, null, null, null, window.startTime(), window.endTime(), PageQuery.of(0, 1)),
                    window
            ));
        }
        if (properties.hasDailyProjectCostLimit()) {
            String normalizedProjectId = trimToNull(projectId);
            if (normalizedProjectId == null) {
                repository.invocations(new InvocationQuery(null, null, null, null, null, null, window.startTime(), window.endTime(), PageQuery.of(0, 1000)))
                        .stream()
                        .map(InvocationRecord::projectId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .sorted()
                        .forEach(id -> alerts.add(projectCostAlert(id, window)));
            } else {
                alerts.add(projectCostAlert(normalizedProjectId, window));
            }
        }
        boolean explicitProject = trimToNull(projectId) != null;
        return alerts.stream()
                .filter(alert -> explicitProject || !"OK".equals(alert.level()) || alert.spentCost().signum() > 0)
                .toList();
    }

    public CostReportResponse costReport(LocalDate startDate, LocalDate endDate, String projectId) {
        BudgetReportWindow window = normalizeReportWindow(startDate, endDate);
        InvocationQuery query = new InvocationQuery(
                trimToNull(projectId),
                null,
                null,
                null,
                null,
                null,
                window.startInstant(),
                window.endExclusiveInstant(),
                PageQuery.of(0, Math.max(1, properties.maxExportRows() <= 0 ? 10000 : Math.min(50000, properties.maxExportRows())))
        );
        Map<CostReportKey, List<InvocationRecord>> grouped = new LinkedHashMap<>();
        repository.invocations(query).forEach(record -> grouped
                .computeIfAbsent(new CostReportKey(
                        LocalDate.ofInstant(record.createdAt(), reportZone()),
                        record.projectId(),
                        record.applicationId()
                ), ignored -> new ArrayList<>())
                .add(record));
        List<CostReportResponse.CostReportRow> rows = grouped.entrySet()
                .stream()
                .map(entry -> costReportRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing(CostReportResponse.CostReportRow::date)
                        .thenComparing(row -> row.projectId() == null ? "" : row.projectId())
                        .thenComparing(row -> row.applicationId() == null ? "" : row.applicationId()))
                .toList();
        return new CostReportResponse(window.startDate(), window.endDate(), rows);
    }

    public String exportInvocationsCsv(InvocationQuery query) {
        InvocationQuery normalized = normalizeQuery(query);
        int exportRows = properties.maxExportRows() <= 0 ? 10000 : properties.maxExportRows();
        InvocationQuery exportQuery = new InvocationQuery(
                normalized.projectId(),
                normalized.applicationId(),
                normalized.sensitivityLevel(),
                normalized.status(),
                normalized.providerId(),
                normalized.actorService(),
                normalized.startTime(),
                normalized.endTime(),
                PageQuery.of(0, Math.min(50000, exportRows))
        );
        StringBuilder csv = new StringBuilder();
        csv.append("invocationId,createdAt,projectId,applicationId,environmentId,sensitivityLevel,status,")
                .append("providerId,providerName,modelName,routingRuleName,routingGroup,modelCapability,promptKey,promptVersion,fallbackUsed,")
                .append("promptDigest,inputTokens,outputTokens,totalCost,latencyMs,actorService,")
                .append("delegatedUserId,errorCode,errorMessage,requestPreview,responsePreview\n");
        repository.invocations(exportQuery).forEach(record -> appendCsvRecord(csv, record));
        return csv.toString();
    }

    public int enabledProviderCount() {
        return (int) repository.providers().stream().filter(ModelProviderConfig::enabled).count();
    }

    public int activePromptCount() {
        return (int) repository.prompts(null).stream().filter(prompt -> prompt.status() == PromptStatus.ACTIVE).count();
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

    private InvocationQuery normalizeQuery(InvocationQuery query) {
        PageQuery pageQuery = PageQuery.of(query.index(), query.size());
        return new InvocationQuery(
                trimToNull(query.projectId()),
                trimToNull(query.applicationId()),
                normalizeQuerySensitivityLevel(query.sensitivityLevel()),
                query.status(),
                query.providerId(),
                trimToNull(query.actorService()),
                query.startTime(),
                query.endTime(),
                pageQuery
        );
    }

    private String normalizeQuerySensitivityLevel(String sensitivityLevel) {
        if (!StringUtils.hasText(sensitivityLevel)) {
            return null;
        }
        return sensitivityLevel(sensitivityLevel);
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

    private void appendCsvRecord(StringBuilder csv, InvocationRecord record) {
        appendCsvValue(csv, record.id());
        appendCsvValue(csv, record.createdAt());
        appendCsvValue(csv, record.projectId());
        appendCsvValue(csv, record.applicationId());
        appendCsvValue(csv, record.environmentId());
        appendCsvValue(csv, record.sensitivityLevel());
        appendCsvValue(csv, record.status());
        appendCsvValue(csv, record.providerId());
        appendCsvValue(csv, record.providerName());
        appendCsvValue(csv, record.modelName());
        appendCsvValue(csv, record.routingRuleName());
        appendCsvValue(csv, record.routingGroup());
        appendCsvValue(csv, record.modelCapability());
        appendCsvValue(csv, record.promptKey());
        appendCsvValue(csv, record.promptVersion());
        appendCsvValue(csv, record.fallbackUsed());
        appendCsvValue(csv, record.promptDigest());
        appendCsvValue(csv, record.inputTokens());
        appendCsvValue(csv, record.outputTokens());
        appendCsvValue(csv, record.totalCost());
        appendCsvValue(csv, record.latencyMs());
        appendCsvValue(csv, record.actorService());
        appendCsvValue(csv, record.delegatedUserId());
        appendCsvValue(csv, record.errorCode());
        appendCsvValue(csv, record.errorMessage());
        appendCsvValue(csv, record.requestPreview());
        appendCsvValue(csv, record.responsePreview());
        csv.setCharAt(csv.length() - 1, '\n');
    }

    private void appendCsvValue(StringBuilder csv, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (quote) {
            csv.append('"').append(text.replace("\"", "\"\"")).append('"');
        } else {
            csv.append(text);
        }
        csv.append(',');
    }

    private BudgetViolation budgetViolation(
            InvokeModelRequest request,
            ModelProviderConfig provider,
            String fullPrompt
    ) {
        if (!properties.hasDailyPlatformCostLimit() && !properties.hasDailyProjectCostLimit()) {
            return null;
        }
        BudgetWindow window = currentBudgetWindow();
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

    private CostAlertResponse projectCostAlert(String projectId, BudgetWindow window) {
        return costAlert(
                "PROJECT",
                projectId,
                properties.dailyProjectCostLimit(),
                new InvocationQuery(projectId, null, null, null, null, null, window.startTime(), window.endTime(), PageQuery.of(0, 1)),
                window
        );
    }

    private CostAlertResponse costAlert(
            String scope,
            String projectId,
            BigDecimal limit,
            InvocationQuery query,
            BudgetWindow window
    ) {
        InvocationSummaryResponse summary = repository.invocationSummary(query);
        BigDecimal spent = summary.totalCost() == null ? BigDecimal.ZERO : summary.totalCost();
        BigDecimal ratio = limit.signum() <= 0
                ? BigDecimal.ZERO
                : spent.divide(limit, 4, RoundingMode.HALF_UP);
        String level;
        if (spent.compareTo(limit) >= 0) {
            level = "EXCEEDED";
        } else if (ratio.compareTo(properties.safeCostAlertWarningRatio()) >= 0) {
            level = "WARNING";
        } else {
            level = "OK";
        }
        String message = "%s daily cost %s/%s".formatted(scope.toLowerCase(Locale.ROOT), spent, limit);
        return new CostAlertResponse(
                scope,
                projectId,
                window.startTime().toString(),
                window.endTime().toString(),
                spent.setScale(8, RoundingMode.HALF_UP),
                limit,
                ratio,
                level,
                message
        );
    }

    private BudgetReportWindow normalizeReportWindow(LocalDate startDate, LocalDate endDate) {
        ZoneId zone = reportZone();
        LocalDate safeEnd = endDate == null ? LocalDate.now(zone) : endDate;
        LocalDate safeStart = startDate == null ? safeEnd.minusDays(6) : startDate;
        if (safeStart.isAfter(safeEnd)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate 不能晚于 endDate");
        }
        if (ChronoUnit.DAYS.between(safeStart, safeEnd) > 31) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成本报表时间范围不能超过 31 天");
        }
        return new BudgetReportWindow(
                safeStart,
                safeEnd,
                safeStart.atStartOfDay(zone).toInstant(),
                safeEnd.plusDays(1).atStartOfDay(zone).toInstant()
        );
    }

    private ZoneId reportZone() {
        try {
            return StringUtils.hasText(properties.budgetZoneId())
                    ? ZoneId.of(properties.budgetZoneId())
                    : ZoneId.of("Asia/Shanghai");
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP2 预算时区配置无效");
        }
    }

    private CostReportResponse.CostReportRow costReportRow(CostReportKey key, List<InvocationRecord> records) {
        long succeeded = records.stream().filter(record -> record.status() == InvocationStatus.SUCCEEDED).count();
        long failed = records.stream().filter(record -> record.status() == InvocationStatus.FAILED).count();
        long blocked = records.stream().filter(record -> record.status() == InvocationStatus.BLOCKED).count();
        long inputTokens = records.stream().mapToLong(InvocationRecord::inputTokens).sum();
        long outputTokens = records.stream().mapToLong(InvocationRecord::outputTokens).sum();
        BigDecimal totalCost = records.stream()
                .map(InvocationRecord::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
        return new CostReportResponse.CostReportRow(
                key.date(),
                key.projectId(),
                key.applicationId(),
                records.size(),
                succeeded,
                failed,
                blocked,
                inputTokens,
                outputTokens,
                totalCost
        );
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

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
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

    private record BudgetReportWindow(
            LocalDate startDate,
            LocalDate endDate,
            Instant startInstant,
            Instant endExclusiveInstant
    ) {
    }

    private record BudgetViolation(String message) {
    }

    private record CostReportKey(LocalDate date, String projectId, String applicationId) {
    }

}
