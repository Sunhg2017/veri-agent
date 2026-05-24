package com.songhg.veri.agent.modelaccess.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.modelaccess.api.response.CostAlertResponse;
import com.songhg.veri.agent.modelaccess.api.response.CostReportResponse;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelCostAnalysisServiceTest {

    @Test
    void costReportGroupsInvocationCostByDateProjectAndApplication() {
        InMemoryModelAccessRepository repository = new InMemoryModelAccessRepository();
        repository.saveInvocation(invocation(
                "project-a",
                "app-a",
                InvocationStatus.SUCCEEDED,
                10,
                5,
                "0.10000000",
                "2026-05-23T01:00:00Z"
        ));
        repository.saveInvocation(invocation(
                "project-a",
                "app-a",
                InvocationStatus.FAILED,
                7,
                3,
                "0.02000000",
                "2026-05-23T02:00:00Z"
        ));
        repository.saveInvocation(invocation(
                "project-a",
                "app-b",
                InvocationStatus.BLOCKED,
                2,
                0,
                "0.00000000",
                "2026-05-23T03:00:00Z"
        ));
        repository.saveInvocation(invocation(
                "project-b",
                "app-a",
                InvocationStatus.SUCCEEDED,
                9,
                6,
                "0.20000000",
                "2026-05-24T01:00:00Z"
        ));
        repository.saveInvocation(invocation(
                "project-outside",
                "app-z",
                InvocationStatus.SUCCEEDED,
                1,
                1,
                "0.99000000",
                "2026-05-22T23:59:59Z"
        ));
        ModelCostAnalysisService service = new ModelCostAnalysisService(repository, properties());

        CostReportResponse report = service.costReport(
                LocalDate.parse("2026-05-23"),
                LocalDate.parse("2026-05-24"),
                null
        );

        assertThat(report.startDate()).isEqualTo(LocalDate.parse("2026-05-23"));
        assertThat(report.endDate()).isEqualTo(LocalDate.parse("2026-05-24"));
        assertThat(report.rows()).hasSize(3);
        CostReportResponse.CostReportRow first = report.rows().get(0);
        assertThat(first.projectId()).isEqualTo("project-a");
        assertThat(first.applicationId()).isEqualTo("app-a");
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.succeeded()).isEqualTo(1);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.blocked()).isZero();
        assertThat(first.inputTokens()).isEqualTo(17);
        assertThat(first.outputTokens()).isEqualTo(8);
        assertThat(first.totalCost()).isEqualByComparingTo("0.12000000");

        CostReportResponse projectOnly = service.costReport(
                LocalDate.parse("2026-05-23"),
                LocalDate.parse("2026-05-24"),
                " project-a "
        );

        assertThat(projectOnly.rows())
                .extracting(CostReportResponse.CostReportRow::projectId)
                .containsExactly("project-a", "project-a");
    }

    @Test
    void costReportRejectsInvalidDateWindow() {
        ModelCostAnalysisService service = new ModelCostAnalysisService(
                new InMemoryModelAccessRepository(),
                properties()
        );

        assertThatThrownBy(() -> service.costReport(
                LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-23"),
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startDate 不能晚于 endDate");
        assertThatThrownBy(() -> service.costReport(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-10"),
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("成本报表时间范围不能超过 31 天");
    }

    @Test
    void costAlertsEvaluatePlatformProjectAndCallerServiceScopes() {
        InMemoryModelAccessRepository repository = new InMemoryModelAccessRepository();
        repository.saveInvocation(currentInvocation("project-a", "wp4-document-input", "6.00000000"));
        repository.saveInvocation(currentInvocation("project-a", "wp5-test-design", "2.00000000"));
        repository.saveInvocation(currentInvocation("project-b", "wp5-test-design", "1.00000000"));
        ModelCostAnalysisService service = new ModelCostAnalysisService(repository, properties());

        Map<String, CostAlertResponse> alerts = service.costAlerts(" project-a ", " wp4-document-input ")
                .stream()
                .collect(java.util.stream.Collectors.toMap(CostAlertResponse::scope, alert -> alert));

        assertThat(alerts.get("PLATFORM").level()).isEqualTo("EXCEEDED");
        assertThat(alerts.get("PLATFORM").spentCost()).isEqualByComparingTo("9.00000000");
        assertThat(alerts.get("PROJECT").level()).isEqualTo("WARNING");
        assertThat(alerts.get("PROJECT").spentCost()).isEqualByComparingTo("8.00000000");
        assertThat(alerts.get("CALLER_SERVICE").level()).isEqualTo("EXCEEDED");
        assertThat(alerts.get("CALLER_SERVICE").spentCost()).isEqualByComparingTo("6.00000000");
    }

    private InvocationRecord currentInvocation(String projectId, String actorService, String totalCost) {
        return currentInvocation(projectId, actorService, totalCost, Instant.now());
    }

    private InvocationRecord invocation(
            String projectId,
            String applicationId,
            InvocationStatus status,
            int inputTokens,
            int outputTokens,
            String totalCost,
            String createdAt
    ) {
        return invocation(
                projectId,
                applicationId,
                status,
                inputTokens,
                outputTokens,
                totalCost,
                Instant.parse(createdAt)
        );
    }

    private InvocationRecord invocation(
            String projectId,
            String applicationId,
            InvocationStatus status,
            int inputTokens,
            int outputTokens,
            String totalCost,
            Instant createdAt
    ) {
        return new InvocationRecord(
                UUID.randomUUID(),
                projectId,
                applicationId,
                "env-a",
                "INTERNAL",
                "prompt-key",
                1,
                UUID.randomUUID(),
                "local-echo-primary",
                "local-echo",
                "default",
                "default",
                "CHAT",
                status,
                false,
                "digest",
                "request",
                "response",
                inputTokens,
                outputTokens,
                new BigDecimal(totalCost),
                status == InvocationStatus.FAILED ? "PROVIDER_ERROR" : null,
                status == InvocationStatus.FAILED ? "provider failed" : null,
                10,
                "wp4-document-input",
                null,
                createdAt
        );
    }

    private InvocationRecord currentInvocation(
            String projectId,
            String actorService,
            String totalCost,
            Instant createdAt
    ) {
        InvocationRecord seed = invocation(
                projectId,
                "app-current",
                InvocationStatus.SUCCEEDED,
                1,
                1,
                totalCost,
                createdAt
        );
        return new InvocationRecord(
                seed.id(),
                seed.projectId(),
                seed.applicationId(),
                seed.environmentId(),
                seed.sensitivityLevel(),
                seed.promptKey(),
                seed.promptVersion(),
                seed.providerId(),
                seed.providerName(),
                seed.modelName(),
                seed.routingRuleName(),
                seed.routingGroup(),
                seed.modelCapability(),
                seed.status(),
                seed.fallbackUsed(),
                seed.promptDigest(),
                seed.requestPreview(),
                seed.responsePreview(),
                seed.inputTokens(),
                seed.outputTokens(),
                seed.totalCost(),
                seed.errorCode(),
                seed.errorMessage(),
                seed.latencyMs(),
                actorService,
                seed.delegatedUserId(),
                seed.createdAt()
        );
    }

    private ModelAccessProperties properties() {
        return new ModelAccessProperties(
                "test-model-token",
                "local-echo",
                4000,
                new BigDecimal("8.00000000"),
                new BigDecimal("10.00000000"),
                256,
                "UTC",
                10000,
                1,
                1,
                1000,
                1000,
                new BigDecimal("0.75"),
                0,
                1,
                0,
                1,
                0,
                new BigDecimal("5.00000000"),
                "BLOCK",
                List.of()
        );
    }
}
