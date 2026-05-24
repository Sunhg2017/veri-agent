package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.application.command.CandidateBatchActionRequest;
import com.songhg.veri.agent.documentinput.application.command.ConfirmDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.command.IgnoreDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.command.UpdateDocumentCandidateRequest;
import com.songhg.veri.agent.documentinput.application.port.DocumentInputRepository;
import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateBatchActionItemResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateBatchActionResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateResponse;
import com.songhg.veri.agent.documentinput.application.view.DocumentInputMetrics;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentCandidateStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentRequirementCandidate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;



@Service
public class DocumentCandidateWorkflowService {

    private final DocumentInputRepository repository;
    private final DocumentParseFeedbackCaptureService feedbackCaptureService;
    private final DocumentInputPlatformContextClient contextClient;
    private final DocumentInputMetrics metrics;
    private final DocumentInputProperties properties;
    private final DocumentInputActorResolver actorResolver;

    private record CandidateBatchTarget(UUID id, Long version) {
    }

    public DocumentCandidateWorkflowService(
            DocumentInputRepository repository,
            DocumentParseFeedbackCaptureService feedbackCaptureService,
            DocumentInputPlatformContextClient contextClient,
            DocumentInputMetrics metrics,
            DocumentInputProperties properties,
            DocumentInputActorResolver actorResolver
    ) {
        this.repository = repository;
        this.feedbackCaptureService = feedbackCaptureService;
        this.contextClient = contextClient;
        this.metrics = metrics;
        this.properties = properties;
        this.actorResolver = actorResolver;
    }

    public DocumentCandidateResponse updateCandidate(UUID id, UpdateDocumentCandidateRequest request) {
        DocumentRequirementCandidate existing = candidateOrThrow(id);
        ensureCandidateEditable(existing);
        assertRequiredVersion(existing, request.version());
        DocumentRequirementCandidate updated = new DocumentRequirementCandidate(
                existing.id(),
                existing.importId(),
                existing.projectId(),
                request.title().trim(),
                trimToNull(request.description()),
                normalizePriority(request.priority()),
                trimToNull(request.acceptanceCriteria()),
                normalizeTags(request.tags()),
                existing.status(),
                existing.sourceRef(),
                existing.sourceFragment(),
                existing.externalRequirementId(),
                existing.confidence(),
                existing.parseSource(),
                existing.modelInvocationId(),
                existing.modelProviderName(),
                existing.modelName(),
                existing.assetRequirementId(),
                null,
                existing.ignoredReason(),
                existing.confirmedBy(),
                existing.confirmedAt(),
                existing.version() + 1,
                existing.createdAt(),
                Instant.now()
        );
        repository.saveCandidate(updated);
        feedbackCaptureService.captureManualEdit(existing, updated, currentActor());
        writeAudit("UPDATE", "DOCUMENT_CANDIDATE", updated.id().toString(), updated.projectId(), updated);
        return toCandidateResponse(updated);
    }

    public DocumentCandidateResponse confirmCandidate(UUID id, ConfirmDocumentCandidateRequest request) {
        Long expectedVersion = request == null ? null : request.version();
        return confirmCandidate(id, expectedVersion, true);
    }

