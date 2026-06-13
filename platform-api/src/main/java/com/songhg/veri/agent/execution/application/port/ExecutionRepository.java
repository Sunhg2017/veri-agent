package com.songhg.veri.agent.execution.application.port;

import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {

    void insertPlan(ExecutionPlan plan);

    void updatePlan(ExecutionPlan plan);

    void archivePlan(ExecutionPlan plan);

    void replacePlanNodes(UUID planId, List<ExecutionPlanNode> nodes);

    Optional<ExecutionPlan> plan(UUID id);

    List<ExecutionPlanNode> planNodes(UUID planId);

    List<ExecutionPlan> plans(ExecutionPlanQuery query);

    long countPlans(ExecutionPlanQuery query);

    Optional<String> planProjectScopeId(UUID id);

    boolean insertRun(ExecutionRun run);

    void insertNodeRuns(List<ExecutionNodeRun> nodeRuns);

    Optional<ExecutionRun> run(UUID id);

    Optional<ExecutionRun> runByPlanAndRequestKey(UUID planId, String requestKey);

    List<ExecutionNodeRun> nodeRuns(UUID runId);

    List<ExecutionRun> runs(ExecutionRunQuery query);

    long countRuns(ExecutionRunQuery query);

    Optional<String> runProjectScopeId(UUID id);
}
