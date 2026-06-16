package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.reporting.application.view.ReportDetailResponse;
import com.songhg.veri.agent.reporting.application.view.ReportSummaryResponse;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import java.util.List;
import java.util.Map;

final class ReportResponseMapper {

    private final ReportingJsonSupport jsonSupport;

    ReportResponseMapper(ReportingJsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    ReportSummaryResponse toSummary(ReportExecutionReport report, boolean idempotentReplay) {
        return new ReportSummaryResponse(
                report.id(),
                report.projectId(),
                report.executionRunId(),
                report.requestKey(),
                report.status(),
                report.schemaVersion(),
                report.sourceRunDigest(),
                jsonSupport.readMap(report.reportSummaryJson()),
                idempotentReplay,
                report.generatedBy(),
                report.generatedAt(),
                report.failedCode(),
                report.failureSummary(),
                report.traceId(),
                report.archivedAt(),
                report.createdAt(),
                report.updatedAt()
        );
    }

    ReportDetailResponse toDetail(ReportExecutionReport report, boolean idempotentReplay) {
        return new ReportDetailResponse(
                report.id(),
                report.projectId(),
                report.executionRunId(),
                report.requestKey(),
                report.status(),
                report.schemaVersion(),
                report.sourceRunDigest(),
                jsonSupport.readMap(report.reportSummaryJson()),
                jsonSupport.readMap(report.redactionPolicyJson()),
                List.of(),
                Map.of("status", "NOT_REQUESTED"),
                List.of(),
                idempotentReplay,
                report.generatedBy(),
                report.generatedAt(),
                report.failedCode(),
                report.failureSummary(),
                report.traceId(),
                report.archivedAt(),
                report.createdAt(),
                report.updatedAt()
        );
    }
}
