package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.api.request.AssetPrototypeSyncRequest;
import com.songhg.veri.agent.asset.api.response.AssetImportItemResponse;
import com.songhg.veri.agent.asset.api.response.AssetPrototypeSyncResponse;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetPrototypeSyncService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> PAGE_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED");
    private static final Set<String> PROTOTYPE_SOURCES = Set.of("FIGMA", "LANHU", "AXURE");

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final ObjectMapper objectMapper;

    public AssetPrototypeSyncService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssetPrototypeSyncResponse syncPrototypePages(AssetPrototypeSyncRequest request) {
        String source = valueIn(request.source(), null, PROTOTYPE_SOURCES, "source");
        String projectId = projectAuditService.resolveProjectScopeId(request.projectId());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        projectAuditService.writeAssetBatchAudit(
                dryRun ? "PROTOTYPE_SYNC_DRY_RUN" : "PROTOTYPE_SYNC",
                "PAGE",
                projectId,
                "SUCCEEDED"
        );
        List<AssetImportItemResponse> items = new ArrayList<>();
        for (int i = 0; i < request.pages().size(); i++) {
            items.add(syncPrototypePage(projectId, source, request, request.pages().get(i), i + 1, dryRun));
        }
        return new AssetPrototypeSyncResponse(
                source,
                dryRun,
                request.pages().size(),
                countAction(items, "CREATE"),
                countAction(items, "UPDATE"),
                countAction(items, "LINK_EXISTING"),
                (int) items.stream().filter(item -> "FAILED".equals(item.status())).count(),
                items
        );
    }

    private AssetImportItemResponse syncPrototypePage(
            String projectId,
            String source,
            AssetPrototypeSyncRequest request,
            AssetPrototypeSyncRequest.PageItem item,
            int row,
            boolean dryRun
    ) {
        try {
            String sourceRef = trimToNull(item.sourceRef());
            if (sourceRef == null) {
                return new AssetImportItemResponse(row, "INVALID", null, null, "FAILED", "sourceRef 不能为空", List.of("sourceRef 不能为空"));
            }
            Optional<AssetPage> existing = repository.pageBySourceRef(projectId, source, sourceRef);
            if (existing.isPresent()) {
                AssetPage merged = mergePrototypePage(existing.get(), source, request, item);
                if (samePage(existing.get(), merged)) {
                    return new AssetImportItemResponse(row, "LINK_EXISTING", existing.get().id(), existing.get().code(), dryRun ? "PLANNED" : "SUCCEEDED", "无差异，复用既有页面", List.of());
                }
                if (!dryRun) {
                    projectAuditService.writeProjectAudit("PROTOTYPE_SYNC_UPDATE", "PAGE", existing.get().id(), projectId);
                    repository.savePage(merged);
                }
                return new AssetImportItemResponse(row, "UPDATE", existing.get().id(), existing.get().code(), dryRun ? "PLANNED" : "SUCCEEDED", "同步更新页面", List.of());
            }
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            AssetPage created = new AssetPage(
                    id,
                    assetCode("PAGE", id),
                    item.name(),
                    trimToNull(item.urlPattern()),
                    source,
                    sourceRef,
                    firstText(item.sourceVersion(), request.sourceVersion()),
                    jsonValue(item.componentTree()),
                    trimToNull(item.screenshotUrl()),
                    projectId,
                    initialStatus(item.status(), STATUS_ACTIVE),
                    STATUS_ACTIVE,
                    null,
                    null,
                    now,
                    now
            );
            if (!dryRun) {
                projectAuditService.writeProjectAudit("PROTOTYPE_SYNC_CREATE", "PAGE", id, projectId);
                repository.savePage(created);
            }
            return new AssetImportItemResponse(row, "CREATE", id, created.code(), dryRun ? "PLANNED" : "SUCCEEDED", "同步创建页面", List.of());
        } catch (BusinessException e) {
            return new AssetImportItemResponse(row, "FAILED", null, null, "FAILED", e.getMessage(), List.of(e.getMessage()));
        }
    }

    private AssetPage mergePrototypePage(
            AssetPage existing,
            String source,
            AssetPrototypeSyncRequest request,
            AssetPrototypeSyncRequest.PageItem item
    ) {
        return new AssetPage(
                existing.id(),
                existing.code(),
                item.name(),
                trimToNull(item.urlPattern()),
                source,
                trimToNull(item.sourceRef()),
                firstText(item.sourceVersion(), request.sourceVersion(), existing.sourceVersion()),
                jsonValue(item.componentTree()),
                trimToNull(item.screenshotUrl()),
                existing.projectId(),
                initialStatus(item.status(), existing.status()),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                Instant.now()
        );
    }

    private boolean samePage(AssetPage left, AssetPage right) {
        return Objects.equals(left.name(), right.name())
                && Objects.equals(left.urlPattern(), right.urlPattern())
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceRef(), right.sourceRef())
                && Objects.equals(left.sourceVersion(), right.sourceVersion())
                && Objects.equals(jsonNode(left.componentTree()), jsonNode(right.componentTree()))
                && Objects.equals(left.screenshotUrl(), right.screenshotUrl())
                && Objects.equals(left.status(), right.status());
    }

    private JsonNode jsonNode(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text : null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 字段格式不合法");
        }
    }

    private static int countAction(List<AssetImportItemResponse> items, String action) {
        return (int) items.stream()
                .filter(item -> action.equals(item.action()))
                .filter(item -> !"FAILED".equals(item.status()))
                .count();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(java.util.Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String initialStatus(String rawValue, String defaultValue) {
        return valueIn(rawValue, defaultValue, PAGE_STATUSES, "status");
    }

    private static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
