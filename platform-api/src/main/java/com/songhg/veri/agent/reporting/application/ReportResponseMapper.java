package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.reporting.application.view.ReportDetailResponse;
import com.songhg.veri.agent.reporting.application.view.ReportEvidenceManifestResponse;
import com.songhg.veri.agent.reporting.application.view.ReportSummaryResponse;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        return toDetail(report, List.of(), Optional.empty(), idempotentReplay);
    }

    ReportDetailResponse toDetail(
            ReportExecutionReport report,
            List<ReportEvidenceManifest> evidenceManifests,
            boolean idempotentReplay
    ) {
        return toDetail(report, evidenceManifests, Optional.empty(), idempotentReplay);
    }

    ReportDetailResponse toDetail(
            ReportExecutionReport report,
            List<ReportEvidenceManifest> evidenceManifests,
            Optional<ReportFailureDiagnosis> latestDiagnosis,
            boolean idempotentReplay
    ) {
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
                evidenceManifests.stream().map(this::toEvidenceManifest).toList(),
                latestDiagnosis.map(this::toLatestDiagnosis).orElseGet(() -> Map.of("status", "NOT_REQUESTED")),
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

    private ReportEvidenceManifestResponse toEvidenceManifest(ReportEvidenceManifest manifest) {
        return new ReportEvidenceManifestResponse(
                manifest.id(),
                manifest.reportId(),
                manifest.sourceWp(),
                manifest.sourceType(),
                manifest.sourceRefDigest(),
                manifest.schemaVersion(),
                jsonSupport.readStringList(manifest.summaryKeysJson()),
                jsonSupport.readMap(manifest.redactionFlagsJson()),
                jsonSupport.readMap(manifest.evidenceSummaryJson()),
                manifest.createdAt()
        );
    }

    private Map<String, Object> toLatestDiagnosis(ReportFailureDiagnosis diagnosis) {
        Map<String, Object> summary = jsonSupport.readMap(diagnosis.diagnosisSummaryJson());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", diagnosis.id());
        response.put("reportId", diagnosis.reportId());
        response.put("status", diagnosis.status());
        response.put("classification", jsonSupport.readMap(diagnosis.classificationJson()));
        response.put("rootCauseCandidates", summary.getOrDefault("rootCauseCandidates", List.of()));
        response.put("confidence", diagnosis.confidence());
        response.put("manualReviewRequired", diagnosis.manualReviewRequired());
        response.put("modelInvocationDigest", diagnosis.modelInvocationDigest());
        response.put("errorCode", diagnosis.errorCode());
        response.put("aiDiagnosisReady", summary.getOrDefault("aiDiagnosisReady", false));
        response.put("modelInvoked", summary.getOrDefault("modelInvoked", false));
        response.put("classificationOnly", summary.getOrDefault("classificationOnly", true));
        response.put("redactionPolicy", summary.getOrDefault("redactionPolicy", Map.of()));
        response.put("createdAt", diagnosis.createdAt());
        response.put("updatedAt", diagnosis.updatedAt());
        return response;
    }
}
