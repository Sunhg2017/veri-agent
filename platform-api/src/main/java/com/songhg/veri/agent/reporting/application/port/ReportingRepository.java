package com.songhg.veri.agent.reporting.application.port;

import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.domain.ReportDefectDraft;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportExportManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportingRepository {

    boolean insertReportIfAbsent(ReportExecutionReport report);

    void updateReport(ReportExecutionReport report);

    boolean updateReportIfStatus(ReportExecutionReport report, String expectedStatus);

    List<ReportExecutionReport> queuedReports(int limit);

    List<ReportExecutionReport> generatingReportsUpdatedBefore(Instant threshold, int limit);

    void replaceEvidenceManifests(UUID reportId, List<ReportEvidenceManifest> manifests);

    void replaceLatestFailureDiagnosis(UUID reportId, ReportFailureDiagnosis diagnosis);

    void insertExportManifest(ReportExportManifest manifest);

    void insertDefectDraft(ReportDefectDraft draft);

    void updateDefectDraft(ReportDefectDraft draft);

    Optional<ReportExecutionReport> report(UUID id);

    List<ReportEvidenceManifest> evidenceManifests(UUID reportId);

    Optional<ReportFailureDiagnosis> latestFailureDiagnosis(UUID reportId);

    List<ReportDefectDraft> defectDrafts(UUID reportId);

    Optional<ReportDefectDraft> defectDraft(UUID reportId, UUID draftId);

    Optional<ReportExportManifest> latestExportManifest(UUID reportId, String exportType);

    long countDefectDrafts(UUID reportId);

    long countExportManifests(UUID reportId);

    Optional<ReportExecutionReport> reportByProjectRunRequestKey(
            String projectId,
            UUID executionRunId,
            String requestKey
    );

    List<ReportExecutionReport> reports(ReportQuery query);

    long countReports(ReportQuery query);

    Optional<String> reportProjectScopeId(UUID id);
}
