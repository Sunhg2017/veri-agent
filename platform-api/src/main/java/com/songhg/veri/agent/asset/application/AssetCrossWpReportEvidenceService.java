package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.command.AssetReportEvidenceQuery;
import com.songhg.veri.agent.asset.application.port.AssetRepository;
import com.songhg.veri.agent.asset.application.view.AssetReportEvidenceResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetCrossWpReportEvidenceService {

    private static final int MAX_REPORT_REF_COUNT = 100;

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;

    public AssetCrossWpReportEvidenceService(
            AssetRepository repository,
            AssetProjectAuditService projectAuditService
    ) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
    }

    /**
     * Resolves WP3 aggregate evidence for WP10 without returning asset body text or trace identifier lists.
     *
     * <p>Every reference is validated against the normalized project scope before any evidence object is returned. The
     * response intentionally exposes only lifecycle/status/count signals; WP10 is expected to digest the stable refs
     * again before persistence.</p>
     */
    @Transactional(readOnly = true)
    public AssetReportEvidenceResponse reportEvidence(AssetReportEvidenceQuery query) {
        String projectId = projectAuditService.resolveProjectScopeId(query.projectId());
        ProjectAssetRefs projectAssetRefs = projectAssetRefs(projectId);
        List<TraceLink> traceLinks = projectTraceLinks(projectAssetRefs);
        return new AssetReportEvidenceResponse(
                projectId,
                boundedNullable(query.reportRef(), 128),
                boundedRefs(query.requirementRefs()).stream()
                        .map(ref -> requirementEvidence(ref, projectId, traceLinks))
                        .toList(),
                boundedRefs(query.apiRefs()).stream()
                        .map(ref -> apiEvidence(ref, projectId, traceLinks))
                        .toList(),
                boundedRefs(query.pageRefs()).stream()
                        .map(ref -> pageEvidence(ref, projectId, traceLinks))
                        .toList(),
                boundedRefs(query.businessFlowRefs()).stream()
                        .map(ref -> businessFlowEvidence(ref, projectId, traceLinks))
                        .toList(),
                boundedRefs(query.testCaseRefs()).stream()
                        .map(ref -> testCaseEvidence(ref, projectId, traceLinks))
                        .toList(),
                reportRedactionPolicy()
        );
    }

    private ProjectAssetRefs projectAssetRefs(String projectId) {
        return new ProjectAssetRefs(
                ids(repository.requirements(projectId), AssetRequirement::id),
                ids(repository.apis(projectId), AssetApi::id),
                ids(repository.pages(projectId), AssetPage::id),
                ids(repository.businessFlows(projectId), AssetBusinessFlow::id),
                ids(repository.testCases(projectId), TestCaseRecord::id)
        );
    }

    /**
     * Keeps trace coverage aggregate-only while preventing links from other project scopes from affecting counts.
     */
    private List<TraceLink> projectTraceLinks(ProjectAssetRefs refs) {
        return repository.traceLinks(null, null, null, null, null).stream()
                .filter(refs::matchesProjectScope)
                .toList();
    }

    private AssetReportEvidenceResponse.RequirementEvidence requirementEvidence(
            UUID ref,
            String projectId,
            List<TraceLink> traceLinks
    ) {
        AssetRequirement requirement = repository.requirement(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP3_REQUIREMENT_NOT_FOUND"));
        assertSameProject(requirement.projectId(), projectId);
        List<TraceLink> related = traceLinks.stream()
                .filter(link -> ref.equals(link.requirementId()))
                .toList();
        return new AssetReportEvidenceResponse.RequirementEvidence(
                requirement.id(),
                requirement.status(),
                requirement.priority(),
                requirement.version(),
                requirement.lifecycleStatus(),
                tagCount(requirement.tags()),
                related.size(),
                related.stream().filter(link -> link.apiId() != null).count(),
                related.stream().filter(link -> link.pageId() != null).count(),
                related.stream().filter(link -> link.flowId() != null).count(),
                related.stream().filter(link -> link.caseId() != null).count(),
                requirement.updatedAt()
        );
    }

    private AssetReportEvidenceResponse.ApiEvidence apiEvidence(
            UUID ref,
            String projectId,
            List<TraceLink> traceLinks
    ) {
        AssetApi api = repository.api(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP3_API_NOT_FOUND"));
        assertSameProject(api.projectId(), projectId);
        List<TraceLink> related = traceLinks.stream()
                .filter(link -> ref.equals(link.apiId()))
                .toList();
        return new AssetReportEvidenceResponse.ApiEvidence(
                api.id(),
                api.status(),
                api.lifecycleStatus(),
                api.httpMethod(),
                StringUtils.hasText(api.version()) ? 1 : 0,
                related.size(),
                related.stream().filter(link -> link.requirementId() != null).count(),
                related.stream().filter(link -> link.caseId() != null).count(),
                api.updatedAt()
        );
    }

    private AssetReportEvidenceResponse.PageEvidence pageEvidence(
            UUID ref,
            String projectId,
            List<TraceLink> traceLinks
    ) {
        AssetPage page = repository.page(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP3_PAGE_NOT_FOUND"));
        assertSameProject(page.projectId(), projectId);
        List<TraceLink> related = traceLinks.stream()
                .filter(link -> ref.equals(link.pageId()))
                .toList();
        return new AssetReportEvidenceResponse.PageEvidence(
                page.id(),
                page.status(),
                page.lifecycleStatus(),
                StringUtils.hasText(page.sourceVersion()) ? 1 : 0,
                related.size(),
                related.stream().filter(link -> link.requirementId() != null).count(),
                related.stream().filter(link -> link.caseId() != null).count(),
                page.updatedAt()
        );
    }

    private AssetReportEvidenceResponse.BusinessFlowEvidence businessFlowEvidence(
            UUID ref,
            String projectId,
            List<TraceLink> traceLinks
    ) {
        AssetBusinessFlow flow = repository.businessFlow(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP3_BUSINESS_FLOW_NOT_FOUND"));
        assertSameProject(flow.projectId(), projectId);
        List<TraceLink> related = traceLinks.stream()
                .filter(link -> ref.equals(link.flowId()))
                .toList();
        return new AssetReportEvidenceResponse.BusinessFlowEvidence(
                flow.id(),
                flow.status(),
                flow.priority(),
                flow.lifecycleStatus(),
                related.size(),
                related.stream().filter(link -> link.requirementId() != null).count(),
                related.stream().filter(link -> link.caseId() != null).count(),
                flow.updatedAt()
        );
    }

    private AssetReportEvidenceResponse.TestCaseEvidence testCaseEvidence(
            UUID ref,
            String projectId,
            List<TraceLink> traceLinks
    ) {
        TestCaseRecord testCase = repository.testCase(ref)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "WP3_TEST_CASE_NOT_FOUND"));
        assertSameProject(testCase.projectId(), projectId);
        return new AssetReportEvidenceResponse.TestCaseEvidence(
                testCase.id(),
                testCase.status(),
                testCase.priority(),
                testCase.version(),
                testCase.lifecycleStatus(),
                tagCount(testCase.tags()),
                testCase.steps() == null ? 0 : testCase.steps().size(),
                testCase.requirementId(),
                testCase.apiId(),
                traceLinks.stream().filter(link -> ref.equals(link.caseId())).count(),
                testCase.updatedAt()
        );
    }

    private List<UUID> boundedRefs(List<UUID> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        refs.stream().filter(ref -> ref != null).forEach(deduplicated::add);
        List<UUID> normalized = deduplicated.stream().toList();
        if (normalized.size() > MAX_REPORT_REF_COUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "WP3_REPORT_EVIDENCE_REF_LIMIT_EXCEEDED");
        }
        return normalized;
    }

    private void assertSameProject(String actualProjectId, String expectedProjectId) {
        if (!expectedProjectId.equals(actualProjectId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "WP3_REPORT_EVIDENCE_NOT_FOUND");
        }
    }

    private int tagCount(String tags) {
        if (!StringUtils.hasText(tags)) {
            return 0;
        }
        return (int) List.of(tags.split(",")).stream()
                .filter(StringUtils::hasText)
                .count();
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private Map<String, Object> reportRedactionPolicy() {
        return Map.of(
                "aggregateOnly", true,
                "assetBodyReturned", false,
                "assetIdentifierListReturned", false,
                "traceIdentifierListReturned", false,
                "crossWpTableAccessAllowed", false
        );
    }

    private <T> Set<UUID> ids(List<T> values, Function<T, UUID> mapper) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(mapper)
                .filter(ref -> ref != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record ProjectAssetRefs(
            Set<UUID> requirementIds,
            Set<UUID> apiIds,
            Set<UUID> pageIds,
            Set<UUID> flowIds,
            Set<UUID> caseIds
    ) {

        boolean matchesProjectScope(TraceLink link) {
            return contains(requirementIds, link.requirementId())
                    && contains(apiIds, link.apiId())
                    && contains(pageIds, link.pageId())
                    && contains(flowIds, link.flowId())
                    && contains(caseIds, link.caseId());
        }

        private boolean contains(Set<UUID> refs, UUID ref) {
            return ref == null || refs.contains(ref);
        }
    }
}
