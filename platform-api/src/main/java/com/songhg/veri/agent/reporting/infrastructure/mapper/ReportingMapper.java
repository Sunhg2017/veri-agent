package com.songhg.veri.agent.reporting.infrastructure.mapper;

import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.domain.ReportDefectDraft;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportExportManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportingMapper {

    int insertReportIfAbsent(ReportExecutionReport report);

    void updateReport(ReportExecutionReport report);

    int updateReportIfStatus(
            @Param("report") ReportExecutionReport report,
            @Param("expectedStatus") String expectedStatus
    );

    List<ReportExecutionReport> queuedReports(@Param("limit") int limit);

    List<ReportExecutionReport> generatingReportsUpdatedBefore(
            @Param("threshold") Instant threshold,
            @Param("limit") int limit
    );

    void deleteEvidenceManifests(@Param("reportId") UUID reportId);

    void insertEvidenceManifest(ReportEvidenceManifest manifest);

    void deleteFailureDiagnoses(@Param("reportId") UUID reportId);

    void insertFailureDiagnosis(ReportFailureDiagnosis diagnosis);

    void insertExportManifest(ReportExportManifest manifest);

    void insertDefectDraft(ReportDefectDraft draft);

    void updateDefectDraft(ReportDefectDraft draft);

    ReportExecutionReport report(@Param("id") UUID id);

    List<ReportEvidenceManifest> evidenceManifests(@Param("reportId") UUID reportId);

    ReportFailureDiagnosis latestFailureDiagnosis(@Param("reportId") UUID reportId);

    List<ReportDefectDraft> defectDrafts(@Param("reportId") UUID reportId);

    ReportDefectDraft defectDraft(
            @Param("reportId") UUID reportId,
            @Param("draftId") UUID draftId
    );

    ReportExportManifest latestExportManifest(
            @Param("reportId") UUID reportId,
            @Param("exportType") String exportType
    );

    long countDefectDrafts(@Param("reportId") UUID reportId);

    long countExportManifests(@Param("reportId") UUID reportId);

    ReportExecutionReport reportByProjectRunRequestKey(
            @Param("projectId") String projectId,
            @Param("executionRunId") UUID executionRunId,
            @Param("requestKey") String requestKey
    );

    List<ReportExecutionReport> reports(@Param("query") ReportQuery query);

    long countReports(@Param("query") ReportQuery query);

    String reportProjectScopeId(@Param("id") UUID id);
}
