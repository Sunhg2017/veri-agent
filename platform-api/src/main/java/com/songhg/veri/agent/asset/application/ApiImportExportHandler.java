package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.API_HTTP_METHODS;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.API_SOURCES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.API_STATUSES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.ASSET_API;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_JSON;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_OPENAPI;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.SOURCE_IMPORT;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.STATUS_ACTIVE;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.appendCsvLine;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.assetCode;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.castMap;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.defaultJson;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.hasUsefulSchema;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.requireImportField;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.rowValue;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.trimToNull;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.validateImportEnum;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.valueIn;

import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API 导入导出处理器，负责 OpenAPI 幂等导入、路径方法冲突判断和 OpenAPI 结构导出。
 */
final class ApiImportExportHandler extends AbstractAssetImportExportHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiImportExportHandler.class);

    ApiImportExportHandler(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetService assetService,
            AssetImportExportSupport support
    ) {
        super(repository, projectAuditService, assetService, support);
    }

    @Override
    public String assetType() {
        return ASSET_API;
    }

    @Override
    public ImportPlan planImport(String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return planApiImport(projectId, rowNumber, row);
    }

    @Override
    public AssetImportItemResponse importRow(String projectId, Map<String, String> row, ImportPlan plan) {
        ImportPlan before = planApiImport(projectId, plan.row(), row);
        if (!"PLANNED".equals(before.status())) {
            return before.toResponse();
        }
        if ("LINK_EXISTING".equals(before.action())) {
            return new AssetImportItemResponse(
                    plan.row(),
                    "LINK_EXISTING",
                    before.id(),
                    before.code(),
                    "SUCCEEDED",
                    "导入成功",
                    List.of()
            );
        }
        AssetApi saved = switch (before.action()) {
            case "UPDATE" -> updateImportedApi(before.id(), row);
            default -> createImportedApi(projectId, row);
        };
        return new AssetImportItemResponse(plan.row(), before.action(), saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    @Override
    public String exportAssets(AssetExportRequest request, String format) {
        useFirstExportPage(request);
        List<ApiResponseDTO> rows = assetService.listApis(request).items();
        if (FORMAT_OPENAPI.equals(format)) {
            return exportApisOpenApi(rows);
        }
        if (FORMAT_JSON.equals(format)) {
            return support.jsonString(rows.stream().map(ApiImportExportHandler::apiExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,summary,description,httpMethod,path,status,projectId,source,sourceRef,version,requestSchema,responseSchema,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.summary(), row.description(), row.httpMethod(), row.path(), row.status(),
                row.projectId(), row.source(), row.sourceRef(), row.version(), row.requestSchema(), row.responseSchema(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private ImportPlan planApiImport(String projectId, int rowNumber, Map<String, String> row) {
        String path = trimToNull(rowValue(row, "path"));
        String httpMethod = trimToNull(rowValue(row, "httpMethod"));
        Optional<AssetApi> existing = repository.apiByPath(projectId, path, httpMethod);
        if (existing.isPresent()) {
            if (!FORMAT_OPENAPI.equals(apiImportSource(row))) {
                return ImportPlan.failed(
                        rowNumber,
                        "CONFLICT_REVIEW_REQUIRED",
                        "同项目下 API 路径和方法已存在，需人工处理",
                        List.of("path/httpMethod 已存在")
                );
            }
            AssetApi merged = mergeImportedApi(existing.get(), row, Instant.now());
            if (sameApi(existing.get(), merged)) {
                return ImportPlan.planned(rowNumber, "LINK_EXISTING", existing.get().id(), existing.get().code(), "无差异，复用既有 API");
            }
            return ImportPlan.planned(rowNumber, "UPDATE", existing.get().id(), existing.get().code(), "将更新既有 API 资产");
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建 API 资产");
    }

    private AssetApi createImportedApi(String projectId, Map<String, String> row) {
        String scopeId = projectAuditService.resolveProjectScopeId(projectId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AssetApi api = new AssetApi(
                id,
                assetCode("API", id),
                rowValue(row, "summary"),
                trimToNull(rowValue(row, "description")),
                valueIn(rowValue(row, "httpMethod"), null, API_HTTP_METHODS, "httpMethod"),
                rowValue(row, "path"),
                apiImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "version")),
                defaultJson(rowValue(row, "requestSchema")),
                defaultJson(rowValue(row, "responseSchema")),
                projectId,
                initialStatus(rowValue(row, "status")),
                STATUS_ACTIVE,
                null,
                null,
                now,
                now
        );
        projectAuditService.writeProjectAudit("CREATE", "API", id, scopeId);
        AssetApi stored = repository.saveApi(api);
        log.info("Created imported api id={}, path={}, method={}, trace_id={}",
                id, api.path(), api.httpMethod(), TraceContext.getTraceId());
        return stored;
    }

    private AssetApi updateImportedApi(UUID id, Map<String, String> row) {
        AssetApi existing = repository.api(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + id));
        AssetApi merged = mergeImportedApi(existing, row, Instant.now());
        projectAuditService.writeProjectAudit("UPDATE", "API", id, existing.projectId());
        AssetApi stored = repository.saveApi(merged);
        log.info("Updated imported api id={}, path={}, method={}, trace_id={}",
                id, stored.path(), stored.httpMethod(), TraceContext.getTraceId());
        return stored;
    }

    private AssetApi mergeImportedApi(AssetApi existing, Map<String, String> row, Instant now) {
        return new AssetApi(
                existing.id(),
                existing.code(),
                rowValue(row, "summary"),
                trimToNull(rowValue(row, "description")),
                valueIn(rowValue(row, "httpMethod"), null, API_HTTP_METHODS, "httpMethod"),
                rowValue(row, "path"),
                apiImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "version")),
                defaultJson(rowValue(row, "requestSchema")),
                defaultJson(rowValue(row, "responseSchema")),
                existing.projectId(),
                valueIn(rowValue(row, "status"), existing.status(), API_STATUSES, "status"),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
    }

    private boolean sameApi(AssetApi left, AssetApi right) {
        return Objects.equals(left.summary(), right.summary())
                && Objects.equals(left.description(), right.description())
                && Objects.equals(left.httpMethod(), right.httpMethod())
                && Objects.equals(left.path(), right.path())
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceRef(), right.sourceRef())
                && Objects.equals(left.version(), right.version())
                && Objects.equals(support.jsonNode(left.requestSchema()), support.jsonNode(right.requestSchema()))
                && Objects.equals(support.jsonNode(left.responseSchema()), support.jsonNode(right.responseSchema()))
                && Objects.equals(left.status(), right.status());
    }

    private List<String> validateImportRow(Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        requireImportField(row, "summary", errors);
        requireImportField(row, "httpMethod", errors);
        requireImportField(row, "path", errors);
        validateImportEnum(row, "source", API_SOURCES, errors);
        validateImportEnum(row, "status", API_STATUSES, errors);
        validateImportEnum(row, "httpMethod", API_HTTP_METHODS, errors);
        return errors;
    }

    private String exportApisOpenApi(List<ApiResponseDTO> rows) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");
        root.put("info", Map.of("title", "WP3 API Assets", "version", "1.0.0"));
        Map<String, Object> paths = new LinkedHashMap<>();
        for (ApiResponseDTO row : rows) {
            Map<String, Object> pathItem = castMap(paths.computeIfAbsent(row.path(), ignored -> new LinkedHashMap<>()));
            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("summary", row.summary());
            operation.put("description", row.description());
            operation.put("operationId", row.code());
            operation.put("x-wp3-lifecycleStatus", row.lifecycleStatus());
            operation.put("x-wp3-version", row.version());
            operation.put("x-wp3-source", row.source());
            operation.put("x-wp3-sourceRef", row.sourceRef());
            if (hasUsefulSchema(row.requestSchema())) {
                operation.put("requestBody", Map.of(
                        "required", false,
                        "content", Map.of("application/json", Map.of("schema", support.jsonNode(row.requestSchema())))
                ));
            }
            Map<String, Object> okResponse = new LinkedHashMap<>();
            okResponse.put("description", "OK");
            if (hasUsefulSchema(row.responseSchema())) {
                okResponse.put("content", Map.of("application/json", Map.of("schema", support.jsonNode(row.responseSchema()))));
            }
            operation.put("responses", Map.of("200", okResponse));
            pathItem.put(row.httpMethod().toLowerCase(Locale.ROOT), operation);
        }
        root.put("paths", paths);
        return support.jsonString(root);
    }

    private static Map<String, Object> apiExportMap(ApiResponseDTO row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("summary", row.summary());
        item.put("description", row.description());
        item.put("httpMethod", row.httpMethod());
        item.put("path", row.path());
        item.put("status", row.status());
        item.put("projectId", row.projectId());
        item.put("source", row.source());
        item.put("sourceRef", row.sourceRef());
        item.put("version", row.version());
        item.put("requestSchema", row.requestSchema());
        item.put("responseSchema", row.responseSchema());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
    }

    private static String apiImportSource(Map<String, String> row) {
        return valueIn(rowValue(row, "source"), SOURCE_IMPORT, API_SOURCES, "source");
    }

    private static String initialStatus(String rawValue) {
        return valueIn(rawValue, STATUS_ACTIVE, API_STATUSES, "status");
    }
}
