package com.songhg.veri.agent.execution.infrastructure;

import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionPlanQuery;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
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

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword);
    }
}
