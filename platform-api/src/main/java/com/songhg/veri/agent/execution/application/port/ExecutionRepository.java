package com.songhg.veri.agent.execution.application.port;

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
import java.time.Instant;
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

    void updateRun(ExecutionRun run);

    void insertNodeRuns(List<ExecutionNodeRun> nodeRuns);

    void updateNodeRuns(List<ExecutionNodeRun> nodeRuns);

    boolean updateNodeRunIfStatus(ExecutionNodeRun nodeRun, String expectedStatus);

    List<ExecutionNodeRun> queuedNodeRuns(int limit);

    List<ExecutionNodeRun> followUpNodeRuns(int limit);

    boolean tryInsertQueueClaim(ExecutionQueueClaim claim);

    void updateQueueClaim(ExecutionQueueClaim claim);

    boolean updateQueueClaimIfStatus(ExecutionQueueClaim claim, String expectedStatus);

    boolean updateExpiredQueueClaim(ExecutionQueueClaim claim, Instant referenceTime);

    List<ExecutionQueueClaim> expiredQueueClaims(Instant now, int limit);

    List<ExecutionNodeRun> runningNodeRunsStartedBefore(Instant deadline, int limit);

    Optional<ExecutionRun> run(UUID id);

    Optional<ExecutionNodeRun> nodeRun(UUID id);

    Optional<ExecutionQueueClaim> activeQueueClaim(UUID nodeRunId);

    Optional<ExecutionQueueClaim> queueClaimByToken(String claimToken);

    Optional<ExecutionRun> runByPlanAndRequestKey(UUID planId, String requestKey);

    List<ExecutionNodeRun> nodeRuns(UUID runId);

    List<ExecutionRun> runs(ExecutionRunQuery query);

    long countRuns(ExecutionRunQuery query);

    Optional<String> runProjectScopeId(UUID id);

    void insertTrigger(ExecutionTrigger trigger);

    void updateTrigger(ExecutionTrigger trigger);

    Optional<ExecutionTrigger> trigger(UUID id);

    List<ExecutionTrigger> dueCronTriggers(Instant now, int limit);

    List<ExecutionTrigger> triggers(ExecutionTriggerQuery query);

    long countTriggers(ExecutionTriggerQuery query);

    Optional<String> triggerProjectScopeId(UUID id);

    boolean insertTriggerEvent(ExecutionTriggerEvent event);

    void updateTriggerEvent(ExecutionTriggerEvent event);

    Optional<ExecutionTriggerEvent> triggerEvent(UUID id);

    Optional<ExecutionTriggerEvent> triggerEventBySource(UUID triggerId, String sourceEventId);

    List<ExecutionTriggerEvent> triggerEvents(ExecutionTriggerEventQuery query);

    long countTriggerEvents(ExecutionTriggerEventQuery query);
}
