package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.command.SyncApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncItemResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncPreviewItemResponse;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.command.SyncOpenApiRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Owns WP6 OpenAPI diff evidence and controlled WP3 API asset synchronization.
 */
final class ApiAutomationSyncSupport {

    private static final int ERROR_SUMMARY_MAX_CHARS = 512;
    private static final int WP3_API_PATH_MAX_CHARS = 256;
    private static final int WP3_API_SUMMARY_MAX_CHARS = 256;
    private static final int WP3_API_VERSION_MAX_CHARS = 32;
    private static final int WP3_API_PAGE_SIZE = 100;
    private static final int WP3_API_PAGE_LIMIT = 20;
    private static final List<String> DIFF_STATUSES = List.of("NEW", "CHANGED", "MATCHED", "CONFLICT", "SKIPPED");
    private static final List<String> SYNC_PREVIEW_ACTIONS = List.of("CREATE", "UPDATE", "REVIEW", "SKIP");

    private final ApiAutomationRepository repository;
    private final AssetApiService assetApiService;
    private final ApiAutomationJsonSupport jsonSupport;

    ApiAutomationSyncSupport(
            ApiAutomationRepository repository,
            AssetApiService assetApiService,
            ApiAutomationJsonSupport jsonSupport
    ) {
        this.repository = repository;
        this.assetApiService = assetApiService;
        this.jsonSupport = jsonSupport;
    }

    List<ApiAutomationEndpointSnapshot> evaluateAndPersistDiff(ApiAutomationSpec spec) {
        List<ApiAutomationEndpointSnapshot> updated = evaluateDiff(spec);
        updated.forEach(repository::updateEndpointSnapshotDiff);
        return updated;
    }

    List<ApiAutomationEndpointSnapshot> evaluateDiff(ApiAutomationSpec spec) {
        Instant now = Instant.now();
        // WP3 API 当前没有 serviceName 字段，M3 先按项目内 method + path 匹配，serviceName 仅作为展示和后续扩展依据。
        Map<String, List<ApiResponseDTO>> assetApisByKey = assetApisByKey(spec.projectId());
        return repository.endpointSnapshots(spec.id()).stream()
                .map(endpoint -> diffEndpoint(endpoint, assetApisByKey, now))
                .toList();
    }

    SyncAttempt syncEndpoint(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint,
            SyncApiAutomationSpecCommand command,
            Instant now
    ) {
        // 只有 NEW 和显式允许的 CHANGED 写入 WP3；冲突、跳过和已匹配 endpoint 保留人工运营判断。
        if ("NEW".equals(endpoint.diffStatus())) {
            return createAssetApi(spec, endpoint, now);
        }
        if ("CHANGED".equals(endpoint.diffStatus()) && command.shouldIncludeChanged()) {
            return updateAssetApi(spec, endpoint, now);
        }
        String result = "MATCHED".equals(endpoint.diffStatus()) ? "MATCHED" : "SKIPPED";
        String message = "CHANGED".equals(endpoint.diffStatus()) ? "includeChanged=false" : "diffStatus=" + endpoint.diffStatus();
        return new SyncAttempt(endpoint, syncItem(endpoint, result, message));
    }

    ApiAutomationSyncPreviewItemResponse syncPreviewItem(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint
    ) {
        String action = syncPreviewAction(endpoint.diffStatus());
        Map<String, Object> summary = jsonSupport.readSummary(endpoint.diffSummaryJson());
        String reason = Objects.toString(summary.getOrDefault("reason", endpoint.diffStatus()), endpoint.diffStatus());
        return new ApiAutomationSyncPreviewItemResponse(
                endpoint.id(),
                endpoint.assetApiId(),
                endpoint.httpMethod(),
                endpoint.path(),
                endpoint.diffStatus(),
                action,
                reason,
                syncPreviewPayloadSummary(spec, endpoint, action)
        );
    }

    Map<String, Integer> countDiffStatuses(List<ApiAutomationEndpointSnapshot> endpoints) {
        Map<String, Integer> counts = initializedCounts(DIFF_STATUSES);
        endpoints.forEach(endpoint -> counts.computeIfPresent(endpoint.diffStatus(), (key, value) -> value + 1));
        return counts;
    }

    Map<String, Integer> countSyncResults(List<ApiAutomationSyncItemResponse> items) {
        Map<String, Integer> counts = initializedCounts(List.of("CREATED", "UPDATED", "MATCHED", "SKIPPED", "FAILED"));
        items.forEach(item -> counts.computeIfPresent(item.result(), (key, value) -> value + 1));
        return counts;
    }

