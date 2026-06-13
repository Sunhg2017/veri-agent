package com.songhg.veri.agent.execution.infrastructure.mapper;

import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
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

    int insertRun(ExecutionRun run);

    void updateRun(ExecutionRun run);

    void insertNodeRun(ExecutionNodeRun nodeRun);

    void updateNodeRun(ExecutionNodeRun nodeRun);

    ExecutionRun run(@Param("id") UUID id);

    ExecutionRun runByPlanAndRequestKey(@Param("planId") UUID planId, @Param("requestKey") String requestKey);

    List<ExecutionNodeRun> nodeRuns(@Param("runId") UUID runId);

    List<ExecutionRun> runs(@Param("query") ExecutionRunQuery query);

    long countRuns(@Param("query") ExecutionRunQuery query);

    String runProjectScopeId(@Param("id") UUID id);
}
