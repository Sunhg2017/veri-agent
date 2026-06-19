package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.command.ReviewUiE2eBundleCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eBundlePageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eBundleQuery;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleExportBundleResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleExportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleExportReviewSummaryResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleReviewResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBundleSummaryResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundleReview;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UiE2eBundleService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<String> BUNDLE_STATUSES = Set.of(
            "DRAFT",
            "STATIC_CHECK_FAILED",
            "REVIEWING",
            "APPROVED",
            "REJECTED",
            "ARCHIVED"
    );
    private static final Set<String> SUBMITTABLE_STATUSES = Set.of("DRAFT", "REJECTED", "STATIC_CHECK_FAILED");

    private final UiE2eRepository repository;
    private final UiE2eActorResolver actorResolver;
    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eProperties properties;
    private final UiE2eBundleFactory bundleFactory;
    private final ObjectMapper objectMapper;

    public UiE2eBundleService(
            UiE2eRepository repository,
            UiE2eActorResolver actorResolver,
            UiE2ePlatformContextClient contextClient,
            UiE2eProperties properties,
            UiE2eBundleFactory bundleFactory,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.properties = properties;
        this.bundleFactory = bundleFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * Rebuilds one aggregate-only review bundle from the current scene snapshot and archives older active summaries.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eBundleDetailResponse createOrRefreshBundle(CreateUiE2eBundleCommand command) {
        assertEnabled();
        if (command == null || command.sceneId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "bundle 请求不能为空");
        }
        UiE2eScene scene = requireScene(command.sceneId());
        assertSceneBundlable(scene);
        List<UiE2eSceneStep> steps = repository.sceneSteps(scene.id());
        if (steps.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "场景至少需要一个步骤才能生成 bundle");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eBundle generated = bundleFactory.createBundle(scene, steps, actor, now);
        List<UiE2eBundle> existingSceneBundles = repository.sceneBundles(scene.id());
        UiE2eBundle activeBundle = existingSceneBundles.stream()
                .filter(bundle -> generated.bundleDigest().equals(bundle.bundleDigest()))
                .filter(bundle -> !"ARCHIVED".equals(bundle.status()))
                .findFirst()
                .orElse(null);
        if (activeBundle != null) {
            return detail(activeBundle, scene);
        }
        UiE2eBundle archivedSameDigest = existingSceneBundles.stream()
                .filter(bundle -> generated.bundleDigest().equals(bundle.bundleDigest()))
                .filter(bundle -> "ARCHIVED".equals(bundle.status()))
                .findFirst()
                .orElse(null);
        archiveActiveSceneBundles(scene.id(), actor, now);
        if (archivedSameDigest != null) {
            UiE2eBundle reactivated = new UiE2eBundle(
                    archivedSameDigest.id(),
                    archivedSameDigest.sceneId(),
                    archivedSameDigest.projectId(),
                    generated.status(),
                    generated.bundleDigest(),
                    generated.specSummaryJson(),
                    generated.fixtureSummaryJson(),
                    generated.staticCheckSummaryJson(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    archivedSameDigest.createdBy(),
                    actor,
                    null,
                    archivedSameDigest.createdAt(),
                    now
            );
            repository.updateBundle(reactivated);
            auditBundle(reactivated, "SUCCEEDED", "GENERATED", Map.of(
                    "sceneCode", scene.code(),
                    "stepCount", steps.size(),
                    "staticCheckStatus", staticCheckStatus(reactivated),
                    "reusedArchivedRow", true
            ));
            return detail(reactivated, scene);
        }
        try {
            repository.insertBundle(generated);
        } catch (DuplicateKeyException exception) {
            UiE2eBundle existing = repository.sceneBundles(scene.id()).stream()
                    .filter(bundle -> generated.bundleDigest().equals(bundle.bundleDigest()))
                    .findFirst()
                    .orElseThrow(() -> exception);
            return detail(existing, scene);
        }
        auditBundle(generated, "SUCCEEDED", "GENERATED", Map.of(
                "sceneCode", scene.code(),
                "stepCount", steps.size(),
                "staticCheckStatus", staticCheckStatus(generated)
        ));
        return detail(generated, scene);
    }

    @Transactional(readOnly = true)
    public PageResponse<UiE2eBundleSummaryResponse> bundles(UiE2eBundlePageRequest request) {
        assertEnabled();
        UiE2eBundleQuery query = normalizeQuery(request == null ? new UiE2eBundlePageRequest().toQuery() : request.toQuery());
        List<UiE2eBundleSummaryResponse> items = repository.bundles(query).stream()
                .map(this::summary)
                .toList();
        int index = request == null ? 0 : request.getIndex();
        int size = request == null ? 20 : request.getSize();
        return PageResponse.of(items, index, size, repository.countBundles(query));
    }

    @Transactional(readOnly = true)
    public UiE2eBundleDetailResponse bundle(UUID id) {
        assertEnabled();
        UiE2eBundle bundle = requireBundle(id);
        return detail(bundle, requireScene(bundle.sceneId()));
    }

    /**
     * Exports one aggregate-only bundle snapshot for audit handoff without expanding the review free-text surface.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eBundleExportResponse exportBundle(UUID id) {
        assertEnabled();
        if (!properties.exportEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_EXPORT_DISABLED");
        }
        UiE2eBundle bundle = requireBundle(id);
        UiE2eScene scene = requireScene(bundle.sceneId());
        List<UiE2eBundleReview> reviews = repository.bundleReviews(bundle.id());
        UiE2eBundleExportResponse response = new UiE2eBundleExportResponse(
                "wp7-bundle-export-v1",
                Instant.now(),
                exportBundleSnapshot(bundle, scene),
                exportReviewSummary(reviews),
                exportRedactionPolicy()
        );
        auditBundle(bundle, "SUCCEEDED", "EXPORTED", Map.of(
                "schemaVersion", response.schemaVersion(),
                "reviewCount", response.reviewSummary().reviewCount(),
                "reviewCommentExported", false,
                "reviewerIdentityExported", false,
                "rawScriptExported", false
        ));
        return response;
    }

    /**
     * Archives a bundle through a dedicated transition so closed summaries stop participating in new run requests.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eBundleDetailResponse archiveBundle(UUID id) {
        assertEnabled();
        UiE2eBundle bundle = requireBundle(id);
        if ("ARCHIVED".equals(bundle.status())) {
            return detail(bundle, requireScene(bundle.sceneId()));
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eBundle archived = new UiE2eBundle(
                bundle.id(),
                bundle.sceneId(),
                bundle.projectId(),
                "ARCHIVED",
                bundle.bundleDigest(),
                bundle.specSummaryJson(),
                bundle.fixtureSummaryJson(),
                bundle.staticCheckSummaryJson(),
                bundle.submittedBy(),
                bundle.approvedBy(),
                bundle.submittedAt(),
                bundle.approvedAt(),
                bundle.rejectedAt(),
                bundle.createdBy(),
                actor,
                now,
                bundle.createdAt(),
                now
        );
        repository.updateBundle(archived);
        auditBundle(archived, "SUCCEEDED", "ARCHIVED", Map.of(
                "previousStatus", bundle.status(),
                "reviewCount", repository.bundleReviews(bundle.id()).size()
        ));
        return detail(archived, requireScene(archived.sceneId()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eBundleDetailResponse submitReview(UUID id, ReviewUiE2eBundleCommand command) {
        assertEnabled();
        UiE2eBundle bundle = requireBundle(id);
        if (!SUBMITTABLE_STATUSES.contains(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 DRAFT/REJECTED/STATIC_CHECK_FAILED bundle 可提交评审");
        }
        if (!UiE2eBundleFactory.STATIC_CHECK_PASSED.equals(staticCheckStatus(bundle))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, UiE2eBundleFactory.STATIC_CHECK_FAILED + ": bundle 静态校验未通过");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eBundle updated = new UiE2eBundle(
                bundle.id(),
                bundle.sceneId(),
                bundle.projectId(),
                "REVIEWING",
                bundle.bundleDigest(),
                bundle.specSummaryJson(),
                bundle.fixtureSummaryJson(),
                bundle.staticCheckSummaryJson(),
                actor,
                null,
                now,
                null,
                null,
                bundle.createdBy(),
                actor,
                bundle.archivedAt(),
                bundle.createdAt(),
                now
        );
        repository.updateBundle(updated);
        repository.insertBundleReview(review(updated, "SUBMITTED", note(command), actor, now));
        auditBundle(updated, "SUCCEEDED", "SUBMITTED", Map.of("notePresent", StringUtils.hasText(note(command))));
        return detail(updated, requireScene(updated.sceneId()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eBundleDetailResponse approve(UUID id, ReviewUiE2eBundleCommand command) {
        assertEnabled();
        UiE2eBundle bundle = requireBundle(id);
        if (!"REVIEWING".equals(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 REVIEWING bundle 可审批通过");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eBundle updated = new UiE2eBundle(
                bundle.id(),
                bundle.sceneId(),
                bundle.projectId(),
                "APPROVED",
                bundle.bundleDigest(),
                bundle.specSummaryJson(),
                bundle.fixtureSummaryJson(),
                bundle.staticCheckSummaryJson(),
                bundle.submittedBy(),
                actor,
                bundle.submittedAt(),
                now,
                null,
                bundle.createdBy(),
                actor,
                bundle.archivedAt(),
                bundle.createdAt(),
                now
        );
        repository.updateBundle(updated);
        repository.insertBundleReview(review(updated, "APPROVED", note(command), actor, now));
        auditBundle(updated, "SUCCEEDED", "APPROVED", Map.of("notePresent", StringUtils.hasText(note(command))));
        return detail(updated, requireScene(updated.sceneId()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eBundleDetailResponse reject(UUID id, ReviewUiE2eBundleCommand command) {
        assertEnabled();
        UiE2eBundle bundle = requireBundle(id);
        if (!"REVIEWING".equals(bundle.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅 REVIEWING bundle 可驳回");
        }
        String note = note(command);
        if (!StringUtils.hasText(note)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回原因必填");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eBundle updated = new UiE2eBundle(
                bundle.id(),
                bundle.sceneId(),
                bundle.projectId(),
                "REJECTED",
                bundle.bundleDigest(),
                bundle.specSummaryJson(),
                bundle.fixtureSummaryJson(),
                bundle.staticCheckSummaryJson(),
                bundle.submittedBy(),
                null,
                bundle.submittedAt(),
                null,
                now,
                bundle.createdBy(),
                actor,
                bundle.archivedAt(),
                bundle.createdAt(),
                now
        );
        repository.updateBundle(updated);
        repository.insertBundleReview(review(updated, "REJECTED", note, actor, now));
        auditBundle(updated, "FAILED", "REJECTED", Map.of("notePresent", true));
        return detail(updated, requireScene(updated.sceneId()));
    }

    @Transactional(readOnly = true)
    public String bundleProjectScopeId(UUID id) {
        return repository.bundleProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E bundle 不存在"));
    }

    private UiE2eBundleSummaryResponse summary(UiE2eBundle bundle) {
        UiE2eScene scene = requireScene(bundle.sceneId());
        return new UiE2eBundleSummaryResponse(
                bundle.id(),
                bundle.projectId(),
                bundle.sceneId(),
                scene.code(),
                scene.name(),
                scene.status(),
                bundle.status(),
                bundle.bundleDigest(),
                staticCheckStatus(bundle),
                readMap(bundle.staticCheckSummaryJson()),
                bundle.submittedAt(),
                bundle.approvedAt(),
                bundle.rejectedAt(),
                bundle.archivedAt(),
                bundle.createdAt(),
                bundle.updatedAt()
        );
    }

    private UiE2eBundleDetailResponse detail(UiE2eBundle bundle, UiE2eScene scene) {
        Map<String, Object> staticCheckSummary = readMap(bundle.staticCheckSummaryJson());
        return new UiE2eBundleDetailResponse(
                bundle.id(),
                bundle.projectId(),
                bundle.sceneId(),
                scene.code(),
                scene.name(),
                scene.status(),
                scene.applicationId(),
                scene.environmentId(),
                scene.riskLevel(),
                readStringList(scene.tagsJson()),
                bundle.status(),
                bundle.bundleDigest(),
                staticCheckStatus(bundle),
                readMap(bundle.specSummaryJson()),
                readMap(bundle.fixtureSummaryJson()),
                staticCheckSummary,
                Map.of(
                        "reviewable", "REVIEWING".equals(bundle.status())
                                || (SUBMITTABLE_STATUSES.contains(bundle.status())
                                && UiE2eBundleFactory.STATIC_CHECK_PASSED.equals(staticCheckStatus(bundle))),
                        "approvable", "REVIEWING".equals(bundle.status()),
                        "archivable", !"ARCHIVED".equals(bundle.status()),
                        "rejectedReviewRequiresNote", true,
                        "aggregateOnly", true,
                        "rawScriptStored", false,
                        "sceneExecutable", "APPROVED".equals(scene.status())
                ),
                bundle.submittedBy(),
                bundle.approvedBy(),
                bundle.submittedAt(),
                bundle.approvedAt(),
                bundle.rejectedAt(),
                bundle.archivedAt(),
                bundle.createdAt(),
                bundle.updatedAt(),
                repository.bundleReviews(bundle.id()).stream()
                        .map(this::reviewResponse)
                        .toList()
        );
    }

    private UiE2eBundleReviewResponse reviewResponse(UiE2eBundleReview review) {
        return new UiE2eBundleReviewResponse(
                review.id(),
                review.reviewStatus(),
                review.reviewComment(),
                review.reviewedBy(),
                review.reviewedAt(),
                review.createdAt(),
                review.updatedAt()
        );
    }

    private UiE2eBundleExportBundleResponse exportBundleSnapshot(UiE2eBundle bundle, UiE2eScene scene) {
        return new UiE2eBundleExportBundleResponse(
                bundle.id(),
                bundle.projectId(),
                bundle.sceneId(),
                scene.code(),
                scene.name(),
                scene.status(),
                scene.applicationId(),
                scene.environmentId(),
                scene.riskLevel(),
                readStringList(scene.tagsJson()),
                bundle.status(),
                bundle.bundleDigest(),
                staticCheckStatus(bundle),
                readMap(bundle.specSummaryJson()),
                readMap(bundle.fixtureSummaryJson()),
                readMap(bundle.staticCheckSummaryJson()),
                Map.of(
                        "aggregateOnly", true,
                        "rawScriptStored", false,
                        "reviewCommentExported", false,
                        "reviewerIdentityExported", false,
                        "reviewCountExported", true,
                        "sceneExecutable", "APPROVED".equals(scene.status())
                ),
                bundle.submittedAt(),
                bundle.approvedAt(),
                bundle.rejectedAt(),
                bundle.archivedAt(),
                bundle.createdAt(),
                bundle.updatedAt()
        );
    }

    private UiE2eBundleExportReviewSummaryResponse exportReviewSummary(List<UiE2eBundleReview> reviews) {
        List<UiE2eBundleReview> safeReviews = reviews == null ? List.of() : reviews;
        UiE2eBundleReview latest = safeReviews.isEmpty() ? null : safeReviews.get(0);
        return new UiE2eBundleExportReviewSummaryResponse(
                safeReviews.size(),
                (int) safeReviews.stream().filter(review -> StringUtils.hasText(review.reviewComment())).count(),
                safeReviews.stream().map(UiE2eBundleReview::reviewStatus).distinct().toList(),
                latest == null
                        ? Map.of()
                        : Map.of(
                                "reviewStatus", latest.reviewStatus(),
                                "reviewedAt", latest.reviewedAt(),
                                "commentPresent", StringUtils.hasText(latest.reviewComment())
                        )
        );
    }

    private Map<String, Object> exportRedactionPolicy() {
        return Map.of(
                "aggregateOnly", true,
                "rawScriptExported", false,
                "reviewCommentExported", false,
                "reviewerIdentityExported", false,
                "secretPlaintextExported", false,
                "cookiePlaintextExported", false
        );
    }

    private UiE2eBundleReview review(
            UiE2eBundle bundle,
            String status,
            String note,
            String actor,
            Instant now
    ) {
        return new UiE2eBundleReview(
                UUID.randomUUID(),
                bundle.id(),
                bundle.projectId(),
                status,
                note,
                actor,
                now,
                actor,
                actor,
                now,
                now
        );
    }

    private void archiveActiveSceneBundles(UUID sceneId, String actor, Instant now) {
        for (UiE2eBundle existing : repository.sceneBundles(sceneId)) {
            if ("ARCHIVED".equals(existing.status())) {
                continue;
            }
            repository.updateBundle(new UiE2eBundle(
                    existing.id(),
                    existing.sceneId(),
                    existing.projectId(),
                    "ARCHIVED",
                    existing.bundleDigest(),
                    existing.specSummaryJson(),
                    existing.fixtureSummaryJson(),
                    existing.staticCheckSummaryJson(),
                    existing.submittedBy(),
                    existing.approvedBy(),
                    existing.submittedAt(),
                    existing.approvedAt(),
                    existing.rejectedAt(),
                    existing.createdBy(),
                    actor,
                    now,
                    existing.createdAt(),
                    now
            ));
        }
    }

    private void auditBundle(UiE2eBundle bundle, String result, String action, Map<String, Object> afterJson) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("bundleId", bundle.id().toString());
        payload.put("sceneId", bundle.sceneId().toString());
        payload.put("status", bundle.status());
        payload.put("staticCheckStatus", staticCheckStatus(bundle));
        contextClient.writeAuditEvent(
                switch (action) {
                    case "GENERATED" -> "ui_e2e.bundle.created";
                    case "EXPORTED" -> "ui_e2e.bundle.exported";
                    case "ARCHIVED" -> "ui_e2e.bundle.archived";
                    default -> "ui_e2e.bundle.reviewed";
                },
                "UI_E2E_BUNDLE",
                bundle.id().toString(),
                bundle.projectId(),
                result,
                payload
        );
    }

    private UiE2eScene requireScene(UUID id) {
        return repository.scene(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E 场景不存在"));
    }

    private UiE2eBundle requireBundle(UUID id) {
        return repository.bundle(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E bundle 不存在"));
    }

    private void assertSceneBundlable(UiE2eScene scene) {
        if ("ARCHIVED".equals(scene.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档场景不可生成 bundle");
        }
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP7 UI/E2E 控制面已关闭");
        }
    }

    private UiE2eBundleQuery normalizeQuery(UiE2eBundleQuery query) {
        return new UiE2eBundleQuery(
                boundedNullable(query.projectId(), 64),
                query.sceneId(),
                query.status() == null ? null : normalizeStatus(query.status()),
                boundedNullable(query.keyword(), 128),
                Math.max(query.offset(), 0),
                Math.min(Math.max(query.limit(), 1), 100)
        );
    }

    private String normalizeStatus(String status) {
        String normalized = boundedText(status, 32).toUpperCase(Locale.ROOT);
        if (!BUNDLE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "bundle status 不合法");
        }
        return normalized;
    }

    private String staticCheckStatus(UiE2eBundle bundle) {
        Object value = readMap(bundle.staticCheckSummaryJson()).get("status");
        return value == null ? UiE2eBundleFactory.STATIC_CHECK_PASSED : value.toString();
    }

    private String note(ReviewUiE2eBundleCommand command) {
        return SensitiveTextSanitizer.sanitizedEvidenceText(command == null ? null : command.note(), 512);
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必填字段不能为空");
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E bundle 数据无法解析");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E bundle 标签无法解析");
        }
    }
}
