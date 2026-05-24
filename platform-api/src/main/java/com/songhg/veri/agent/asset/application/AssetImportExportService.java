package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.util.CsvEncoder;
import com.songhg.veri.agent.asset.application.command.AssetImportRequest;
import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.ApiResponseDTO;
import com.songhg.veri.agent.asset.application.view.AssetExportPayload;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.application.view.AssetImportResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
public class AssetImportExportService {

    private static final Logger log = LoggerFactory.getLogger(AssetImportExportService.class);
    private static final String ASSET_REQUIREMENT = "REQUIREMENT";
    private static final String ASSET_API = "API";
    private static final String ASSET_TEST_CASE = "TEST_CASE";
    private static final String FORMAT_CSV = "CSV";
    private static final String FORMAT_JSON = "JSON";
    private static final String FORMAT_OPENAPI = "OPENAPI";
    private static final String SOURCE_IMPORT = "IMPORT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> API_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED", "REMOVED");
    private static final Set<String> API_SOURCES = Set.of(FORMAT_OPENAPI, "MANUAL", SOURCE_IMPORT);
    private static final Set<String> API_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> IMPORT_EXPORT_FORMATS = Set.of(FORMAT_CSV, FORMAT_JSON, FORMAT_OPENAPI);
    private static final Map<String, Set<String>> IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE = Map.of(
            ASSET_REQUIREMENT, Set.of(FORMAT_CSV, FORMAT_JSON),
            ASSET_API, IMPORT_EXPORT_FORMATS,
            ASSET_TEST_CASE, Set.of(FORMAT_CSV, FORMAT_JSON)
    );
    private static final Set<String> IMPORT_EXPORT_ASSET_TYPES = IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE.keySet();

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;
    private final ObjectMapper objectMapper;
    private final AssetService assetService;

    public AssetImportExportService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            ObjectMapper objectMapper,
            AssetService assetService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.objectMapper = objectMapper;
        this.assetService = assetService;
    }

    public AssetImportResponse importAssets(AssetImportRequest request) {
        String assetType = importExportAssetType(request.assetType());
        String format = importExportFormat(assetType, request.format(), "导入");
        projectAuditService.validateProjectWhenProvided(request.projectId());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<Map<String, String>> rows = parseImportRows(format, request.content());
        projectAuditService.writeAssetBatchAudit(
                dryRun ? "IMPORT_DRY_RUN" : "IMPORT",
                "ASSET_" + assetType,
                request.projectId(),
                "SUCCEEDED"
        );
        List<AssetImportItemResponse> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportPlan plan = planImportRow(assetType, request.projectId(), rows.get(i), i + 1);
            items.add(dryRun || !"PLANNED".equals(plan.status())
                    ? plan.toResponse()
                    : applyImportPlan(assetType, request.projectId(), rows.get(i), plan));
        }
        return toImportResponse(assetType, format, dryRun, rows.size(), items);
    }

    public AssetExportPayload exportAssets(AssetExportRequest request) {
        String assetType = importExportAssetType(request.getAssetType());
        String format = importExportFormat(assetType, request.getFormat(), "导出");
        String projectId = trimToNull(request.getProjectId());
        projectAuditService.validateProjectWhenProvided(projectId);
        String content = switch (assetType) {
            case ASSET_REQUIREMENT -> exportRequirements(request, format);
            case ASSET_API -> exportApis(request, format);
            case ASSET_TEST_CASE -> exportTestCases(request, format);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "assetType 不合法: " + assetType);
        };
        projectAuditService.writeAssetBatchAudit("EXPORT", "ASSET_" + assetType, projectId, "SUCCEEDED");
        String extension = FORMAT_OPENAPI.equals(format) ? "json" : format.toLowerCase(Locale.ROOT);
        String contentType = FORMAT_CSV.equals(format) ? "text/csv;charset=UTF-8" : "application/json;charset=UTF-8";
        return new AssetExportPayload(
                "wp3-" + assetType.toLowerCase(Locale.ROOT).replace("_", "-") + "." + extension,
                contentType,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private List<Map<String, String>> parseImportRows(String format, String content) {
        if (FORMAT_CSV.equals(format)) {
            return parseCsv(content);
        }
        if (FORMAT_OPENAPI.equals(format)) {
            return parseOpenApi(content);
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode rows = root.isArray() ? root : root.path("items");
            if (!rows.isArray()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入内容必须是数组或包含 items 数组");
            }
            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode item : rows) {
                result.add(flattenJsonObject(item));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入内容格式不合法");
        }
    }

    private List<Map<String, String>> parseOpenApi(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String apiVersion = trimToNull(textOrNull(root.path("info").path("version")));
            JsonNode paths = root.path("paths");
            if (!paths.isObject()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI 内容缺少 paths");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            paths.fields().forEachRemaining(pathEntry -> pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
                if (!API_HTTP_METHODS.contains(method)) {
                    return;
                }
                JsonNode operation = methodEntry.getValue();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", pathEntry.getKey());
                row.put("httpMethod", method);
                row.put("summary", textOrDefault(operation.path("summary"), method + " " + pathEntry.getKey()));
                row.put("description", textOrNull(operation.path("description")));
                row.put("status", STATUS_ACTIVE);
                row.put("source", FORMAT_OPENAPI);
                row.put("sourceRef", openApiSourceRef(pathEntry.getKey(), method));
                row.put("version", apiVersion);
                row.put("requestSchema", openApiRequestSchema(operation));
                row.put("responseSchema", openApiResponseSchema(operation));
                rows.add(row);
            }));
            return rows;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI 导入内容格式不合法");
        }
    }

    private ImportPlan planImportRow(String assetType, String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(assetType, row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return switch (assetType) {
            case ASSET_REQUIREMENT -> planRequirementImport(projectId, rowNumber, row);
            case ASSET_API -> planApiImport(projectId, rowNumber, row);
            case ASSET_TEST_CASE -> planTestCaseImport(projectId, rowNumber, row);
            default -> ImportPlan.failed(rowNumber, "INVALID", "assetType 不合法", List.of("assetType 不合法"));
        };
    }

    private ImportPlan planRequirementImport(String projectId, int rowNumber, Map<String, String> row) {
        String sourceRef = trimToNull(rowValue(row, "sourceRef"));
        if (sourceRef != null) {
            Optional<AssetRequirement> existing = repository.requirementBySourceRef(projectId, SOURCE_IMPORT, sourceRef);
            if (existing.isPresent()) {
                AssetRequirement merged = mergeImportedRequirement(existing.get(), requirementImportRequest(projectId, row), Instant.now());
                if (sameRequirement(existing.get(), merged)) {
                    return ImportPlan.planned(rowNumber, "LINK_EXISTING", existing.get().id(), existing.get().code(), "无差异，复用既有需求");
                }
                if (!"DRAFT".equals(existing.get().status())) {
                    return ImportPlan.failed(
                            rowNumber,
                            "CONFLICT_REVIEW_REQUIRED",
                            "既有导入需求已进入评审或审批状态，需人工处理差异后再更新",
                            List.of("status=" + existing.get().status())
                    );
                }
                return ImportPlan.planned(rowNumber, "UPDATE", existing.get().id(), existing.get().code(), "将更新既有 DRAFT 需求");
            }
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建需求");
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

    private ImportPlan planTestCaseImport(String projectId, int rowNumber, Map<String, String> row) {
        UUID requirementId = uuidOrNull(rowValue(row, "requirementId"));
        UUID apiId = uuidOrNull(rowValue(row, "apiId"));
        try {
            validateRequirementBelongsToProject(requirementId, projectId);
            validateApiBelongsToProject(apiId, projectId);
        } catch (BusinessException e) {
            return ImportPlan.failed(rowNumber, "INVALID", "关联资产校验失败", List.of(e.getMessage()));
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建测试用例");
    }

    private AssetImportItemResponse applyImportPlan(
            String assetType,
            String projectId,
            Map<String, String> row,
            ImportPlan plan
    ) {
        try {
            return switch (assetType) {
                case ASSET_REQUIREMENT -> importRequirement(projectId, row, plan.row());
                case ASSET_API -> importApi(projectId, row, plan.row());
                case ASSET_TEST_CASE -> importTestCase(projectId, row, plan.row());
                default -> plan.toResponse();
            };
        } catch (BusinessException e) {
            return new AssetImportItemResponse(plan.row(), "FAILED", null, null, "FAILED", e.getMessage(), List.of(e.getMessage()));
        }
    }

    private AssetImportItemResponse importRequirement(String projectId, Map<String, String> row, int rowNumber) {
        ImportPlan before = planRequirementImport(projectId, rowNumber, row);
        if (!"PLANNED".equals(before.status())) {
            return before.toResponse();
        }
        RequirementResponse saved = assetService.createRequirement(requirementImportRequest(projectId, row));
        ImportPlan after = planRequirementImport(projectId, rowNumber, row);
        String action = "LINK_EXISTING".equals(after.action()) ? before.action() : after.action();
        if ("LINK_EXISTING".equals(action) && "CREATE".equals(before.action())) {
            action = "CREATE";
        }
        return new AssetImportItemResponse(rowNumber, action, saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    private AssetImportItemResponse importApi(String projectId, Map<String, String> row, int rowNumber) {
        ImportPlan before = planApiImport(projectId, rowNumber, row);
        if (!"PLANNED".equals(before.status())) {
            return before.toResponse();
        }
        if ("LINK_EXISTING".equals(before.action())) {
            return new AssetImportItemResponse(
                    rowNumber,
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
        return new AssetImportItemResponse(rowNumber, before.action(), saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    private AssetImportItemResponse importTestCase(String projectId, Map<String, String> row, int rowNumber) {
        TestCaseResponse saved = assetService.createTestCase(new CreateTestCaseRequest(
                rowValue(row, "title"),
                rowValue(row, "description"),
                uuidOrNull(rowValue(row, "requirementId")),
                uuidOrNull(rowValue(row, "apiId")),
                projectId,
                rowValue(row, "status"),
                rowValue(row, "priority"),
                rowValue(row, "tags"),
                parseImportSteps(rowValue(row, "steps"))
        ));
        return new AssetImportItemResponse(rowNumber, "CREATE", saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
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
                initialStatus(rowValue(row, "status"), STATUS_ACTIVE, "API"),
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
                initialStatus(rowValue(row, "status"), existing.status(), "API"),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
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

    private static boolean sameRequirement(AssetRequirement left, AssetRequirement right) {
        return Objects.equals(left.title(), right.title())
                && Objects.equals(left.description(), right.description())
                && Objects.equals(left.sourceUrl(), right.sourceUrl())
                && Objects.equals(left.acceptanceCriteria(), right.acceptanceCriteria())
                && Objects.equals(left.status(), right.status())
                && Objects.equals(left.priority(), right.priority())
                && Objects.equals(normalizedTags(left.tags()), normalizedTags(right.tags()));
    }

    private boolean sameApi(AssetApi left, AssetApi right) {
        return Objects.equals(left.summary(), right.summary())
                && Objects.equals(left.description(), right.description())
                && Objects.equals(left.httpMethod(), right.httpMethod())
                && Objects.equals(left.path(), right.path())
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceRef(), right.sourceRef())
                && Objects.equals(left.version(), right.version())
                && Objects.equals(jsonNode(left.requestSchema()), jsonNode(right.requestSchema()))
                && Objects.equals(jsonNode(left.responseSchema()), jsonNode(right.responseSchema()))
                && Objects.equals(left.status(), right.status());
    }

    private CreateRequirementRequest requirementImportRequest(String projectId, Map<String, String> row) {
        return new CreateRequirementRequest(
                rowValue(row, "title"),
                rowValue(row, "description"),
                rowValue(row, "status"),
                rowValue(row, "priority"),
                projectId,
                rowValue(row, "tags"),
                SOURCE_IMPORT,
                rowValue(row, "sourceRef"),
                rowValue(row, "sourceUrl"),
                rowValue(row, "acceptanceCriteria")
        );
    }

    private List<String> validateImportRow(String assetType, Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        switch (assetType) {
            case ASSET_REQUIREMENT -> {
                requireImportField(row, "title", errors);
                validateImportEnum(row, "status", REVIEW_STATUSES, errors);
                validateImportEnum(row, "priority", PRIORITIES, errors);
            }
            case ASSET_API -> {
                requireImportField(row, "summary", errors);
                requireImportField(row, "httpMethod", errors);
                requireImportField(row, "path", errors);
                validateImportEnum(row, "source", API_SOURCES, errors);
                validateImportEnum(row, "status", API_STATUSES, errors);
                validateImportEnum(row, "httpMethod", API_HTTP_METHODS, errors);
            }
            case ASSET_TEST_CASE -> {
                requireImportField(row, "title", errors);
                validateImportEnum(row, "status", REVIEW_STATUSES, errors);
                validateImportEnum(row, "priority", PRIORITIES, errors);
                validateUuidField(row, "requirementId", errors);
                validateUuidField(row, "apiId", errors);
            }
            default -> errors.add("assetType 不合法");
        }
        return errors;
    }

    private String exportRequirements(AssetExportRequest request, String format) {
        List<RequirementResponse> rows = exportRequirementRows(request);
        if (FORMAT_JSON.equals(format)) {
            return jsonString(rows.stream().map(AssetImportExportService::requirementExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,title,description,status,priority,projectId,source,sourceRef,sourceUrl,acceptanceCriteria,tags,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.title(), row.description(), row.status(), row.priority(), row.projectId(),
                row.source(), row.sourceRef(), row.sourceUrl(), row.acceptanceCriteria(), row.tags(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private String exportApis(AssetExportRequest request, String format) {
        List<ApiResponseDTO> rows = exportApiRows(request);
        if (FORMAT_OPENAPI.equals(format)) {
            return exportApisOpenApi(rows);
        }
        if (FORMAT_JSON.equals(format)) {
            return jsonString(rows.stream().map(AssetImportExportService::apiExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,summary,description,httpMethod,path,status,projectId,source,sourceRef,version,requestSchema,responseSchema,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.summary(), row.description(), row.httpMethod(), row.path(), row.status(),
                row.projectId(), row.source(), row.sourceRef(), row.version(), row.requestSchema(), row.responseSchema(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private String exportTestCases(AssetExportRequest request, String format) {
        List<TestCaseResponse> rows = exportTestCaseRows(request);
        if (FORMAT_JSON.equals(format)) {
            return jsonString(rows.stream().map(AssetImportExportService::testCaseExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,title,description,status,priority,projectId,requirementId,apiId,tags,steps,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.title(), row.description(), row.status(), row.priority(), row.projectId(),
                row.requirementId(), row.apiId(), row.tags(), jsonString(row.steps()),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private static AssetImportResponse toImportResponse(
            String assetType,
            String format,
            boolean dryRun,
            int totalRows,
            List<AssetImportItemResponse> items
    ) {
        int created = countAction(items, "CREATE");
        int updated = countAction(items, "UPDATE");
        int skipped = (int) items.stream().filter(item -> "LINK_EXISTING".equals(item.action())).count();
        int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status())).count();
        return new AssetImportResponse(assetType, format, dryRun, totalRows, created, updated, skipped, failed, items);
    }

    private static int countAction(List<AssetImportItemResponse> items, String action) {
        return (int) items.stream()
                .filter(item -> action.equals(item.action()))
                .filter(item -> !"FAILED".equals(item.status()))
                .count();
    }

    private List<RequirementResponse> exportRequirementRows(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
        return assetService.listRequirements(request).items();
    }

    private List<ApiResponseDTO> exportApiRows(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
        return assetService.listApis(request).items();
    }

    private List<TestCaseResponse> exportTestCaseRows(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
        return assetService.listTestCases(request).items();
    }

    private static List<Map<String, String>> parseCsv(String content) {
        List<String> lines = content.lines()
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = splitCsvLine(lines.getFirst()).stream()
                .map(String::trim)
                .toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), column < values.size() ? trimToNull(values.get(column)) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private Map<String, String> flattenJsonObject(JsonNode item) {
        if (!item.isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入数组元素必须是对象");
        }
        Map<String, String> row = new LinkedHashMap<>();
        item.fields().forEachRemaining(entry -> row.put(
                entry.getKey(),
                entry.getValue().isContainerNode() ? jsonString(entry.getValue()) : textOrNull(entry.getValue())
        ));
        return row;
    }

    private static void requireImportField(Map<String, String> row, String field, List<String> errors) {
        if (!StringUtils.hasText(rowValue(row, field))) {
            errors.add(field + " 不能为空");
        }
    }

    private static void validateImportEnum(
            Map<String, String> row,
            String field,
            Set<String> allowedValues,
            List<String> errors
    ) {
        String value = trimToNull(rowValue(row, field));
        if (value != null && !allowedValues.contains(value.toUpperCase(Locale.ROOT))) {
            errors.add(field + " 不合法: " + value);
        }
    }

    private static void validateUuidField(Map<String, String> row, String field, List<String> errors) {
        String value = trimToNull(rowValue(row, field));
        if (value == null) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            errors.add(field + " 不是合法 UUID");
        }
    }

    private static String rowValue(Map<String, String> row, String field) {
        if (row.containsKey(field)) {
            return row.get(field);
        }
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static UUID uuidOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : UUID.fromString(trimmed);
    }

    private static String defaultJson(String value) {
        return StringUtils.hasText(value) ? value : "{}";
    }

    private List<CreateTestCaseRequest.StepDto> parseImportSteps(String rawSteps) {
        if (!StringUtils.hasText(rawSteps)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(rawSteps);
            if (!root.isArray()) {
                return List.of(new CreateTestCaseRequest.StepDto(rawSteps, "待补充"));
            }
            List<CreateTestCaseRequest.StepDto> steps = new ArrayList<>();
            for (JsonNode item : root) {
                steps.add(new CreateTestCaseRequest.StepDto(
                        textOrDefault(item.path("action"), "待补充操作"),
                        textOrDefault(item.path("expectedResult"), "待补充预期")
                ));
            }
            return steps;
        } catch (JsonProcessingException e) {
            return List.of(new CreateTestCaseRequest.StepDto(rawSteps, "待补充"));
        }
    }

    private static Map<String, Object> requirementExportMap(RequirementResponse row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("title", row.title());
        item.put("description", row.description());
        item.put("status", row.status());
        item.put("priority", row.priority());
        item.put("projectId", row.projectId());
        item.put("source", row.source());
        item.put("sourceRef", row.sourceRef());
        item.put("sourceUrl", row.sourceUrl());
        item.put("acceptanceCriteria", row.acceptanceCriteria());
        item.put("tags", row.tags());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
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

    private static Map<String, Object> testCaseExportMap(TestCaseResponse row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("title", row.title());
        item.put("description", row.description());
        item.put("status", row.status());
        item.put("priority", row.priority());
        item.put("projectId", row.projectId());
        item.put("requirementId", row.requirementId());
        item.put("apiId", row.apiId());
        item.put("tags", row.tags());
        item.put("steps", row.steps());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
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
                        "content", Map.of("application/json", Map.of("schema", jsonNode(row.requestSchema())))
                ));
            }
            Map<String, Object> okResponse = new LinkedHashMap<>();
            okResponse.put("description", "OK");
            if (hasUsefulSchema(row.responseSchema())) {
                okResponse.put("content", Map.of("application/json", Map.of("schema", jsonNode(row.responseSchema()))));
            }
            operation.put("responses", Map.of("200", okResponse));
            pathItem.put(row.httpMethod().toLowerCase(Locale.ROOT), operation);
        }
        root.put("paths", paths);
        return jsonString(root);
    }

    private static String openApiSourceRef(String path, String method) {
        return "#/paths/" + openApiPointerSegment(path) + "/" + method.toLowerCase(Locale.ROOT);
    }

    private static String openApiPointerSegment(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String openApiRequestSchema(JsonNode operation) {
        JsonNode schema = operation.path("requestBody").path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? "{}" : jsonString(schema);
    }

    private String openApiResponseSchema(JsonNode operation) {
        JsonNode responses = operation.path("responses");
        if (!responses.isObject()) {
            return "{}";
        }
        for (String code : List.of("200", "201", "202", "default")) {
            String schema = openApiResponseSchemaByCode(responses.path(code));
            if (schema != null) {
                return schema;
            }
        }
        var fields = responses.fields();
        while (fields.hasNext()) {
            String schema = openApiResponseSchemaByCode(fields.next().getValue());
            if (schema != null) {
                return schema;
            }
        }
        return "{}";
    }

    private String openApiResponseSchemaByCode(JsonNode response) {
        JsonNode schema = response.path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? null : jsonString(schema);
    }

    private static boolean hasUsefulSchema(String schema) {
        return StringUtils.hasText(schema) && !"{}".equals(schema.trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void appendCsvLine(StringBuilder csv, Object... values) {
        CsvEncoder.appendLine(csv, values);
    }

    private static String importExportAssetType(String rawValue) {
        return valueIn(rawValue, null, IMPORT_EXPORT_ASSET_TYPES, "assetType");
    }

    private static String importExportFormat(String assetType, String rawValue, String operationName) {
        String format = valueIn(rawValue, FORMAT_CSV, IMPORT_EXPORT_FORMATS, "format");
        if (!IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE.getOrDefault(assetType, Set.of()).contains(format)) {
            if (FORMAT_OPENAPI.equals(format)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI " + operationName + "仅支持 API 资产");
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "format 不支持当前 assetType: " + format + "/" + assetType);
        }
        return format;
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

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产导入导出序列化失败");
        }
    }

    private void validateRequirementBelongsToProject(UUID requirementId, String projectId) {
        if (requirementId == null) {
            return;
        }
        AssetRequirement requirement = repository.requirement(requirementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + requirementId));
        ensureSameProject("需求", requirement.id(), requirement.projectId(), projectId);
    }

    private void validateApiBelongsToProject(UUID apiId, String projectId) {
        if (apiId == null) {
            return;
        }
        AssetApi api = repository.api(apiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + apiId));
        ensureSameProject("API", api.id(), api.projectId(), projectId);
    }

    private void ensureSameProject(String resourceName, UUID resourceId, String actualProjectId, String expectedProjectId) {
        if (!StringUtils.hasText(actualProjectId) || !actualProjectId.equals(expectedProjectId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    resourceName + "不属于当前项目: " + resourceId
            );
        }
    }

    private static String apiImportSource(Map<String, String> row) {
        return valueIn(rowValue(row, "source"), SOURCE_IMPORT, API_SOURCES, "source");
    }

    private static String initialStatus(String rawValue, String defaultValue, String resourceType) {
        return switch (resourceType) {
            case "API" -> valueIn(rawValue, defaultValue, API_STATUSES, "status");
            default -> valueIn(rawValue, defaultValue, REVIEW_STATUSES, "status");
        };
    }

    private static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
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

    private record ImportPlan(
            int row,
            String action,
            UUID id,
            String code,
            String status,
            String message,
            List<String> errors
    ) {
        private static ImportPlan planned(int row, String action, UUID id, String code, String message) {
            return new ImportPlan(row, action, id, code, "PLANNED", message, List.of());
        }

        private static ImportPlan failed(int row, String action, String message, List<String> errors) {
            return new ImportPlan(row, action, null, null, "FAILED", message, errors);
        }

        private AssetImportItemResponse toResponse() {
            return new AssetImportItemResponse(row, action, id, code, status, message, errors);
        }
    }
}
