package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetCodeGenerator.assetCode;

import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.RollbackAssetVersionRequest;
import com.songhg.veri.agent.asset.application.command.UpdateAssetLifecycleRequest;
import com.songhg.veri.agent.asset.application.command.UpdateRequirementRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetListQuery;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.AssetVersionHistoryResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.asset.domain.AssetVersion;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;



@Service
public class AssetRequirementService {

    private static final Logger log = LoggerFactory.getLogger(AssetRequirementService.class);
    private static final String SOURCE_IMPORT = "IMPORT";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    private static final Set<String> LIFECYCLE_STATUSES = AssetLifecycleStatus.codes();
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> REQUIREMENT_SOURCES = Set.of(SOURCE_IMPORT, SOURCE_MANUAL);

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final AssetVersionHistoryService versionHistoryService;
    private final AssetVersionRollbackService versionRollbackService;
    private final AssetLifecycleService lifecycleService;

    public AssetRequirementService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetVersionHistoryService versionHistoryService,
            AssetVersionRollbackService versionRollbackService,
            AssetLifecycleService lifecycleService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.versionHistoryService = versionHistoryService;
        this.versionRollbackService = versionRollbackService;
        this.lifecycleService = lifecycleService;
    }

    public String requirementProjectScopeId(UUID id) {
        AssetRequirement requirement = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        return projectAuditService.projectContext(requirement.projectId()).projectId();
    }

    public com.songhg.veri.agent.common.api.PageResponse<RequirementResponse> listRequirements(AssetListRequest request) {
        projectAuditService.validateProjectWhenProvided(request.getProjectId());
        AssetListQuery query = assetListQuery(request);
        List<RequirementResponse> items = repository.requirements(query).stream()
                .map(AssetResponseMapper::toRequirementResponse)
                .toList();
        return com.songhg.veri.agent.common.api.PageResponse.of(items, query.index(), query.size(), repository.countRequirements(query));
    }

    public RequirementResponse getRequirement(UUID id) {
        return repository.requirement(id)
                .map(AssetResponseMapper::toRequirementResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
    }

    public RequirementResponse getRequirementIncludingInactive(UUID id) {
        AssetRequirement requirement = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        projectAuditService.validateProjectWhenProvided(requirement.projectId());
        return AssetResponseMapper.toRequirementResponse(requirement);
    }

    public Optional<RequirementResponse> findImportedRequirement(String projectId, String sourceRef) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(sourceRef)) {
            return Optional.empty();
        }
        projectAuditService.validateProjectWhenProvided(projectId);
        return repository.requirementBySourceRef(projectId, SOURCE_IMPORT, sourceRef.trim())
                .map(AssetResponseMapper::toRequirementResponse);
    }

    @Transactional
    public RequirementResponse createRequirement(CreateRequirementRequest request) {
        String scopeId = projectAuditService.projectContext(request.projectId()).projectId();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String source = valueIn(request.source(), SOURCE_MANUAL, REQUIREMENT_SOURCES, "source");
        String sourceRef = trimToNull(request.sourceRef());
        if (SOURCE_IMPORT.equals(source) && sourceRef != null) {
            Optional<AssetRequirement> existing = repository.requirementBySourceRef(request.projectId(), source, sourceRef);
            if (existing.isPresent()) {
                AssetRequirement merged = mergeImportedRequirement(existing.get(), request, now);
                if (sameRequirement(existing.get(), merged)) {
                    return AssetResponseMapper.toRequirementResponse(existing.get());
                }
                if (!"DRAFT".equals(existing.get().status())) {
                    projectAuditService.writeProjectAudit("UPSERT_DENIED", "REQUIREMENT", existing.get().id(), scopeId, "DENIED");
                    throw new BusinessException(
                            ErrorCode.INVALID_STATE,
                            "既有导入需求已进入评审或审批状态，需人工处理差异后再更新"
                    );
                }
                projectAuditService.writeProjectAudit("UPSERT", "REQUIREMENT", merged.id(), scopeId);
                AssetRequirement stored = repository.saveRequirement(merged);
                versionHistoryService.recordRequirementChange(existing.get(), stored, "UPSERT");
                log.info("Updated imported requirement id={}, sourceRef={}, trace_id={}",
                        stored.id(), sourceRef, TraceContext.getTraceId());
                return AssetResponseMapper.toRequirementResponse(stored);
            }
        }
        AssetRequirement req = new AssetRequirement(
                id,
                assetCode("REQ", id),
                request.title(),
                request.description(),
                source,
                sourceRef,
                trimToNull(request.sourceUrl()),
                trimToNull(request.acceptanceCriteria()),
                initialStatus(request.status()),
                valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority"),
                request.projectId(),
                request.tags(),
                AssetVersion.initial(),
                "ACTIVE",
                null,
                null,
                now,
                now
        );
        projectAuditService.writeProjectAudit("CREATE", "REQUIREMENT", id, scopeId);
        AssetRequirement stored = repository.saveRequirement(req);
        if (stored.id().equals(id)) {
            versionHistoryService.recordRequirementCreated(stored);
            log.info("Created requirement id={}, title={}, trace_id={}", id, request.title(), TraceContext.getTraceId());
        }
        return AssetResponseMapper.toRequirementResponse(stored);
    }

    @Transactional
    public RequirementResponse updateRequirement(UUID id, UpdateRequirementRequest request) {
        AssetRequirement existing = repository.requirement(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        Instant now = Instant.now();
        String nextStatus = nextRequirementStatus(existing, request.status());
        AssetRequirement updated = new AssetRequirement(
                id,
                existing.code(),
                request.title(),
                request.description(),
                existing.source(),
                existing.sourceRef(),
                existing.sourceUrl(),
                existing.acceptanceCriteria(),
                nextStatus,
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                request.tags(),
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
        projectAuditService.writeProjectAudit("UPDATE", "REQUIREMENT", id, existing.projectId());
        AssetRequirement stored = repository.saveRequirement(updated);
        versionHistoryService.recordRequirementChange(existing, stored, "UPDATE");
        return AssetResponseMapper.toRequirementResponse(stored);
    }

    public List<AssetVersionHistoryResponse> requirementVersions(UUID id) {
        AssetRequirement requirement = repository.requirementIncludingInactive(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + id));
        return versionHistoryService.responses("REQUIREMENT", requirement.id());
    }

    @Transactional
    public RequirementResponse rollbackRequirementVersion(
            UUID id,
            int version,
            RollbackAssetVersionRequest request
    ) {
        return versionRollbackService.rollbackRequirementVersion(id, version, request);
    }

    @Transactional
    public RequirementResponse updateRequirementLifecycle(UUID id, UpdateAssetLifecycleRequest request) {
        return lifecycleService.updateRequirementLifecycle(id, request);
    }

    private AssetRequirement mergeImportedRequirement(
            AssetRequirement existing,
            CreateRequirementRequest request,
            Instant now
    ) {
        return new AssetRequirement(
                existing.id(),
                existing.code(),
                request.title(),
                trimToNull(request.description()),
                existing.source(),
                existing.sourceRef(),
                trimToNull(request.sourceUrl()),
                trimToNull(request.acceptanceCriteria()),
                existing.status(),
                valueIn(request.priority(), existing.priority(), PRIORITIES, "priority"),
                existing.projectId(),
                mergeTags(existing.tags(), request.tags()),
                existing.nextVersion(),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
    }

    private String nextRequirementStatus(AssetRequirement requirement, String rawNextStatus) {
        String nextStatus = valueIn(rawNextStatus, requirement.status(), REVIEW_STATUSES, "status");
        if (!requirement.canTransitionReviewStatusTo(nextStatus)) {
            projectAuditService.writeProjectAudit("STATUS_CHANGE_DENIED", "REQUIREMENT", requirement.id(), requirement.projectId(), "DENIED");
            throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    "REQUIREMENT 状态不允许从 " + requirement.status() + " 变更为 " + nextStatus
            );
        }
        return nextStatus;
    }

    private static boolean sameRequirement(AssetRequirement left, AssetRequirement right) {
        return java.util.Objects.equals(left.title(), right.title())
                && java.util.Objects.equals(left.description(), right.description())
                && java.util.Objects.equals(left.sourceUrl(), right.sourceUrl())
                && java.util.Objects.equals(left.acceptanceCriteria(), right.acceptanceCriteria())
                && java.util.Objects.equals(left.status(), right.status())
                && java.util.Objects.equals(left.priority(), right.priority())
                && java.util.Objects.equals(normalizedTags(left.tags()), normalizedTags(right.tags()));
    }

    private static AssetListQuery assetListQuery(AssetListRequest request) {
        return new AssetListQuery(
                trimToNull(request.getProjectId()),
                lifecycleFilter(request.getLifecycleStatus()),
                request.getStatus(),
                request.getSource(),
                request.getKeyword(),
                request.toPageQuery()
        );
    }

    private static String lifecycleFilter(String rawValue) {
        return valueIn(rawValue, "ACTIVE", LIFECYCLE_STATUSES, "lifecycleStatus");
    }

    private static String initialStatus(String rawValue) {
        return valueIn(rawValue, "DRAFT", REVIEW_STATUSES, "status");
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String mergeTags(String existing, String incoming) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, existing);
        addTags(tags, incoming);
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private static String normalizedTags(String rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, rawTags);
        return String.join(",", tags);
    }

    private static void addTags(LinkedHashSet<String> tags, String rawTags) {
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
}
