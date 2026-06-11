package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.command.SyncApiAutomationSpecCommand;
import com.songhg.veri.agent.apiautomation.application.parser.OpenApiParseResult;
import com.songhg.veri.agent.apiautomation.application.parser.ParsedOpenApiEndpoint;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecPageRequest;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationDiffResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationEndpointSnapshotResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationHealthResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncItemResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSyncResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecResponse;
import com.songhg.veri.agent.apiautomation.config.ApiAutomationProperties;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.openapi.OpenApiSpecParser;
import com.songhg.veri.agent.asset.application.AssetApiService;
import com.songhg.veri.agent.asset.application.command.SyncOpenApiRequest;
import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ApiAutomationService {

    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "UPLOAD", "URL");
    private static final int ERROR_SUMMARY_MAX_CHARS = 512;
    private static final int WP3_API_PATH_MAX_CHARS = 256;
    private static final int WP3_API_SUMMARY_MAX_CHARS = 256;
    private static final int WP3_API_VERSION_MAX_CHARS = 32;
    private static final int WP3_API_PAGE_SIZE = 100;
    private static final int WP3_API_PAGE_LIMIT = 20;
    private static final List<String> DIFF_STATUSES = List.of("NEW", "CHANGED", "MATCHED", "CONFLICT", "SKIPPED");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ApiAutomationRepository repository;
    private final OpenApiSpecParser parser;
    private final ApiAutomationProperties properties;
    private final ApiAutomationPlatformContextClient contextClient;
    private final ApiAutomationActorResolver actorResolver;
    private final AssetApiService assetApiService;
    private final ObjectMapper objectMapper;

    public ApiAutomationService(
            ApiAutomationRepository repository,
            OpenApiSpecParser parser,
            ApiAutomationProperties properties,
            ApiAutomationPlatformContextClient contextClient,
            ApiAutomationActorResolver actorResolver,
            AssetApiService assetApiService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.parser = parser;
        this.properties = properties;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.assetApiService = assetApiService;
        this.objectMapper = objectMapper;
    }

    public ApiAutomationHealthResponse health() {
        return new ApiAutomationHealthResponse(
                "api-automation",
                "UP",
                List.of("3.x"),
                properties.effectiveSpecMaxBytes(),
                properties.effectiveEndpointMaxCount(),
                properties.runnerEnabled(),
                properties.runnerTimeoutSeconds(),
                properties.runnerMaxCases(),
                properties.promptKey(),
                properties.modelFallbackEnabled(),
                Map.ofEntries(
                        Map.entry("parserVersion", OpenApiSpecParser.PARSER_VERSION),
                        Map.entry("sourceTypes", SOURCE_TYPES),
                        Map.entry("urlFetchEnabled", false),
                        Map.entry("rawRequestResponseStored", false),
                        Map.entry("secretExampleStored", false),
                        Map.entry("runnerDefaultDisabled", !properties.runnerEnabled()),
                        Map.entry("diffSyncReady", true),
                        Map.entry("generationReady", false),
                        Map.entry("aggregateOnly", true)
                )
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiAutomationSpecDetailResponse createSpec(CreateApiAutomationSpecCommand command) {
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        String content = command.content() == null ? "" : command.content();
        int contentSize = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentSize > properties.effectiveSpecMaxBytes()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "OPENAPI_TOO_LARGE: OpenAPI 内容超过上限 " + properties.effectiveSpecMaxBytes() + " bytes"
            );
        }
        String sourceType = normalizeSourceType(command.sourceType());
        String specDigest = sha256(content);
        ApiAutomationSpec existing = repository.activeSpecByProjectAndDigest(projectId, specDigest).orElse(null);
        if (existing != null) {
            return specDetail(existing.id());
        }

        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationSpec spec = new ApiAutomationSpec(
                UUID.randomUUID(),
                projectId,
                sourceType,
                sanitizeSourceRef(command.sourceRef()),
                boundedText(command.name(), 128),
                boundedNullableText(command.versionLabel(), 64),
                specDigest,
                contentSize,
                "{}",
                "{}",
                "UPLOADED",
                OpenApiSpecParser.PARSER_VERSION,
                0,
                null,
                actor,
                actor,
                null,
                now,
                now
        );
        repository.insertSpec(spec);
        return parseAndPersist(markParsing(spec, now, actor), content, actor);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiAutomationSpecResponse> specs(ApiAutomationSpecPageRequest request) {
        ApiAutomationSpecQuery query = canonicalProjectQuery(request.toQuery());
        List<ApiAutomationSpecResponse> items = repository.specs(query).stream()
                .map(this::toSpecResponse)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countSpecs(query));
    }

    @Transactional(readOnly = true)
    public ApiAutomationSpecDetailResponse specDetail(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        return toDetail(spec, repository.endpointSnapshots(id));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiAutomationSpecDetailResponse parseSpec(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        if ("ARCHIVED".equals(spec.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档的 OpenAPI 规格不可重新解析");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        ApiAutomationSpec parsingSpec = markParsing(spec, now, actor);
        repository.updateSpecParseResult(parsingSpec);
        return parseAndPersist(parsingSpec, parsingSpec.sanitizedSpecJson(), actor);
    }

    @Transactional
    public ApiAutomationDiffResponse diffSpec(UUID id) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        List<ApiAutomationEndpointSnapshot> endpoints = evaluateAndPersistDiff(spec);
        audit("api_automation.api_diffed", spec, "SUCCESS", Map.of(
                "counts", countDiffStatuses(endpoints),
                "endpointCount", endpoints.size()
        ));
        return new ApiAutomationDiffResponse(spec.id(), countDiffStatuses(endpoints), endpoints.stream()
                .map(this::toEndpointResponse)
                .toList());
    }

    @Transactional
    public ApiAutomationSyncResponse syncSpec(UUID id, SyncApiAutomationSpecCommand command) {
        ApiAutomationSpec spec = requireParsedSpec(id);
        SyncApiAutomationSpecCommand safeCommand = command == null
                ? new SyncApiAutomationSpecCommand(List.of(), true)
                : command;
        Set<UUID> selectedEndpointIds = safeCommand.endpointIdSet();
        List<ApiAutomationEndpointSnapshot> diffedEndpoints = evaluateAndPersistDiff(spec);
        List<ApiAutomationSyncItemResponse> items = new ArrayList<>();
        List<ApiAutomationEndpointSnapshot> updatedEndpoints = new ArrayList<>();
        Instant now = Instant.now();
        for (ApiAutomationEndpointSnapshot endpoint : diffedEndpoints) {
            if (!selectedEndpointIds.isEmpty() && !selectedEndpointIds.contains(endpoint.id())) {
                updatedEndpoints.add(endpoint);
                continue;
            }
            SyncAttempt attempt = syncEndpoint(spec, endpoint, safeCommand, now);
            items.add(attempt.response());
            updatedEndpoints.add(attempt.snapshot());
        }
        Map<String, Integer> counts = countSyncResults(items);
        audit("api_automation.api_synced", spec, syncResult(counts), Map.of(
                "counts", counts,
                "requestedEndpointCount", selectedEndpointIds.isEmpty() ? diffedEndpoints.size() : selectedEndpointIds.size()
        ));
        return new ApiAutomationSyncResponse(spec.id(), counts, items, updatedEndpoints.stream()
                .map(this::toEndpointResponse)
                .toList());
    }

    private ApiAutomationSpecDetailResponse parseAndPersist(ApiAutomationSpec spec, String content, String actor) {
        try {
            OpenApiParseResult parseResult = parser.parse(content, properties.effectiveEndpointMaxCount());
            Instant now = Instant.now();
            ApiAutomationSpec parsedSpec = new ApiAutomationSpec(
                    spec.id(),
                    spec.projectId(),
                    spec.sourceType(),
                    spec.sourceRef(),
                    spec.name(),
                    spec.versionLabel(),
                    spec.specDigest(),
                    spec.contentSizeBytes(),
                    parseResult.sanitizedSpecJson(),
                    writeJson(parseResult.summary()),
                    "PARSED",
                    OpenApiSpecParser.PARSER_VERSION,
                    parseResult.endpoints().size(),
                    null,
                    spec.createdBy(),
                    actor,
                    now,
                    spec.createdAt(),
                    now
            );
            repository.updateSpecParseResult(parsedSpec);
            repository.deleteEndpointSnapshots(spec.id());
            List<ApiAutomationEndpointSnapshot> snapshots = snapshots(parsedSpec, parseResult.endpoints(), now);
            snapshots.forEach(repository::insertEndpointSnapshot);
            audit("api_automation.spec.parsed", parsedSpec, "SUCCESS", Map.of(
                    "endpointCount", parsedSpec.endpointCount(),
                    "parserVersion", parsedSpec.parserVersion()
            ));
            return toDetail(parsedSpec, snapshots);
        } catch (BusinessException exception) {
            ApiAutomationSpec failedSpec = markParseFailed(spec, exception.getMessage(), actor);
            repository.updateSpecParseResult(failedSpec);
            audit("api_automation.spec.parse_failed", failedSpec, "FAILED", Map.of(
                    "errorCode", exception.getErrorCode().name(),
                    "errorSummary", failedSpec.parseErrorSummary()
            ));
            throw exception;
        }
    }

    private List<ApiAutomationEndpointSnapshot> snapshots(
            ApiAutomationSpec spec,
            List<ParsedOpenApiEndpoint> endpoints,
            Instant now
    ) {
        return endpoints.stream()
                .map(endpoint -> new ApiAutomationEndpointSnapshot(
                        UUID.randomUUID(),
                        spec.id(),
                        spec.projectId(),
                        endpoint.serviceName(),
                        endpoint.operationId(),
                        endpoint.httpMethod(),
                        endpoint.path(),
                        endpoint.summary(),
                        String.join(",", endpoint.tags()),
                        endpoint.parameterCount(),
                        endpoint.requestBodyPresent(),
                        String.join(",", endpoint.responseStatuses()),
                        endpoint.schemaDigest(),
                        "UNKNOWN",
                        null,
                        "{}",
                        null,
                        null,
                        null,
                        now,
                        now
                ))
                .toList();
    }

    private ApiAutomationSpec markParsing(ApiAutomationSpec spec, Instant now, String actor) {
        return new ApiAutomationSpec(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                spec.sanitizedSpecJson(),
                spec.parseSummaryJson(),
                "PARSING",
                OpenApiSpecParser.PARSER_VERSION,
                spec.endpointCount(),
                null,
                spec.createdBy(),
                actor,
                spec.parsedAt(),
                spec.createdAt(),
                now
        );
    }

    private ApiAutomationSpec markParseFailed(ApiAutomationSpec spec, String message, String actor) {
        Instant now = Instant.now();
        return new ApiAutomationSpec(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                "{}",
                writeJson(Map.of(
                        "parserVersion", OpenApiSpecParser.PARSER_VERSION,
                        "endpointCount", 0,
                        "parseFailed", true,
                        "aggregateOnly", true
                )),
                "PARSE_FAILED",
                OpenApiSpecParser.PARSER_VERSION,
                0,
                boundedText(message, ERROR_SUMMARY_MAX_CHARS),
                spec.createdBy(),
                actor,
                null,
                spec.createdAt(),
                now
        );
    }

    private List<ApiAutomationEndpointSnapshot> evaluateAndPersistDiff(ApiAutomationSpec spec) {
        Instant now = Instant.now();
        Map<String, List<ApiResponseDTO>> assetApisByKey = assetApisByKey(spec.projectId());
        List<ApiAutomationEndpointSnapshot> updated = repository.endpointSnapshots(spec.id()).stream()
                .map(endpoint -> diffEndpoint(endpoint, assetApisByKey, now))
                .toList();
        updated.forEach(repository::updateEndpointSnapshotDiff);
        return updated;
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
        List<ApiResponseDTO> matches = assetApisByKey.getOrDefault(assetKey(endpoint.httpMethod(), endpoint.path()), List.of());
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
                "assetSource", nullToEmpty(asset.source()),
                "assetSourceRef", nullToEmpty(asset.sourceRef())
        ), now, endpoint.syncedAt(), null);
    }

    private SyncAttempt syncEndpoint(
            ApiAutomationSpec spec,
            ApiAutomationEndpointSnapshot endpoint,
            SyncApiAutomationSpecCommand command,
            Instant now
    ) {
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
                    readSummary(endpoint.diffSummaryJson()), endpoint.lastDiffAt(), endpoint.syncedAt(),
                    boundedText(exception.getMessage(), ERROR_SUMMARY_MAX_CHARS));
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
                    readSummary(endpoint.diffSummaryJson()), endpoint.lastDiffAt(), endpoint.syncedAt(),
                    boundedText(exception.getMessage(), ERROR_SUMMARY_MAX_CHARS));
            repository.updateEndpointSnapshotDiff(failed);
            return new SyncAttempt(failed, syncItem(failed, "FAILED", failed.syncErrorSummary()));
        }
    }

    private SyncOpenApiRequest syncRequest(ApiAutomationSpec spec, ApiAutomationEndpointSnapshot endpoint, boolean create) {
        return new SyncOpenApiRequest(
                boundedText(StringUtils.hasText(endpoint.summary()) ? endpoint.summary() : endpoint.httpMethod() + " " + endpoint.path(),
                        WP3_API_SUMMARY_MAX_CHARS),
                boundedText("WP6 OpenAPI sync " + nullToEmpty(endpoint.serviceName()) + " " + nullToEmpty(endpoint.operationId()), 512),
                endpoint.httpMethod(),
                endpoint.path(),
                boundedText(endpoint.schemaDigest(), WP3_API_VERSION_MAX_CHARS),
                requestSchema(endpoint),
                responseSchema(endpoint),
                spec.projectId(),
                endpointSourceRef(endpoint),
                create ? "ACTIVE" : null
        );
    }

    private String requestSchema(ApiAutomationEndpointSnapshot endpoint) {
        return writeJson(Map.of(
                "wp6SchemaDigest", endpoint.schemaDigest(),
                "parameterCount", endpoint.parameterCount(),
                "requestBodyPresent", endpoint.requestBodyPresent(),
                "aggregateOnly", true
        ));
    }

    private String responseSchema(ApiAutomationEndpointSnapshot endpoint) {
        return writeJson(Map.of(
                "wp6SchemaDigest", endpoint.schemaDigest(),
                "responseStatuses", StringUtils.hasText(endpoint.responseStatuses()) ? endpoint.responseStatuses() : "",
                "aggregateOnly", true
        ));
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
                writeJson(diffSummary == null ? Map.of() : diffSummary),
                lastDiffAt,
                syncedAt,
                syncErrorSummary,
                endpoint.createdAt(),
                Instant.now()
        );
    }

    private Map<String, List<ApiResponseDTO>> assetApisByKey(String projectId) {
        List<ApiResponseDTO> assets = new ArrayList<>();
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
                asset -> assetKey(asset.httpMethod(), asset.path()),
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private Map<String, Integer> countDiffStatuses(List<ApiAutomationEndpointSnapshot> endpoints) {
        Map<String, Integer> counts = initializedCounts(DIFF_STATUSES);
        endpoints.forEach(endpoint -> counts.computeIfPresent(endpoint.diffStatus(), (key, value) -> value + 1));
        return counts;
    }

    private Map<String, Integer> countSyncResults(List<ApiAutomationSyncItemResponse> items) {
        Map<String, Integer> counts = initializedCounts(List.of("CREATED", "UPDATED", "MATCHED", "SKIPPED", "FAILED"));
        items.forEach(item -> counts.computeIfPresent(item.result(), (key, value) -> value + 1));
        return counts;
    }

    private Map<String, Integer> initializedCounts(List<String> keys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        keys.forEach(key -> counts.put(key, 0));
        return counts;
    }

    private String syncResult(Map<String, Integer> counts) {
        return counts.getOrDefault("FAILED", 0) > 0 ? "FAILED" : "SUCCESS";
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

    private ApiAutomationSpecDetailResponse toDetail(
            ApiAutomationSpec spec,
            List<ApiAutomationEndpointSnapshot> endpoints
    ) {
        return new ApiAutomationSpecDetailResponse(
                toSpecResponse(spec),
                readSummary(spec.parseSummaryJson()),
                endpoints.stream().map(this::toEndpointResponse).toList()
        );
    }

    private ApiAutomationSpecResponse toSpecResponse(ApiAutomationSpec spec) {
        return new ApiAutomationSpecResponse(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                spec.status(),
                spec.parserVersion(),
                spec.endpointCount(),
                spec.parseErrorSummary(),
                spec.parsedAt(),
                spec.createdAt(),
                spec.updatedAt()
        );
    }

    private ApiAutomationEndpointSnapshotResponse toEndpointResponse(ApiAutomationEndpointSnapshot snapshot) {
        return new ApiAutomationEndpointSnapshotResponse(
                snapshot.id(),
                snapshot.serviceName(),
                snapshot.operationId(),
                snapshot.httpMethod(),
                snapshot.path(),
                snapshot.summary(),
                snapshot.tags(),
                snapshot.parameterCount(),
                snapshot.requestBodyPresent(),
                snapshot.responseStatuses(),
                snapshot.schemaDigest(),
                snapshot.diffStatus(),
                snapshot.assetApiId(),
                readSummary(snapshot.diffSummaryJson()),
                snapshot.lastDiffAt(),
                snapshot.syncedAt(),
                snapshot.syncErrorSummary()
        );
    }

    private ApiAutomationSpec requireParsedSpec(UUID id) {
        ApiAutomationSpec spec = requireSpec(id);
        if (!"PARSED".equals(spec.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "OpenAPI 规格未完成解析，不能执行 diff/sync");
        }
        return spec;
    }

    private ApiAutomationSpec requireSpec(UUID id) {
        return repository.spec(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "OpenAPI 规格不存在: " + id));
    }

    private String assetKey(String httpMethod, String path) {
        return (httpMethod == null ? "" : httpMethod.trim().toUpperCase(Locale.ROOT)) + " " + nullToEmpty(path).trim();
    }

    private String assetSchemaDigest(ApiResponseDTO asset) {
        String requestDigest = schemaDigest(asset.requestSchema());
        return StringUtils.hasText(requestDigest) ? requestDigest : schemaDigest(asset.responseSchema());
    }

    private String schemaDigest(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return "";
        }
        try {
            Map<String, Object> value = objectMapper.readValue(schemaJson, MAP_TYPE);
            Object digest = value.get("wp6SchemaDigest");
            if (digest == null) {
                digest = value.get("schemaDigest");
            }
            return digest == null ? "" : digest.toString();
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private String endpointSourceRef(ApiAutomationEndpointSnapshot endpoint) {
        return "wp6:" + endpoint.specId() + ":" + endpoint.id();
    }

    private Map<String, Object> readSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("parseSummaryUnreadable", true, "aggregateOnly", true);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAPI 摘要序列化失败");
        }
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceType 仅支持 TEXT/UPLOAD/URL");
        }
        return normalized;
    }

    private ApiAutomationSpecQuery canonicalProjectQuery(ApiAutomationSpecQuery query) {
        if (!StringUtils.hasText(query.projectId())) {
            return query;
        }
        // 权限解析允许项目 ID 或编码，数据库过滤必须统一使用平台上下文返回的规范资源 ID。
        String projectId = contextClient.projectContext(query.projectId()).resourceId();
        return new ApiAutomationSpecQuery(projectId, query.status(), query.keyword(), query.limit(), query.offset());
    }

    private String sanitizeSourceRef(String sourceRef) {
        if (!StringUtils.hasText(sourceRef)) {
            return null;
        }
        String trimmed = sourceRef.trim();
        int queryIndex = trimmed.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        return boundedText(withoutQuery, 512);
    }

    private String boundedNullableText(String value, int maxLength) {
        return StringUtils.hasText(value) ? boundedText(value, maxLength) : null;
    }

    private String boundedText(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }

    private void audit(
            String action,
            ApiAutomationSpec spec,
            String result,
            Map<String, Object> afterJson
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(afterJson);
        payload.put("status", spec.status());
        payload.put("specId", spec.id().toString());
        contextClient.writeAuditEvent(
                action,
                "API_AUTOMATION_SPEC",
                spec.id().toString(),
                spec.projectId(),
                result,
                payload
        );
    }

    private record SyncAttempt(
            ApiAutomationEndpointSnapshot snapshot,
            ApiAutomationSyncItemResponse response
    ) {
    }
}