    Map<String, Integer> countSyncPreviewActions(List<ApiAutomationSyncPreviewItemResponse> items) {
        Map<String, Integer> counts = initializedCounts(SYNC_PREVIEW_ACTIONS);
        items.forEach(item -> counts.computeIfPresent(item.action(), (key, value) -> value + 1));
        return counts;
    }

    String syncResult(Map<String, Integer> counts) {
        return counts.getOrDefault("FAILED", 0) > 0 ? "FAILED" : "SUCCESS";
    }

    private ApiAutomationEndpointSnapshot diffEndpoint(
            ApiAutomationEndpointSnapshot endpoint,
            Map<String, List<ApiResponseDTO>> assetApisByKey,
            Instant now
    ) {
        if (endpoint.path().length() > WP3_API_PATH_MAX_CHARS) {
            return endpointWithDiff(endpoint, null, "SKIPPED", Map.of(
                    "reason", "PATH_TOO_LONG",
                    "action", "NONE",
                    "maxPathChars", WP3_API_PATH_MAX_CHARS
            ), now, endpoint.syncedAt(), null);
        }
        List<ApiResponseDTO> matches = assetApisByKey.getOrDefault(ApiAutomationJsonSupport.assetKey(endpoint.httpMethod(), endpoint.path()), List.of());
        if (matches.isEmpty()) {
            return endpointWithDiff(endpoint, null, "NEW", Map.of(
                    "reason", "NO_MATCHING_WP3_API",
                    "action", "CREATE",
                    "sourceRef", endpointSourceRef(endpoint)
            ), now, endpoint.syncedAt(), null);
        }
        if (matches.size() > 1) {
            return endpointWithDiff(endpoint, null, "CONFLICT", Map.of(
                    "reason", "DUPLICATE_WP3_API_MATCHES",
                    "action", "REVIEW",
                    "assetApiIds", matches.stream().map(value -> value.id().toString()).toList()
            ), now, endpoint.syncedAt(), null);
        }
        ApiResponseDTO asset = matches.getFirst();
        boolean sameDigest = endpoint.schemaDigest().equals(assetSchemaDigest(asset));
        return endpointWithDiff(endpoint, asset.id(), sameDigest ? "MATCHED" : "CHANGED", Map.of(
                "reason", sameDigest ? "SCHEMA_DIGEST_MATCHED" : "SCHEMA_DIGEST_CHANGED",
                "action", sameDigest ? "NONE" : "UPDATE",
                "assetApiId", asset.id().toString(),
                "assetSource", ApiAutomationJsonSupport.nullToEmpty(asset.source()),
                "assetSourceRef", ApiAutomationJsonSupport.nullToEmpty(asset.sourceRef())
        ), now, endpoint.syncedAt(), null);
    }

    private SyncAttempt createAssetApi(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, Instant now) {
        try {
            ApiResponseDTO asset = assetApiService.createOpenApiSyncedApi(syncRequest(spec, endpoint, true));
            ApiAutomationEndpointSnapshot synced = endpointWithDiff(endpoint, asset.id(), "MATCHED", Map.of(
                    "reason", "SYNC_CREATED",
                    "action", "CREATE",
                    "assetApiId", asset.id().toString()
            ), endpoint.lastDiffAt(), now, null);
            repository.updateEndpointSnapshotDiff(synced);
            return new SyncAttempt(synced, syncItem(synced, endpoint.diffStatus(), "CREATED", "已创建 WP3 API 资产"));
        } catch (BusinessException exception) {
            ApiAutomationEndpointSnapshot failed = endpointWithDiff(endpoint, endpoint.assetApiId(), endpoint.diffStatus(),
                    jsonSupport.readSummary(endpoint.diffSummaryJson()), endpoint.lastDiffAt(), endpoint.syncedAt(),
                    SensitiveTextSanitizer.boundedText(exception.getMessage(), ERROR_SUMMARY_MAX_CHARS));
            repository.updateEndpointSnapshotDiff(failed);
            return new SyncAttempt(failed, syncItem(failed, "FAILED", failed.syncErrorSummary()));
        }
    }

