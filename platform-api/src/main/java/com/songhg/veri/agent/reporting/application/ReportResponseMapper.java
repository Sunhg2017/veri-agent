package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.reporting.application.view.ReportDetailResponse;
import com.songhg.veri.agent.reporting.application.view.ReportDiagnosisResponse;
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

    ReportDiagnosisResponse toDiagnosis(ReportFailureDiagnosis diagnosis) {
        Map<String, Object> summary = jsonSupport.readMap(diagnosis.diagnosisSummaryJson());
        return new ReportDiagnosisResponse(
                diagnosis.id(),
                diagnosis.reportId(),
                diagnosis.status(),
                jsonSupport.readMap(diagnosis.classificationJson()),
                summary.getOrDefault("rootCauseCandidates", List.of()),
                diagnosis.confidence(),
                diagnosis.manualReviewRequired(),
                diagnosis.modelInvocationDigest(),
                diagnosis.errorCode(),
                summary.getOrDefault("aiDiagnosisReady", false),
                summary.getOrDefault("modelInvoked", false),
                summary.getOrDefault("classificationOnly", true),
                summary.getOrDefault("redactionPolicy", Map.of()),
                summary.get("diagnosisContext"),
                diagnosis.createdAt(),
                diagnosis.updatedAt()
        );
    }

    private Map<String, Object> toLatestDiagnosis(ReportFailureDiagnosis diagnosis) {
        ReportDiagnosisResponse diagnosisResponse = toDiagnosis(diagnosis);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", diagnosisResponse.id());
        response.put("reportId", diagnosisResponse.reportId());
        response.put("status", diagnosisResponse.status());
        response.put("classification", diagnosisResponse.classification());
        response.put("rootCauseCandidates", diagnosisResponse.rootCauseCandidates());
        response.put("confidence", diagnosisResponse.confidence());
        response.put("manualReviewRequired", diagnosisResponse.manualReviewRequired());
        response.put("modelInvocationDigest", diagnosisResponse.modelInvocationDigest());
        response.put("errorCode", diagnosisResponse.errorCode());
        response.put("aiDiagnosisReady", diagnosisResponse.aiDiagnosisReady());
        response.put("modelInvoked", diagnosisResponse.modelInvoked());
        response.put("classificationOnly", diagnosisResponse.classificationOnly());
        response.put("redactionPolicy", diagnosisResponse.redactionPolicy());
        if (diagnosisResponse.diagnosisContext() != null) {
            response.put("diagnosisContext", diagnosisResponse.diagnosisContext());
        }
        response.put("createdAt", diagnosisResponse.createdAt());
        response.put("updatedAt", diagnosisResponse.updatedAt());
        return response;
    }
}
