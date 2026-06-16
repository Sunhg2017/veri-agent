package com.songhg.veri.agent.reporting.infrastructure;

import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.infrastructure.mapper.ReportingMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcReportingRepository implements ReportingRepository {

    private final ReportingMapper mapper;

    public JdbcReportingRepository(ReportingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean insertReportIfAbsent(ReportExecutionReport report) {
        return mapper.insertReportIfAbsent(report) == 1;
    }

    @Override
    public void updateReport(ReportExecutionReport report) {
        mapper.updateReport(report);
    }

    @Override
    public void replaceEvidenceManifests(UUID reportId, List<ReportEvidenceManifest> manifests) {
        mapper.deleteEvidenceManifests(reportId);
        for (ReportEvidenceManifest manifest : manifests) {
            mapper.insertEvidenceManifest(manifest);
        }
    }

    @Override
    public Optional<ReportExecutionReport> report(UUID id) {
        return Optional.ofNullable(mapper.report(id));
    }

    @Override
    public List<ReportEvidenceManifest> evidenceManifests(UUID reportId) {
        return mapper.evidenceManifests(reportId);
    }

    @Override
    public Optional<ReportExecutionReport> reportByProjectRunRequestKey(
            String projectId,
            UUID executionRunId,
            String requestKey
    ) {
        return Optional.ofNullable(mapper.reportByProjectRunRequestKey(projectId, executionRunId, requestKey));
    }

    @Override
    public List<ReportExecutionReport> reports(ReportQuery query) {
        return mapper.reports(query);
    }

    @Override
    public long countReports(ReportQuery query) {
        return mapper.countReports(query);
    }

    @Override
    public Optional<String> reportProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.reportProjectScopeId(id));
    }
}