    private SyncAttempt updateAssetApi(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, Instant now) {
        if (endpoint.assetApiId() == null) {
            ApiAutomationEndpointSnapshot skipped = endpointWithDiff(endpoint, null, "CONFLICT", Map.of(
                    "reason", "MISSING_ASSET_API_ID",
                    "action", "REVIEW"
            ), endpoint.lastDiffAt(), endpoint.syncedAt(), "缺少可更新的 WP3 API 资产 ID");
            repository.updateEndpointSnapshotDiff(skipped);
            return new SyncAttempt(skipped, syncItem(skipped, "FAILED", skipped.syncErrorSummary()));
        }
        try {
            ApiResponseDTO asset = assetApiService.updateOpenApiSyncedApi(endpoint.assetApiId(), syncRequest(spec, endpoint, false));
            ApiAutomationEndpointSnapshot synced = endpointWithDiff(endpoint, asset.id(), "MATCHED", Map.of(
                    "reason", "SYNC_UPDATED",
                    "action", "UPDATE",
                    "assetApiId", asset.id().toString()
            ), endpoint.lastDiffAt(), now, null);
            repository.updateEndpointSnapshotDiff(synced);
            return new SyncAttempt(synced, syncItem(synced, endpoint.diffStatus(), "UPDATED", "已更新 WP3 API 资产"));
        } catch (BusinessException exception) {
            ApiAutomationEndpointSnapshot failed = endpointWithDiff(endpoint, endpoint.assetApiId(), endpoint.diffStatus(),
                    jsonSupport.readSummary(endpoint.diffSummaryJson()), endpoint.lastDiffAt(), endpoint.syncedAt(),
                    SensitiveTextSanitizer.boundedText(exception.getMessage(), ERROR_SUMMARY_MAX_CHARS));
            repository.updateEndpointSnapshotDiff(failed);
            return new SyncAttempt(failed, syncItem(failed, "FAILED", failed.syncErrorSummary()));
        }
    }

    private SyncOpenApiRequest syncRequest(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, boolean create) {
        return new SyncOpenApiRequest(
                SensitiveTextSanitizer.boundedText(StringUtils.hasText(endpoint.summary()) ? endpoint.summary() : endpoint.httpMethod() + " " + endpoint.path(),
                        WP3_API_SUMMARY_MAX_CHARS),
                SensitiveTextSanitizer.boundedText("WP6 OpenAPI sync " + ApiAutomationJsonSupport.nullToEmpty(endpoint.serviceName()) + " "
                        + ApiAutomationJsonSupport.nullToEmpty(endpoint.operationId()), 512),
                endpoint.httpMethod(),
                endpoint.path(),
                SensitiveTextSanitizer.boundedText(endpoint.schemaDigest(), WP3_API_VERSION_MAX_CHARS),
                requestSchema(endpoint),
                responseSchema(endpoint),
                spec.projectId(),
                endpointSourceRef(endpoint),
                create ? "ACTIVE" : null
        );
    }

    private String requestSchema(ApiAutomationEndpointSnapshot endpoint) {
        return jsonSupport.writeJson(Map.of(
                "wp6SchemaDigest", endpoint.schemaDigest(),
                "parameterCount", endpoint.parameterCount(),
                "requestBodyPresent", endpoint.requestBodyPresent(),
                "aggregateOnly", true
        ));
    }

    private String responseSchema(ApiAutomationEndpointSnapshot endpoint) {
        return jsonSupport.writeJson(Map.of(
                "wp6SchemaDigest", endpoint.schemaDigest(),
                "responseStatuses", StringUtils.hasText(endpoint.responseStatuses()) ? endpoint.responseStatuses() : "",
                "aggregateOnly", true
        ));
    }

    private String syncPreviewAction(String diffStatus) {
        return switch (diffStatus) {
            case "NEW" -> "CREATE";
            case "CHANGED" -> "UPDATE";
            case "CONFLICT" -> "REVIEW";
            default -> "SKIP";
        };
    }

