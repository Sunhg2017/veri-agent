package com.songhg.veri.agent.modelaccess.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CostReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<CostReportRow> rows
) {

    public record CostReportRow(
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
