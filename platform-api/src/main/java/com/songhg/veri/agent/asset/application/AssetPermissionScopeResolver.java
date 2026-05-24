package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.AssetListRequest;
import com.songhg.veri.agent.asset.api.request.TraceLinkListRequest;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves asset request ids to authorization resource scopes for controller annotations.
 */
@Service
public class AssetPermissionScopeResolver {

    private final AssetProjectAuditService projectAuditService;
    private final AssetService assetService;

    public AssetPermissionScopeResolver(
            AssetProjectAuditService projectAuditService,
            AssetService assetService
    ) {
        this.projectAuditService = projectAuditService;
        this.assetService = assetService;
    }

    public ResourceScope project(String projectId) {
        return ResourceScope.project(projectAuditService.resolveProjectScopeId(projectId));
    }

    public ResourceScope assetList(AssetListRequest request) {
        if (request != null && StringUtils.hasText(request.getProjectId())) {
            return project(request.getProjectId());
        }
        return ResourceScope.platform();
    }

    public ResourceScope requirement(UUID id) {
        return ResourceScope.project(assetService.requirementProjectScopeId(id));
    }

    public ResourceScope api(UUID id) {
        return ResourceScope.project(assetService.apiProjectScopeId(id));
    }

    public ResourceScope page(UUID id) {
        return ResourceScope.project(assetService.pageProjectScopeId(id));
    }

    public ResourceScope businessFlow(UUID id) {
        return ResourceScope.project(assetService.businessFlowProjectScopeId(id));
    }

    public ResourceScope testCase(UUID id) {
        return ResourceScope.project(assetService.testCaseProjectScopeId(id));
    }

    /**
     * Trace link queries can reference multiple asset types, so every referenced project scope is checked.
     */
    public List<ResourceScope> traceLinkList(TraceLinkListRequest request) {
        if (request == null || hasNoAssetFilter(request)) {
            return List.of(ResourceScope.platform());
        }
        List<ResourceScope> scopes = new ArrayList<>();
        addScope(scopes, request.getRequirementId(), this::requirement);
        addScope(scopes, request.getApiId(), this::api);
        addScope(scopes, request.getPageId(), this::page);
        addScope(scopes, request.getFlowId(), this::businessFlow);
        addScope(scopes, request.getCaseId(), this::testCase);
        return List.copyOf(scopes);
    }

    private boolean hasNoAssetFilter(TraceLinkListRequest request) {
        return request.getRequirementId() == null
                && request.getApiId() == null
                && request.getPageId() == null
                && request.getFlowId() == null
                && request.getCaseId() == null;
    }

    private void addScope(List<ResourceScope> scopes, UUID id, ScopeLookup lookup) {
        if (id != null) {
            scopes.add(lookup.resolve(id));
        }
    }

    @FunctionalInterface
    private interface ScopeLookup {

        ResourceScope resolve(UUID id);
    }
}
