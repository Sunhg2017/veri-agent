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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Repository
public class InMemoryExecutionRepository implements ExecutionRepository {

    private final ConcurrentHashMap<UUID, ExecutionPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExecutionPlanNode> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExecutionRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExecutionNodeRun> nodeRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExecutionQueueClaim> queueClaims = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExecutionTrigger> triggers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExecutionTriggerEvent> triggerEvents = new ConcurrentHashMap<>();

    @Override
    public void insertPlan(ExecutionPlan plan) {
        plans.put(plan.id(), plan);
    }

    @Override
    public void updatePlan(ExecutionPlan plan) {
        plans.computeIfPresent(plan.id(), (ignored, current) -> "ARCHIVED".equals(current.status()) ? current : plan);
    }

    @Override
    public void archivePlan(ExecutionPlan plan) {
        plans.computeIfPresent(plan.id(), (ignored, current) -> "ARCHIVED".equals(current.status()) ? current : plan);
    }

    @Override
    public void replacePlanNodes(UUID planId, List<ExecutionPlanNode> newNodes) {
        nodes.entrySet().removeIf(entry -> planId.equals(entry.getValue().planId()));
        for (ExecutionPlanNode node : newNodes) {
            nodes.put(node.id(), node);
        }
    }

    @Override
    public Optional<ExecutionPlan> plan(UUID id) {
        return Optional.ofNullable(plans.get(id));
    }

    @Override
    public List<ExecutionPlanNode> planNodes(UUID planId) {
        return nodes.values().stream()
                .filter(node -> planId.equals(node.planId()))
                .sorted(Comparator.comparing(ExecutionPlanNode::nodeKey))
                .toList();
    }

