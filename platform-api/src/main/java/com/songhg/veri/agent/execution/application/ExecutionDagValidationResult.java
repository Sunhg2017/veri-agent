package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.execution.application.view.ExecutionNodePolicyResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionValidationIssueResponse;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import java.util.List;

record ExecutionDagValidationResult(
        String dagDigest,
        List<ExecutionPlanNode> nodes,
        List<ExecutionNodePolicyResponse> nodePolicies,
        List<ExecutionValidationIssueResponse> issues
) {

    boolean valid() {
        return issues.stream().noneMatch(issue -> !"WARN".equals(issue.severity()));
    }
}
