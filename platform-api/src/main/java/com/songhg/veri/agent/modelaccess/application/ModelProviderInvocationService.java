package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Owns provider attempts, budget checks, fallback handling and invocation persistence.
 */
@Service
public class ModelProviderInvocationService {

    private final ModelAccessRepository repository;
    private final List<ModelProviderClient> providerClients;
    private final PlatformContextClient platformContextClient;
    private final SensitiveContentGuard contentGuard;
    private final ModelAccessProperties properties;
    private final ModelAccessMetrics metrics;
    private final ProviderResilienceManager providerResilienceManager;

    public ModelProviderInvocationService(
            ModelAccessRepository repository,
            List<ModelProviderClient> providerClients,
            PlatformContextClient platformContextClient,
            SensitiveContentGuard contentGuard,
            ModelAccessProperties properties,
            ModelAccessMetrics metrics,
            ProviderResilienceManager providerResilienceManager
    ) {
        this.repository = repository;
        this.providerClients = providerClients;
        this.platformContextClient = platformContextClient;
        this.contentGuard = contentGuard;
        this.properties = properties;
        this.metrics = metrics;
        this.providerResilienceManager = providerResilienceManager;
    }

    /**
     * Attempts routed providers in order and persists the final failure record when all candidates fail.
     */
    public InvokeModelResponse invoke(
            InvokeModelRequest request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan
    ) {
        RuntimeException lastFailure = null;
        boolean fallbackUsed = false;
        boolean fallbackOnBudgetOverrun = properties.fallbackOnBudgetOverrun();
        BudgetWindow budgetWindow = budgetCheckEnabled() ? currentBudgetWindow() : null;
        for (int index = 0; index < plan.candidates().size(); index++) {
            ModelProviderConfig provider = plan.candidates().get(index);
            ProviderAttemptResult attempt = attemptProvider(
                    request,
                    principal,
                    plan,
                    provider,
                    index,
                    fallbackUsed,
                    fallbackOnBudgetOverrun,
                    budgetWindow
            );
            if (attempt.response() != null) {
                return attempt.response();
            }
            lastFailure = attempt.failure();
            fallbackUsed = attempt.fallbackUsed();
        }

        InvocationRecord failed = saveRecord(
                request,
                principal,
                plan.prompt(),
                null,
                InvocationStatus.FAILED,
                fallbackUsed,
                contentGuard.digest(plan.fullPrompt()),
                contentGuard.mask(plan.fullPrompt()),
                null,
                0,
                0,
                BigDecimal.ZERO,
                ErrorCode.MODEL_PROVIDER_UNAVAILABLE.name(),
                lastFailure == null ? "无可用模型供应商" : lastFailure.getMessage(),
                plan.routingRuleName(),
                plan.modelCapability(),
                plan.effectiveSensitivityLevel(),
                plan.startedAt()
        );
        throw new BusinessException(
                ErrorCode.MODEL_PROVIDER_UNAVAILABLE,
                "模型调用失败，invocationId=" + failed.id()
        );
    }

