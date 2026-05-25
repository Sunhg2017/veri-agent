package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_CSV;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_OPENAPI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.AssetImportRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetExportPayload;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.application.view.AssetImportResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 资产导入导出编排服务。
 *
 * <p>本类只负责资产类型/格式归一、批量审计、导入行分发和导出载荷封装；
 * 各资产类型的校验、计划、落库和导出格式由独立 handler 承接，避免入口类继续膨胀。
 */
@Service
public class AssetImportExportService {

    private final AssetProjectAuditService projectAuditService;
    private final AssetImportExportSupport support;
    private final Map<String, AssetImportExportHandler> handlers;

    public AssetImportExportService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            ObjectMapper objectMapper,
            AssetService assetService
    ) {
        this.projectAuditService = projectAuditService;
        this.support = new AssetImportExportSupport(objectMapper);
        this.handlers = Map.of(
                AssetFormatValidator.ASSET_REQUIREMENT,
                new RequirementImportExportHandler(repository, projectAuditService, assetService, support),
                AssetFormatValidator.ASSET_API,
                new ApiImportExportHandler(repository, projectAuditService, assetService, support),
                AssetFormatValidator.ASSET_PAGE,
                new PageImportExportHandler(repository, projectAuditService, assetService, support),
                AssetFormatValidator.ASSET_BUSINESS_FLOW,
                new BusinessFlowImportExportHandler(repository, projectAuditService, assetService, support),
                AssetFormatValidator.ASSET_TEST_CASE,
                new TestCaseImportExportHandler(repository, projectAuditService, assetService, support)
        );
    }

    public AssetImportResponse importAssets(AssetImportRequest request) {
        String assetType = importExportAssetType(request.assetType());
        String format = importExportFormat(assetType, request.format(), "导入");
        projectAuditService.validateProjectWhenProvided(request.projectId());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<Map<String, String>> rows = support.parseImportRows(format, request.content());
        projectAuditService.writeAssetBatchAudit(
                dryRun ? "IMPORT_DRY_RUN" : "IMPORT",
                "ASSET_" + assetType,
                request.projectId(),
                "SUCCEEDED"
        );

        AssetImportExportHandler handler = handlerFor(assetType);
        List<AssetImportItemResponse> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportPlan plan = handler.planImport(request.projectId(), rows.get(i), i + 1);
            items.add(dryRun || !"PLANNED".equals(plan.status())
                    ? plan.toResponse()
                    : applyImportPlan(handler, request.projectId(), rows.get(i), plan));
        }
        return toImportResponse(assetType, format, dryRun, rows.size(), items);
    }

    public AssetExportPayload exportAssets(AssetExportRequest request) {
        String assetType = importExportAssetType(request.getAssetType());
        String format = importExportFormat(assetType, request.getFormat(), "导出");
        String projectId = AssetImportExportSupport.trimToNull(request.getProjectId());
        projectAuditService.validateProjectWhenProvided(projectId);

        String content = handlerFor(assetType).exportAssets(request, format);
        projectAuditService.writeAssetBatchAudit("EXPORT", "ASSET_" + assetType, projectId, "SUCCEEDED");
        String extension = FORMAT_OPENAPI.equals(format) ? "json" : format.toLowerCase(Locale.ROOT);
        String contentType = FORMAT_CSV.equals(format) ? "text/csv;charset=UTF-8" : "application/json;charset=UTF-8";
        return new AssetExportPayload(
                "wp3-" + assetType.toLowerCase(Locale.ROOT).replace("_", "-") + "." + extension,
                contentType,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private AssetImportItemResponse applyImportPlan(
            AssetImportExportHandler handler,
            String projectId,
            Map<String, String> row,
            ImportPlan plan
    ) {
        try {
            return handler.importRow(projectId, row, plan);
        } catch (BusinessException exception) {
            return new AssetImportItemResponse(
                    plan.row(),
                    "FAILED",
                    null,
                    null,
                    "FAILED",
                    exception.getMessage(),
                    List.of(exception.getMessage())
            );
        }
    }

    private AssetImportExportHandler handlerFor(String assetType) {
        AssetImportExportHandler handler = handlers.get(assetType);
        if (handler == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "assetType 不合法: " + assetType);
        }
        return handler;
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

    private static String importExportAssetType(String rawValue) {
        return AssetFormatValidator.normalizeAssetType(rawValue);
    }

    private static String importExportFormat(String assetType, String rawValue, String operationName) {
        return AssetFormatValidator.normalizeFormat(assetType, rawValue, operationName);
    }
}
