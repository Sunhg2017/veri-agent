package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.reporting.application.port.ReportingRepository;
import com.songhg.veri.agent.reporting.application.view.ReportExportResponse;
import com.songhg.veri.agent.reporting.config.ReportingProperties;
import com.songhg.veri.agent.reporting.domain.ReportEvidenceManifest;
import com.songhg.veri.agent.reporting.domain.ReportExecutionReport;
import com.songhg.veri.agent.reporting.domain.ReportExportManifest;
import com.songhg.veri.agent.reporting.domain.ReportFailureDiagnosis;
import java.io.IOException;
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
public class ReportExportService {

    private static final SetLike EXPORT_TYPES = new SetLike("JSON", "MARKDOWN");
    private static final Pattern UNSAFE_EXPORT_KEY_PATTERN = Pattern.compile(
            "(?i).*(authorization|cookie|password|passwd|secret|token|credential|payload|raw|prompt|response"
                    + "|stdout|stderr).*"
    );
    private static final Pattern FORBIDDEN_EXPORT_TEXT_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+[a-z0-9._\\-]{8,}|secret://|authorization\\s*[:=]|cookie\\s*[:=]"
                    + "|password\\s*[:=]|token\\s*[:=]|lease\\s+token|raw\\s+prompt|raw\\s+response"
                    + "|stdout|stderr)"
    );

    private final ReportingRepository repository;
    private final ReportingProperties properties;
    private final ReportingActorResolver actorResolver;
    private final ReportingPlatformContextClient contextClient;
    private final ReportingJsonSupport jsonSupport;
    private final ReportExportFileStorage fileStorage;

    public ReportExportService(
            ReportingRepository repository,
            ReportingProperties properties,
            ReportingActorResolver actorResolver,
            ReportingPlatformContextClient contextClient,
            ObjectMapper objectMapper,
            ReportExportFileStorage fileStorage
    ) {
        this.repository = repository;
        this.properties = properties;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.jsonSupport = new ReportingJsonSupport(objectMapper);
        this.fileStorage = fileStorage;
    }

    /**
     * Creates a one-time sanitized export body and persists only its digest manifest.
     *
     * <p>WP10 exports are meant for release notes and defect handoff, not raw artifact archival. The returned content is
     * rebuilt from stored aggregate snapshots, while the database keeps only manifest metadata, digest and policy flags
     * so a later audit can prove what was generated without retaining export body text.</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ReportExportResponse exportReport(UUID reportId, String exportType) {
        requireExportEnabled();
        ReportExecutionReport report = requireReport(reportId);
        String normalizedType = normalizeExportType(exportType);
        if (!"READY".equals(report.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_INVALID_STATE");
        }
        List<ReportEvidenceManifest> evidenceManifests = repository.evidenceManifests(report.id());
        Optional<ReportFailureDiagnosis> latestDiagnosis = repository.latestFailureDiagnosis(report.id());
        Map<String, Object> exportContent = exportContent(report, evidenceManifests, latestDiagnosis, normalizedType);
        Object renderedContent = "MARKDOWN".equals(normalizedType)
                ? markdownContent(exportContent)
                : exportContent;
        String serializedContent = "MARKDOWN".equals(normalizedType)
                ? String.valueOf(renderedContent)
                : jsonSupport.json(renderedContent);
        Map<String, Object> redactionPolicy = exportRedactionPolicy(normalizedType, renderedContent);
        Instant now = Instant.now();
        if (FORBIDDEN_EXPORT_TEXT_PATTERN.matcher(serializedContent).find()) {
            return blockedExport(report, normalizedType, redactionPolicy, now);
        }
        String contentDigest = SensitiveTextSanitizer.sha256Hex(serializedContent);
        ReportExportManifest manifest = manifest(
                report,
                normalizedType,
                "CREATED",
                redactionPolicy,
                contentDigest,
                null,
                now
        );
        ReportExportFileStorage.StoredExport storedExport = storeExportFile(manifest, serializedContent);
        repository.insertExportManifest(manifest);
        repository.updateReport(withExportManifestCount(report, repository.countExportManifests(report.id())));
        audit(report, "report.exported", "SUCCESS", Map.of(
                "exportType", normalizedType,
                "contentDigest", contentDigest,
                "fieldSetVersion", manifest.fieldSetVersion(),
                "aggregateOnly", true,
                "downloadReady", true
        ));
        return response(
                manifest,
                redactionPolicy,
                exportResponseManifest(manifest, redactionPolicy),
                storedExport,
                renderedContent
        );
    }

    private ReportExportResponse blockedExport(
            ReportExecutionReport report,
            String exportType,
            Map<String, Object> redactionPolicy,
            Instant now
    ) {
        ReportExportManifest manifest = manifest(
                report,
                exportType,
                "BLOCKED",
                redactionPolicy,
                null,
                "REPORT_EXPORT_REDACTION_BLOCKED",
                now
        );
        repository.insertExportManifest(manifest);
        repository.updateReport(withExportManifestCount(report, repository.countExportManifests(report.id())));
        audit(report, "report.export.blocked", "BLOCKED", Map.of(
                "exportType", exportType,
                "blockedReason", "REPORT_EXPORT_REDACTION_BLOCKED",
                "matchedPolicyCode", "WP10_EXPORT_FORBIDDEN_TEXT"
        ));
        return response(manifest, redactionPolicy, exportResponseManifest(manifest, redactionPolicy), null, null);
    }

    /**
     * Downloads one sanitized export file through the same manifest boundary used for audit and redaction decisions.
     *
     * <p>Only exports that were successfully materialized into controlled storage can be downloaded. Blocked exports
     * and missing files are folded into the same not-ready error so callers never learn storage topology details.</p>
     */
    @Transactional(readOnly = true)
    public DownloadableExport downloadExport(UUID reportId, UUID exportId) {
        requireExportEnabled();
        ReportExecutionReport report = requireReport(reportId);
        ReportExportManifest manifest = repository.exportManifest(reportId, exportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_EXPORT_NOT_FOUND"));
        if (!"CREATED".equals(manifest.status())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "REPORT_EXPORT_DOWNLOAD_NOT_READY");
        }
        try {
            ReportExportFileStorage.DownloadableExport content = fileStorage.read(manifest);
            audit(report, "report.export.downloaded", "SUCCESS", Map.of(
                    "exportId", exportId,
                    "exportType", manifest.exportType(),
                    "contentDigest", manifest.contentDigest()
            ));
            return new DownloadableExport(
                    manifest.id(),
                    manifest.reportId(),
                    manifest.exportType(),
                    content.fileName(),
                    content.contentType(),
                    content.content()
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "REPORT_EXPORT_DOWNLOAD_NOT_READY");
        }
    }

    private Map<String, Object> exportContent(
            ReportExecutionReport report,
            List<ReportEvidenceManifest> evidenceManifests,
            Optional<ReportFailureDiagnosis> latestDiagnosis,
            String exportType
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("schemaVersion", report.schemaVersion());
        content.put("fieldSetVersion", properties.effectiveFieldSetVersion());
        content.put("exportedAt", Instant.now().toString());
        content.put("report", reportSnapshot(report));
        content.put("summary", sanitizedMap(jsonSupport.readMap(report.reportSummaryJson())));
        content.put("evidenceManifests", evidenceManifests.stream().map(this::evidenceSnapshot).toList());
        content.put("latestDiagnosis", latestDiagnosis.map(this::diagnosisSnapshot)
                .orElseGet(() -> Map.of("status", "NOT_REQUESTED")));
        content.put("redactionPolicy", exportRedactionPolicy(exportType, null));
        return content;
    }

    private Map<String, Object> reportSnapshot(ReportExecutionReport report) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", report.id());
        snapshot.put("projectId", report.projectId());
        snapshot.put("executionRunId", report.executionRunId());
        snapshot.put("status", report.status());
        snapshot.put("schemaVersion", report.schemaVersion());
        snapshot.put("sourceRunDigest", report.sourceRunDigest());
        snapshot.put("generatedBy", safeText(report.generatedBy(), 128));
        snapshot.put("generatedAt", stringInstant(report.generatedAt()));
        snapshot.put("traceId", safeText(report.traceId(), 96));
        return snapshot;
    }

    private Map<String, Object> evidenceSnapshot(ReportEvidenceManifest manifest) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", manifest.id());
        snapshot.put("sourceWp", manifest.sourceWp());
        snapshot.put("sourceType", manifest.sourceType());
        snapshot.put("sourceRefDigest", manifest.sourceRefDigest());
        snapshot.put("schemaVersion", manifest.schemaVersion());
        snapshot.put("summaryKeys", safeStringList(jsonSupport.readStringList(manifest.summaryKeysJson())));
        snapshot.put("summaryKeyCount", jsonSupport.readStringList(manifest.summaryKeysJson()).size());
        snapshot.put("evidenceSummary", sanitizedMap(jsonSupport.readMap(manifest.evidenceSummaryJson())));
        snapshot.put("createdAt", stringInstant(manifest.createdAt()));
        return snapshot;
    }

    private Map<String, Object> diagnosisSnapshot(ReportFailureDiagnosis diagnosis) {
        Map<String, Object> summary = jsonSupport.readMap(diagnosis.diagnosisSummaryJson());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", diagnosis.id());
        snapshot.put("status", diagnosis.status());
        snapshot.put("classification", sanitizedMap(jsonSupport.readMap(diagnosis.classificationJson())));
        snapshot.put("rootCauseCandidates", sanitizedValue(summary.getOrDefault("rootCauseCandidates", List.of())));
        snapshot.put("confidence", diagnosis.confidence());
        snapshot.put("manualReviewRequired", diagnosis.manualReviewRequired());
        snapshot.put("modelInvocationDigest", diagnosis.modelInvocationDigest());
        snapshot.put("errorCode", safeText(diagnosis.errorCode(), 64));
        snapshot.put("createdAt", stringInstant(diagnosis.createdAt()));
        snapshot.put("updatedAt", stringInstant(diagnosis.updatedAt()));
        return snapshot;
    }

    private Map<String, Object> exportRedactionPolicy(String exportType, Object renderedContent) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("aggregateOnly", true);
        policy.put("exportType", exportType);
        policy.put("contentStored", false);
        policy.put("sourceValuesFiltered", true);
        policy.put("unsafeKeysFiltered", true);
        policy.put("runnerArtifactsIncluded", false);
        policy.put("requestBodiesIncluded", false);
        policy.put("modelBodiesIncluded", false);
        policy.put("credentialValuesIncluded", false);
        policy.put("externalDefectWriteAttempted", false);
        if (renderedContent instanceof String text) {
            policy.put("markdownTruncated", text.length() >= properties.effectiveMaxExportMarkdownChars());
            policy.put("maxMarkdownChars", properties.effectiveMaxExportMarkdownChars());
        }
        return policy;
    }

    private String markdownContent(Map<String, Object> content) {
        Map<String, Object> report = mapValue(content.get("report"));
        Map<String, Object> summary = mapValue(content.get("summary"));
        Map<String, Object> diagnosis = mapValue(content.get("latestDiagnosis"));
        StringBuilder builder = new StringBuilder();
        builder.append("# WP10 Report Export\n\n");
        appendLine(builder, "Report ID", report.get("id"));
        appendLine(builder, "Project", report.get("projectId"));
        appendLine(builder, "Execution Run", report.get("executionRunId"));
        appendLine(builder, "Status", report.get("status"));
        appendLine(builder, "Run Status", summary.get("runStatus"));
        appendLine(builder, "Source Digest", report.get("sourceRunDigest"));
        appendLine(builder, "Evidence Count", summary.get("evidenceManifestCount"));
        appendLine(builder, "Diagnosis Status", diagnosis.get("status"));
        appendLine(builder, "Manual Review Required", diagnosis.get("manualReviewRequired"));
        builder.append("\n## Evidence\n");
        Object evidence = content.get("evidenceManifests");
        if (evidence instanceof List<?> items) {
            for (Object item : items) {
                Map<String, Object> manifest = mapValue(item);
                builder.append("- ")
                        .append(valueText(manifest.get("sourceWp")))
                        .append(" / ")
                        .append(valueText(manifest.get("sourceType")))
                        .append(" / ")
                        .append(valueText(manifest.get("sourceRefDigest")))
                        .append("\n");
            }
        }
        builder.append("\n## Diagnosis Candidates\n");
        Object candidates = diagnosis.get("rootCauseCandidates");
        if (candidates instanceof List<?> items && !items.isEmpty()) {
            for (Object item : items) {
                Map<String, Object> candidate = mapValue(item);
                builder.append("- ")
                        .append(valueText(candidate.get("category")))
                        .append(": ")
                        .append(valueText(candidate.get("summary")))
                        .append("\n");
            }
        } else {
            builder.append("- None\n");
        }
        return SensitiveTextSanitizer.boundedWithEllipsis(builder.toString(), properties.effectiveMaxExportMarkdownChars());
    }

    private void appendLine(StringBuilder builder, String label, Object value) {
        builder.append("- ").append(label).append(": ").append(valueText(value)).append("\n");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private Map<String, Object> sanitizedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (safeExportKey(key)) {
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
                if (safeExportKey(textKey)) {
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
            return safeText(text, 512);
        }
        return value;
    }

    private List<String> safeStringList(List<String> values) {
        return values.stream()
                .filter(this::safeExportKey)
                .map(value -> safeText(value, 128))
                .toList();
    }

    private boolean safeExportKey(String key) {
        return StringUtils.hasText(key) && !UNSAFE_EXPORT_KEY_PATTERN.matcher(key).matches();
    }

    private String safeText(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        return SensitiveTextSanitizer.sanitizedEvidenceText(String.valueOf(value), maxLength);
    }

    private String valueText(Object value) {
        String text = safeText(value, 256);
        return StringUtils.hasText(text) ? text : "-";
    }

    private ReportExportManifest manifest(
            ReportExecutionReport report,
            String exportType,
            String status,
            Map<String, Object> redactionPolicy,
            String contentDigest,
            String blockReason,
            Instant now
    ) {
        return new ReportExportManifest(
                UUID.randomUUID(),
                report.id(),
                exportType,
                status,
                report.schemaVersion(),
                properties.effectiveFieldSetVersion(),
                jsonSupport.json(redactionPolicy),
                contentDigest,
                true,
                actorResolver.currentActor(),
                now,
                blockReason,
                now
        );
    }

    private Map<String, Object> exportResponseManifest(
            ReportExportManifest manifest,
            Map<String, Object> redactionPolicy
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", manifest.id());
        response.put("reportId", manifest.reportId());
        response.put("exportType", manifest.exportType());
        response.put("status", manifest.status());
        response.put("schemaVersion", manifest.schemaVersion());
        response.put("fieldSetVersion", manifest.fieldSetVersion());
        response.put("contentDigest", manifest.contentDigest());
        response.put("aggregateOnly", manifest.aggregateOnly());
        response.put("redactionPolicy", redactionPolicy);
        response.put("exportedBy", manifest.exportedBy());
        response.put("exportedAt", stringInstant(manifest.exportedAt()));
        response.put("blockReason", manifest.blockReason());
        return response;
    }

    private ReportExportResponse response(
            ReportExportManifest manifest,
            Map<String, Object> redactionPolicy,
            Map<String, Object> responseManifest,
            ReportExportFileStorage.StoredExport storedExport,
            Object content
    ) {
        return new ReportExportResponse(
                manifest.id(),
                manifest.reportId(),
                manifest.exportType(),
                manifest.status(),
                manifest.schemaVersion(),
                manifest.fieldSetVersion(),
                manifest.contentDigest(),
                manifest.aggregateOnly(),
                manifest.exportedBy(),
                manifest.exportedAt(),
                manifest.blockReason(),
                redactionPolicy,
                responseManifest,
                storedExport != null,
                storedExport == null ? null : storedExport.fileName(),
                storedExport == null ? null : storedExport.contentType(),
                content,
                manifest.createdAt()
        );
    }

    private ReportExportFileStorage.StoredExport storeExportFile(
            ReportExportManifest manifest,
            String serializedContent
    ) {
        try {
            return fileStorage.store(manifest, serializedContent);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "REPORT_EXPORT_STORAGE_FAILED");
        }
    }

    private ReportExecutionReport withExportManifestCount(ReportExecutionReport report, long exportManifestCount) {
        Map<String, Object> summary = new LinkedHashMap<>(jsonSupport.readMap(report.reportSummaryJson()));
        summary.put("exportManifestCount", exportManifestCount);
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

    private String normalizeExportType(String exportType) {
        String normalized = StringUtils.hasText(exportType)
                ? exportType.trim().toUpperCase(Locale.ROOT)
                : "JSON";
        if (!EXPORT_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REPORT_EXPORT_TYPE_INVALID");
        }
        return normalized;
    }

    private void requireExportEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_DISABLED");
        }
        if (!properties.exportEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "REPORT_EXPORT_DISABLED");
        }
    }

    private ReportExecutionReport requireReport(UUID id) {
        return repository.report(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "REPORT_NOT_FOUND"));
    }

    private String stringInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private void audit(ReportExecutionReport report, String action, String result, Map<String, Object> afterJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.id());
        payload.put("projectId", report.projectId());
        payload.put("executionRunId", report.executionRunId());
        payload.put("traceId", TraceContext.getOrCreateTraceId());
        payload.putAll(afterJson);
        contextClient.writeAuditEvent(
                action,
                "REPORT_EXPORT_MANIFEST",
                report.id().toString(),
                report.projectId(),
                result,
                payload
        );
    }

    private record SetLike(String... values) {

        private boolean contains(String value) {
            for (String item : values) {
                if (item.equals(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record DownloadableExport(
            UUID exportId,
            UUID reportId,
            String exportType,
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
