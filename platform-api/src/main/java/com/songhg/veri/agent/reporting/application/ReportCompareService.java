package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.view.ReportCompareDefectDraftDiffResponse;
import com.songhg.veri.agent.reporting.application.view.ReportCompareEvidenceDiffResponse;
import com.songhg.veri.agent.reporting.application.view.ReportCompareFieldDiffResponse;
import com.songhg.veri.agent.reporting.application.view.ReportCompareResponse;
import com.songhg.veri.agent.reporting.domain.ReportDefectDraft;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReportCompareService {

    private static final List<String> SUMMARY_FIELD_ORDER = List.of(
            "generationStatus",
            "runStatus",
            "triggerType",
            "durationMillis",
            "nodeCount",
            "nodeStatusCounts",
            "failureBucketCounts",
            "evidenceManifestCount",
            "evidenceManifestTruncated",
            "wp8EvidenceReferenceCount",
            "wp8EvidenceManifestCount",
            "wp3EvidenceReferenceCount",
            "wp3EvidenceManifestCount",
            "wp5EvidenceReferenceCount",
            "wp5EvidenceManifestCount",
            "diagnosisStatus",
            "diagnosisPrimaryCategory",
            "diagnosisManualReviewRequired",
            "defectDraftCount",
            "exportManifestCount"
    );

    private final ReportingRepository repository;
    private final ReportingJsonSupport jsonSupport;

    public ReportCompareService(
            ReportingRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.jsonSupport = new ReportingJsonSupport(objectMapper);
    }

    /**
     * Compares two existing WP10 snapshots inside the same project and returns aggregate-only diffs.
     *
     * <p>The compare service deliberately stays on top of already persisted report/evidence/diagnosis/draft snapshots
     * so it never needs to re-read WP9/WP8/WP3/WP5 raw sources. This keeps the feature read-only, replay-safe and
     * consistent with WP10's aggregate-only redaction boundary.</p>
     */
    @Transactional(readOnly = true)
    public ReportCompareResponse compare(UUID reportId, UUID baselineReportId) {
        requireBaseline(reportId, baselineReportId);
        ReportExecutionReport current = requireReport(reportId);
        ReportExecutionReport baseline = requireReport(baselineReportId);
        requireSameProject(current, baseline);

        List<ReportCompareFieldDiffResponse> metadataDiffs = metadataDiffs(baseline, current);
        List<ReportCompareFieldDiffResponse> summaryDiffs = summaryDiffs(
                jsonSupport.readMap(baseline.reportSummaryJson()),
                jsonSupport.readMap(current.reportSummaryJson())
        );
        List<ReportCompareFieldDiffResponse> diagnosisDiffs = diagnosisDiffs(
                repository.latestFailureDiagnosis(baseline.id()).orElse(null),
                repository.latestFailureDiagnosis(current.id()).orElse(null)
        );
        ReportCompareEvidenceDiffResponse evidenceDiff = evidenceDiff(
                repository.evidenceManifests(baseline.id()),
                repository.evidenceManifests(current.id())
        );
        ReportCompareDefectDraftDiffResponse defectDraftDiff = defectDraftDiff(
                repository.defectDrafts(baseline.id()),
                repository.defectDrafts(current.id())
        );

        List<String> changedFields = new ArrayList<>();
        collectChangedFields(changedFields, "metadata", metadataDiffs);
        collectChangedFields(changedFields, "summary", summaryDiffs);
        collectChangedFields(changedFields, "diagnosis", diagnosisDiffs);
        collectChangedFields(changedFields, evidenceDiff);
        collectChangedFields(changedFields, defectDraftDiff);

        return new ReportCompareResponse(
                current.id(),
                baseline.id(),
                current.projectId(),
                changedFields.isEmpty(),
                List.copyOf(changedFields),
                metadataDiffs,
                summaryDiffs,
                diagnosisDiffs,
                evidenceDiff,
                defectDraftDiff
        );
    }

    private void requireBaseline(UUID reportId, UUID baselineReportId) {
        if (baselineReportId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_COMPARE_BASELINE_REQUIRED");
        }
        if (Objects.equals(reportId, baselineReportId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_COMPARE_SAME_REPORT");
        }
    }

    private void requireSameProject(ReportExecutionReport current, ReportExecutionReport baseline) {
        if (!Objects.equals(current.projectId(), baseline.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_COMPARE_PROJECT_MISMATCH");
        }
    }

    private ReportExecutionReport requireReport(UUID id) {
        return repository.report(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_NOT_FOUND"));
    }

    private List<ReportCompareFieldDiffResponse> metadataDiffs(
            ReportExecutionReport baseline,
            ReportExecutionReport current
    ) {
        List<ReportCompareFieldDiffResponse> diffs = new ArrayList<>();
        addDiff(diffs, "executionRunId", baseline.executionRunId(), current.executionRunId());
        addDiff(diffs, "status", baseline.status(), current.status());
        addDiff(diffs, "schemaVersion", baseline.schemaVersion(), current.schemaVersion());
        addDiff(diffs, "sourceRunDigest", baseline.sourceRunDigest(), current.sourceRunDigest());
        addDiff(diffs, "generatedAt", baseline.generatedAt(), current.generatedAt());
        addDiff(diffs, "archivedAt", baseline.archivedAt(), current.archivedAt());
        addDiff(diffs, "failedCode", baseline.failedCode(), current.failedCode());
        return List.copyOf(diffs);
    }

    /**
     * Summary compare is whitelist-first so the UI stays anchored on stable release signals while still surfacing
     * future aggregate fields that WP10 may add later.
     */
    private List<ReportCompareFieldDiffResponse> summaryDiffs(
            Map<String, Object> baselineSummary,
            Map<String, Object> currentSummary
    ) {
        List<ReportCompareFieldDiffResponse> diffs = new ArrayList<>();
        orderedKeys(baselineSummary, currentSummary, SUMMARY_FIELD_ORDER)
                .forEach(field -> addDiff(diffs, field, baselineSummary.get(field), currentSummary.get(field)));
        return List.copyOf(diffs);
    }

    private List<ReportCompareFieldDiffResponse> diagnosisDiffs(
            ReportFailureDiagnosis baselineDiagnosis,
            ReportFailureDiagnosis currentDiagnosis
    ) {
        Map<String, Object> baseline = diagnosisSnapshot(baselineDiagnosis);
        Map<String, Object> current = diagnosisSnapshot(currentDiagnosis);
        List<ReportCompareFieldDiffResponse> diffs = new ArrayList<>();
        orderedKeys(baseline, current, List.of(
                "status",
                "primaryCategory",
                "secondaryCategory",
                "ruleVersion",
                "confidence",
                "manualReviewRequired",
                "candidateCount",
                "aiDiagnosisReady",
                "modelInvoked",
                "classificationOnly",
                "errorCode"
        )).forEach(field -> addDiff(diffs, field, baseline.get(field), current.get(field)));
        return List.copyOf(diffs);
    }

    /**
     * Evidence diff reduces manifests to source digests and aggregate counts so operators can spot additions/removals
     * without exporting raw evidence bodies or cross-WP internal ids.
     */
    private ReportCompareEvidenceDiffResponse evidenceDiff(
            List<ReportEvidenceManifest> baselineEvidence,
            List<ReportEvidenceManifest> currentEvidence
    ) {
        List<String> baselineKeys = baselineEvidence.stream()
                .map(this::manifestKey)
                .sorted()
                .toList();
        List<String> currentKeys = currentEvidence.stream()
                .map(this::manifestKey)
                .sorted()
                .toList();
        Set<String> baselineKeySet = new LinkedHashSet<>(baselineKeys);
        Set<String> currentKeySet = new LinkedHashSet<>(currentKeys);
        List<String> addedManifestKeys = currentKeys.stream()
                .filter(key -> !baselineKeySet.contains(key))
                .toList();
        List<String> removedManifestKeys = baselineKeys.stream()
                .filter(key -> !currentKeySet.contains(key))
                .toList();
        Map<String, Long> baselineWpCounts = counts(baselineEvidence, ReportEvidenceManifest::sourceWp);
        Map<String, Long> currentWpCounts = counts(currentEvidence, ReportEvidenceManifest::sourceWp);
        Map<String, Long> baselineTypeCounts = counts(baselineEvidence, ReportEvidenceManifest::sourceType);
        Map<String, Long> currentTypeCounts = counts(currentEvidence, ReportEvidenceManifest::sourceType);
        boolean changed = baselineEvidence.size() != currentEvidence.size()
                || !addedManifestKeys.isEmpty()
                || !removedManifestKeys.isEmpty()
                || !baselineWpCounts.equals(currentWpCounts)
                || !baselineTypeCounts.equals(currentTypeCounts);
        return new ReportCompareEvidenceDiffResponse(
                changed,
                baselineEvidence.size(),
                currentEvidence.size(),
                addedManifestKeys,
                removedManifestKeys,
                baselineWpCounts,
                currentWpCounts,
                baselineTypeCounts,
                currentTypeCounts
        );
    }

    private ReportCompareDefectDraftDiffResponse defectDraftDiff(
            List<ReportDefectDraft> baselineDrafts,
            List<ReportDefectDraft> currentDrafts
    ) {
        Map<String, Long> baselineStatusCounts = counts(baselineDrafts, ReportDefectDraft::status);
        Map<String, Long> currentStatusCounts = counts(currentDrafts, ReportDefectDraft::status);
        boolean changed = baselineDrafts.size() != currentDrafts.size()
                || !baselineStatusCounts.equals(currentStatusCounts);
        return new ReportCompareDefectDraftDiffResponse(
                changed,
                baselineDrafts.size(),
                currentDrafts.size(),
                baselineStatusCounts,
                currentStatusCounts
        );
    }

    private Map<String, Object> diagnosisSnapshot(ReportFailureDiagnosis diagnosis) {
        if (diagnosis == null) {
            return Map.of(
                    "status", "NOT_REQUESTED",
                    "confidence", BigDecimal.ZERO,
                    "manualReviewRequired", true,
                    "candidateCount", 0,
                    "aiDiagnosisReady", false,
                    "modelInvoked", false,
                    "classificationOnly", true
            );
        }
        Map<String, Object> classification = jsonSupport.readMap(diagnosis.classificationJson());
        Map<String, Object> summary = jsonSupport.readMap(diagnosis.diagnosisSummaryJson());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", diagnosis.status());
        snapshot.put("primaryCategory", classification.getOrDefault("primaryCategory", "UNKNOWN"));
        snapshot.put("secondaryCategory", classification.get("secondaryCategory"));
        snapshot.put("ruleVersion", classification.get("ruleVersion"));
        snapshot.put("confidence", diagnosis.confidence());
        snapshot.put("manualReviewRequired", diagnosis.manualReviewRequired());
        snapshot.put("candidateCount", summary.getOrDefault("candidateCount", 0));
        snapshot.put("aiDiagnosisReady", summary.getOrDefault("aiDiagnosisReady", false));
        snapshot.put("modelInvoked", summary.getOrDefault("modelInvoked", false));
        snapshot.put("classificationOnly", summary.getOrDefault("classificationOnly", true));
        snapshot.put("errorCode", diagnosis.errorCode());
        return snapshot;
    }

    private List<String> orderedKeys(
            Map<String, Object> baseline,
            Map<String, Object> current,
            List<String> preferredOrder
    ) {
        Set<String> keys = new LinkedHashSet<>(preferredOrder);
        keys.addAll(baseline.keySet().stream().sorted().toList());
        keys.addAll(current.keySet().stream().sorted().toList());
        return List.copyOf(keys);
    }

    private String manifestKey(ReportEvidenceManifest manifest) {
        String fingerprint = manifest.sourceRefDigest();
        if (!StringUtils.hasText(fingerprint)) {
            fingerprint = manifest.schemaVersion();
        }
        return manifest.sourceWp() + ":" + manifest.sourceType() + ":" + fingerprint;
    }

    private <T> Map<String, Long> counts(List<T> items, Function<T, String> classifier) {
        return items.stream()
                .map(classifier)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        LinkedHashMap::new,
                        Collectors.counting()
                )).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
    }

    private void collectChangedFields(
            List<String> changedFields,
            String prefix,
            List<ReportCompareFieldDiffResponse> diffs
    ) {
        diffs.forEach(diff -> changedFields.add(prefix + "." + diff.field()));
    }

    private void collectChangedFields(
            List<String> changedFields,
            ReportCompareEvidenceDiffResponse evidenceDiff
    ) {
        if (!evidenceDiff.changed()) {
            return;
        }
        if (evidenceDiff.baselineCount() != evidenceDiff.currentCount()) {
            changedFields.add("evidence.count");
        }
        if (!evidenceDiff.addedManifestKeys().isEmpty() || !evidenceDiff.removedManifestKeys().isEmpty()) {
            changedFields.add("evidence.manifests");
        }
        if (!evidenceDiff.baselineSourceWpCounts().equals(evidenceDiff.currentSourceWpCounts())) {
            changedFields.add("evidence.sourceWpCounts");
        }
        if (!evidenceDiff.baselineSourceTypeCounts().equals(evidenceDiff.currentSourceTypeCounts())) {
            changedFields.add("evidence.sourceTypeCounts");
        }
    }

    private void collectChangedFields(
            List<String> changedFields,
            ReportCompareDefectDraftDiffResponse defectDraftDiff
    ) {
        if (!defectDraftDiff.changed()) {
            return;
        }
        if (defectDraftDiff.baselineCount() != defectDraftDiff.currentCount()) {
            changedFields.add("defectDrafts.count");
        }
        if (!defectDraftDiff.baselineStatusCounts().equals(defectDraftDiff.currentStatusCounts())) {
            changedFields.add("defectDrafts.statusCounts");
        }
    }

    private void addDiff(
            List<ReportCompareFieldDiffResponse> diffs,
            String field,
            Object baselineValue,
            Object currentValue
    ) {
        if (!sameValue(baselineValue, currentValue)) {
            diffs.add(new ReportCompareFieldDiffResponse(field, baselineValue, currentValue));
        }
    }

    private boolean sameValue(Object baselineValue, Object currentValue) {
        if (baselineValue == null || currentValue == null) {
            return baselineValue == currentValue;
        }
        if (baselineValue instanceof BigDecimal baselineDecimal && currentValue instanceof BigDecimal currentDecimal) {
            return baselineDecimal.compareTo(currentDecimal) == 0;
        }
        if (baselineValue instanceof Map<?, ?> || baselineValue instanceof List<?>
                || currentValue instanceof Map<?, ?> || currentValue instanceof List<?>) {
            return jsonSupport.json(baselineValue).equals(jsonSupport.json(currentValue));
        }
        return Objects.equals(baselineValue, currentValue);
    }
}
