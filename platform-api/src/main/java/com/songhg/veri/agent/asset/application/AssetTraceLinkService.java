package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.request.CreateLinkRequest;
import com.songhg.veri.agent.asset.api.request.TraceLinkListRequest;
import com.songhg.veri.agent.asset.api.response.TraceLinkResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetTraceLinkService {

    private static final Logger log = LoggerFactory.getLogger(AssetTraceLinkService.class);

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;

    public AssetTraceLinkService(AssetRepository repository, AssetProjectAuditService projectAuditService) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
    }

    public PageResponse<TraceLinkResponse> listLinks(TraceLinkListRequest request) {
        List<TraceLinkResponse> filtered = repository.traceLinks(
                        request.getRequirementId(),
                        request.getApiId(),
                        request.getPageId(),
                        request.getFlowId(),
                        request.getCaseId()
                ).stream()
                .map(AssetTraceLinkService::toTraceLinkResponse)
                .toList();
        return page(filtered, request.getIndex(), request.getSize());
    }

    public TraceLinkResponse createLink(CreateLinkRequest request) {
        AssetRequirement requirement = repository.requirement(request.requirementId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "需求不存在: " + request.requirementId()));
        if (request.apiId() == null && request.pageId() == null && request.flowId() == null && request.caseId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "追踪链接至少需要一个目标资产");
        }
        validateApiBelongsToProject(request.apiId(), requirement.projectId());
        validatePageBelongsToProject(request.pageId(), requirement.projectId());
        validateBusinessFlowBelongsToProject(request.flowId(), requirement.projectId());
        validateTestCaseBelongsToProject(request.caseId(), requirement.projectId());
        UUID id = UUID.randomUUID();
        TraceLink link = new TraceLink(
                id,
                request.requirementId(),
                request.apiId(),
                request.pageId(),
                request.flowId(),
                request.caseId(),
                java.time.Instant.now()
        );
        projectAuditService.writeProjectAudit("CREATE", "TRACE_LINK", id, requirement.projectId());
        repository.saveTraceLink(link);
        log.info("Created trace link id={}, requirementId={}, trace_id={}",
                id, request.requirementId(), TraceContext.getTraceId());
        return toTraceLinkResponse(link);
    }

    private void validateApiBelongsToProject(UUID apiId, String projectId) {
        if (apiId == null) {
            return;
        }
        AssetApi api = repository.api(apiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API不存在: " + apiId));
        ensureSameProject("API", api.id(), api.projectId(), projectId);
    }

    private void validatePageBelongsToProject(UUID pageId, String projectId) {
        if (pageId == null) {
            return;
        }
        AssetPage page = repository.page(pageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "页面资产不存在: " + pageId));
        ensureSameProject("页面", page.id(), page.projectId(), projectId);
    }

    private void validateBusinessFlowBelongsToProject(UUID flowId, String projectId) {
        if (flowId == null) {
            return;
        }
        AssetBusinessFlow flow = repository.businessFlow(flowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "业务流资产不存在: " + flowId));
        ensureSameProject("业务流", flow.id(), flow.projectId(), projectId);
    }

    private void validateTestCaseBelongsToProject(UUID caseId, String projectId) {
        if (caseId == null) {
            return;
        }
        TestCaseRecord testCase = repository.testCase(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        ensureSameProject("测试用例", testCase.id(), testCase.projectId(), projectId);
    }

    private void ensureSameProject(String resourceName, UUID resourceId, String actualProjectId, String expectedProjectId) {
        if (!StringUtils.hasText(actualProjectId) || !actualProjectId.equals(expectedProjectId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    resourceName + "不属于当前项目: " + resourceId
            );
        }
    }

    private static TraceLinkResponse toTraceLinkResponse(TraceLink link) {
        return new TraceLinkResponse(
                link.id(),
                link.requirementId(),
                link.apiId(),
                link.pageId(),
                link.flowId(),
                link.caseId(),
                link.createdAt()
        );
    }

    private static <T> PageResponse<T> page(List<T> items, int index, int size) {
        int safeIndex = Math.max(0, index);
        int safeSize = Math.max(1, Math.min(size, 100));
        int from = Math.min(safeIndex * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        return PageResponse.of(items.subList(from, to), safeIndex, safeSize, items.size());
    }
}
