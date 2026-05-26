package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.ASSET_PAGE;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_JSON;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.PAGE_SOURCES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.PAGE_SOURCE_MANUAL;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.PAGE_STATUSES;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.appendCsvLine;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.requireImportField;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.rowValue;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.trimToNull;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.validateImportEnum;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.valueIn;

import com.songhg.veri.agent.asset.application.command.CreatePageRequest;
import com.songhg.veri.agent.asset.application.command.UpdatePageRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.application.view.PageResponse;
import com.songhg.veri.agent.asset.domain.AssetPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 页面导入导出处理器，负责原型 sourceRef 幂等导入和组件树 JSON 标准化
 */
final class PageImportExportHandler extends AbstractAssetImportExportHandler {

    PageImportExportHandler(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetService assetService,
            AssetImportExportSupport support
    ) {
        super(repository, projectAuditService, assetService, support);
    }

    @Override
    public String assetType() {
        return ASSET_PAGE;
    }

    @Override
    public ImportPlan planImport(String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return planPageImport(projectId, rowNumber, row);
    }

    @Override
    public AssetImportItemResponse importRow(String projectId, Map<String, String> row, ImportPlan plan) {
        ImportPlan before = planPageImport(projectId, plan.row(), row);
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
        PageResponse saved = switch (before.action()) {
            case "UPDATE" -> assetService.updatePage(before.id(), pageUpdateRequest(row));
            default -> assetService.createPage(pageCreateRequest(projectId, row));
        };
        return new AssetImportItemResponse(plan.row(), before.action(), saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    @Override
    public String exportAssets(AssetExportRequest request, String format) {
        useFirstExportPage(request);
        List<PageResponse> rows = assetService.listPages(request).items();
        if (FORMAT_JSON.equals(format)) {
            return support.jsonString(rows.stream().map(PageImportExportHandler::pageExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,name,urlPattern,status,projectId,source,sourceRef,sourceVersion,componentTree,screenshotUrl,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.name(), row.urlPattern(), row.status(), row.projectId(),
                row.source(), row.sourceRef(), row.sourceVersion(), row.componentTree(), row.screenshotUrl(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private ImportPlan planPageImport(String projectId, int rowNumber, Map<String, String> row) {
        String sourceRef = trimToNull(rowValue(row, "sourceRef"));
        if (sourceRef == null) {
            return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建页面资产");
        }
        Optional<AssetPage> existing = repository.pageBySourceRef(projectId, pageImportSource(row), sourceRef);
        if (existing.isEmpty()) {
            return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建页面资产");
        }
        AssetPage merged = mergeImportedPage(existing.get(), row, Instant.now());
        if (samePage(existing.get(), merged)) {
            return ImportPlan.planned(rowNumber, "LINK_EXISTING", existing.get().id(), existing.get().code(), "无差异，复用既有页面");
        }
        return ImportPlan.planned(rowNumber, "UPDATE", existing.get().id(), existing.get().code(), "将更新既有页面资产");
    }

    private CreatePageRequest pageCreateRequest(String projectId, Map<String, String> row) {
        return new CreatePageRequest(
                rowValue(row, "name"),
                trimToNull(rowValue(row, "urlPattern")),
                pageImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "sourceVersion")),
                support.jsonCommandValue(rowValue(row, "componentTree")),
                trimToNull(rowValue(row, "screenshotUrl")),
                projectId,
                rowValue(row, "status")
        );
    }

    private UpdatePageRequest pageUpdateRequest(Map<String, String> row) {
        return new UpdatePageRequest(
                rowValue(row, "name"),
                trimToNull(rowValue(row, "urlPattern")),
                pageImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "sourceVersion")),
                support.jsonCommandValue(rowValue(row, "componentTree")),
                trimToNull(rowValue(row, "screenshotUrl")),
                rowValue(row, "status")
        );
    }

    private AssetPage mergeImportedPage(AssetPage existing, Map<String, String> row, Instant now) {
        return new AssetPage(
                existing.id(),
                existing.code(),
                rowValue(row, "name"),
                trimToNull(rowValue(row, "urlPattern")),
                pageImportSource(row),
                trimToNull(rowValue(row, "sourceRef")),
                trimToNull(rowValue(row, "sourceVersion")),
                support.jsonCommandString(rowValue(row, "componentTree")),
                trimToNull(rowValue(row, "screenshotUrl")),
                existing.projectId(),
                valueIn(rowValue(row, "status"), existing.status(), PAGE_STATUSES, "status"),
                existing.lifecycleStatus(),
                existing.archivedAt(),
                existing.deletedAt(),
                existing.createdAt(),
                now
        );
    }

    private boolean samePage(AssetPage left, AssetPage right) {
        return Objects.equals(left.name(), right.name())
                && Objects.equals(left.urlPattern(), right.urlPattern())
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceRef(), right.sourceRef())
                && Objects.equals(left.sourceVersion(), right.sourceVersion())
                && Objects.equals(support.jsonNode(left.componentTree()), support.jsonNode(right.componentTree()))
                && Objects.equals(left.screenshotUrl(), right.screenshotUrl())
                && Objects.equals(left.status(), right.status());
    }

    private List<String> validateImportRow(Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        requireImportField(row, "name", errors);
        validateImportEnum(row, "source", PAGE_SOURCES, errors);
        validateImportEnum(row, "status", PAGE_STATUSES, errors);
        support.validateJsonField(row, "componentTree", errors);
        return errors;
    }

    private static Map<String, Object> pageExportMap(PageResponse row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("name", row.name());
        item.put("urlPattern", row.urlPattern());
        item.put("status", row.status());
        item.put("projectId", row.projectId());
        item.put("source", row.source());
        item.put("sourceRef", row.sourceRef());
        item.put("sourceVersion", row.sourceVersion());
        item.put("componentTree", row.componentTree());
        item.put("screenshotUrl", row.screenshotUrl());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
    }

    private static String pageImportSource(Map<String, String> row) {
        return valueIn(rowValue(row, "source"), PAGE_SOURCE_MANUAL, PAGE_SOURCES, "source");
    }
}