    private Map<String, Object> syncPreviewPayloadSummary(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint,
            String action
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aggregateOnly", true);
        summary.put("dryRun", true);
        summary.put("wp3Write", false);
        summary.put("rawSchemaStored", false);
        summary.put("rawRequestResponseStored", false);
        summary.put("action", action);
        summary.put("httpMethod", endpoint.httpMethod());
        summary.put("path", endpoint.path());
        summary.put("summary", SensitiveTextSanitizer.boundedText(
                StringUtils.hasText(endpoint.summary()) ? endpoint.summary() : endpoint.httpMethod() + " " + endpoint.path(),
                WP3_API_SUMMARY_MAX_CHARS
        ));
        summary.put("projectId", spec.projectId());
        summary.put("versionLabel", SensitiveTextSanitizer.boundedText(endpoint.schemaDigest(), WP3_API_VERSION_MAX_CHARS));
        summary.put("sourceRef", endpointSourceRef(endpoint));
        summary.put("requestSchemaDigest", SensitiveTextSanitizer.sha256Hex(requestSchema(endpoint)));
        summary.put("responseSchemaDigest", SensitiveTextSanitizer.sha256Hex(responseSchema(endpoint)));
        summary.put("parameterCount", endpoint.parameterCount());
        summary.put("requestBodyPresent", endpoint.requestBodyPresent());
        summary.put("responseStatuses", StringUtils.hasText(endpoint.responseStatuses()) ? endpoint.responseStatuses() : "");
        if (endpoint.assetApiId() != null) {
            summary.put("assetApiId", endpoint.assetApiId().toString());
        }
        return summary;
    }

    private ApiAutomationEndpointSnapshot endpointWithDiff(
            ApiAutomationEndpointSnapshot endpoint,
            UUID assetApiId,
            String diffStatus,
            Map<String, Object> diffSummary,
            Instant lastDiffAt,
            Instant syncedAt,
            String syncErrorSummary
    ) {
        return new ApiAutomationEndpointSnapshot(
                endpoint.id(),
                endpoint.specId(),
                endpoint.projectId(),
                endpoint.serviceName(),
                endpoint.operationId(),
                endpoint.httpMethod(),
                endpoint.path(),
                endpoint.summary(),
                endpoint.tags(),
                endpoint.parameterCount(),
                endpoint.requestBodyPresent(),
                endpoint.responseStatuses(),
                endpoint.schemaDigest(),
                diffStatus,
                assetApiId,
                jsonSupport.writeJson(diffSummary == null ? Map.of() : diffSummary),
                lastDiffAt,
                syncedAt,
                syncErrorSummary,
                endpoint.createdAt(),
                Instant.now()
        );
    }

    private Map<String, List<ApiResponseDTO>> assetApisByKey(String projectId) {
        List<ApiResponseDTO> assets = new ArrayList<>();
        // 防御性分页上限避免一次 diff 因异常资产量拖垮控制面，后续可按 WP3 查询能力改为精确按 path 拉取。
        for (int index = 0; index < WP3_API_PAGE_LIMIT; index++) {
            AssetListRequest request = new AssetListRequest();
            request.setProjectId(projectId);
            request.setLifecycleStatus("ACTIVE");
            request.setIndex(index);
            request.setSize(WP3_API_PAGE_SIZE);
            PageResponse<ApiResponseDTO> page = assetApiService.listApis(request);
            assets.addAll(page.items());
            if (page.items().size() < WP3_API_PAGE_SIZE) {
                break;
            }
        }
        return assets.stream().collect(Collectors.groupingBy(
                asset -> ApiAutomationJsonSupport.assetKey(asset.httpMethod(), asset.path()),
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private String assetSchemaDigest(ApiResponseDTO asset) {
        String requestDigest = schemaDigest(asset.requestSchema());
        return StringUtils.hasText(requestDigest) ? requestDigest : schemaDigest(asset.responseSchema());
    }

    private String schemaDigest(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return "";
        }
        Map<String, Object> value = jsonSupport.readSummary(schemaJson);
        Object digest = value.get("wp6SchemaDigest");
        if (digest == null) {
            digest = value.get("schemaDigest");
        }
        return digest == null ? "" : digest.toString();
    }

    private String endpointSourceRef(ApiAutomationEndpointSnapshot endpoint) {
        return "wp6:" + endpoint.specId() + ":" + endpoint.id();
    }

    private ApiAutomationSyncItemResponse syncItem(ApiAutomationEndpointSnapshot endpoint, String result, String message) {
        return syncItem(endpoint, endpoint.diffStatus(), result, message);
    }

    private ApiAutomationSyncItemResponse syncItem(
            ApiAutomationEndpointSnapshot endpoint,
            String beforeStatus,
            String result,
            String message
    ) {
        return new ApiAutomationSyncItemResponse(
                endpoint.id(),
                endpoint.assetApiId(),
                endpoint.httpMethod(),
                endpoint.path(),
                beforeStatus,
                result,
                message
        );
    }

    private Map<String, Integer> initializedCounts(List<String> keys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        keys.forEach(key -> counts.put(key, 0));
        return counts;
    }

    record SyncAttempt(
            ApiAutomationEndpointSnapshot snapshot,
            ApiAutomationSyncItemResponse response
    ) {
    }
}
