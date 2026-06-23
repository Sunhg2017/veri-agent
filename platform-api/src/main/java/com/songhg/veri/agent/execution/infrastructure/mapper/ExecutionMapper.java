package com.songhg.veri.agent.execution.infrastructure.mapper;

import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunLogQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerEventQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionTriggerQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.execution.domain.ExecutionRunLogEntry;
import com.songhg.veri.agent.execution.domain.ExecutionTrigger;
import com.songhg.veri.agent.execution.domain.ExecutionTriggerEvent;
import java.time.Instant;
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

    void insertPlanNodes(@Param("nodes") List<ExecutionPlanNode> nodes);

    ExecutionPlan plan(@Param("id") UUID id);

    List<ExecutionPlanNode> planNodes(@Param("planId") UUID planId);

    List<ExecutionPlan> plans(@Param("query") ExecutionPlanQuery query);

    long countPlans(@Param("query") ExecutionPlanQuery query);

    String planProjectScopeId(@Param("id") UUID id);

    int insertRun(ExecutionRun run);

    void updateRun(ExecutionRun run);

    void insertNodeRun(ExecutionNodeRun nodeRun);

    void insertNodeRuns(@Param("nodeRuns") List<ExecutionNodeRun> nodeRuns);

    void updateNodeRun(ExecutionNodeRun nodeRun);

    void updateNodeRuns(@Param("nodeRuns") List<ExecutionNodeRun> nodeRuns);

    int updateNodeRunIfStatus(@Param("nodeRun") ExecutionNodeRun nodeRun, @Param("expectedStatus") String expectedStatus);

    List<ExecutionNodeRun> queuedNodeRuns(@Param("limit") int limit);

    List<ExecutionNodeRun> followUpNodeRuns(@Param("limit") int limit);

    int insertQueueClaim(ExecutionQueueClaim claim);

    void updateQueueClaim(ExecutionQueueClaim claim);

    int updateQueueClaimIfStatus(@Param("claim") ExecutionQueueClaim claim, @Param("expectedStatus") String expectedStatus);

    int updateExpiredQueueClaim(@Param("claim") ExecutionQueueClaim claim, @Param("referenceTime") Instant referenceTime);

    List<ExecutionQueueClaim> expiredQueueClaims(@Param("now") Instant now, @Param("limit") int limit);

    List<ExecutionNodeRun> runningNodeRunsStartedBefore(@Param("deadline") Instant deadline, @Param("limit") int limit);

    ExecutionRun run(@Param("id") UUID id);

    ExecutionNodeRun nodeRun(@Param("id") UUID id);

    ExecutionQueueClaim activeQueueClaim(@Param("nodeRunId") UUID nodeRunId);

    ExecutionQueueClaim queueClaimByToken(@Param("claimToken") String claimToken);

    ExecutionRun runByPlanAndRequestKey(@Param("planId") UUID planId, @Param("requestKey") String requestKey);

    List<ExecutionNodeRun> nodeRuns(@Param("runId") UUID runId);

    List<ExecutionRun> runs(@Param("query") ExecutionRunQuery query);

    long countRuns(@Param("query") ExecutionRunQuery query);

    String runProjectScopeId(@Param("id") UUID id);

    void insertRunLog(ExecutionRunLogEntry entry);

    List<ExecutionRunLogEntry> runLogs(@Param("runId") UUID runId, @Param("query") ExecutionRunLogQuery query);

    long countRunLogs(@Param("runId") UUID runId, @Param("query") ExecutionRunLogQuery query);

    void insertTrigger(ExecutionTrigger trigger);

    void updateTrigger(ExecutionTrigger trigger);

    ExecutionTrigger trigger(@Param("id") UUID id);

    List<ExecutionTrigger> dueCronTriggers(@Param("now") Instant now, @Param("limit") int limit);

    List<ExecutionTrigger> triggers(@Param("query") ExecutionTriggerQuery query);

    long countTriggers(@Param("query") ExecutionTriggerQuery query);

    String triggerProjectScopeId(@Param("id") UUID id);

    int insertTriggerEvent(ExecutionTriggerEvent event);

    void updateTriggerEvent(ExecutionTriggerEvent event);

    ExecutionTriggerEvent triggerEvent(@Param("id") UUID id);

    ExecutionTriggerEvent triggerEventBySource(
            @Param("triggerId") UUID triggerId,
            @Param("sourceEventId") String sourceEventId
    );

    List<ExecutionTriggerEvent> triggerEvents(@Param("query") ExecutionTriggerEventQuery query);

    long countTriggerEvents(@Param("query") ExecutionTriggerEventQuery query);
}
