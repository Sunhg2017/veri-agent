package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.request.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelInvocationBudgetServiceTest {

    @Test
    void returnsNoBudgetWindowWhenNoLimitConfigured() {
        ModelInvocationBudgetService service = new ModelInvocationBudgetService(
                new InMemoryModelAccessRepository(),
                properties(null, null, null)
        );

        assertThat(service.currentWindowIfEnabled()).isNull();
    }

    @Test
    void detectsProjectBudgetViolationBeforeProviderCall() {
        InMemoryModelAccessRepository repository = new InMemoryModelAccessRepository();
        ModelInvocationBudgetService service = new ModelInvocationBudgetService(
                repository,
                properties(null, new BigDecimal("0.00002000"), null)
        );
        ModelProviderConfig provider = repository.providers().get(0);

        ModelInvocationBudgetService.BudgetViolation violation = service.budgetViolation(
                request("project-budget"),
                new ServicePrincipal("wp2-budget-test", "user-1"),
                provider,
                "user: 这是一段足够触发预算预估的模型输入内容",
                service.currentWindowIfEnabled()
        );

        assertThat(violation).isNotNull();
        assertThat(violation.message()).contains("PROJECT日预算", "limit=0.00002000");
    }

    @Test
    void calculatesActualProviderCostWithTokenPricing() {
        InMemoryModelAccessRepository repository = new InMemoryModelAccessRepository();
        ModelInvocationBudgetService service = new ModelInvocationBudgetService(
                repository,
                properties(null, null, null)
        );

        BigDecimal cost = service.actualCost(repository.providers().get(0), 8, 4);

        assertThat(cost).isEqualByComparingTo("0.00000160");
    }

    private InvokeModelRequest request(String projectId) {
        return new InvokeModelRequest(
                projectId,
                "app-1",
                "env-1",
                null,
                null,
                List.of(new ChatMessage("user", "预算测试")),
                null,
                null,
                false,
                "INTERNAL",
                null
        );
    }

    private ModelAccessProperties properties(
            BigDecimal platformLimit,
            BigDecimal projectLimit,
            BigDecimal callerLimit
    ) {
        return new ModelAccessProperties(
                "test-model-token",
                "test-local-model",
                4000,
                platformLimit,
                projectLimit,
                100,
                "UTC",
                10000,
                0,
                1,
                1000,
                1000,
                new BigDecimal("0.8"),
                0,
                1,
                0,
                1,
                0,
                callerLimit,
                "BLOCK",
                List.of()
        );
    }
}
