package com.songhg.veri.agent.execution.infrastructure.mapper;

import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExecutionMapper {

    void insertPlan(ExecutionPlan plan);

    void updatePlan(ExecutionPlan plan);

    void archivePlan(ExecutionPlan plan);

    void deletePlanNodes(@Param("planId") UUID planId);

    void insertPlanNode(ExecutionPlanNode node);

    ExecutionPlan plan(@Param("id") UUID id);

    List<ExecutionPlanNode> planNodes(@Param("planId") UUID planId);

    List<ExecutionPlan> plans(@Param("query") ExecutionPlanQuery query);

    long countPlans(@Param("query") ExecutionPlanQuery query);

    String planProjectScopeId(@Param("id") UUID id);
}
