package com.songhg.veri.agent.execution.infrastructure;

import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.application.query.ExecutionRunQuery;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
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
    public Optional<ExecutionRun> run(UUID id) {
        return Optional.ofNullable(runs.get(id));
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

    private String planNodeKey(UUID planNodeId) {
        ExecutionPlanNode node = nodes.get(planNodeId);
        return node == null ? "" : node.nodeKey();
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword);
    }
}
