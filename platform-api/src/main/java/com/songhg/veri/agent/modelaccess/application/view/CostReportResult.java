package com.songhg.veri.agent.modelaccess.application.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Application result for bounded daily model cost reporting.
 */
public record CostReportResult(
        LocalDate startDate,
        LocalDate endDate,
        List<CostReportRowResult> rows
) {

    public record CostReportRowResult(
            LocalDate date,
            String projectId,
            String applicationId,
            long total,
            long succeeded,
            long failed,
            long blocked,
            long inputTokens,
            long outputTokens,
            BigDecimal totalCost
    ) {
    }
}