    private ProviderAttemptResult attemptProvider(
            InvokeModelRequest request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan,
            ModelProviderConfig provider,
            int index,
            boolean fallbackUsed,
            boolean fallbackOnBudgetOverrun,
            BudgetWindow budgetWindow
    ) {
        if (providerResilienceManager.isCircuitOpen(provider)) {
            return ProviderAttemptResult.fallback(
                    new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商熔断中")
            );
        }
        ModelProviderClient client = clientFor(provider);
        BudgetViolation budgetViolation = budgetViolation(
                request,
                principal,
                provider,
                plan.fullPrompt(),
                budgetWindow
        );
        if (budgetViolation != null) {
            return handleBudgetViolation(
                    request,
                    principal,
                    plan,
                    provider,
                    index > 0 || fallbackUsed,
                    budgetViolation,
                    fallbackOnBudgetOverrun && index < plan.candidates().size() - 1
            );
        }
        try {
            return invokeProvider(request, principal, plan, provider, client, index > 0 || fallbackUsed);
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException
                    && businessException.getErrorCode() == ErrorCode.BUDGET_EXCEEDED) {
                recordBlocked(
                        request,
                        principal,
                        plan.prompt(),
                        provider,
                        index > 0 || fallbackUsed,
                        plan.fullPrompt(),
                        ErrorCode.BUDGET_EXCEEDED.name(),
                        businessException.getMessage(),
                        plan.routingRuleName(),
                        plan.modelCapability(),
                        plan.effectiveSensitivityLevel(),
                        plan.startedAt()
                );
                throw businessException;
            }
            providerResilienceManager.recordProviderFailure(provider);
            return ProviderAttemptResult.fallback(exception);
        }
    }

    private ProviderAttemptResult handleBudgetViolation(
            InvokeModelRequest request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan,
            ModelProviderConfig provider,
            boolean fallbackUsed,
            BudgetViolation budgetViolation,
            boolean canFallback
    ) {
        if (canFallback) {
            return ProviderAttemptResult.fallback(
                    new BusinessException(ErrorCode.BUDGET_EXCEEDED, budgetViolation.message())
            );
        }
        recordBlocked(
                request,
                principal,
                plan.prompt(),
                provider,
                fallbackUsed,
                plan.fullPrompt(),
                ErrorCode.BUDGET_EXCEEDED.name(),
                budgetViolation.message(),
                plan.routingRuleName(),
                plan.modelCapability(),
                plan.effectiveSensitivityLevel(),
                plan.startedAt()
        );
        throw new BusinessException(ErrorCode.BUDGET_EXCEEDED, budgetViolation.message());
    }

    /**
     * Performs the provider call and persists the successful invocation record.
     */
    private ProviderAttemptResult invokeProvider(
            InvokeModelRequest request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan,
            ModelProviderConfig provider,
            ModelProviderClient client,
            boolean fallbackUsed
    ) {
        ProviderCallResult result = providerResilienceManager.callWithRetry(client, provider, new ProviderCallRequest(
                StringUtils.hasText(request.modelName()) ? request.modelName().trim() : properties.defaultModel(),
                plan.renderedPrompt(),
                plan.messageText()
        ));
        providerResilienceManager.recordProviderSuccess(provider);
        BigDecimal totalCost = cost(provider, result.inputTokens(), result.outputTokens());
        InvocationRecord record = saveRecord(
                request,
                principal,
                plan.prompt(),
                provider,
                InvocationStatus.SUCCEEDED,
                fallbackUsed,
                contentGuard.digest(plan.fullPrompt()),
                contentGuard.mask(plan.fullPrompt()),
                contentGuard.mask(result.content()),
                result.inputTokens(),
                result.outputTokens(),
                totalCost,
                null,
                null,
                plan.routingRuleName(),
                plan.modelCapability(),
                plan.effectiveSensitivityLevel(),
                plan.startedAt()
        );
        return ProviderAttemptResult.success(new InvokeModelResponse(
                record.id(),
                provider.id(),
                provider.name(),
                record.modelName(),
                record.fallbackUsed(),
                result.content(),
                result.inputTokens(),
                result.outputTokens(),
                totalCost
        ));
    }

    private ModelProviderClient clientFor(ModelProviderConfig provider) {
        return providerClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MODEL_PROVIDER_UNAVAILABLE,
                        "模型供应商适配器未配置"
                ));
    }

    /**
     * Persists a blocked invocation with masked request preview and audit side effect.
     */
    void recordBlocked(
            InvokeModelRequest request,
            ServicePrincipal principal,
            PromptTemplate prompt,
            ModelProviderConfig provider,
            boolean fallbackUsed,
            String fullPrompt,
            String errorCode,
            String errorMessage,
            String routingRuleName,
            String modelCapability,
            String sensitivityLevel,
            Instant startedAt
    ) {
        saveRecord(
                request,
                principal,
                prompt,
                provider,
                InvocationStatus.BLOCKED,
                fallbackUsed,
                contentGuard.digest(fullPrompt),
                contentGuard.mask(fullPrompt),
                null,
                0,
                0,
                BigDecimal.ZERO,
                errorCode,
                errorMessage,
                routingRuleName,
                modelCapability,
                sensitivityLevel,
                startedAt
        );
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

    BigDecimal estimatedCost(ModelProviderConfig provider, String fullPrompt) {
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record BudgetWindow(Instant startTime, Instant endTime) {
    }

    private record ProviderAttemptResult(
            InvokeModelResponse response,
            RuntimeException failure,
            boolean fallbackUsed
    ) {

        static ProviderAttemptResult success(InvokeModelResponse response) {
            return new ProviderAttemptResult(response, null, false);
        }

        static ProviderAttemptResult fallback(RuntimeException failure) {
            return new ProviderAttemptResult(null, failure, true);
        }
    }

    private record BudgetViolation(String message) {
    }
}
