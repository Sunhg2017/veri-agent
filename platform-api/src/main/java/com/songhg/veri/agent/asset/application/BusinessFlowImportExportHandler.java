package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.ASSET_BUSINESS_FLOW;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FLOW_STATUSES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_JSON;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.PRIORITIES;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.appendCsvLine;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.requireImportField;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.rowValue;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.trimToNull;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.validateImportEnum;

import com.songhg.veri.agent.asset.application.command.CreateBusinessFlowRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.application.view.BusinessFlowResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务流导入导出处理器，负责 flowJson 校验和业务流 CSV/JSON 导出
 */
final class BusinessFlowImportExportHandler extends AbstractAssetImportExportHandler {

    BusinessFlowImportExportHandler(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetService assetService,
            AssetImportExportSupport support
    ) {
        super(repository, projectAuditService, assetService, support);
    }

    @Override
    public String assetType() {
        return ASSET_BUSINESS_FLOW;
    }

    @Override
    public ImportPlan planImport(String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建业务流资产");
    }

    @Override
    public AssetImportItemResponse importRow(String projectId, Map<String, String> row, ImportPlan plan) {
        BusinessFlowResponse saved = assetService.createBusinessFlow(new CreateBusinessFlowRequest(
                rowValue(row, "name"),
                trimToNull(rowValue(row, "description")),
                support.jsonCommandValue(rowValue(row, "flowJson")),
                rowValue(row, "priority"),
                projectId,
                rowValue(row, "status")
        ));
        return new AssetImportItemResponse(plan.row(), "CREATE", saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    @Override
    public String exportAssets(AssetExportRequest request, String format) {
        useFirstExportPage(request);
        List<BusinessFlowResponse> rows = assetService.listBusinessFlows(request).items();
        if (FORMAT_JSON.equals(format)) {
            return support.jsonString(rows.stream().map(BusinessFlowImportExportHandler::businessFlowExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,name,description,status,priority,projectId,flowJson,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.name(), row.description(), row.status(), row.priority(), row.projectId(),
                row.flowJson(), row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private List<String> validateImportRow(Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        requireImportField(row, "name", errors);
        validateImportEnum(row, "status", FLOW_STATUSES, errors);
        validateImportEnum(row, "priority", PRIORITIES, errors);
        support.validateJsonField(row, "flowJson", errors);
        return errors;
    }

    private static Map<String, Object> businessFlowExportMap(BusinessFlowResponse row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.code());
        item.put("name", row.name());
        item.put("description", row.description());
        item.put("status", row.status());
        item.put("priority", row.priority());
        item.put("projectId", row.projectId());
        item.put("flowJson", row.flowJson());
        item.put("lifecycleStatus", row.lifecycleStatus());
        item.put("createdAt", row.createdAt());
        item.put("updatedAt", row.updatedAt());
        return item;
    }
}
