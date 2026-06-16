package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.reporting.application.command.ReviewDefectDraftCommand;
import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.view.ReportDefectDraftResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportDefectDraft;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReportDefectDraftService {

    private static final Pattern UNSAFE_DRAFT_KEY_PATTERN = Pattern.compile(
            "(?i).*(authorization|cookie|password|passwd|secret|token|credential|payload|raw|prompt|response"
                    + "|stdout|stderr).*"
    );

    private final ReportingRepository repository;
    private final ReportingProperties properties;
    private final ReportingActorResolver actorResolver;
    private final ReportingPlatformContextClient contextClient;
    private final ReportingJsonSupport jsonSupport;
    private final ReportResponseMapper responseMapper;

    public ReportDefectDraftService(
            ReportingRepository repository,
            ReportingProperties properties,
            ReportingActorResolver actorResolver,
            ReportingPlatformContextClient contextClient,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.jsonSupport = new ReportingJsonSupport(objectMapper);
        this.responseMapper = new ReportResponseMapper(jsonSupport);
    }

    /**
     * Creates a platform-local defect draft from aggregate report and diagnosis snapshots only.
     *
     * <p>The generated preview is intentionally masked and non-sendable: it helps a reviewer copy safe fields into a
     * future WP11 connector flow while proving that WP10 did not attempt an external defect-system write.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ReportDefectDraftResponse createDraft(UUID reportId) {
        requireDefectDraftEnabled();
        ReportExecutionReport report = requireReadyReport(reportId);
        Optional<ReportFailureDiagnosis> latestDiagnosis = repository.latestFailureDiagnosis(report.id());
        List<ReportEvidenceManifest> evidenceManifests = repository.evidenceManifests(report.id());
        Map<String, Object> summary = jsonSupport.readMap(report.reportSummaryJson());
        Map<String, Object> classification = latestDiagnosis
                .map(diagnosis -> jsonSupport.readMap(diagnosis.classificationJson()))
                .orElseGet(Map::of);
        List<String> evidenceRefs = evidenceRefs(latestDiagnosis, evidenceManifests);
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ReportDefectDraft draft = new ReportDefectDraft(
                UUID.randomUUID(),
                report.id(),
                latestDiagnosis.map(ReportFailureDiagnosis::id).orElse(null),
                "DRAFT",
                title(report, summary, classification),
                reproductionSummary(report, summary, classification),
                impactSummary(summary, latestDiagnosis),
                prioritySuggestion(summary, latestDiagnosis),
                jsonSupport.json(evidenceRefs),
                jsonSupport.json(payloadPreview(report, summary, classification, evidenceRefs, now)),
                actor,
                actor,
                now,
                now
        );
        repository.insertDefectDraft(draft);
        repository.updateReport(withDefectDraftCount(report, repository.countDefectDrafts(report.id())));
        audit(report, draft, "report.defect_draft.created", "SUCCESS", Map.of(
                "draftId", draft.id(),
                "diagnosisId", draft.diagnosisId() == null ? "" : draft.diagnosisId(),
                "evidenceRefCount", evidenceRefs.size(),
                "externalSystemWriteAttempted", false
        ));
        return responseMapper.toDefectDraft(draft);
    }

    /**
     * Applies the reviewer-controlled status transition without mutating the draft content or external systems.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ReportDefectDraftResponse reviewDraft(UUID reportId, UUID draftId, ReviewDefectDraftCommand command) {
        requireDefectDraftEnabled();
        ReportExecutionReport report = requireReport(reportId);
        ReportDefectDraft current = repository.defectDraft(report.id(), draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_DEFECT_DRAFT_NOT_FOUND"));
        String nextStatus = normalizeReviewStatus(command);
        requireAllowedTransition(current.status(), nextStatus);
        Instant now = Instant.now();
        ReportDefectDraft updated = new ReportDefectDraft(
                current.id(),
                current.reportId(),
                current.diagnosisId(),
                nextStatus,
                current.title(),
                current.reproductionSummary(),
                current.impactSummary(),
                current.prioritySuggestion(),
                current.evidenceRefsJson(),
                current.payloadPreviewJson(),
                current.createdBy(),
                actorResolver.currentActor(),
                current.createdAt(),
                now
        );
        repository.updateDefectDraft(updated);
        audit(report, updated, "report.defect_draft.reviewed", "SUCCESS", Map.of(
                "draftId", updated.id(),
                "fromStatus", current.status(),
                "toStatus", nextStatus,
                "externalSystemWriteAttempted", false
        ));
        return responseMapper.toDefectDraft(updated);
    }

    private void requireAllowedTransition(String currentStatus, String nextStatus) {
        boolean allowed = "DRAFT".equals(currentStatus)
                && ("REVIEWED".equals(nextStatus) || "DISMISSED".equals(nextStatus));
        allowed |= "DISMISSED".equals(currentStatus) && "DRAFT".equals(nextStatus);
        if (!allowed) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DEFECT_DRAFT_INVALID_STATE");
        }
    }

    private List<String> evidenceRefs(
            Optional<ReportFailureDiagnosis> latestDiagnosis,
            List<ReportEvidenceManifest> evidenceManifests
    ) {
        List<String> refs = new ArrayList<>();
        latestDiagnosis
                .map(diagnosis -> jsonSupport.readMap(diagnosis.diagnosisSummaryJson()))
                .map(summary -> summary.get("rootCauseCandidates"))
                .ifPresent(candidates -> appendCandidateEvidenceRefs(refs, candidates));
        if (refs.isEmpty()) {
            evidenceManifests.stream()
                    .limit(10)
                    .map(manifest -> safeRef(manifest.sourceWp(), manifest.sourceType(), manifest.sourceRefDigest()))
                    .filter(StringUtils::hasText)
                    .forEach(refs::add);
        }
        return refs.stream().distinct().limit(20).toList();
    }

    private void appendCandidateEvidenceRefs(List<String> refs, Object candidates) {
        if (!(candidates instanceof Iterable<?> items)) {
            return;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> candidate)) {
                continue;
            }
            Object candidateRefs = candidate.get("evidenceRefs");
            if (!(candidateRefs instanceof Iterable<?> values)) {
                continue;
            }
            for (Object value : values) {
                String ref = safeEvidenceText(value, 160);
                if (StringUtils.hasText(ref)) {
                    refs.add(ref);
                }
            }
        }
    }

    private String safeRef(String sourceWp, String sourceType, String digest) {
        if (!StringUtils.hasText(sourceWp) || !StringUtils.hasText(sourceType) || !StringUtils.hasText(digest)) {
            return null;
        }
        return "%s:%s:%s".formatted(
                sourceWp.toLowerCase(Locale.ROOT),
                sourceType.toLowerCase(Locale.ROOT),
                safeEvidenceText(digest, 96)
        );
    }

    private String title(
            ReportExecutionReport report,
            Map<String, Object> summary,
            Map<String, Object> classification
    ) {
        String category = textValue(classification.getOrDefault(
                "primaryCategory",
                summary.getOrDefault("diagnosisPrimaryCategory", "UNKNOWN")
        ));
        String runStatus = textValue(summary.getOrDefault("runStatus", report.status()));
        return safeEvidenceText("[" + category + "] WP10 report " + shortId(report.id()) + " requires review: "
                + runStatus, 200);
    }

    private String reproductionSummary(
            ReportExecutionReport report,
            Map<String, Object> summary,
            Map<String, Object> classification
    ) {
        StringBuilder builder = new StringBuilder();
        appendSentence(builder, "Report " + shortId(report.id())
                + " was generated from execution run " + shortId(report.executionRunId()));
        appendSentence(builder, "runStatus=" + textValue(summary.get("runStatus"))
                + ", triggerType=" + textValue(summary.get("triggerType"))
                + ", nodeCount=" + textValue(summary.get("nodeCount")));
        appendSentence(builder, "primaryCategory=" + textValue(classification.getOrDefault(
                "primaryCategory",
                summary.get("diagnosisPrimaryCategory")
        )));
        appendSentence(builder, "sourceRunDigest=" + textValue(report.sourceRunDigest()));
        return safeEvidenceText(builder.toString(), 2000);
    }

    private String impactSummary(Map<String, Object> summary, Optional<ReportFailureDiagnosis> latestDiagnosis) {
        String impact = "failureBucketCounts=" + safeMapText(summary.get("failureBucketCounts"))
                + "; nodeStatusCounts=" + safeMapText(summary.get("nodeStatusCounts"))
                + "; manualReviewRequired=" + latestDiagnosis
                        .map(ReportFailureDiagnosis::manualReviewRequired)
                        .orElse(Boolean.TRUE);
        return safeEvidenceText(impact, 2000);
    }

    private String prioritySuggestion(Map<String, Object> summary, Optional<ReportFailureDiagnosis> latestDiagnosis) {
        String category = textValue(summary.get("diagnosisPrimaryCategory"));
        BigDecimal confidence = latestDiagnosis.map(ReportFailureDiagnosis::confidence).orElse(BigDecimal.ZERO);
        boolean manualReview = latestDiagnosis.map(ReportFailureDiagnosis::manualReviewRequired).orElse(true);
        if ("NO_FAILURE".equals(category)) {
            return "P4";
        }
        if (manualReview && confidence.compareTo(new BigDecimal("0.7000")) >= 0) {
            return "P1";
        }
        if (confidence.compareTo(new BigDecimal("0.5000")) >= 0) {
            return "P2";
        }
        return "UNKNOWN";
    }

    private Map<String, Object> payloadPreview(
            ReportExecutionReport report,
            Map<String, Object> summary,
            Map<String, Object> classification,
            List<String> evidenceRefs,
            Instant now
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", title(report, summary, classification));
        fields.put("priority", prioritySuggestion(summary, repository.latestFailureDiagnosis(report.id())));
        fields.put("reportId", report.id());
        fields.put("executionRunId", report.executionRunId());
        fields.put("evidenceRefs", evidenceRefs);
        fields.put("sourceRunDigest", report.sourceRunDigest());
        fields.put("runStatus", summary.get("runStatus"));
        fields.put("primaryCategory", classification.getOrDefault(
                "primaryCategory",
                summary.getOrDefault("diagnosisPrimaryCategory", "UNKNOWN")
        ));

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("schemaVersion", "wp10-defect-preview-v1");
        preview.put("externalSystem", "MANUAL_COPY_ONLY");
        preview.put("fieldMappingVersion", "wp10-defect-preview-fields-v1");
        preview.put("masked", true);
        preview.put("aggregateOnly", true);
        preview.put("externalSystemWriteAttempted", false);
        preview.put("generatedAt", now.toString());
        preview.put("fields", sanitizedMap(fields));
        preview.put("redactionPolicy", Map.of(
                "payloadStored", false,
                "payloadPreviewMasked", true,
                "rawEvidenceIncluded", false,
                "rawPromptStored", false,
                "rawResponseStored", false,
                "credentialPlaintextStored", false,
                "externalWebhookUrlStored", false
        ));
        return preview;
    }

    private Map<String, Object> sanitizedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (safeDraftKey(key)) {
                result.put(key, sanitizedValue(value));
            }
        });
        return result;
    }

    private Object sanitizedValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String textKey = String.valueOf(key);
                if (safeDraftKey(textKey)) {
                    nested.put(textKey, sanitizedValue(item));
                }
            });
            return nested;
        }
        if (value instanceof Iterable<?> items) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : items) {
                sanitized.add(sanitizedValue(item));
            }
            return sanitized;
        }
        if (value instanceof String text) {
            return safeEvidenceText(text, 512);
        }
        return value;
    }

    private boolean safeDraftKey(String key) {
        return StringUtils.hasText(key) && !UNSAFE_DRAFT_KEY_PATTERN.matcher(key).matches();
    }

    private ReportExecutionReport withDefectDraftCount(ReportExecutionReport report, long defectDraftCount) {
        Map<String, Object> summary = new LinkedHashMap<>(jsonSupport.readMap(report.reportSummaryJson()));
        summary.put("defectDraftCount", defectDraftCount);
        return new ReportExecutionReport(
                report.id(),
                report.projectId(),
                report.executionRunId(),
                report.requestKey(),
                report.status(),
                report.schemaVersion(),
                report.sourceRunDigest(),
                jsonSupport.json(summary),
                report.redactionPolicyJson(),
                report.generatedBy(),
                report.generatedAt(),
                report.failedCode(),
                report.failureSummary(),
                report.traceId(),
                report.archivedAt(),
                report.createdAt(),
                Instant.now()
        );
    }

    private String normalizeReviewStatus(ReviewDefectDraftCommand command) {
        if (command == null || !StringUtils.hasText(command.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_DEFECT_DRAFT_STATUS_INVALID");
        }
        String status = command.status().trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "REVIEWED", "DISMISSED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_DEFECT_DRAFT_STATUS_INVALID");
        }
        return status;
    }

    private ReportExecutionReport requireReadyReport(UUID id) {
        ReportExecutionReport report = requireReport(id);
        if (!"READY".equals(report.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_INVALID_STATE");
        }
        return report;
    }

    private ReportExecutionReport requireReport(UUID id) {
        return repository.report(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_NOT_FOUND"));
    }

    private void requireDefectDraftEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DISABLED");
        }
        if (!properties.defectDraftEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DEFECT_DRAFT_DISABLED");
        }
    }

    private void appendSentence(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            if (!builder.isEmpty()) {
                builder.append(". ");
            }
            builder.append(value);
        }
    }

    private String safeMapText(Object value) {
        return safeEvidenceText(String.valueOf(sanitizedValue(value)), 512);
    }

    private String safeEvidenceText(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        return SensitiveTextSanitizer.sanitizedEvidenceText(String.valueOf(value), maxLength);
    }

    private String textValue(Object value) {
        String text = safeEvidenceText(value, 160);
        return StringUtils.hasText(text) ? text : "UNKNOWN";
    }

    private String shortId(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.length() <= 12 ? text : text.substring(0, 12);
    }

    private void audit(ReportExecutionReport report, ReportDefectDraft draft, String action, String result,
            Map<String, Object> afterJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.id());
        payload.put("projectId", report.projectId());
        payload.put("executionRunId", report.executionRunId());
        payload.put("draftId", draft.id());
        payload.put("traceId", TraceContext.getOrCreateTraceId());
        payload.putAll(afterJson);
        contextClient.writeAuditEvent(
                action,
                "REPORT_DEFECT_DRAFT",
                draft.id().toString(),
                report.projectId(),
                result,
                payload
        );
    }
}
