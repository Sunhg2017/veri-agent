package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.CostAlertResult;
import com.songhg.veri.agent.modelaccess.application.view.CostReportResult;
import com.songhg.veri.agent.modelaccess.application.view.InvocationSummaryResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;






@Service
public class ModelCostAnalysisService {

    private final ModelAccessRepository repository;
    private final ModelAccessProperties properties;

    public ModelCostAnalysisService(ModelAccessRepository repository, ModelAccessProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Evaluates configured daily cost guardrails for platform, project and caller-service scopes.
     * Empty scope arguments mean "discover active scopes from today's invocation records".
     */
    public List<CostAlertResult> costAlerts(String projectId, String actorService) {
        BudgetWindow window = currentBudgetWindow();
        List<CostAlertResult> alerts = new ArrayList<>();
        String normalizedProjectId = trimToNull(projectId);
        String normalizedActorService = trimToNull(actorService);
        if (properties.hasDailyPlatformCostLimit()) {
            alerts.add(costAlert(
                    "PLATFORM",
                    null,
                    null,
                    properties.dailyPlatformCostLimit(),
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
                    ),
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

    /**
     * Builds a bounded daily cost report. The 31-day cap protects API latency and export memory use
     * because repository implementations may need to aggregate raw invocation rows.
     */
    public CostReportResult costReport(LocalDate startDate, LocalDate endDate, String projectId) {
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
                PageQuery.of(0, safeReportPageSize())
        );
        Map<CostReportKey, List<InvocationRecord>> grouped = new LinkedHashMap<>();
        repository.invocations(query).forEach(record -> grouped
                .computeIfAbsent(new CostReportKey(
                        LocalDate.ofInstant(record.createdAt(), reportZone()),
                        record.projectId(),
                        record.applicationId()
                ), ignored -> new ArrayList<>())
                .add(record));
        List<CostReportResult.CostReportRowResult> rows = grouped.entrySet()
                .stream()
                .map(entry -> costReportRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing(CostReportResult.CostReportRowResult::date)
                        .thenComparing(row -> row.projectId() == null ? "" : row.projectId())
                        .thenComparing(row -> row.applicationId() == null ? "" : row.applicationId()))
                .toList();
        return new CostReportResult(window.startDate(), window.endDate(), rows);
    }

    private CostAlertResult projectCostAlert(String projectId, BudgetWindow window) {
        return costAlert(
                "PROJECT",
                projectId,
                null,
                properties.dailyProjectCostLimit(),
                new InvocationQuery(
                        projectId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        window.startTime(),
                        window.endTime(),
                        PageQuery.of(0, 1)
                ),
                window
        );
    }

    private CostAlertResult callerServiceCostAlert(String actorService, BudgetWindow window) {
        return costAlert(
                "CALLER_SERVICE",
                null,
                actorService,
                properties.dailyCallerServiceCostLimit(),
                new InvocationQuery(
                        null,
                        null,
                        null,
                        null,
                        null,
                        actorService,
                        window.startTime(),
                        window.endTime(),
                        PageQuery.of(0, 1)
                ),
                window
        );
    }

    private CostAlertResult costAlert(
            String scope,
            String projectId,
            String actorService,
            BigDecimal limit,
            InvocationQuery query,
            BudgetWindow window
    ) {
        InvocationSummaryResult summary = repository.invocationSummary(query);
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
        return new CostAlertResult(
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

    private int safeReportPageSize() {
        int configuredRows = properties.maxExportRows() <= 0 ? 10000 : properties.maxExportRows();
        return Math.max(1, Math.min(50000, configuredRows));
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

    private CostReportResult.CostReportRowResult costReportRow(CostReportKey key, List<InvocationRecord> records) {
        long succeeded = records.stream().filter(record -> record.status() == InvocationStatus.SUCCEEDED).count();
        long failed = records.stream().filter(record -> record.status() == InvocationStatus.FAILED).count();
        long blocked = records.stream().filter(record -> record.status() == InvocationStatus.BLOCKED).count();
        long inputTokens = records.stream().mapToLong(InvocationRecord::inputTokens).sum();
        long outputTokens = records.stream().mapToLong(InvocationRecord::outputTokens).sum();
        BigDecimal totalCost = records.stream()
                .map(InvocationRecord::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
        return new CostReportResult.CostReportRowResult(
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
