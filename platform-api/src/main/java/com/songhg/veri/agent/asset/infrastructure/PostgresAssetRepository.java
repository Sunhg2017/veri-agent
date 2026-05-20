package com.songhg.veri.agent.asset.infrastructure;

import com.songhg.veri.agent.asset.application.AssetRepository;
import com.songhg.veri.agent.asset.domain.AssetApi;
import com.songhg.veri.agent.asset.domain.AssetBusinessFlow;
import com.songhg.veri.agent.asset.domain.AssetPage;
import com.songhg.veri.agent.asset.domain.AssetRequirement;
import com.songhg.veri.agent.asset.domain.TestCaseRecord;
import com.songhg.veri.agent.asset.domain.TestCaseStep;
import com.songhg.veri.agent.asset.domain.TraceLink;
import com.songhg.veri.agent.asset.infrastructure.mapper.AssetMapper;
import java.util.List;
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
    public Optional<AssetRequirement> requirementBySourceRef(String projectId, String source, String sourceRef) {
        return Optional.ofNullable(mapper.getRequirementBySourceRef(projectId, source, sourceRef));
    }

    @Override
    public AssetRequirement saveRequirement(AssetRequirement requirement) {
        AssetRequirement stored = normalizeRequirement(requirement);
        try {
            if (mapper.getRequirement(stored.id()) == null) {
                mapper.insertRequirement(stored);
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
    public List<AssetApi> apis(String projectId) {
        return mapper.listApis(projectId);
    }

    @Override
    public Optional<AssetApi> api(UUID id) {
        return Optional.ofNullable(mapper.getApi(id));
    }

    @Override
    public AssetApi saveApi(AssetApi api) {
        AssetApi stored = normalizeApi(api);
        if (mapper.getApi(stored.id()) == null) {
            mapper.insertApi(stored);
        } else {
            mapper.updateApi(stored);
        }
        return api;
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
    public AssetPage savePage(AssetPage page) {
        AssetPage stored = normalizePage(page);
        if (mapper.getPage(stored.id()) == null) {
            mapper.insertPage(stored);
        } else {
            mapper.updatePage(stored);
        }
        return page;
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
    public AssetBusinessFlow saveBusinessFlow(AssetBusinessFlow flow) {
        AssetBusinessFlow stored = normalizeBusinessFlow(flow);
        if (mapper.getBusinessFlow(stored.id()) == null) {
            mapper.insertBusinessFlow(stored);
        } else {
            mapper.updateBusinessFlow(stored);
        }
        return flow;
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
    @Transactional
    public TestCaseRecord saveTestCase(TestCaseRecord testCase) {
        TestCaseRecord stored = normalizeTestCase(testCase);
        if (mapper.getTestCase(stored.id()) == null) {
            mapper.insertTestCase(stored);
        } else {
            mapper.updateTestCase(stored);
        }
        replaceTestCaseSteps(stored.id(), stored.steps());
        return testCase;
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
                api.requestSchema(),
                api.responseSchema(),
                api.projectId(),
                api.status(),
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
                page.componentTree(),
                page.screenshotUrl(),
                page.projectId(),
                page.status(),
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
                testCase.createdAt(),
                testCase.updatedAt()
        );
    }

}
