package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.domain.DocumentImportRecord;
import com.songhg.veri.agent.documentinput.domain.DocumentParseFeedbackSample;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DocumentParseFeedbackCaptureService {

    private static final int FEEDBACK_TEXT_LIMIT = 2000;
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|api[_-]?key|authorization)\\s*[:=]\\s*[^\\s,;]+"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://\\S+");
    private static final Pattern UUID_PATTERN = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("(?<!\\d)\\d{8,}(?!\\d)");

    private final DocumentInputRepository repository;
    private final DocumentInputPlatformContextClient contextClient;
    private final ObjectMapper objectMapper;

    public DocumentParseFeedbackCaptureService(
            DocumentInputRepository repository,
            DocumentInputPlatformContextClient contextClient,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
    }

    public void captureManualEdit(
            DocumentRequirementCandidate before,
            DocumentRequirementCandidate after,
            String actor
    ) {
        if (!"MODEL".equalsIgnoreCase(before.parseSource())) {
            return;
        }
        Set<String> changedFields = changedCandidateFields(before, after);
        if (changedFields.isEmpty()) {
            return;
        }
        DocumentImportRecord record = repository.importRecord(before.importId()).orElse(null);
        if (record == null) {
            return;
        }
        Instant now = Instant.now();
        DocumentParseFeedbackSample sample = new DocumentParseFeedbackSample(
                UUID.randomUUID(),
                before.id(),
                before.importId(),
                before.projectId(),
                record.sourceType().name(),
                record.rawDigest(),
                sha256OrNull(before.sourceRef()),
                sha256OrNull(before.sourceFragment()),
                before.parseSource(),
                before.modelInvocationId(),
                before.modelProviderName(),
                before.modelName(),
                "MANUAL_EDIT",
                String.join(",", changedFields),
                feedbackSnapshotJson(before),
                feedbackSnapshotJson(after),
                "READY_FOR_CORPUS",
                StringUtils.hasText(actor) ? actor : "system",
                now,
                now
        );
        repository.saveParseFeedbackSample(sample);
        writeAudit("CAPTURE_PARSE_FEEDBACK", "DOCUMENT_PARSE_FEEDBACK_SAMPLE", sample.id().toString(), sample.projectId(), sample);
    }

    private Set<String> changedCandidateFields(DocumentRequirementCandidate before, DocumentRequirementCandidate after) {
        Set<String> changedFields = new LinkedHashSet<>();
        addChangedField(changedFields, "title", before.title(), after.title());
        addChangedField(changedFields, "description", before.description(), after.description());
        addChangedField(changedFields, "priority", before.priority(), after.priority());
        addChangedField(changedFields, "acceptanceCriteria", before.acceptanceCriteria(), after.acceptanceCriteria());
        addChangedField(changedFields, "tags", before.tags(), after.tags());
        return changedFields;
    }

    private void addChangedField(Set<String> changedFields, String fieldName, String before, String after) {
        if (!Objects.equals(before, after)) {
            changedFields.add(fieldName);
        }
    }

    private String feedbackSnapshotJson(DocumentRequirementCandidate candidate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", sanitizeFeedbackText(candidate.title()));
        snapshot.put("description", sanitizeFeedbackText(candidate.description()));
        snapshot.put("priority", sanitizeFeedbackText(candidate.priority()));
        snapshot.put("acceptanceCriteria", sanitizeFeedbackText(candidate.acceptanceCriteria()));
        snapshot.put("tags", sanitizeFeedbackText(candidate.tags()));
        snapshot.put("confidence", candidate.confidence());
        snapshot.put("parseSource", candidate.parseSource());
        snapshot.put("sourceRefDigest", sha256OrNull(candidate.sourceRef()));
        snapshot.put("sourceFragmentDigest", sha256OrNull(candidate.sourceFragment()));
        snapshot.put("externalRequirementIdDigest", sha256OrNull(candidate.externalRequirementId()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "纠错反馈样本无法序列化");
        }
    }

    private String sanitizeFeedbackText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value.trim();
        sanitized = SECRET_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("[SECRET]");
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL]");
        sanitized = URL_PATTERN.matcher(sanitized).replaceAll("[URL]");
        sanitized = UUID_PATTERN.matcher(sanitized).replaceAll("[UUID]");
        sanitized = MOBILE_PATTERN.matcher(sanitized).replaceAll("[PHONE]");
        sanitized = LONG_NUMBER_PATTERN.matcher(sanitized).replaceAll("[NUMBER]");
        if (sanitized.length() > FEEDBACK_TEXT_LIMIT) {
            return sanitized.substring(0, FEEDBACK_TEXT_LIMIT) + "...[TRUNCATED]";
        }
        return sanitized;
    }

    private String sha256OrNull(String value) {
        return StringUtils.hasText(value) ? sha256(value.trim()) : null;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "纠错反馈摘要计算失败");
        }
    }

    private void writeAudit(String action, String resourceType, String resourceId, String scopeId, Object afterJson) {
        contextClient.writeAuditEvent(
                action,
                resourceType,
                resourceId,
                scopeId,
                "SUCCEEDED",
                Map.of("resourceType", resourceType, "resourceId", resourceId, "after", afterJson)
        );
    }
}
