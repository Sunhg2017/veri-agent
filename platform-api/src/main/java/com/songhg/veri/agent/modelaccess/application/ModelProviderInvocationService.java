package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.port.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.port.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
    private final ModelInvocationBudgetService budgetService;

    public ModelProviderInvocationService(
            ModelAccessRepository repository,
            List<ModelProviderClient> providerClients,
            PlatformContextClient platformContextClient,
            SensitiveContentGuard contentGuard,
            ModelAccessProperties properties,
            ModelAccessMetrics metrics,
            ProviderResilienceManager providerResilienceManager,
            ModelInvocationBudgetService budgetService
    ) {
        this.repository = repository;
        this.providerClients = providerClients;
        this.platformContextClient = platformContextClient;
        this.contentGuard = contentGuard;
        this.properties = properties;
        this.metrics = metrics;
        this.providerResilienceManager = providerResilienceManager;
        this.budgetService = budgetService;
    }

    /**
     * Attempts routed providers in order and persists the final failure record when all candidates fail.
     */
    public ModelInvocationResult invoke(
            ModelInvocationCommand request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan
    ) {
        RuntimeException lastFailure = null;
        boolean fallbackUsed = false;
        boolean fallbackOnBudgetOverrun = properties.fallbackOnBudgetOverrun();
        ModelInvocationBudgetService.BudgetWindow budgetWindow = budgetService.currentWindowIfEnabled();
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
            ModelInvocationCommand request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan,
            ModelProviderConfig provider,
            int index,
            boolean fallbackUsed,
            boolean fallbackOnBudgetOverrun,
            ModelInvocationBudgetService.BudgetWindow budgetWindow
    ) {
        if (providerResilienceManager.isCircuitOpen(provider)) {
            return ProviderAttemptResult.fallback(
                    new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模型供应商熔断中")
            );
        }
        ModelProviderClient client = clientFor(provider);
        ModelInvocationBudgetService.BudgetViolation budgetViolation = budgetService.budgetViolation(
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
            ModelInvocationCommand request,
            ServicePrincipal principal,
            ModelInvocationExecutionPlan plan,
            ModelProviderConfig provider,
            boolean fallbackUsed,
            ModelInvocationBudgetService.BudgetViolation budgetViolation,
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
            ModelInvocationCommand request,
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
        BigDecimal totalCost = budgetService.actualCost(provider, result.inputTokens(), result.outputTokens());
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
        return ProviderAttemptResult.success(new ModelInvocationResult(
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
            ModelInvocationCommand request,
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
            ModelInvocationCommand request,
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

    BigDecimal estimatedCost(ModelProviderConfig provider, String fullPrompt) {
        return budgetService.estimatedCost(provider, fullPrompt);
    }

    private record ProviderAttemptResult(
            ModelInvocationResult response,
            RuntimeException failure,
            boolean fallbackUsed
    ) {

        static ProviderAttemptResult success(ModelInvocationResult response) {
            return new ProviderAttemptResult(response, null, false);
        }

        static ProviderAttemptResult fallback(RuntimeException failure) {
            return new ProviderAttemptResult(null, failure, true);
        }
    }
}
