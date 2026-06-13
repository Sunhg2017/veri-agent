package com.songhg.veri.agent.execution.infrastructure;

import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.execution.infrastructure.mapper.ExecutionMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcExecutionRepository implements ExecutionRepository {

    private final ExecutionMapper mapper;

    public JdbcExecutionRepository(ExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertPlan(ExecutionPlan plan) {
        mapper.insertPlan(plan);
    }

    @Override
    public void updatePlan(ExecutionPlan plan) {
        mapper.updatePlan(plan);
    }

    @Override
    public void archivePlan(ExecutionPlan plan) {
        mapper.archivePlan(plan);
    }

    @Override
    public void replacePlanNodes(UUID planId, List<ExecutionPlanNode> nodes) {
        mapper.deletePlanNodes(planId);
        for (ExecutionPlanNode node : nodes) {
            mapper.insertPlanNode(node);
        }
    }

    @Override
    public Optional<ExecutionPlan> plan(UUID id) {
        return Optional.ofNullable(mapper.plan(id));
    }

    @Override
    public List<ExecutionPlanNode> planNodes(UUID planId) {
        return mapper.planNodes(planId);
    }

    @Override
    public List<ExecutionPlan> plans(ExecutionPlanQuery query) {
        return mapper.plans(query);
    }

    @Override
    public long countPlans(ExecutionPlanQuery query) {
        return mapper.countPlans(query);
    }

    @Override
    public Optional<String> planProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.planProjectScopeId(id));
    }

    @Override
    public boolean insertRun(ExecutionRun run) {
        return mapper.insertRun(run) > 0;
    }

    @Override
    public void insertNodeRuns(List<ExecutionNodeRun> nodeRuns) {
        for (ExecutionNodeRun nodeRun : nodeRuns) {
            mapper.insertNodeRun(nodeRun);
        }
    }

    @Override
    public Optional<ExecutionRun> run(UUID id) {
        return Optional.ofNullable(mapper.run(id));
    }

    @Override
    public Optional<ExecutionRun> runByPlanAndRequestKey(UUID planId, String requestKey) {
        return Optional.ofNullable(mapper.runByPlanAndRequestKey(planId, requestKey));
    }

    @Override
    public List<ExecutionNodeRun> nodeRuns(UUID runId) {
        return mapper.nodeRuns(runId);
    }

    @Override
    public List<ExecutionRun> runs(ExecutionRunQuery query) {
        return mapper.runs(query);
    }

    @Override
    public long countRuns(ExecutionRunQuery query) {
        return mapper.countRuns(query);
    }

    @Override
    public Optional<String> runProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.runProjectScopeId(id));
    }
}
