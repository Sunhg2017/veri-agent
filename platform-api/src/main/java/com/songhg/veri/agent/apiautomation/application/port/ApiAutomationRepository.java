package com.songhg.veri.agent.apiautomation.application.port;

import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiAutomationRepository {

    void insertSpec(ApiAutomationSpec spec);

    void updateSpecParseResult(ApiAutomationSpec spec);

    void deleteEndpointSnapshots(UUID specId);

    void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot);

    void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot);

    Optional<ApiAutomationSpec> spec(UUID id);

    Optional<ApiAutomationSpec> activeSpecByProjectAndDigest(String projectId, String specDigest);

    List<ApiAutomationSpec> specs(ApiAutomationSpecQuery query);

    long countSpecs(ApiAutomationSpecQuery query);

    List<ApiAutomationEndpointSnapshot> endpointSnapshots(UUID specId);

    Optional<String> specProjectScopeId(UUID id);
}
