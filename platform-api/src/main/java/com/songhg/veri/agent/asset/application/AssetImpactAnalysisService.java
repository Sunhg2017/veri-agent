package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.api.response.AssetImpactAnalysisResponse;
import com.songhg.veri.agent.asset.api.response.AssetImpactNodeResponse;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetLifecycleStatus;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetImpactAnalysisService {

    private static final String SUBJECT_REQUIREMENT = "REQUIREMENT";
    private static final String SUBJECT_API = "API";
    private static final String SUBJECT_PAGE = "PAGE";
    private static final String SUBJECT_FLOW = "FLOW";
    private static final String SUBJECT_CASE = "CASE";
    private static final Set<String> SUBJECT_TYPES = Set.of(
            SUBJECT_REQUIREMENT,
            SUBJECT_API,
            SUBJECT_PAGE,
            SUBJECT_FLOW,
            SUBJECT_CASE
    );
    private static final Map<String, String> SUBJECT_TYPE_ALIASES = Map.of(
            "BUSINESS_FLOW", SUBJECT_FLOW,
            "TEST_CASE", SUBJECT_CASE
    );

    private final AssetRepository repository;
    private final AssetProjectAuditService projectAuditService;

    public AssetImpactAnalysisService(AssetRepository repository, AssetProjectAuditService projectAuditService) {
        this.repository = repository;
        this.projectAuditService = projectAuditService;
    }

    public AssetImpactAnalysisResponse analyzeImpact(String projectId, String rawSubjectType, UUID subjectId) {
        String scopeProjectId = projectAuditService.resolveProjectScopeId(projectId);
        String subjectType = subjectType(rawSubjectType);
        Map<UUID, AssetRequirement> requirements = mapById(repository.requirements(scopeProjectId), AssetRequirement::id);
        Map<UUID, AssetApi> apis = mapById(repository.apis(scopeProjectId), AssetApi::id);
        Map<UUID, AssetPage> pages = mapById(repository.pages(scopeProjectId), AssetPage::id);
        Map<UUID, AssetBusinessFlow> flows = mapById(repository.businessFlows(scopeProjectId), AssetBusinessFlow::id);
        Map<UUID, TestCaseRecord> cases = mapById(repository.testCases(scopeProjectId), TestCaseRecord::id);
        List<TraceLink> links = repository.traceLinks(null, null, null, null, null);
        ImpactSelection selection = selectImpactScope(subjectType, subjectId, requirements, apis, pages, flows, cases, links);

        List<String> gaps = impactGaps(selection, links, requirements, apis, pages, flows, cases);
        List<AssetImpactNodeResponse> requirementNodes = nodes(selection.requirementIds(), requirements, AssetImpactAnalysisService::toImpactNode);
        List<AssetImpactNodeResponse> apiNodes = nodes(selection.apiIds(), apis, AssetImpactAnalysisService::toImpactNode);
        List<AssetImpactNodeResponse> pageNodes = nodes(selection.pageIds(), pages, AssetImpactAnalysisService::toImpactNode);
        List<AssetImpactNodeResponse> flowNodes = nodes(selection.flowIds(), flows, AssetImpactAnalysisService::toImpactNode);
        List<AssetImpactNodeResponse> caseNodes = nodes(selection.caseIds(), cases, AssetImpactAnalysisService::toImpactNode);
        writeImpactAudit(scopeProjectId);
        return new AssetImpactAnalysisResponse(
                scopeProjectId,
                subjectType,
                subjectId,
                requirementNodes.size(),
                apiNodes.size(),
                pageNodes.size(),
                flowNodes.size(),
                caseNodes.size(),
                requirementNodes,
                apiNodes,
                pageNodes,
                flowNodes,
                caseNodes,
                gaps,
                Instant.now()
        );
    }

    private ImpactSelection selectImpactScope(
            String subjectType,
            UUID subjectId,
            Map<UUID, AssetRequirement> requirements,
            Map<UUID, AssetApi> apis,
            Map<UUID, AssetPage> pages,
            Map<UUID, AssetBusinessFlow> flows,
            Map<UUID, TestCaseRecord> cases,
            List<TraceLink> links
    ) {
        ImpactSelection selection = new ImpactSelection(
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>()
        );
        if (subjectType == null || subjectId == null) {
            selection.requirementIds().addAll(requirements.keySet());
            selection.apiIds().addAll(apis.keySet());
            selection.pageIds().addAll(pages.keySet());
            selection.flowIds().addAll(flows.keySet());
            selection.caseIds().addAll(cases.keySet());
        } else {
            addSubject(subjectType, subjectId, requirements, apis, pages, flows, cases, selection);
        }
        expandRelatedAssets(selection, requirements, apis, pages, flows, cases, links);
        return selection;
    }

    private void expandRelatedAssets(
            ImpactSelection selection,
            Map<UUID, AssetRequirement> requirements,
            Map<UUID, AssetApi> apis,
            Map<UUID, AssetPage> pages,
            Map<UUID, AssetBusinessFlow> flows,
            Map<UUID, TestCaseRecord> cases,
            List<TraceLink> links
    ) {
        boolean changed;
        do {
            changed = false;
            for (TraceLink link : links) {
                if (link.requirementId() == null || !requirements.containsKey(link.requirementId())) {
                    continue;
                }
                boolean related = selection.requirementIds().contains(link.requirementId())
                        || (link.apiId() != null && selection.apiIds().contains(link.apiId()))
                        || (link.pageId() != null && selection.pageIds().contains(link.pageId()))
                        || (link.flowId() != null && selection.flowIds().contains(link.flowId()))
                        || (link.caseId() != null && selection.caseIds().contains(link.caseId()));
                if (!related) {
                    continue;
                }
                changed |= selection.requirementIds().add(link.requirementId());
                if (link.apiId() != null && apis.containsKey(link.apiId())) {
                    changed |= selection.apiIds().add(link.apiId());
                }
                if (link.pageId() != null && pages.containsKey(link.pageId())) {
                    changed |= selection.pageIds().add(link.pageId());
                }
                if (link.flowId() != null && flows.containsKey(link.flowId())) {
                    changed |= selection.flowIds().add(link.flowId());
                }
                if (link.caseId() != null && cases.containsKey(link.caseId())) {
                    changed |= selection.caseIds().add(link.caseId());
                }
            }
        } while (changed);
    }

    private void writeImpactAudit(String projectId) {
        projectAuditService.writeAssetBatchAudit("IMPACT_ANALYSIS", "ASSET_IMPACT", projectId, "SUCCEEDED");
    }

    private static <T> Map<UUID, T> mapById(List<T> items, Function<T, UUID> idGetter) {
        Map<UUID, T> result = new LinkedHashMap<>();
        for (T item : items) {
            UUID id = idGetter.apply(item);
            if (id != null) {
                result.put(id, item);
            }
        }
        return result;
    }

    private static String subjectType(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim().toUpperCase(Locale.ROOT);
        String canonical = SUBJECT_TYPE_ALIASES.getOrDefault(value, value);
        if (!SUBJECT_TYPES.contains(canonical)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "assetType 不合法: " + rawValue);
        }
        return canonical;
    }

    private static void addSubject(
            String subjectType,
            UUID subjectId,
            Map<UUID, AssetRequirement> requirements,
            Map<UUID, AssetApi> apis,
            Map<UUID, AssetPage> pages,
            Map<UUID, AssetBusinessFlow> flows,
            Map<UUID, TestCaseRecord> cases,
            ImpactSelection selection
    ) {
        boolean exists = switch (subjectType) {
            case SUBJECT_REQUIREMENT -> selection.requirementIds().add(subjectId) && requirements.containsKey(subjectId);
            case SUBJECT_API -> selection.apiIds().add(subjectId) && apis.containsKey(subjectId);
            case SUBJECT_PAGE -> selection.pageIds().add(subjectId) && pages.containsKey(subjectId);
            case SUBJECT_FLOW -> selection.flowIds().add(subjectId) && flows.containsKey(subjectId);
            case SUBJECT_CASE -> selection.caseIds().add(subjectId) && cases.containsKey(subjectId);
            default -> false;
        };
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "影响分析资产不存在: " + subjectType + "/" + subjectId);
        }
    }

    private static List<String> impactGaps(
            ImpactSelection selection,
            List<TraceLink> links,
            Map<UUID, AssetRequirement> requirements,
            Map<UUID, AssetApi> apis,
            Map<UUID, AssetPage> pages,
            Map<UUID, AssetBusinessFlow> flows,
            Map<UUID, TestCaseRecord> cases
    ) {
        List<String> gaps = new ArrayList<>();
        for (UUID requirementId : selection.requirementIds()) {
            AssetRequirement requirement = requirements.get(requirementId);
            if (requirement == null) {
                continue;
            }
            boolean hasApi = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.apiId() != null && apis.containsKey(link.apiId()));
            boolean hasPage = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.pageId() != null && pages.containsKey(link.pageId()));
            boolean hasFlow = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.flowId() != null && flows.containsKey(link.flowId()));
            boolean hasCase = links.stream().anyMatch(link -> requirementId.equals(link.requirementId())
                    && link.caseId() != null && cases.containsKey(link.caseId()));
            if (!hasApi) {
                gaps.add("需求 " + requirement.code() + " 缺少 API 覆盖");
            }
            if (!hasPage) {
                gaps.add("需求 " + requirement.code() + " 缺少页面覆盖");
            }
            if (!hasFlow) {
                gaps.add("需求 " + requirement.code() + " 缺少业务流覆盖");
            }
            if (!hasCase) {
                gaps.add("需求 " + requirement.code() + " 缺少测试用例覆盖");
            }
        }
        for (UUID apiId : selection.apiIds()) {
            boolean linked = links.stream().anyMatch(link -> apiId.equals(link.apiId()));
            if (!linked && apis.containsKey(apiId)) {
                gaps.add("API " + apis.get(apiId).code() + " 未关联需求");
            }
        }
        for (UUID pageId : selection.pageIds()) {
            boolean linked = links.stream().anyMatch(link -> pageId.equals(link.pageId()));
            if (!linked && pages.containsKey(pageId)) {
                gaps.add("页面 " + pages.get(pageId).code() + " 未关联需求");
            }
        }
        for (UUID flowId : selection.flowIds()) {
            boolean linked = links.stream().anyMatch(link -> flowId.equals(link.flowId()));
            if (!linked && flows.containsKey(flowId)) {
                gaps.add("业务流 " + flows.get(flowId).code() + " 未关联需求");
            }
        }
        for (UUID caseId : selection.caseIds()) {
            boolean linked = links.stream().anyMatch(link -> caseId.equals(link.caseId()));
            if (!linked && cases.containsKey(caseId)) {
                gaps.add("测试用例 " + cases.get(caseId).code() + " 未关联需求");
            }
        }
        return gaps;
    }

    private static <T> List<AssetImpactNodeResponse> nodes(
            Set<UUID> ids,
            Map<UUID, T> source,
            Function<T, AssetImpactNodeResponse> mapper
    ) {
        return ids.stream()
                .map(source::get)
                .filter(Objects::nonNull)
                .map(mapper)
                .sorted(Comparator.comparing(AssetImpactNodeResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static AssetImpactNodeResponse toImpactNode(AssetRequirement value) {
        return new AssetImpactNodeResponse(
                SUBJECT_REQUIREMENT,
                value.id(),
                value.code(),
                value.title(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(AssetApi value) {
        return new AssetImpactNodeResponse(
                SUBJECT_API,
                value.id(),
                value.code(),
                value.summary(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(AssetPage value) {
        return new AssetImpactNodeResponse(
                SUBJECT_PAGE,
                value.id(),
                value.code(),
                value.name(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(AssetBusinessFlow value) {
        return new AssetImpactNodeResponse(
                SUBJECT_FLOW,
                value.id(),
                value.code(),
                value.name(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static AssetImpactNodeResponse toImpactNode(TestCaseRecord value) {
        return new AssetImpactNodeResponse(
                SUBJECT_CASE,
                value.id(),
                value.code(),
                value.title(),
                value.projectId(),
                value.status(),
                lifecycleStatus(value.lifecycleStatus(), value.deletedAt()),
                value.updatedAt()
        );
    }

    private static String lifecycleStatus(String lifecycleStatus, Instant deletedAt) {
        return AssetLifecycleStatus.normalize(lifecycleStatus, deletedAt);
    }

    private record ImpactSelection(
            LinkedHashSet<UUID> requirementIds,
            LinkedHashSet<UUID> apiIds,
            LinkedHashSet<UUID> pageIds,
            LinkedHashSet<UUID> flowIds,
            LinkedHashSet<UUID> caseIds
    ) {
    }
}