    public DocumentCandidateResponse ignoreCandidate(UUID id, IgnoreDocumentCandidateRequest request) {
        String reason = request == null ? null : trimToNull(request.reason());
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "忽略候选项必须填写 reason");
        }
        return ignoreCandidate(id, reason, request.version(), true);
    }

    public DocumentCandidateBatchActionResponse batchCandidateAction(CandidateBatchActionRequest request) {
        List<CandidateBatchTarget> targets = batchTargets(request);
        if (targets.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "批量候选操作必须指定 candidateIds 或 candidates"
            );
        }
        if (targets.size() > batchActionLimit()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "批量候选操作最多支持 " + batchActionLimit() + " 项"
            );
        }
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CONFIRM", "IGNORE").contains(action)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "不支持的候选批量动作: " + request.action()
            );
        }
        String ignoreReason = trimToNull(request.reason());
        if ("IGNORE".equals(action) && !StringUtils.hasText(ignoreReason)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量忽略候选项必须填写 reason");
        }
        List<DocumentCandidateBatchActionItemResponse> items = new ArrayList<>();
        for (CandidateBatchTarget target : targets) {
            try {
                DocumentCandidateResponse candidate = "CONFIRM".equals(action)
                        ? confirmCandidate(target.id(), target.version(), false)
                        : ignoreCandidate(target.id(), ignoreReason, target.version(), false);
                items.add(new DocumentCandidateBatchActionItemResponse(
                        target.id(),
                        "SUCCEEDED",
                        candidate,
                        null,
                        null
                ));
            } catch (BusinessException exception) {
                items.add(new DocumentCandidateBatchActionItemResponse(
                        target.id(),
                        "FAILED",
                        null,
                        exception.getErrorCode().name(),
                        exception.getMessage()
                ));
                metrics.recordCandidateAction(action, "FAILED");
            }
        }
        long succeeded = items.stream().filter(item -> "SUCCEEDED".equals(item.result())).count();
        writeAudit("BATCH_" + action, "DOCUMENT_CANDIDATE", "batch", null, Map.of(
                "action", action,
                "total", items.size(),
                "succeeded", succeeded,
                "failed", items.size() - succeeded
        ));
        return new DocumentCandidateBatchActionResponse(
                action,
                items.size(),
                Math.toIntExact(succeeded),
                items.size() - Math.toIntExact(succeeded),
                items
        );
    }

    private DocumentCandidateResponse confirmCandidate(UUID id, Long expectedVersion, boolean requireVersion) {
        DocumentRequirementCandidate existing = candidateOrThrow(id);
        ensureCandidateEditable(existing);
        assertCandidateVersion(existing, expectedVersion, requireVersion);
        Instant now = Instant.now();
        DocumentRequirementCandidate updated = new DocumentRequirementCandidate(
                existing.id(),
                existing.importId(),
                existing.projectId(),
                existing.title(),
                existing.description(),
                existing.priority(),
                existing.acceptanceCriteria(),
                existing.tags(),
                DocumentCandidateStatus.CONFIRMED,
                existing.sourceRef(),
                existing.sourceFragment(),
                existing.externalRequirementId(),
                existing.confidence(),
                existing.parseSource(),
                existing.modelInvocationId(),
                existing.modelProviderName(),
                existing.modelName(),
                existing.assetRequirementId(),
                null,
                null,
                currentActor(),
                now,
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        repository.saveCandidate(updated);
        writeAudit("CONFIRM", "DOCUMENT_CANDIDATE", updated.id().toString(), updated.projectId(), updated);
        metrics.recordCandidateAction("CONFIRM", "SUCCEEDED");
        return toCandidateResponse(updated);
    }

    private DocumentCandidateResponse ignoreCandidate(
            UUID id,
            String reason,
            Long expectedVersion,
            boolean requireVersion
    ) {
        DocumentRequirementCandidate existing = candidateOrThrow(id);
        ensureCandidateEditable(existing);
        assertCandidateVersion(existing, expectedVersion, requireVersion);
        Instant now = Instant.now();
        DocumentRequirementCandidate updated = new DocumentRequirementCandidate(
                existing.id(),
                existing.importId(),
                existing.projectId(),
                existing.title(),
                existing.description(),
                existing.priority(),
                existing.acceptanceCriteria(),
                existing.tags(),
                DocumentCandidateStatus.IGNORED,
                existing.sourceRef(),
                existing.sourceFragment(),
                existing.externalRequirementId(),
                existing.confidence(),
                existing.parseSource(),
                existing.modelInvocationId(),
                existing.modelProviderName(),
                existing.modelName(),
                existing.assetRequirementId(),
                null,
                reason,
                existing.confirmedBy(),
                existing.confirmedAt(),
                existing.version() + 1,
                existing.createdAt(),
                now
        );
        repository.saveCandidate(updated);
        writeAudit("IGNORE", "DOCUMENT_CANDIDATE", updated.id().toString(), updated.projectId(), updated);
        metrics.recordCandidateAction("IGNORE", "SUCCEEDED");
        return toCandidateResponse(updated);
    }

    private List<CandidateBatchTarget> batchTargets(CandidateBatchActionRequest request) {
        if (request.candidates() != null && !request.candidates().isEmpty()) {
            return request.candidates().stream()
                    .filter(Objects::nonNull)
                    .map(item -> {
                        if (item.id() == null) {
                            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "批量候选项 id 不能为空");
                        }
                        return new CandidateBatchTarget(item.id(), item.version());
                    })
                    .toList();
        }
        if (request.candidateIds() == null) {
            return List.of();
        }
        return request.candidateIds().stream()
                .map(id -> new CandidateBatchTarget(id, null))
                .toList();
    }

    private DocumentRequirementCandidate candidateOrThrow(UUID id) {
        return repository.candidate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选需求不存在: " + id));
    }

    private void ensureCandidateEditable(DocumentRequirementCandidate candidate) {
        if (candidate.status() == DocumentCandidateStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已发布候选项不可编辑");
        }
    }

    private void assertRequiredVersion(DocumentRequirementCandidate candidate, Long expectedVersion) {
        assertCandidateVersion(candidate, expectedVersion, true);
    }

    private void assertCandidateVersion(
            DocumentRequirementCandidate candidate,
            Long expectedVersion,
            boolean requireVersion
    ) {
        if (expectedVersion == null) {
            if (requireVersion) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "候选项版本号不能为空");
            }
            return;
        }
        if (expectedVersion != candidate.version()) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选项版本已变化，请刷新后重试");
        }
    }

    private int batchActionLimit() {
        return properties.batchActionLimit() <= 0 ? 100 : properties.batchActionLimit();
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

    /**
     * Uses the shared actor resolver so candidate audit and feedback samples agree on identity.
     */
    private String currentActor() {
        return actorResolver.currentActor();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeTags(JsonNode tags) {
        if (tags == null || tags.isNull() || tags.isMissingNode()) {
            return null;
        }
        if (tags.isTextual() || tags.isNumber() || tags.isBoolean()) {
            return trimToNull(tags.asText());
        }
        if (!tags.isArray()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "tags 必须是字符串或字符串数组");
        }
        StringBuilder value = new StringBuilder();
        for (JsonNode tag : tags) {
            if (tag == null || tag.isNull() || tag.isMissingNode()) {
                continue;
            }
            if (!tag.isValueNode()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "tags 必须是字符串或字符串数组");
            }
            String normalized = trimToNull(tag.asText());
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (!value.isEmpty()) {
                value.append(',');
            }
            value.append(normalized);
        }
        return value.isEmpty() ? null : value.toString();
    }

    private String normalizePriority(String value) {
        if (!StringUtils.hasText(value)) {
            return "MEDIUM";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "CRITICAL", "BLOCKER" -> "CRITICAL";
            case "P1", "HIGH" -> "HIGH";
            case "P3", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private static DocumentCandidateResponse toCandidateResponse(DocumentRequirementCandidate candidate) {
        return new DocumentCandidateResponse(
                candidate.id(),
                candidate.importId(),
                candidate.projectId(),
                candidate.title(),
                candidate.description(),
                candidate.priority(),
                candidate.acceptanceCriteria(),
                candidate.tags(),
                candidate.status(),
                candidate.sourceRef(),
                candidate.sourceFragment(),
                candidate.externalRequirementId(),
                candidate.confidence(),
                candidate.parseSource(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                candidate.assetRequirementId(),
                candidate.errorMessage(),
                candidate.ignoredReason(),
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }
}