    @Override
    public List<ExecutionPlan> plans(ExecutionPlanQuery query) {
        return filteredPlans(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countPlans(ExecutionPlanQuery query) {
        return filteredPlans(query).count();
    }

    @Override
    public Optional<String> planProjectScopeId(UUID id) {
        return plan(id).map(ExecutionPlan::projectId);
    }

    @Override
    public boolean insertRun(ExecutionRun run) {
        if (StringUtils.hasText(run.requestKey()) && runByPlanAndRequestKey(run.planId(), run.requestKey()).isPresent()) {
            return false;
        }
        return runs.putIfAbsent(run.id(), run) == null;
    }

    @Override
    public void updateRun(ExecutionRun run) {
        runs.computeIfPresent(run.id(), (ignored, current) -> run);
    }

    @Override
    public void insertNodeRuns(List<ExecutionNodeRun> newNodeRuns) {
        for (ExecutionNodeRun nodeRun : newNodeRuns) {
            nodeRuns.put(nodeRun.id(), nodeRun);
        }
    }

    @Override
    public void updateNodeRuns(List<ExecutionNodeRun> updatedNodeRuns) {
        for (ExecutionNodeRun nodeRun : updatedNodeRuns) {
            nodeRuns.computeIfPresent(nodeRun.id(), (ignored, current) -> nodeRun);
        }
    }

    @Override
    public boolean updateNodeRunIfStatus(ExecutionNodeRun nodeRun, String expectedStatus) {
        ExecutionNodeRun current = nodeRuns.get(nodeRun.id());
        if (current == null || !expectedStatus.equals(current.status())) {
            return false;
        }
        nodeRuns.put(nodeRun.id(), nodeRun);
        return true;
    }

    @Override
    public List<ExecutionNodeRun> queuedNodeRuns(int limit) {
        return nodeRuns.values().stream()
                .filter(nodeRun -> "QUEUED".equals(nodeRun.status()))
                .filter(nodeRun -> run(nodeRun.runId())
                        .map(run -> "QUEUED".equals(run.status()) || "RUNNING".equals(run.status()))
                        .orElse(false))
                .sorted(Comparator
                        .comparing((ExecutionNodeRun nodeRun) -> run(nodeRun.runId())
                                .map(ExecutionRun::createdAt)
                                .orElse(java.time.Instant.EPOCH))
                        .thenComparing(nodeRun -> planNodeKey(nodeRun.planNodeId()))
                        .thenComparing(ExecutionNodeRun::attempt))
                .limit(limit)
                .toList();
    }

    @Override
    public boolean tryInsertQueueClaim(ExecutionQueueClaim claim) {
        boolean hasActiveClaim = queueClaims.values().stream()
                .anyMatch(existing -> claim.nodeRunId().equals(existing.nodeRunId())
                        && "CLAIMED".equals(existing.status()));
        if (hasActiveClaim) {
            return false;
        }
        return queueClaims.putIfAbsent(claim.id(), claim) == null;
    }

    @Override
    public void updateQueueClaim(ExecutionQueueClaim claim) {
        queueClaims.computeIfPresent(claim.id(), (ignored, current) -> claim);
    }

    @Override
    public boolean updateQueueClaimIfStatus(ExecutionQueueClaim claim, String expectedStatus) {
        ExecutionQueueClaim current = queueClaims.get(claim.id());
        if (current == null || !expectedStatus.equals(current.status())) {
            return false;
        }
        queueClaims.put(claim.id(), claim);
        return true;
    }

    @Override
    public boolean updateExpiredQueueClaim(ExecutionQueueClaim claim, Instant referenceTime) {
        ExecutionQueueClaim current = queueClaims.get(claim.id());
        if (current == null || !"CLAIMED".equals(current.status()) || current.expiresAt().isAfter(referenceTime)) {
            return false;
        }
        queueClaims.put(claim.id(), claim);
        return true;
    }

    @Override
    public List<ExecutionQueueClaim> expiredQueueClaims(Instant now, int limit) {
        return queueClaims.values().stream()
                .filter(claim -> "CLAIMED".equals(claim.status()))
                .filter(claim -> !claim.expiresAt().isAfter(now))
                .sorted(Comparator
                        .comparing(ExecutionQueueClaim::expiresAt)
                        .thenComparing(ExecutionQueueClaim::claimedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public List<ExecutionNodeRun> runningNodeRunsStartedBefore(Instant deadline, int limit) {
        return nodeRuns.values().stream()
                .filter(nodeRun -> "RUNNING".equals(nodeRun.status()))
                .filter(nodeRun -> run(nodeRun.runId())
                        .map(run -> "QUEUED".equals(run.status()) || "RUNNING".equals(run.status()))
                        .orElse(false))
                .filter(nodeRun -> recoveryBaseTime(nodeRun).compareTo(deadline) <= 0)
                .sorted(Comparator.comparing(this::recoveryBaseTime))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<ExecutionRun> run(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public Optional<ExecutionNodeRun> nodeRun(UUID id) {
        return Optional.ofNullable(nodeRuns.get(id));
    }

    @Override
    public Optional<ExecutionQueueClaim> activeQueueClaim(UUID nodeRunId) {
        return queueClaims.values().stream()
                .filter(claim -> nodeRunId.equals(claim.nodeRunId()) && "CLAIMED".equals(claim.status()))
                .findFirst();
    }

    @Override
    public Optional<ExecutionQueueClaim> queueClaimByToken(String claimToken) {
        if (!StringUtils.hasText(claimToken)) {
            return Optional.empty();
        }
        return queueClaims.values().stream()
                .filter(claim -> claimToken.equals(claim.claimToken()))
                .findFirst();
    }

    @Override
    public Optional<ExecutionRun> runByPlanAndRequestKey(UUID planId, String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return Optional.empty();
        }
        return runs.values().stream()
                .filter(run -> planId.equals(run.planId()) && requestKey.equals(run.requestKey()))
                .findFirst();
    }

    @Override
    public List<ExecutionNodeRun> nodeRuns(UUID runId) {
        return nodeRuns.values().stream()
                .filter(nodeRun -> runId.equals(nodeRun.runId()))
                .sorted(Comparator
                        .comparing((ExecutionNodeRun nodeRun) -> planNodeKey(nodeRun.planNodeId()))
                        .thenComparing(ExecutionNodeRun::attempt))
                .toList();
    }

    @Override
    public List<ExecutionRun> runs(ExecutionRunQuery query) {
        return filteredRuns(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countRuns(ExecutionRunQuery query) {
        return filteredRuns(query).count();
    }

    @Override
    public Optional<String> runProjectScopeId(UUID id) {
        return run(id).map(ExecutionRun::projectId);
    }

    @Override
    public void insertTrigger(ExecutionTrigger trigger) {
        triggers.put(trigger.id(), trigger);
    }

    @Override
    public void updateTrigger(ExecutionTrigger trigger) {
        triggers.computeIfPresent(trigger.id(), (ignored, current) -> trigger);
    }

    @Override
    public Optional<ExecutionTrigger> trigger(UUID id) {
        return Optional.ofNullable(triggers.get(id));
    }

    @Override
    public List<ExecutionTrigger> dueCronTriggers(Instant now, int limit) {
        return triggers.values().stream()
                .filter(trigger -> "CRON".equals(trigger.triggerType()))
                .filter(trigger -> "ENABLED".equals(trigger.status()))
                .filter(trigger -> trigger.nextFireAt() != null && !trigger.nextFireAt().isAfter(now))
                .sorted(Comparator
                        .comparing(ExecutionTrigger::nextFireAt)
                        .thenComparing(ExecutionTrigger::updatedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public List<ExecutionTrigger> triggers(ExecutionTriggerQuery query) {
        return filteredTriggers(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countTriggers(ExecutionTriggerQuery query) {
        return filteredTriggers(query).count();
    }

    @Override
    public Optional<String> triggerProjectScopeId(UUID id) {
        return trigger(id)
                .flatMap(trigger -> plan(trigger.planId()))
                .map(ExecutionPlan::projectId);
    }

    @Override
    public boolean insertTriggerEvent(ExecutionTriggerEvent event) {
        boolean duplicate = triggerEvents.values().stream()
                .anyMatch(existing -> event.triggerId().equals(existing.triggerId())
                        && event.sourceEventId().equals(existing.sourceEventId()));
        if (duplicate) {
            return false;
        }
        return triggerEvents.putIfAbsent(event.id(), event) == null;
    }

    @Override
    public void updateTriggerEvent(ExecutionTriggerEvent event) {
        triggerEvents.computeIfPresent(event.id(), (ignored, current) -> event);
    }

    @Override
    public Optional<ExecutionTriggerEvent> triggerEvent(UUID id) {
        return Optional.ofNullable(triggerEvents.get(id));
    }

    @Override
    public Optional<ExecutionTriggerEvent> triggerEventBySource(UUID triggerId, String sourceEventId) {
        if (triggerId == null || !StringUtils.hasText(sourceEventId)) {
            return Optional.empty();
        }
        return triggerEvents.values().stream()
                .filter(event -> triggerId.equals(event.triggerId()) && sourceEventId.equals(event.sourceEventId()))
                .findFirst();
    }

    @Override
    public List<ExecutionTriggerEvent> triggerEvents(ExecutionTriggerEventQuery query) {
        return filteredTriggerEvents(query)
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    @Override
    public long countTriggerEvents(ExecutionTriggerEventQuery query) {
        return filteredTriggerEvents(query).count();
    }

    private Stream<ExecutionPlan> filteredPlans(ExecutionPlanQuery query) {
        Stream<ExecutionPlan> stream = plans.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(plan -> query.projectId().equals(plan.projectId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(plan -> query.status().equals(plan.status()));
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().toLowerCase();
            stream = stream.filter(plan -> contains(plan.name(), keyword) || contains(plan.description(), keyword));
        }
        return stream.sorted(Comparator.comparing(ExecutionPlan::updatedAt).reversed());
    }

    private Stream<ExecutionRun> filteredRuns(ExecutionRunQuery query) {
        Stream<ExecutionRun> stream = runs.values().stream();
        if (StringUtils.hasText(query.projectId())) {
            stream = stream.filter(run -> query.projectId().equals(run.projectId()));
        }
        if (query.planId() != null) {
            stream = stream.filter(run -> query.planId().equals(run.planId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(run -> query.status().equals(run.status()));
        }
        return stream.sorted(Comparator.comparing(ExecutionRun::createdAt).reversed());
    }

    private Stream<ExecutionTrigger> filteredTriggers(ExecutionTriggerQuery query) {
        Stream<ExecutionTrigger> stream = triggers.values().stream();
        if (query.planId() != null) {
            stream = stream.filter(trigger -> query.planId().equals(trigger.planId()));
        }
        if (StringUtils.hasText(query.triggerType())) {
            stream = stream.filter(trigger -> query.triggerType().equals(trigger.triggerType()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(trigger -> query.status().equals(trigger.status()));
        }
        return stream.sorted(Comparator.comparing(ExecutionTrigger::updatedAt).reversed());
    }

    private Stream<ExecutionTriggerEvent> filteredTriggerEvents(ExecutionTriggerEventQuery query) {
        Stream<ExecutionTriggerEvent> stream = triggerEvents.values().stream();
        if (query.triggerId() != null) {
            stream = stream.filter(event -> query.triggerId().equals(event.triggerId()));
        }
        if (StringUtils.hasText(query.status())) {
            stream = stream.filter(event -> query.status().equals(event.status()));
        }
        return stream.sorted(Comparator.comparing(ExecutionTriggerEvent::receivedAt).reversed());
    }

    private String planNodeKey(UUID planNodeId) {
        ExecutionPlanNode node = nodes.get(planNodeId);
        return node == null ? "" : node.nodeKey();
    }

    private Instant recoveryBaseTime(ExecutionNodeRun nodeRun) {
        return nodeRun.startedAt() == null ? nodeRun.createdAt() : nodeRun.startedAt();
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword);
    }
}
