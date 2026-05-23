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
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptApprovalStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelAccessService {

    private static final String DEFAULT_ROUTING_GROUP = "default";
    private static final String DEFAULT_PROVIDER_CAPABILITIES = "CHAT,TEXT,JSON,REQUIREMENT_PARSE";

    private final ModelAccessRepository repository;
    private final List<ModelProviderClient> providerClients;
    private final SensitiveContentGuard contentGuard;
    private final ModelAccessProperties properties;
    private final ModelAccessMetrics metrics;
    private final ProviderResilienceManager providerResilienceManager;
    private final ModelInvocationService invocationService;

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
        this(
                repository,
                providerClients,
                platformContextClient,
                contentGuard,
                promptRenderer,
                properties,
                metrics,
                providerResilienceManager,
                new ModelInvocationService(
                        repository,
                        providerClients,
                        platformContextClient,
                        contentGuard,
                        promptRenderer,
                        properties,
                        metrics,
                        providerResilienceManager
                )
        );
    }

    @Autowired
    public ModelAccessService(
            ModelAccessRepository repository,
            List<ModelProviderClient> providerClients,
            PlatformContextClient platformContextClient,
            SensitiveContentGuard contentGuard,
            PromptRenderer promptRenderer,
            ModelAccessProperties properties,
            ModelAccessMetrics metrics,
            ProviderResilienceManager providerResilienceManager,
            ModelInvocationService invocationService
    ) {
        this.repository = repository;
        this.providerClients = providerClients;
        this.contentGuard = contentGuard;
        this.properties = properties;
        this.metrics = metrics;
        this.providerResilienceManager = providerResilienceManager;
        this.invocationService = invocationService;
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
        boolean highRisk = Boolean.TRUE.equals(request.highRisk());
        PromptStatus status = Boolean.TRUE.equals(request.activate()) && !highRisk
                ? PromptStatus.ACTIVE
                : PromptStatus.DRAFT;
        PromptApprovalStatus approvalStatus = highRisk
                ? PromptApprovalStatus.PENDING
                : PromptApprovalStatus.NOT_REQUIRED;
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
                highRisk,
                approvalStatus,
                null,
                null,
                null,
                now,
                now
        ));
    }

    public PromptTemplate activatePrompt(UUID id) {
        PromptTemplate prompt = repository.prompt(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prompt 版本不存在"));
        if (prompt.highRisk() && prompt.approvalStatus() != PromptApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "高风险 Prompt 需审批通过后才能激活");
        }
        repository.deactivateActivePrompts(prompt.promptKey());
        return repository.savePrompt(new PromptTemplate(
                prompt.id(),
                prompt.promptKey(),
                prompt.name(),
                prompt.version(),
                prompt.content(),
                PromptStatus.ACTIVE,
                prompt.changeNote(),
                prompt.highRisk(),
                prompt.approvalStatus(),
                prompt.approvedBy(),
                prompt.approvedAt(),
                prompt.approvalNote(),
                prompt.createdAt(),
                Instant.now()
        ));
    }

    public PromptTemplate approvePrompt(UUID id, String approvedBy, String reviewNote) {
        PromptTemplate prompt = promptForReview(id);
        return repository.savePrompt(reviewedPrompt(
                prompt,
                PromptApprovalStatus.APPROVED,
                approvedBy,
                reviewNote
        ));
    }

    public PromptTemplate rejectPrompt(UUID id, String approvedBy, String reviewNote) {
        PromptTemplate prompt = promptForReview(id);
        return repository.savePrompt(reviewedPrompt(
                prompt,
                PromptApprovalStatus.REJECTED,
                approvedBy,
                reviewNote
        ));
    }

    private PromptTemplate promptForReview(UUID id) {
        PromptTemplate prompt = repository.prompt(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prompt 版本不存在"));
        if (!prompt.highRisk()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "低风险 Prompt 不需要审批");
        }
        if (prompt.status() == PromptStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已激活 Prompt 不能重新审批");
        }
        return prompt;
    }

    private PromptTemplate reviewedPrompt(
            PromptTemplate prompt,
            PromptApprovalStatus approvalStatus,
            String approvedBy,
            String reviewNote
    ) {
        Instant now = Instant.now();
        return new PromptTemplate(
                prompt.id(),
                prompt.promptKey(),
                prompt.name(),
                prompt.version(),
                prompt.content(),
                prompt.status(),
                prompt.changeNote(),
                prompt.highRisk(),
                approvalStatus,
                trimToNull(approvedBy) == null ? "system" : approvedBy.trim(),
                now,
                trimToNull(reviewNote),
                prompt.createdAt(),
                now
        );
    }

    public InvokeModelResponse invoke(InvokeModelRequest request, ServicePrincipal principal) {
        return invocationService.invoke(request, principal);
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

    public List<CostAlertResponse> costAlerts(String projectId, String actorService) {
        BudgetWindow window = currentBudgetWindow();
        List<CostAlertResponse> alerts = new ArrayList<>();
        String normalizedProjectId = trimToNull(projectId);
        String normalizedActorService = trimToNull(actorService);
        if (properties.hasDailyPlatformCostLimit()) {
            alerts.add(costAlert(
                    "PLATFORM",
                    null,
                    null,
                    properties.dailyPlatformCostLimit(),
                    new InvocationQuery(null, null, null, null, null, null, window.startTime(), window.endTime(), PageQuery.of(0, 1)),
                    window
            ));
        }
        if (properties.hasDailyProjectCostLimit()) {
            if (normalizedProjectId == null) {
                repository.distinctProjectIds(window.startTime(), window.endTime())
                        .forEach(id -> alerts.add(projectCostAlert(id, window)));
            } else {
                alerts.add(projectCostAlert(normalizedProjectId, window));
            }
        }
        if (properties.hasDailyCallerServiceCostLimit()) {
            if (normalizedActorService == null) {
                repository.distinctActorServices(window.startTime(), window.endTime())
                        .forEach(service -> alerts.add(callerServiceCostAlert(service, window)));
            } else {
                alerts.add(callerServiceCostAlert(normalizedActorService, window));
            }
        }
        boolean explicitScope = normalizedProjectId != null || normalizedActorService != null;
        return alerts.stream()
                .filter(alert -> explicitScope || !"OK".equals(alert.level()) || alert.spentCost().signum() > 0)
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

    public void writeInvocationsCsv(InvocationQuery query, OutputStream outputStream) throws IOException {
        InvocationQuery normalized = normalizeQuery(query);
        int exportRows = properties.maxExportRows() <= 0 ? 10000 : Math.min(50000, properties.maxExportRows());
        int chunkSize = 100;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.append("invocationId,createdAt,projectId,applicationId,environmentId,sensitivityLevel,status,")
                .append("providerId,providerName,modelName,routingRuleName,routingGroup,modelCapability,promptKey,promptVersion,fallbackUsed,")
                .append("promptDigest,inputTokens,outputTokens,totalCost,latencyMs,actorService,")
                .append("delegatedUserId,errorCode,errorMessage,requestPreview,responsePreview\n");
        int written = 0;
        int pageIndex = 0;
        while (written < exportRows) {
            InvocationQuery exportQuery = new InvocationQuery(
                    normalized.projectId(),
                    normalized.applicationId(),
                    normalized.sensitivityLevel(),
                    normalized.status(),
                    normalized.providerId(),
                    normalized.actorService(),
                    normalized.startTime(),
                    normalized.endTime(),
                    PageQuery.of(pageIndex, chunkSize)
            );
            List<InvocationRecord> records = repository.invocations(exportQuery);
            if (records.isEmpty()) {
                break;
            }
            for (InvocationRecord record : records) {
                if (written >= exportRows) {
                    break;
                }
                appendCsvRecord(writer, record);
                written++;
            }
            writer.flush();
            if (records.size() < chunkSize) {
                break;
            }
            pageIndex++;
        }
        writer.flush();
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

    private void appendCsvRecord(BufferedWriter writer, InvocationRecord record) throws IOException {
        appendCsvRow(
                writer,
                record.id(),
                record.createdAt(),
                record.projectId(),
                record.applicationId(),
                record.environmentId(),
                record.sensitivityLevel(),
                record.status(),
                record.providerId(),
                record.providerName(),
                record.modelName(),
                record.routingRuleName(),
                record.routingGroup(),
                record.modelCapability(),
                record.promptKey(),
                record.promptVersion(),
                record.fallbackUsed(),
                record.promptDigest(),
                record.inputTokens(),
                record.outputTokens(),
                record.totalCost(),
                record.latencyMs(),
                record.actorService(),
                record.delegatedUserId(),
                record.errorCode(),
                record.errorMessage(),
                record.requestPreview(),
                record.responsePreview()
        );
    }

    private void appendCsvRow(BufferedWriter writer, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.append(',');
            }
            appendCsvValue(writer, values[i]);
        }
        writer.append('\n');
    }

    private void appendCsvValue(BufferedWriter writer, Object value) throws IOException {
        String text = value == null ? "" : String.valueOf(value);
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (quote) {
            writer.append('"').append(text.replace("\"", "\"\"")).append('"');
        } else {
            writer.append(text);
        }
    }

    private CostAlertResponse projectCostAlert(String projectId, BudgetWindow window) {
        return costAlert(
                "PROJECT",
                projectId,
                null,
                properties.dailyProjectCostLimit(),
                new InvocationQuery(projectId, null, null, null, null, null, window.startTime(), window.endTime(), PageQuery.of(0, 1)),
                window
        );
    }

    private CostAlertResponse callerServiceCostAlert(String actorService, BudgetWindow window) {
        return costAlert(
                "CALLER_SERVICE",
                null,
                actorService,
                properties.dailyCallerServiceCostLimit(),
                new InvocationQuery(null, null, null, null, null, actorService, window.startTime(), window.endTime(), PageQuery.of(0, 1)),
                window
        );
    }

    private CostAlertResponse costAlert(
            String scope,
            String projectId,
            String actorService,
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
        String subject = actorService == null
                ? scope.toLowerCase(Locale.ROOT)
                : scope.toLowerCase(Locale.ROOT) + "[" + actorService + "]";
        String message = "%s daily cost %s/%s".formatted(subject, spent, limit);
        return new CostAlertResponse(
                scope,
                projectId,
                actorService,
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record BudgetWindow(Instant startTime, Instant endTime) {
    }

    private record BudgetReportWindow(
            LocalDate startDate,
            LocalDate endDate,
            Instant startInstant,
            Instant endExclusiveInstant
    ) {
    }

    private record CostReportKey(LocalDate date, String projectId, String applicationId) {
    }

}
