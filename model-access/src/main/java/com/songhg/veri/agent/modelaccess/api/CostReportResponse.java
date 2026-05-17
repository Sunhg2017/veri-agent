package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CostReportResponse(
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        List<CostReportRow> rows
) {

    public record CostReportRow(
            LocalDate date,
            @JsonProperty("project_id") String projectId,
            @JsonProperty("application_id") String applicationId,
            long total,
            long succeeded,
            long failed,
            long blocked,
            @JsonProperty("input_tokens") long inputTokens,
            @JsonProperty("output_tokens") long outputTokens,
            @JsonProperty("total_cost") BigDecimal totalCost
    ) {
    }
}
