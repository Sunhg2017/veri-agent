package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationBundleScope;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationBundleScopeService;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.command.ExecutionDagCommand;
import com.songhg.veri.agent.execution.application.command.ExecutionDagNodeCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionNodePolicyResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionValidationIssueResponse;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExecutionDagValidator {

    private static final Pattern NODE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Set<String> NODE_TYPES = Set.of(
            "API_TEST", "UI_TEST", "SETUP", "VERIFY", "CLEANUP", "REPORT_HANDOFF"
    );
    private static final Set<String> P0_READY_NODE_TYPES = Set.of("API_TEST", "REPORT_HANDOFF");
    private static final Set<String> FAILURE_POLICIES = Set.of("FAIL_FAST", "CONTINUE", "BLOCK_DOWNSTREAM");
    private static final Set<String> SENSITIVE_INPUT_KEYS = Set.of(
            "secret", "secrets", "secretref", "secretrefs", "token", "password", "authorization", "cookie", "apikey",
            "api_key"
    );
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_TIMEOUT_SECONDS = 86_400;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ApiAutomationBundleScopeService bundleScopeService;
    private final ObjectMapper objectMapper;

    public ExecutionDagValidator(ApiAutomationBundleScopeService bundleScopeService, ObjectMapper objectMapper) {
        this.bundleScopeService = bundleScopeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Normalizes and validates a plan DAG before persistence or dry-run.
     *
     * <p>The validator intentionally stores only bounded summaries: secret-like keys are masked, resource checks use
     * application ports, and the resulting digest is computed from normalized content so idempotency and change review do
     * not depend on request field ordering.</p>
     */
    public ExecutionDagValidationResult validate(
            UUID planId,
            String projectId,
            ExecutionDagCommand dag,
            Instant now
    ) {
        List<ExecutionValidationIssueResponse> issues = new ArrayList<>();
        if (dag == null || dag.nodes() == null || dag.nodes().isEmpty()) {
            issues.add(issue("EXECUTION_DAG_EMPTY", null, "DAG 至少需要 1 个节点"));
            return new ExecutionDagValidationResult(sha256("[]"), List.of(), List.of(), List.copyOf(issues));
        }

        Map<String, NormalizedNode> nodesByKey = new LinkedHashMap<>();
        for (ExecutionDagNodeCommand rawNode : dag.nodes()) {
            NormalizedNode node = normalizeNode(rawNode, issues);
            if (node.key() == null) {
                continue;
            }
            if (nodesByKey.putIfAbsent(node.key(), node) != null) {
                issues.add(issue("EXECUTION_DAG_DUPLICATE_NODE", node.key(), "节点 key 重复"));
            }
        }

        validateDependencies(nodesByKey, issues);
        validateResources(projectId, nodesByKey.values(), issues);
        List<String> orderedKeys = topologicalOrder(nodesByKey, issues);
        List<NormalizedNode> orderedNodes = orderedKeys.stream()
                .map(nodesByKey::get)
                .toList();

        List<ExecutionPlanNode> planNodes = new ArrayList<>();
        List<ExecutionNodePolicyResponse> nodePolicies = new ArrayList<>();
        for (NormalizedNode node : orderedNodes) {
            planNodes.add(toPlanNode(planId, node, now));
            nodePolicies.add(toPolicy(node));
        }
        String dagDigest = digest(orderedNodes);
        return new ExecutionDagValidationResult(
                dagDigest,
                List.copyOf(planNodes),
                List.copyOf(nodePolicies),
                List.copyOf(issues)
        );
    }

    public List<ExecutionNodePolicyResponse> policies(List<ExecutionPlanNode> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparing(ExecutionPlanNode::nodeKey))
                .map(this::toPolicy)
                .toList();
    }

    private NormalizedNode normalizeNode(
            ExecutionDagNodeCommand rawNode,
            List<ExecutionValidationIssueResponse> issues
    ) {
        String key = normalizeKey(rawNode == null ? null : rawNode.key());
        if (!StringUtils.hasText(key) || !NODE_KEY_PATTERN.matcher(key).matches()) {
            issues.add(issue("EXECUTION_DAG_NODE_KEY_INVALID", key, "节点 key 仅允许字母、数字、下划线和连字符"));
            return new NormalizedNode(null, null, List.of(), Map.of(), DEFAULT_TIMEOUT_SECONDS, "FAIL_FAST", Map.of());
        }
        String type = upper(rawNode.type());
        if (!NODE_TYPES.contains(type)) {
            issues.add(issue("EXECUTION_DAG_NODE_TYPE_INVALID", key, "节点类型不受支持"));
        }
        String failurePolicy = StringUtils.hasText(rawNode.failurePolicy()) ? upper(rawNode.failurePolicy()) : "FAIL_FAST";
        if (!FAILURE_POLICIES.contains(failurePolicy)) {
            issues.add(issue("EXECUTION_DAG_FAILURE_POLICY_INVALID", key, "failurePolicy 不受支持"));
            failurePolicy = "FAIL_FAST";
        }
        int timeoutSeconds = normalizeTimeout(rawNode.timeoutSeconds(), key, issues);
        List<String> dependencies = normalizeDependencies(rawNode.dependencies(), key, issues);
        Map<String, Object> inputSummary = sanitizeMap(rawNode.input());
        Map<String, Object> retryPolicy = sanitizeMap(rawNode.retryPolicy());
        validateNodeInput(key, type, inputSummary, issues);
        return new NormalizedNode(key, type, dependencies, inputSummary, timeoutSeconds, failurePolicy, retryPolicy);
    }

    private void validateDependencies(
            Map<String, NormalizedNode> nodesByKey,
            List<ExecutionValidationIssueResponse> issues
    ) {
        for (NormalizedNode node : nodesByKey.values()) {
            for (String dependency : node.dependencies()) {
                if (!nodesByKey.containsKey(dependency)) {
                    issues.add(issue("EXECUTION_DAG_DEPENDENCY_MISSING", node.key(), "依赖节点不存在: " + dependency));
                }
                if (node.key().equals(dependency)) {
                    issues.add(issue("EXECUTION_DAG_SELF_DEPENDENCY", node.key(), "节点不能依赖自身"));
                }
            }
        }
    }

    private List<String> topologicalOrder(
            Map<String, NormalizedNode> nodesByKey,
            List<ExecutionValidationIssueResponse> issues
    ) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (String key : nodesByKey.keySet()) {
            indegree.put(key, 0);
            outgoing.put(key, new ArrayList<>());
        }
        for (NormalizedNode node : nodesByKey.values()) {
            for (String dependency : node.dependencies()) {
                if (nodesByKey.containsKey(dependency) && !node.key().equals(dependency)) {
                    indegree.computeIfPresent(node.key(), (ignored, value) -> value + 1);
                    outgoing.get(dependency).add(node.key());
                }
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .forEach(queue::add);
        List<String> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            ordered.add(key);
            outgoing.getOrDefault(key, List.of()).stream()
                    .sorted()
                    .forEach(next -> {
                        int nextDegree = indegree.computeIfPresent(next, (ignored, value) -> value - 1);
                        if (nextDegree == 0) {
                            queue.add(next);
                        }
                    });
        }
        if (ordered.size() != nodesByKey.size()) {
            Set<String> orderedSet = new HashSet<>(ordered);
            nodesByKey.keySet().stream()
                    .filter(key -> !orderedSet.contains(key))
                    .forEach(key -> issues.add(issue("EXECUTION_DAG_CYCLE", key, "DAG 存在循环依赖")));
            return nodesByKey.keySet().stream().sorted().toList();
        }
        return ordered;
    }

    private void validateResources(
            String projectId,
            Iterable<NormalizedNode> nodes,
            List<ExecutionValidationIssueResponse> issues
    ) {
        for (NormalizedNode node : nodes) {
            if (!"API_TEST".equals(node.type())) {
                continue;
            }
            Object bundleIdValue = node.inputSummary().get("apiAutomationBundleId");
            Optional<UUID> bundleId = uuid(bundleIdValue);
            if (bundleId.isEmpty()) {
                issues.add(issue(
                        "EXECUTION_RESOURCE_REQUIRED",
                        node.key(),
                        "API_TEST 节点必须提供 apiAutomationBundleId"
                ));
                continue;
            }
            Optional<ApiAutomationBundleScope> bundleScope = bundleScopeService.bundleScope(bundleId.get());
            if (bundleScope.isEmpty()) {
                issues.add(issue("EXECUTION_RESOURCE_NOT_FOUND", node.key(), "API_TEST 脚本包不存在"));
                continue;
            }
            if (!projectId.equals(bundleScope.get().projectId())) {
                issues.add(issue("EXECUTION_RESOURCE_SCOPE_DENIED", node.key(), "API_TEST 脚本包不属于当前项目"));
            }
            if (!"APPROVED".equals(bundleScope.get().status())) {
                issues.add(issue("EXECUTION_RESOURCE_NOT_READY", node.key(), "API_TEST 脚本包未审批通过"));
            }
        }
    }

    private void validateNodeInput(
            String key,
            String type,
            Map<String, Object> inputSummary,
            List<ExecutionValidationIssueResponse> issues
    ) {
        if (!P0_READY_NODE_TYPES.contains(type)) {
            issues.add(issue("EXECUTION_RUNNER_NOT_READY", key, type + " 节点暂未进入 P0 可执行范围"));
        }
        if ("REPORT_HANDOFF".equals(type) && inputSummary.containsKey("reportBody")) {
            issues.add(issue("EXECUTION_REPORT_BODY_FORBIDDEN", key, "WP9 只允许保存报告移交摘要"));
        }
    }

    private ExecutionPlanNode toPlanNode(UUID planId, NormalizedNode node, Instant now) {
        return new ExecutionPlanNode(
                UUID.randomUUID(),
                planId,
                node.key(),
                node.type(),
                String.join(",", node.dependencies()),
                json(node.inputSummary()),
                node.failurePolicy(),
                node.timeoutSeconds(),
                json(node.retryPolicy()),
                now,
                now
        );
    }

    private ExecutionNodePolicyResponse toPolicy(NormalizedNode node) {
        return new ExecutionNodePolicyResponse(
                node.key(),
                node.type(),
                node.dependencies(),
                node.failurePolicy(),
                node.timeoutSeconds(),
                node.retryPolicy(),
                node.inputSummary(),
                runnerType(node.type())
        );
    }

    private ExecutionNodePolicyResponse toPolicy(ExecutionPlanNode node) {
        return new ExecutionNodePolicyResponse(
                node.nodeKey(),
                node.nodeType(),
                node.dependencyKeys(),
                node.failurePolicy(),
                node.timeoutSeconds(),
                readMap(node.retryPolicyJson()),
                readMap(node.inputSummaryJson()),
                runnerType(node.nodeType())
        );
    }

    private String digest(List<NormalizedNode> nodes) {
        List<Map<String, Object>> canonical = nodes.stream()
                .map(node -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("key", node.key());
                    value.put("type", node.type());
                    value.put("dependencies", node.dependencies());
                    value.put("failurePolicy", node.failurePolicy());
                    value.put("timeoutSeconds", node.timeoutSeconds());
                    value.put("inputSummary", node.inputSummary());
                    value.put("retryPolicy", node.retryPolicy());
                    return value;
                })
                .toList();
        return sha256(json(canonical));
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), sanitizeValue(entry.getKey(), entry.getValue())));
        return result;
    }

    private Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            if (value instanceof List<?> list) {
                return Map.of("masked", true, "count", list.size());
            }
            return "***MASKED***";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> child = new LinkedHashMap<>();
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> child.put(String.valueOf(entry.getKey()),
                            sanitizeValue(String.valueOf(entry.getKey()), entry.getValue())));
            return child;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> sanitizeValue(key, item))
                    .toList();
        }
        if (value instanceof String text && text.length() > 512) {
            return text.substring(0, 512);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_INPUT_KEYS.contains(normalized);
    }

    private List<String> normalizeDependencies(
            List<String> dependencies,
            String nodeKey,
            List<ExecutionValidationIssueResponse> issues
    ) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String dependency : dependencies) {
            String value = normalizeKey(dependency);
            if (!StringUtils.hasText(value) || !NODE_KEY_PATTERN.matcher(value).matches()) {
                issues.add(issue("EXECUTION_DAG_DEPENDENCY_KEY_INVALID", nodeKey, "依赖 key 格式非法"));
                continue;
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private int normalizeTimeout(Integer timeoutSeconds, String nodeKey, List<ExecutionValidationIssueResponse> issues) {
        if (timeoutSeconds == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            issues.add(issue("EXECUTION_DAG_TIMEOUT_INVALID", nodeKey, "timeoutSeconds 必须在 1 到 86400 之间"));
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return timeoutSeconds;
    }

    private String normalizeKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private Optional<UUID> uuid(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(String.valueOf(value)));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String runnerType(String nodeType) {
        return switch (nodeType) {
            case "API_TEST" -> "WP6_API";
            case "UI_TEST" -> "WP7_UI";
            case "SETUP", "VERIFY", "CLEANUP" -> "UTILITY";
            case "REPORT_HANDOFF" -> "REPORT";
            default -> "CONTROL";
        };
    }

    private ExecutionValidationIssueResponse issue(String code, String nodeKey, String message) {
        return new ExecutionValidationIssueResponse(code, nodeKey, "ERROR", message);
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("unreadable", true);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "EXECUTION_JSON_INVALID");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record NormalizedNode(
            String key,
            String type,
            List<String> dependencies,
            Map<String, Object> inputSummary,
            int timeoutSeconds,
            String failurePolicy,
            Map<String, Object> retryPolicy
    ) {
    }
}
