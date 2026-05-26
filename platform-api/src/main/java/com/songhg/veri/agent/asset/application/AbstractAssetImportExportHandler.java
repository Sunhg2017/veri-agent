package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 资产导入导出处理器基类，集中保留跨资产类型的项目归属校验和导出分页策略
 */
abstract class AbstractAssetImportExportHandler implements AssetImportExportHandler {

    protected final AssetRepository repository;
    protected final AssetProjectAuditService projectAuditService;
    protected final AssetService assetService;
    protected final AssetImportExportSupport support;

    AbstractAssetImportExportHandler(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService,
            AssetService assetService,
            AssetImportExportSupport support
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
        this.assetService = assetService;
        this.support = support;
    }

    protected void useFirstExportPage(AssetExportRequest request) {
        request.setIndex(0);
        request.setSize(100);
    }

    protected void validateRequirementBelongsToProject(UUID requirementId, String projectId) {
        if (requirementId == null) {
            return;
        }
        AssetRequirement requirement = repository.requirement(requirementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + requirementId));
        ensureSameProject("需求", requirement.id(), requirement.projectId(), projectId);
    }

    protected void validateApiBelongsToProject(UUID apiId, String projectId) {
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
}
