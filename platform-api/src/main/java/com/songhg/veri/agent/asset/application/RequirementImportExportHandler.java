package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.ASSET_REQUIREMENT;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_JSON;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.PRIORITIES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.REVIEW_STATUSES;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.SOURCE_IMPORT;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.appendCsvLine;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.mergeTags;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.normalizedTags;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.requireImportField;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.rowValue;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.trimToNull;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.validateImportEnum;
import static com.songhg.veri.agent.asset.application.AssetImportExportSupport.valueIn;

import com.songhg.veri.agent.asset.application.command.CreateRequirementRequest;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 需求导入导出处理器，负责 sourceRef 幂等导入和 DRAFT 状态下的差异更新
 */
final class RequirementImportExportHandler extends AbstractAssetImportExportHandler {

    RequirementImportExportHandler(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetService assetService,
            AssetImportExportSupport support
    ) {
        super(repository, projectAuditService, assetService, support);
    }

    @Override
    public String assetType() {
        return ASSET_REQUIREMENT;
    }

    @Override
    public ImportPlan planImport(String projectId, Map<String, String> row, int rowNumber) {
        List<String> errors = validateImportRow(row);
        if (!errors.isEmpty()) {
            return ImportPlan.failed(rowNumber, "INVALID", "校验失败", errors);
        }
        return planRequirementImport(projectId, rowNumber, row);
    }

    @Override
    public AssetImportItemResponse importRow(String projectId, Map<String, String> row, ImportPlan plan) {
        ImportPlan before = planRequirementImport(projectId, plan.row(), row);
        if (!"PLANNED".equals(before.status())) {
            return before.toResponse();
        }
        RequirementResponse saved = assetService.createRequirement(requirementImportRequest(projectId, row));
        ImportPlan after = planRequirementImport(projectId, plan.row(), row);
        String action = "LINK_EXISTING".equals(after.action()) ? before.action() : after.action();
        if ("LINK_EXISTING".equals(action) && "CREATE".equals(before.action())) {
            action = "CREATE";
        }
        return new AssetImportItemResponse(plan.row(), action, saved.id(), saved.code(), "SUCCEEDED", "导入成功", List.of());
    }

    @Override
    public String exportAssets(AssetExportRequest request, String format) {
        useFirstExportPage(request);
        List<RequirementResponse> rows = assetService.listRequirements(request).items();
        if (FORMAT_JSON.equals(format)) {
            return support.jsonString(rows.stream().map(RequirementImportExportHandler::requirementExportMap).toList());
        }
        StringBuilder csv = new StringBuilder("code,title,description,status,priority,projectId,source,sourceRef,sourceUrl,acceptanceCriteria,tags,lifecycleStatus,createdAt,updatedAt\n");
        rows.forEach(row -> appendCsvLine(csv,
                row.code(), row.title(), row.description(), row.status(), row.priority(), row.projectId(),
                row.source(), row.sourceRef(), row.sourceUrl(), row.acceptanceCriteria(), row.tags(),
                row.lifecycleStatus(), row.createdAt(), row.updatedAt()));
        return csv.toString();
    }

    private ImportPlan planRequirementImport(String projectId, int rowNumber, Map<String, String> row) {
        String sourceRef = trimToNull(rowValue(row, "sourceRef"));
        if (sourceRef != null) {
            Optional<AssetRequirement> existing = repository.requirementBySourceRef(projectId, SOURCE_IMPORT, sourceRef);
            if (existing.isPresent()) {
                AssetRequirement merged = mergeImportedRequirement(
                        existing.get(),
                        requirementImportRequest(projectId, row),
                        Instant.now()
                );
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

    private List<String> validateImportRow(Map<String, String> row) {
        List<String> errors = new ArrayList<>();
        requireImportField(row, "title", errors);
        validateImportEnum(row, "status", REVIEW_STATUSES, errors);
        validateImportEnum(row, "priority", PRIORITIES, errors);
        return errors;
    }

    private static AssetRequirement mergeImportedRequirement(
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
}
