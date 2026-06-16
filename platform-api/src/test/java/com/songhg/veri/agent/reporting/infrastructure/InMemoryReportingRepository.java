package com.songhg.veri.agent.reporting.infrastructure;

import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.query.ReportQuery;
import com.songhg.veri.agent.reporting.domain.ReportDefectDraft;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportExportManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryReportingRepository implements ReportingRepository {

    private final ConcurrentHashMap<UUID, ReportExecutionReport> reports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<ReportEvidenceManifest>> evidenceManifests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReportFailureDiagnosis> failureDiagnoses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<ReportExportManifest>> exportManifests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<ReportDefectDraft>> defectDrafts = new ConcurrentHashMap<>();

    @Override
    public boolean insertReportIfAbsent(ReportExecutionReport report) {
        if (StringUtils.hasText(report.requestKey())
                && reportByProjectRunRequestKey(
                        report.projectId(),
                        report.executionRunId(),
                        report.requestKey()
                ).isPresent()) {
            return false;
        }
        return reports.putIfAbsent(report.id(), report) == null;
    }

    @Override
    public void updateReport(ReportExecutionReport report) {
        reports.computeIfPresent(report.id(), (ignored, current) -> report);
    }

    @Override
    public void replaceEvidenceManifests(UUID reportId, List<ReportEvidenceManifest> manifests) {
        evidenceManifests.put(reportId, List.copyOf(manifests));
    }

    @Override
    public void replaceLatestFailureDiagnosis(UUID reportId, ReportFailureDiagnosis diagnosis) {
        failureDiagnoses.put(reportId, diagnosis);
    }

    @Override
    public void insertExportManifest(ReportExportManifest manifest) {
        exportManifests.compute(manifest.reportId(), (ignored, current) -> {
            List<ReportExportManifest> next = new ArrayList<>(current == null ? List.of() : current);
            next.add(manifest);
            return List.copyOf(next);
        });
    }

    @Override
    public void insertDefectDraft(ReportDefectDraft draft) {
        defectDrafts.compute(draft.reportId(), (ignored, current) -> {
            List<ReportDefectDraft> next = new ArrayList<>(current == null ? List.of() : current);
            next.add(draft);
            return List.copyOf(next);
        });
    }

    @Override
    public void updateDefectDraft(ReportDefectDraft draft) {
        defectDrafts.computeIfPresent(draft.reportId(), (ignored, current) -> current.stream()
                .map(item -> draft.id().equals(item.id()) ? draft : item)
                .toList());
    }

    @Override
    public Optional<ReportExecutionReport> report(UUID id) {
        return Optional.ofNullable(reports.get(id));
    }

    @Override
    public List<ReportEvidenceManifest> evidenceManifests(UUID reportId) {
        return evidenceManifests.getOrDefault(reportId, List.of());
    }

    @Override
    public Optional<ReportFailureDiagnosis> latestFailureDiagnosis(UUID reportId) {
        return Optional.ofNullable(failureDiagnoses.get(reportId));
    }

    @Override
    public List<ReportDefectDraft> defectDrafts(UUID reportId) {
        return defectDrafts.getOrDefault(reportId, List.of()).stream()
                .sorted(Comparator.comparing(ReportDefectDraft::updatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<ReportDefectDraft> defectDraft(UUID reportId, UUID draftId) {
        return defectDrafts(reportId).stream()
                .filter(draft -> draft.id().equals(draftId))
                .findFirst();
    }

    @Override
    public Optional<ReportExportManifest> latestExportManifest(UUID reportId, String exportType) {
        return exportManifests.getOrDefault(reportId, List.of()).stream()
                .filter(manifest -> exportType == null || exportType.equals(manifest.exportType()))
                .max(Comparator.comparing(ReportExportManifest::createdAt));
    }

    @Override
    public long countExportManifests(UUID reportId) {
        return exportManifests.getOrDefault(reportId, List.of()).size();
    }

    @Override
    public long countDefectDrafts(UUID reportId) {
        return defectDrafts.getOrDefault(reportId, List.of()).size();
    }

    @Override
    public Optional<ReportExecutionReport> reportByProjectRunRequestKey(
            String projectId,
            UUID executionRunId,
            String requestKey
    ) {
        if (!StringUtils.hasText(requestKey)) {
            return Optional.empty();
        }
        return reports.values().stream()
                .filter(report -> projectId.equals(report.projectId()))
                .filter(report -> executionRunId.equals(report.executionRunId()))
                .filter(report -> requestKey.equals(report.requestKey()))
                .findFirst();
    }

    @Override
    public List<ReportExecutionReport> reports(ReportQuery query) {
        return filteredReports(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countReports(ReportQuery query) {
        return filteredReports(query).count();
    }

    @Override
    public Optional<String> reportProjectScopeId(UUID id) {
        return report(id).map(ReportExecutionReport::projectId);
    }

    private Stream<ReportExecutionReport> filteredReports(ReportQuery query) {
        Stream<ReportExecutionReport> stream = reports.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(report -> query.projectId().equals(report.projectId()));
        }
        if (query.executionRunId() != null) {
            stream = stream.filter(report -> query.executionRunId().equals(report.executionRunId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(report -> query.status().equals(report.status()));
        }
        return stream.sorted(Comparator
                .comparing((ReportExecutionReport report) -> report.generatedAt() == null
                        ? report.createdAt()
                        : report.generatedAt())
                .reversed());
    }
}
