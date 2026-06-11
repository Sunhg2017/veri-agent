package com.songhg.veri.agent.apiautomation.infrastructure.mapper;

import com.songhg.veri.agent.apiautomation.application.query.ApiAutomationSpecQuery;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiAutomationMapper {

    void insertSpec(ApiAutomationSpec spec);

    void updateSpecParseResult(ApiAutomationSpec spec);

    ApiAutomationSpec spec(@Param("id") UUID id);

    ApiAutomationSpec activeSpecByProjectAndDigest(
            @Param("projectId") String projectId,
            @Param("specDigest") String specDigest
    );

    List<ApiAutomationSpec> specs(@Param("query") ApiAutomationSpecQuery query);

    long countSpecs(@Param("query") ApiAutomationSpecQuery query);

    String specProjectScopeId(@Param("id") UUID id);

    void deleteEndpointSnapshots(@Param("specId") UUID specId);

    void insertEndpointSnapshot(ApiAutomationEndpointSnapshot snapshot);

    void updateEndpointSnapshotDiff(ApiAutomationEndpointSnapshot snapshot);

    List<ApiAutomationEndpointSnapshot> endpointSnapshots(@Param("specId") UUID specId);
}
