package com.songhg.veri.agent.execution.infrastructure;

import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerEventQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.execution.domain.ExecutionTrigger;
import com.songhg.veri.agent.execution.domain.ExecutionTriggerEvent;
import com.songhg.veri.agent.execution.infrastructure.mapper.ExecutionMapper;
import java.time.Instant;
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
        if (nodes != null && !nodes.isEmpty()) {
            mapper.insertPlanNodes(nodes);
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
    public void updateRun(ExecutionRun run) {
        mapper.updateRun(run);
    }

    @Override
    public void insertNodeRuns(List<ExecutionNodeRun> nodeRuns) {
        if (nodeRuns != null && !nodeRuns.isEmpty()) {
            mapper.insertNodeRuns(nodeRuns);
        }
    }

    @Override
    public void updateNodeRuns(List<ExecutionNodeRun> nodeRuns) {
        if (nodeRuns != null && !nodeRuns.isEmpty()) {
            mapper.updateNodeRuns(nodeRuns);
        }
    }

    @Override
    public boolean updateNodeRunIfStatus(ExecutionNodeRun nodeRun, String expectedStatus) {
        return mapper.updateNodeRunIfStatus(nodeRun, expectedStatus) > 0;
    }

    @Override
    public List<ExecutionNodeRun> queuedNodeRuns(int limit) {
        return mapper.queuedNodeRuns(limit);
    }

    @Override
    public boolean tryInsertQueueClaim(ExecutionQueueClaim claim) {
        return mapper.insertQueueClaim(claim) > 0;
    }

    @Override
    public void updateQueueClaim(ExecutionQueueClaim claim) {
        mapper.updateQueueClaim(claim);
    }

    @Override
    public boolean updateQueueClaimIfStatus(ExecutionQueueClaim claim, String expectedStatus) {
        return mapper.updateQueueClaimIfStatus(claim, expectedStatus) > 0;
    }

    @Override
    public boolean updateExpiredQueueClaim(ExecutionQueueClaim claim, Instant referenceTime) {
        return mapper.updateExpiredQueueClaim(claim, referenceTime) > 0;
    }

    @Override
    public List<ExecutionQueueClaim> expiredQueueClaims(Instant now, int limit) {
        return mapper.expiredQueueClaims(now, limit);
    }

    @Override
    public List<ExecutionNodeRun> runningNodeRunsStartedBefore(Instant deadline, int limit) {
        return mapper.runningNodeRunsStartedBefore(deadline, limit);
    }

    @Override
    public Optional<ExecutionRun> run(UUID id) {
        return Optional.ofNullable(mapper.run(id));
    }

    @Override
    public Optional<ExecutionNodeRun> nodeRun(UUID id) {
        return Optional.ofNullable(mapper.nodeRun(id));
    }

    @Override
    public Optional<ExecutionQueueClaim> activeQueueClaim(UUID nodeRunId) {
        return Optional.ofNullable(mapper.activeQueueClaim(nodeRunId));
    }

    @Override
    public Optional<ExecutionQueueClaim> queueClaimByToken(String claimToken) {
        return Optional.ofNullable(mapper.queueClaimByToken(claimToken));
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

    @Override
    public void insertTrigger(ExecutionTrigger trigger) {
        mapper.insertTrigger(trigger);
    }

    @Override
    public void updateTrigger(ExecutionTrigger trigger) {
        mapper.updateTrigger(trigger);
    }

    @Override
    public Optional<ExecutionTrigger> trigger(UUID id) {
        return Optional.ofNullable(mapper.trigger(id));
    }

    @Override
    public List<ExecutionTrigger> dueCronTriggers(Instant now, int limit) {
        return mapper.dueCronTriggers(now, limit);
    }

    @Override
    public List<ExecutionTrigger> triggers(ExecutionTriggerQuery query) {
        return mapper.triggers(query);
    }

    @Override
    public long countTriggers(ExecutionTriggerQuery query) {
        return mapper.countTriggers(query);
    }

    @Override
    public Optional<String> triggerProjectScopeId(UUID id) {
        return Optional.ofNullable(mapper.triggerProjectScopeId(id));
    }

    @Override
    public boolean insertTriggerEvent(ExecutionTriggerEvent event) {
        return mapper.insertTriggerEvent(event) > 0;
    }

    @Override
    public void updateTriggerEvent(ExecutionTriggerEvent event) {
        mapper.updateTriggerEvent(event);
    }

    @Override
    public Optional<ExecutionTriggerEvent> triggerEvent(UUID id) {
        return Optional.ofNullable(mapper.triggerEvent(id));
    }

    @Override
    public Optional<ExecutionTriggerEvent> triggerEventBySource(UUID triggerId, String sourceEventId) {
        return Optional.ofNullable(mapper.triggerEventBySource(triggerId, sourceEventId));
    }

    @Override
    public List<ExecutionTriggerEvent> triggerEvents(ExecutionTriggerEventQuery query) {
        return mapper.triggerEvents(query);
    }

    @Override
    public long countTriggerEvents(ExecutionTriggerEventQuery query) {
        return mapper.countTriggerEvents(query);
    }
}
