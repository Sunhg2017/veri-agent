package com.songhg.veri.agent.reporting.application.port;

import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportingRepository {

    boolean insertReportIfAbsent(ReportExecutionReport report);

    void updateReport(ReportExecutionReport report);

    void replaceEvidenceManifests(UUID reportId, List<ReportEvidenceManifest> manifests);

    Optional<ReportExecutionReport> report(UUID id);

    List<ReportEvidenceManifest> evidenceManifests(UUID reportId);

    Optional<ReportExecutionReport> reportByProjectRunRequestKey(
            String projectId,
            UUID executionRunId,
            String requestKey
    );

    List<ReportExecutionReport> reports(ReportQuery query);

    long countReports(ReportQuery query);

    Optional<String> reportProjectScopeId(UUID id);
}
