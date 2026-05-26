package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.AssetService;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.document.application.command.DocumentPublishRequest;
import com.songhg.veri.agent.document.application.port.DocumentInputRepository;
import com.songhg.veri.agent.document.application.view.DocumentInputMetrics;
import com.songhg.veri.agent.document.application.view.DocumentPublishRecordResponse;
import com.songhg.veri.agent.document.application.view.DocumentPublishResponse;
import com.songhg.veri.agent.document.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.document.domain.DocumentImportRecord;
import com.songhg.veri.agent.document.domain.DocumentRequirementCandidate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;



@Service
public class DocumentRequirementPublishService {

    private final DocumentInputRepository repository;
    private final AssetService assetService;
    private final DocumentInputPlatformContextClient contextClient;
    private final ObjectMapper objectMapper;
    private final DocumentInputMetrics metrics;

    public DocumentRequirementPublishService(
            DocumentInputRepository repository,
            AssetService assetService,
            DocumentInputPlatformContextClient contextClient,
            ObjectMapper objectMapper,
            DocumentInputMetrics metrics
    ) {
        this.repository = repository;
        this.assetService = assetService;
        this.contextClient = contextClient;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public DocumentPublishResponse publishImport(UUID importId, DocumentPublishRequest request) {
        DocumentImportRecord record = repository.importRecord(importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + importId));
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        List<DocumentRequirementCandidate> selected = selectPublishCandidates(record, request == null ? null : request.candidateIds());
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "没有已确认候选项可发布");
        }
        if (dryRun) {
            List<DocumentPublishRecordResponse> dryRunRecords = selected.stream()
                    .map(candidate -> toPublishRecord(candidate, true))
                    .toList();
            DocumentPublishResponse response = toPublishResponse(record, true, dryRunRecords);
            metrics.recordPublish(true, publishResult(response), response.records().size());
            writeAudit("PUBLISH_DRY_RUN", "DOCUMENT_IMPORT", record.id().toString(), record.projectId(), response);
            return response;
        }
        for (DocumentRequirementCandidate candidate : selected) {
            if (candidate.status() == DocumentCandidateStatus.PUBLISHED && candidate.assetRequirementId() != null) {
                continue;
            }
            if (candidate.status() != DocumentCandidateStatus.CONFIRMED
                    && candidate.status() != DocumentCandidateStatus.PUBLISHED) {
                continue;
            }
            try {
                publishCandidate(candidate);
            } catch (BusinessException ignored) {
                // Per-item failure is persisted on the candidate and returned in publish records.
            }
        }
        List<DocumentRequirementCandidate> refreshed = repository.candidates(importId, 0, 10000);
        List<UUID> requirementIds = refreshed.stream()
                .filter(candidate -> candidate.status() == DocumentCandidateStatus.PUBLISHED)
                .map(DocumentRequirementCandidate::assetRequirementId)
                .filter(Objects::nonNull)
                .toList();
        DocumentImportRecord updated = new DocumentImportRecord(
                record.id(),
                record.projectId(),
                record.sourceId(),
                record.sourceCode(),
                record.sourceType(),
                record.sourceRef(),
                record.sourceUrl(),
                record.title(),
                record.status(),
                record.totalParsed(),
                requirementIds.size(),
                requirementIdsJson(requirementIds),
                record.errorMessage(),
                record.rawDigest(),
                record.createdAt(),
                Instant.now()
        );
        repository.saveImport(updated);
        List<DocumentPublishRecordResponse> records = selectPublishCandidates(updated, request == null ? null : request.candidateIds()).stream()
                .map(candidate -> toPublishRecord(candidate, false))
                .toList();
        DocumentPublishResponse response = toPublishResponse(updated, false, records);
        writeAudit("PUBLISH", "DOCUMENT_IMPORT", updated.id().toString(), updated.projectId(), response);
        metrics.recordPublish(false, publishResult(response), records.size());
        return response;
    }

    public PageResponse<DocumentPublishRecordResponse> publishRecords(UUID importId) {
        DocumentImportRecord record = repository.importRecord(importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导入记录不存在: " + importId));
        List<DocumentPublishRecordResponse> records = repository.candidates(importId, 0, 10000).stream()
                .map(candidate -> toPublishRecord(candidate, false))
                .toList();
        return PageResponse.of(records, 0, Math.max(1, records.size()), record.totalParsed());
    }

    private DocumentRequirementCandidate publishCandidate(DocumentRequirementCandidate candidate) {
        try {
            RequirementResponse existingRequirement = existingImportedRequirement(candidate);
            String diffSummary = existingRequirement == null ? null : requirementDiffSummary(candidate, existingRequirement);
            if (isReviewConflict(existingRequirement, diffSummary)) {
                throw new BusinessException(
                        ErrorCode.INVALID_STATE,
                        reviewConflictMessage(existingRequirement)
                );
            }
            DocumentImportRecord record = repository.importRecord(candidate.importId()).orElse(null);
            RequirementResponse response = assetService.createRequirement(new CreateRequirementRequest(
                    candidate.title(),
                    candidate.description(),
                    "DRAFT",
                    normalizePriority(candidate.priority()),
                    candidate.projectId(),
                    mergeTags(candidate.tags(), "document-input"),
                    "IMPORT",
                    firstText(candidate.externalRequirementId(), candidate.sourceRef()),
                    record == null ? null : record.sourceUrl(),
                    candidate.acceptanceCriteria()
            ));
            DocumentRequirementCandidate updated = withCandidatePublishResult(candidate, DocumentCandidateStatus.PUBLISHED, response.id(), null);
            repository.saveCandidate(updated);
            return updated;
        } catch (BusinessException exception) {
            DocumentRequirementCandidate failed = withCandidatePublishResult(candidate, DocumentCandidateStatus.PUBLISH_FAILED, null, exception.getMessage());
            repository.saveCandidate(failed);
            throw exception;
        }
    }

    private List<DocumentRequirementCandidate> selectPublishCandidates(DocumentImportRecord record, List<UUID> candidateIds) {
        List<DocumentRequirementCandidate> all = repository.candidates(record.id(), 0, 10000);
        if (candidateIds == null || candidateIds.isEmpty()) {
            return all.stream()
                    .filter(candidate -> candidate.status() == DocumentCandidateStatus.CONFIRMED
                            || candidate.status() == DocumentCandidateStatus.PUBLISHED
                            || candidate.status() == DocumentCandidateStatus.PUBLISH_FAILED)
                    .toList();
        }
        return candidateIds.stream()
                .map(this::candidateOrThrow)
                .peek(candidate -> {
                    if (!record.id().equals(candidate.importId())) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选项不属于当前导入批次: " + candidate.id());
                    }
                })
                .toList();
    }

    private DocumentPublishRecordResponse toPublishRecord(DocumentRequirementCandidate candidate, boolean dryRun) {
        RequirementResponse existingRequirement = existingImportedRequirement(candidate);
        String diffSummary = existingRequirement == null ? null : requirementDiffSummary(candidate, existingRequirement);
        String action = publishAction(candidate, existingRequirement, diffSummary);
        String result;
        String errorMessage = candidate.errorMessage();
        if (candidate.status() == DocumentCandidateStatus.PUBLISH_FAILED) {
            result = "FAILED";
        } else if ("CONFLICT_REVIEW_REQUIRED".equals(action)) {
            result = "CONFLICT";
            errorMessage = reviewConflictMessage(existingRequirement);
        } else if ("SKIP_UNCONFIRMED".equals(action) || "SKIP_PUBLISHED".equals(action)) {
            result = "SKIPPED";
        } else {
            result = dryRun ? "PLANNED" : "SUCCEEDED";
        }
        if ("SKIP_UNCONFIRMED".equals(action) && !StringUtils.hasText(errorMessage)) {
            errorMessage = "候选项未确认，不能发布";
        }
        return new DocumentPublishRecordResponse(
                candidate.id(),
                candidate.title(),
                candidate.status(),
                action,
                result,
                candidate.projectId(),
                candidate.externalRequirementId(),
                candidate.sourceRef(),
                candidate.sourceFragment(),
                firstNonNull(candidate.assetRequirementId(), existingRequirement == null ? null : existingRequirement.id()),
                existingRequirement == null ? null : existingRequirement.id(),
                diffSummary,
                errorMessage,
                candidate.version()
        );
    }

    private String publishAction(
            DocumentRequirementCandidate candidate,
            RequirementResponse existingRequirement,
            String diffSummary
    ) {
        if (candidate.status() == DocumentCandidateStatus.PUBLISH_FAILED) {
            return "PUBLISH_FAILED";
        }
        if (candidate.status() == DocumentCandidateStatus.PUBLISHED && candidate.assetRequirementId() != null) {
            return "SKIP_PUBLISHED";
        }
        if (candidate.status() != DocumentCandidateStatus.CONFIRMED && candidate.status() != DocumentCandidateStatus.PUBLISHED) {
            return "SKIP_UNCONFIRMED";
        }
        if (existingRequirement == null) {
            return "CREATE";
        }
        if (isReviewConflict(existingRequirement, diffSummary)) {
            return "CONFLICT_REVIEW_REQUIRED";
        }
        return StringUtils.hasText(diffSummary) ? "UPDATE" : "LINK_EXISTING";
    }

    private boolean isReviewConflict(RequirementResponse existingRequirement, String diffSummary) {
        return existingRequirement != null
                && StringUtils.hasText(diffSummary)
                && !"DRAFT".equalsIgnoreCase(existingRequirement.status());
    }

    private String reviewConflictMessage(RequirementResponse existingRequirement) {
        String status = existingRequirement == null ? "UNKNOWN" : existingRequirement.status();
        return "既有 WP3 需求资产状态为 " + status + "，需人工处理差异后再更新";
    }

    private RequirementResponse existingImportedRequirement(DocumentRequirementCandidate candidate) {
        if (candidate.assetRequirementId() != null) {
            try {
                return assetService.getRequirement(candidate.assetRequirementId());
            } catch (BusinessException ignored) {
                // Fall through to sourceRef lookup; stale candidate links should not hide a valid imported asset.
            }
        }
        return assetService.findImportedRequirement(candidate.projectId(), candidate.externalRequirementId())
                .orElseGet(() -> repository.candidateByExternalId(candidate.projectId(), candidate.externalRequirementId())
                        .filter(existing -> !existing.id().equals(candidate.id()))
                        .filter(existing -> existing.status() == DocumentCandidateStatus.PUBLISHED)
                        .map(DocumentRequirementCandidate::assetRequirementId)
                        .filter(Objects::nonNull)
                        .map(this::requirementByIdOrNull)
                        .filter(Objects::nonNull)
                        .orElse(null));
    }

    private RequirementResponse requirementByIdOrNull(UUID id) {
        try {
            return assetService.getRequirement(id);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private String requirementDiffSummary(DocumentRequirementCandidate candidate, RequirementResponse existing) {
        List<String> changedFields = new ArrayList<>();
        addChangedField(changedFields, "title", candidate.title(), existing.title());
        addChangedField(changedFields, "description", candidate.description(), existing.description());
        addChangedField(changedFields, "priority", normalizePriority(candidate.priority()), existing.priority());
        addChangedField(changedFields, "acceptanceCriteria", candidate.acceptanceCriteria(), existing.acceptanceCriteria());
        addChangedField(changedFields, "tags", publishedRequirementTags(candidate, existing), normalizeTagsText(existing.tags()));
        return changedFields.isEmpty() ? null : String.join(",", changedFields);
    }

    private void addChangedField(List<String> changedFields, String field, String incoming, String existing) {
        if (!Objects.equals(normalizeCompareText(incoming), normalizeCompareText(existing))) {
            changedFields.add(field);
        }
    }

    private String normalizeCompareText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeTagsText(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace("，", ",").replaceAll("\\s*,\\s*", ",")
                : "";
    }

    private String publishedRequirementTags(DocumentRequirementCandidate candidate, RequirementResponse existing) {
        return mergeDistinctTags(existing.tags(), mergeTags(candidate.tags(), "document-input"));
    }

    private String mergeDistinctTags(String existing, String incoming) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addDistinctTags(tags, existing);
        addDistinctTags(tags, incoming);
        return tags.isEmpty() ? "" : String.join(",", tags);
    }

    private void addDistinctTags(LinkedHashSet<String> tags, String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return;
        }
        for (String tag : rawTags.replace("，", ",").split(",")) {
            String trimmed = tag.trim();
            if (StringUtils.hasText(trimmed)) {
                tags.add(trimmed);
            }
        }
    }

    private DocumentPublishResponse toPublishResponse(
            DocumentImportRecord record,
            boolean dryRun,
            List<DocumentPublishRecordResponse> records
    ) {
        List<DocumentRequirementCandidate> candidates = repository.candidates(record.id(), 0, 10000);
        List<UUID> requirementIds = requirementIds(record.createdRequirementIds());
        return new DocumentPublishResponse(
                record.id(),
                record.id(),
                record.projectId(),
                record.sourceId(),
                record.sourceCode(),
                record.sourceType(),
                record.sourceRef(),
                record.sourceUrl(),
                record.title(),
                record.status(),
                dryRun,
                record.totalParsed(),
                record.totalCreated(),
                requirementIds,
                countCandidates(candidates, DocumentCandidateStatus.PENDING),
                countCandidates(candidates, DocumentCandidateStatus.CONFIRMED),
                countCandidates(candidates, DocumentCandidateStatus.PUBLISHED),
                countCandidates(candidates, DocumentCandidateStatus.PUBLISH_FAILED),
                countPublishRecords(records, "CREATE"),
                countPublishRecords(records, "UPDATE"),
                countPublishRecords(records, "LINK_EXISTING"),
                countPublishRecords(records, "CONFLICT_REVIEW_REQUIRED"),
                (int) records.stream().filter(recordItem -> "SKIPPED".equals(recordItem.result())).count(),
                (int) records.stream().filter(recordItem -> "FAILED".equals(recordItem.result())).count(),
                records,
                record.errorMessage(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private int countPublishRecords(List<DocumentPublishRecordResponse> records, String action) {
        return (int) records.stream().filter(record -> action.equals(record.action())).count();
    }

    private long countCandidates(List<DocumentRequirementCandidate> candidates, DocumentCandidateStatus status) {
        return candidates.stream().filter(candidate -> candidate.status() == status).count();
    }

    private String publishResult(DocumentPublishResponse response) {
        if (response.conflictCount() > 0
                && response.plannedCreateCount() == 0
                && response.plannedUpdateCount() == 0
                && response.linkedExistingCount() == 0
                && response.publishedCount() == 0) {
            return "CONFLICT";
        }
        if (response.conflictCount() > 0) {
            return "PARTIAL";
        }
        if (response.publishFailedCount() > 0 && response.publishedCount() == 0) {
            return "FAILED";
        }
        if (response.publishFailedCount() > 0) {
            return "PARTIAL";
        }
        return "SUCCEEDED";
    }

    private DocumentRequirementCandidate withCandidatePublishResult(
            DocumentRequirementCandidate candidate,
            DocumentCandidateStatus status,
            UUID assetRequirementId,
            String errorMessage
    ) {
        return new DocumentRequirementCandidate(
                candidate.id(),
                candidate.importId(),
                candidate.projectId(),
                candidate.title(),
                candidate.description(),
                candidate.priority(),
                candidate.acceptanceCriteria(),
                candidate.tags(),
                status,
                candidate.sourceRef(),
                candidate.sourceFragment(),
                candidate.externalRequirementId(),
                candidate.confidence(),
                candidate.parseSource(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                assetRequirementId == null ? candidate.assetRequirementId() : assetRequirementId,
                errorMessage,
                candidate.ignoredReason(),
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version() + 1,
                candidate.createdAt(),
                Instant.now()
        );
    }

    private DocumentRequirementCandidate candidateOrThrow(UUID id) {
        return repository.candidate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选需求不存在: " + id));
    }

    private String requirementIdsJson(List<UUID> requirementIds) {
        try {
            return objectMapper.writeValueAsString(requirementIds);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导入记录无法序列化");
        }
    }

    private List<UUID> requirementIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(UUID.class).readValue(json);
        } catch (Exception exception) {
            return List.of();
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

    private UUID firstNonNull(UUID first, UUID second) {
        return first == null ? second : first;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizePriority(String value) {
        if (!StringUtils.hasText(value)) {
            return "MEDIUM";
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "P0", "CRITICAL", "BLOCKER" -> "CRITICAL";
            case "P1", "HIGH" -> "HIGH";
            case "P3", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private String mergeTags(String existing, String... tags) {
        StringBuilder value = new StringBuilder(StringUtils.hasText(existing) ? existing.trim() : "");
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            if (!value.isEmpty()) {
                value.append(',');
            }
            value.append(tag.trim());
        }
        return value.toString();
    }
}
