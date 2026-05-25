package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.ASSET_TEST_CASE;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_JSON;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.PRIORITIES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.REVIEW_STATUSES;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.appendCsvLine;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.requireImportField;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.rowValue;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.uuidOrNull;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.validateImportEnum;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.validateUuidField;

import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.application.view.TestCaseResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试用例导入导出处理器，负责关联需求/API 的项目归属校验和步骤 JSON 转换。
 */
final class TestCaseImportExportHandler extends AbstractAssetImportExportHandler {

    TestCaseImportExportHandler(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetService assetService,
            AssetImportExportSupport support
    ) {
        super(repository, projectAuditService, assetService, support);
    }

    @Override
    public String assetType() {
        return ASSET_TEST_CASE;
    }

    @Override
    public ImportPlan planImport(String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return planTestCaseImport(projectId, rowNumber, row);
    }

    @Override
    public AssetImportItemResponse importRow(String projectId, Map<String, String> row, ImportPlan plan) {
        TestCaseResponse saved = assetService.createTestCase(new CreateTestCaseRequest(
                rowValue(row, "title"),
                rowValue(row, "description"),
                uuidOrNull(rowValue(row, "requirementId")),
                uuidOrNull(rowValue(row, "apiId")),
                projectId,
                rowValue(row, "status"),
                rowValue(row, "priority"),
                rowValue(row, "tags"),
                support.parseImportSteps(rowValue(row, "steps"))
        ));
        return new AssetImportItemResponse(plan.row(), "CREATE", saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    @Override
    public String exportAssets(AssetExportRequest request, String format) {
        useFirstExportPage(request);
        List<TestCaseResponse> rows = assetService.listTestCases(request).items();
        if (FORMAT_JSON.equals(format)) {
            return support.jsonString(rows.stream().map(TestCaseImportExportHandler::testCaseExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,title,description,status,priority,projectId,requirementId,apiId,tags,steps,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.title(), row.description(), row.status(), row.priority(), row.projectId(),
                row.requirementId(), row.apiId(), row.tags(), support.jsonString(row.steps()),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private ImportPlan planTestCaseImport(String projectId, int rowNumber, Map<String, String> row) {
        UUID requirementId = uuidOrNull(rowValue(row, "requirementId"));
        UUID apiId = uuidOrNull(rowValue(row, "apiId"));
        try {
            validateRequirementBelongsToProject(requirementId, projectId);
            validateApiBelongsToProject(apiId, projectId);
        } catch (BusinessException exception) {
            return ImportPlan.failed(rowNumber, "INVALID", "关联资产校验失败", List.of(exception.getMessage()));
        }
        return ImportPlan.planned(rowNumber, "CREATE", null, null, "将创建测试用例");
    }

    private List<String> validateImportRow(Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        requireImportField(row, "title", errors);
        validateImportEnum(row, "status", REVIEW_STATUSES, errors);
        validateImportEnum(row, "priority", PRIORITIES, errors);
        validateUuidField(row, "requirementId", errors);
        validateUuidField(row, "apiId", errors);
        return errors;
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
}
