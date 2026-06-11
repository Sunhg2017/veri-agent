package com.songhg.veri.agent.apiautomation.infrastructure;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import com.songhg.veri.agent.apiautomation.infrastructure.mapper.ApiAutomationMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcApiAutomationRepository implements ApiAutomationRepository {

    private final ApiAutomationMapper mapper;

    public JdbcApiAutomationRepository(ApiAutomationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertSpec(ApiAutomationSpec spec) {
        mapper.insertSpec(spec);
    }

    @Override
    public void updateSpecParseResult(ApiAutomationSpec spec) {
        mapper.updateSpecParseResult(spec);
    }

    @Override
    public void deleteEndpointSnapshots(UUID specId) {
        mapper.deleteEndpointSnapshots(specId);
    }

    @Override
    public void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot) {
        mapper.insertEndpointSnapshot(snapshot);
    }

    @Override
    public void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot) {
        mapper.updateEndpointSnapshotDiff(snapshot);
    }

    @Override
    public Optional<ApiAutomationSpec> spec(UUID id) {
        return Optional.ofNullable(mapper.spec(id));
    }

    @Override
    public Optional<ApiAutomationSpec> activeSpecByProjectAndDigest(String projectId, String specDigest) {
        return Optional.ofNullable(mapper.activeSpecByProjectAndDigest(projectId, specDigest));
    }

    @Override
    public List<ApiAutomationSpec> specs(ApiAutomationSpecQuery query) {
        return mapper.specs(query);
    }

    @Override
    public long countSpecs(ApiAutomationSpecQuery query) {
        return mapper.countSpecs(query);
    }

    @Override
    public List<ApiAutomationEndpointSnapshot> endpointSnapshots(UUID specId) {
        return mapper.endpointSnapshots(specId);
    }

    @Override
    public Optional<String> specProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.specProjectScopeId(id));
    }
}
