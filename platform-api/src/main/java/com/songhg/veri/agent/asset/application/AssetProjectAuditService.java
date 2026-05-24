package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.port.PlatformContextClient;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
public class AssetProjectAuditService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final PlatformContextClient contextClient;

    public AssetProjectAuditService(PlatformContextClient contextClient) {
        this.contextClient = contextClient;
    }

    public String resolveProjectScopeId(String projectId) {
        return projectContext(projectId).projectId();
    }

    public void validateProjectWhenProvided(String projectId) {
        if (StringUtils.hasText(projectId)) {
            projectContext(projectId);
        }
    }

    public PlatformContextClient.ProjectContext projectContext(String projectId) {
        PlatformContextClient.ProjectContext context = contextClient.getProjectContext(projectId);
        if (!StringUtils.hasText(context.projectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目上下文不存在: " + projectId);
        }
        if (!STATUS_ACTIVE.equals(context.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许写入资产: " + projectId);
        }
        return context;
    }

    public void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId) {
        writeProjectAudit(action, resourceType, resourceId, projectId, "SUCCEEDED");
    }

    public void writeProjectAudit(String action, String resourceType, UUID resourceId, String projectId, String result) {
        String scopeId = StringUtils.hasText(projectId) ? projectContext(projectId).projectId() : null;
        contextClient.writeAuditEvent(action, resourceType, resourceId.toString(), scopeId, result);
    }

    public void writeAssetBatchAudit(String action, String resourceType, String projectId, String result) {
        String scopeId = StringUtils.hasText(projectId) ? projectContext(projectId).projectId() : null;
        contextClient.writeAuditEvent(action, resourceType, UUID.randomUUID().toString(), scopeId, result);
    }
}
