package com.songhg.veri.agent.asset.infrastructure;

import com.songhg.veri.agent.asset.application.AssetRepository;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.AssetVersionHistory;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.asset.infrastructure.mapper.AssetMapper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Repository
public class PostgresAssetRepository implements AssetRepository {

    private final AssetMapper mapper;

    public PostgresAssetRepository(AssetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AssetRequirement> requirements(String projectId) {
        return mapper.listRequirements(projectId);
    }

    @Override
    public Optional<AssetRequirement> requirement(UUID id) {
        return Optional.ofNullable(mapper.getRequirement(id));
    }

    @Override
    public Optional<AssetRequirement> requirementIncludingInactive(UUID id) {
        return Optional.ofNullable(mapper.getRequirementIncludingInactive(id));
    }

    @Override
    public Optional<AssetRequirement> requirementBySourceRef(String projectId, String source, String sourceRef) {
        return Optional.ofNullable(mapper.getRequirementBySourceRef(projectId, source, sourceRef));
    }

    @Override
    public AssetRequirement saveRequirement(AssetRequirement requirement) {
        AssetRequirement stored = normalizeRequirement(requirement);
        try {
            if (mapper.getRequirement(stored.id()) == null) {
                if (mapper.getRequirementIncludingInactive(stored.id()) == null) {
                    mapper.insertRequirement(stored);
                } else {
                    mapper.updateRequirementLifecycle(stored);
                }
            } else {
                mapper.updateRequirement(stored);
            }
            return stored;
        } catch (DuplicateKeyException exception) {
            if (stored.source() != null && stored.sourceRef() != null) {
                return requirementBySourceRef(stored.projectId(), stored.source(), stored.sourceRef())
                        .orElseThrow(() -> exception);
            }
            throw exception;
        }
    }

    @Override
    public boolean hasActiveRequirementCodeConflict(String projectId, String code, UUID excludeId) {
        return mapper.countActiveRequirementCodeConflict(projectId, code, excludeId) > 0;
    }

    @Override
    public boolean hasActiveRequirementSourceRefConflict(String projectId, String source, String sourceRef, UUID excludeId) {
        if (projectId == null || source == null || sourceRef == null) {
            return false;
        }
        return mapper.countActiveRequirementSourceRefConflict(projectId, source, sourceRef, excludeId) > 0;
    }

    @Override
    public List<AssetVersionHistory> assetVersionHistory(String assetType, UUID assetId) {
        return mapper.listAssetVersionHistory(assetType, assetId);
    }

    @Override
    public AssetVersionHistory saveVersionHistory(AssetVersionHistory history) {
        mapper.insertAssetVersionHistory(history);
        return history;
    }

    @Override
    public List<AssetApi> apis(String projectId) {
        return mapper.listApis(projectId);
    }

    @Override
    public Optional<AssetApi> api(UUID id) {
        return Optional.ofNullable(mapper.getApi(id));
    }

    @Override
    public Optional<AssetApi> apiIncludingInactive(UUID id) {
        return Optional.ofNullable(mapper.getApiIncludingInactive(id));
    }

    @Override
    public Optional<AssetApi> apiByPath(String projectId, String path, String httpMethod) {
        return Optional.ofNullable(mapper.getApiByPath(
                projectId,
                path,
                httpMethod == null ? null : httpMethod.toUpperCase(Locale.ROOT)
        ));
    }

    @Override
    public AssetApi saveApi(AssetApi api) {
        AssetApi stored = normalizeApi(api);
        if (mapper.getApi(stored.id()) == null) {
            if (mapper.getApiIncludingInactive(stored.id()) == null) {
                mapper.insertApi(stored);
            } else {
                mapper.updateApiLifecycle(stored);
            }
        } else {
            mapper.updateApi(stored);
        }
        return api;
    }

    @Override
    public boolean hasActiveApiPathConflict(String projectId, String path, String httpMethod, UUID excludeId) {
        return mapper.countActiveApiPathConflict(
                projectId,
                path,
                httpMethod == null ? null : httpMethod.toUpperCase(Locale.ROOT),
                excludeId
        ) > 0;
    }

    @Override
    public List<AssetPage> pages(String projectId) {
        return mapper.listPages(projectId);
    }

    @Override
    public Optional<AssetPage> page(UUID id) {
        return Optional.ofNullable(mapper.getPage(id));
    }

    @Override
    public Optional<AssetPage> pageIncludingInactive(UUID id) {
        return Optional.ofNullable(mapper.getPageIncludingInactive(id));
    }

    @Override
    public AssetPage savePage(AssetPage page) {
        AssetPage stored = normalizePage(page);
        if (mapper.getPage(stored.id()) == null) {
            if (mapper.getPageIncludingInactive(stored.id()) == null) {
                mapper.insertPage(stored);
            } else {
                mapper.updatePageLifecycle(stored);
            }
        } else {
            mapper.updatePage(stored);
        }
        return page;
    }

    @Override
    public boolean hasActivePageCodeConflict(String projectId, String code, UUID excludeId) {
        return mapper.countActivePageCodeConflict(projectId, code, excludeId) > 0;
    }

    @Override
    public List<AssetBusinessFlow> businessFlows(String projectId) {
        return mapper.listBusinessFlows(projectId);
    }

    @Override
    public Optional<AssetBusinessFlow> businessFlow(UUID id) {
        return Optional.ofNullable(mapper.getBusinessFlow(id));
    }

    @Override
    public Optional<AssetBusinessFlow> businessFlowIncludingInactive(UUID id) {
        return Optional.ofNullable(mapper.getBusinessFlowIncludingInactive(id));
    }

    @Override
    public AssetBusinessFlow saveBusinessFlow(AssetBusinessFlow flow) {
        AssetBusinessFlow stored = normalizeBusinessFlow(flow);
        if (mapper.getBusinessFlow(stored.id()) == null) {
            if (mapper.getBusinessFlowIncludingInactive(stored.id()) == null) {
                mapper.insertBusinessFlow(stored);
            } else {
                mapper.updateBusinessFlowLifecycle(stored);
            }
        } else {
            mapper.updateBusinessFlow(stored);
        }
        return flow;
    }

    @Override
    public boolean hasActiveBusinessFlowCodeConflict(String projectId, String code, UUID excludeId) {
        return mapper.countActiveBusinessFlowCodeConflict(projectId, code, excludeId) > 0;
    }

    @Override
    public List<TestCaseRecord> testCases(String projectId) {
        return mapper.listTestCases(projectId);
    }

    @Override
    public Optional<TestCaseRecord> testCase(UUID id) {
        TestCaseRecord testCase = mapper.getTestCase(id);
        if (testCase == null) {
            return Optional.empty();
        }
        return Optional.of(withSteps(testCase));
    }

    @Override
    public Optional<TestCaseRecord> testCaseIncludingInactive(UUID id) {
        TestCaseRecord testCase = mapper.getTestCaseIncludingInactive(id);
        if (testCase == null) {
            return Optional.empty();
        }
        return Optional.of(withSteps(testCase));
    }

    @Override
    @Transactional
    public TestCaseRecord saveTestCase(TestCaseRecord testCase) {
        TestCaseRecord stored = normalizeTestCase(testCase);
        if (mapper.getTestCase(stored.id()) == null) {
            if (mapper.getTestCaseIncludingInactive(stored.id()) == null) {
                mapper.insertTestCase(stored);
            } else {
                mapper.updateTestCaseLifecycle(stored);
            }
        } else {
            mapper.updateTestCase(stored);
        }
        if (!"DELETED".equals(stored.lifecycleStatus())) {
            replaceTestCaseSteps(stored.id(), stored.steps());
        }
        return testCase;
    }

    @Override
    public boolean hasActiveTestCaseCodeConflict(String projectId, String code, UUID excludeId) {
        return mapper.countActiveTestCaseCodeConflict(projectId, code, excludeId) > 0;
    }

    @Override
    public List<TestCaseStep> testCaseSteps(UUID caseId) {
        return mapper.listTestCaseSteps(caseId);
    }

    @Override
    @Transactional
    public void replaceTestCaseSteps(UUID caseId, List<TestCaseStep> steps) {
        mapper.deleteTestCaseSteps(caseId);
        for (TestCaseStep step : steps) {
            mapper.insertTestCaseStep(step);
        }
    }

    @Override
    public List<TraceLink> traceLinks(UUID requirementId, UUID apiId, UUID caseId) {
        return mapper.listTraceLinks(requirementId, apiId, caseId);
    }

    @Override
    public TraceLink saveTraceLink(TraceLink link) {
        mapper.insertTraceLink(link);
        return link;
    }

    private TestCaseRecord withSteps(TestCaseRecord testCase) {
        return new TestCaseRecord(
                testCase.id(),
                testCase.code(),
                testCase.title(),
                testCase.description(),
                testCase.projectId(),
                testCase.requirementId(),
                testCase.apiId(),
                testCase.source(),
                testCase.sourceRef(),
                testCase.status(),
                testCase.priority(),
                testCase.tags(),
                mapper.listTestCaseSteps(testCase.id()),
                testCase.version(),
                testCase.lifecycleStatus(),
                testCase.archivedAt(),
                testCase.deletedAt(),
                testCase.createdAt(),
                testCase.updatedAt()
        );
    }

    private AssetRequirement normalizeRequirement(AssetRequirement requirement) {
        return new AssetRequirement(
                requirement.id(),
                requirement.code(),
                requirement.title(),
                requirement.description(),
                requirement.source(),
                requirement.sourceRef(),
                requirement.sourceUrl(),
                requirement.acceptanceCriteria(),
                requirement.status(),
                requirement.priority(),
                requirement.projectId(),
                requirement.tags(),
                requirement.version(),
                lifecycleStatus(requirement.lifecycleStatus(), requirement.deletedAt()),
                requirement.archivedAt(),
                requirement.deletedAt(),
                requirement.createdAt(),
                requirement.updatedAt()
        );
    }

    private AssetApi normalizeApi(AssetApi api) {
        return new AssetApi(
                api.id(),
                api.code(),
                api.summary(),
                api.description(),
                api.httpMethod() == null ? null : api.httpMethod().toUpperCase(java.util.Locale.ROOT),
                api.path(),
                api.source(),
                api.sourceRef(),
                api.version(),
                api.requestSchema(),
                api.responseSchema(),
                api.projectId(),
                api.status(),
                lifecycleStatus(api.lifecycleStatus(), api.deletedAt()),
                api.archivedAt(),
                api.deletedAt(),
                api.createdAt(),
                api.updatedAt()
        );
    }

    private AssetPage normalizePage(AssetPage page) {
        return new AssetPage(
                page.id(),
                page.code(),
                page.name(),
                page.urlPattern(),
                page.source(),
                page.sourceRef(),
                page.sourceVersion(),
                page.componentTree(),
                page.screenshotUrl(),
                page.projectId(),
                page.status(),
                lifecycleStatus(page.lifecycleStatus(), page.deletedAt()),
                page.archivedAt(),
                page.deletedAt(),
                page.createdAt(),
                page.updatedAt()
        );
    }

    private AssetBusinessFlow normalizeBusinessFlow(AssetBusinessFlow flow) {
        return new AssetBusinessFlow(
                flow.id(),
                flow.code(),
                flow.name(),
                flow.description(),
                flow.flowJson(),
                flow.priority(),
                flow.projectId(),
                flow.status(),
                lifecycleStatus(flow.lifecycleStatus(), flow.deletedAt()),
                flow.archivedAt(),
                flow.deletedAt(),
                flow.createdAt(),
                flow.updatedAt()
        );
    }

    private TestCaseRecord normalizeTestCase(TestCaseRecord testCase) {
        return new TestCaseRecord(
                testCase.id(),
                testCase.code(),
                testCase.title(),
                testCase.description(),
                testCase.projectId(),
                testCase.requirementId(),
                testCase.apiId(),
                testCase.source(),
                testCase.sourceRef(),
                testCase.status(),
                testCase.priority(),
                testCase.tags(),
                testCase.steps(),
                testCase.version(),
                lifecycleStatus(testCase.lifecycleStatus(), testCase.deletedAt()),
                testCase.archivedAt(),
                testCase.deletedAt(),
                testCase.createdAt(),
                testCase.updatedAt()
        );
    }

    private static String lifecycleStatus(String lifecycleStatus, java.time.Instant deletedAt) {
        if (deletedAt != null) {
            return "DELETED";
        }
        return lifecycleStatus == null ? "ACTIVE" : lifecycleStatus;
    }

}
