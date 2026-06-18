package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds aggregate-only WP7 bundle summaries so review and execution gates can work without persisting raw scripts.
 */
@Component
public class UiE2eBundleFactory {

    static final String STATIC_CHECK_PASSED = "PASSED";
    static final String STATIC_CHECK_FAILED = "SCRIPT_STATIC_CHECK_FAILED";

    private static final String TEMPLATE_VERSION = "wp7-playwright-summary-v1";
    private static final Set<String> ACTION_STEP_TYPES = Set.of("LOGIN", "NAVIGATE", "QUERY", "FORM_FILL", "CLICK", "APPROVAL", "EXPORT");
    private static final Set<String> NETWORK_LOCATOR_KEYS = Set.of("url", "endpoint", "baseUrl");
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of("token", "password", "secret", "cookie", "authorization");

    private final ObjectMapper objectMapper;

    public UiE2eBundleFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UiE2eBundle createBundle(
            UiE2eScene scene,
            List<UiE2eSceneStep> steps,
            String actor,
            Instant now
    ) {
        Map<String, Object> specSummary = specSummary(scene, steps);
        Map<String, Object> fixtureSummary = fixtureSummary(scene, steps);
        StaticCheckResult staticCheckResult = staticCheck(scene, steps);
        String bundleDigest = SensitiveTextSanitizer.sha256Hex(writeJson(Map.of(
                "sceneId", scene.id(),
                "templateVersion", TEMPLATE_VERSION,
                "specSummary", specSummary,
                "fixtureSummary", fixtureSummary,
                "staticCheckStatus", staticCheckResult.status()
        )));
        return new UiE2eBundle(
                UUID.randomUUID(),
                scene.id(),
                scene.projectId(),
                STATIC_CHECK_PASSED.equals(staticCheckResult.status()) ? "DRAFT" : "STATIC_CHECK_FAILED",
                bundleDigest,
                writeJson(specSummary),
                writeJson(fixtureSummary),
                writeJson(staticCheckResult.summary()),
                null,
                null,
                null,
                null,
                null,
                actor,
                actor,
                null,
                now,
                now
        );
    }

    private Map<String, Object> specSummary(UiE2eScene scene, List<UiE2eSceneStep> steps) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateVersion", TEMPLATE_VERSION);
        summary.put("sceneId", scene.id().toString());
        summary.put("sceneCode", scene.code());
        summary.put("sceneStatus", scene.status());
        summary.put("stepCount", steps.size());
        summary.put("stepTypes", steps.stream().map(UiE2eSceneStep::stepType).distinct().toList());
        summary.put("sourceSummaryDigest", SensitiveTextSanitizer.sha256Hex(scene.sourceSummaryJson()));
        summary.put("aggregateOnly", true);
        summary.put("rawDomSnapshotStored", false);
        summary.put("plaintextCredentialStored", false);
        return Map.copyOf(summary);
    }

    private Map<String, Object> fixtureSummary(UiE2eScene scene, List<UiE2eSceneStep> steps) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        LinkedHashSet<String> requiredFixtures = new LinkedHashSet<>();
        requiredFixtures.add("page");
        if (steps.stream().anyMatch(step -> "LOGIN".equals(step.stepType()))) {
            requiredFixtures.add("authenticatedSession");
        }
        if (steps.stream().anyMatch(step -> "EXPORT".equals(step.stepType()))) {
            requiredFixtures.add("downloadCapture");
        }
        if (steps.stream().anyMatch(step -> "WAIT".equals(step.stepType()))) {
            requiredFixtures.add("boundedWaitPolicy");
        }
        summary.put("requiredFixtures", List.copyOf(requiredFixtures));
        summary.put("tagCount", readTags(scene.tagsJson()).size());
        summary.put("tags", readTags(scene.tagsJson()));
        summary.put("applicationIdPresent", StringUtils.hasText(scene.applicationId()));
        summary.put("environmentIdPresent", StringUtils.hasText(scene.environmentId()));
        summary.put("networkAccessMode", "ALLOWLIST_ONLY");
        summary.put("credentialMode", "LEASE_INJECTION_ONLY");
        return Map.copyOf(summary);
    }

    private StaticCheckResult staticCheck(UiE2eScene scene, List<UiE2eSceneStep> steps) {
        List<Map<String, Object>> findings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (UiE2eSceneStep step : steps) {
            Map<String, Object> actionSummary = readMap(step.actionSummaryJson());
            Map<String, Object> locatorStrategy = readMap(step.locatorStrategyJson());
            Map<String, Object> waitPolicy = readMap(step.waitPolicyJson());

            if (ACTION_STEP_TYPES.contains(step.stepType()) && locatorStrategy.isEmpty()) {
                findings.add(finding(step, "MISSING_LOCATOR", "动作类步骤缺少 locator 策略摘要"));
            }
            if (containsNetworkEscape(locatorStrategy) || containsNetworkEscape(actionSummary)) {
                findings.add(finding(step, "UNCONTROLLED_NETWORK", "检测到未受控的网络访问入口"));
            }
            if (containsSensitivePayload(actionSummary) || containsSensitivePayload(locatorStrategy)) {
                findings.add(finding(step, "HARDCODED_SECRET", "检测到疑似硬编码凭据或敏感字段"));
            }
            if (hasInfiniteWait(waitPolicy)) {
                findings.add(finding(step, "INFINITE_WAIT", "等待策略必须是有限时长"));
            }
            if ("CUSTOM".equals(step.stepType())) {
                warnings.add("CUSTOM step requires runner-side guard before executable mode");
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", findings.isEmpty() ? STATIC_CHECK_PASSED : STATIC_CHECK_FAILED);
        summary.put("checkVersion", TEMPLATE_VERSION);
        summary.put("findingCount", findings.size());
        summary.put("warningCount", warnings.size());
        summary.put("findings", findings);
        summary.put("warnings", List.copyOf(warnings));
        summary.put("aggregateOnly", true);
        summary.put("rawScriptStored", false);
        summary.put("sceneRiskLevel", scene.riskLevel());
        return new StaticCheckResult(findings.isEmpty() ? STATIC_CHECK_PASSED : STATIC_CHECK_FAILED, Map.copyOf(summary));
    }

    private boolean containsNetworkEscape(Map<String, Object> payload) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String loweredKey = entry.getKey().toLowerCase(Locale.ROOT);
            if (NETWORK_LOCATOR_KEYS.contains(loweredKey) && containsExternalUrl(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsExternalUrl(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return text.startsWith("http://") || text.startsWith("https://") || text.startsWith("ws://") || text.startsWith("wss://");
    }

    private boolean containsSensitivePayload(Map<String, Object> payload) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String loweredKey = entry.getKey().toLowerCase(Locale.ROOT);
            if (SENSITIVE_KEYWORDS.stream().anyMatch(loweredKey::contains)) {
                return true;
            }
            Object value = entry.getValue();
            if (value != null && SensitiveTextSanitizer.containsSensitiveText(value.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInfiniteWait(Map<String, Object> waitPolicy) {
        Object timeout = waitPolicy.get("timeoutSeconds");
        if (timeout instanceof Number number) {
            return number.intValue() <= 0;
        }
        if (timeout instanceof String value && StringUtils.hasText(value)) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return "0".equals(normalized) || "infinite".equals(normalized) || "unbounded".equals(normalized);
        }
        return false;
    }

    private Map<String, Object> finding(UiE2eSceneStep step, String code, String message) {
        return Map.of(
                "stepOrder", step.stepOrder(),
                "stepType", step.stepType(),
                "code", code,
                "message", message
        );
    }

    private List<String> readTags(String tagsJson) {
        try {
            if (!StringUtils.hasText(tagsJson)) {
                return List.of();
            }
            return objectMapper.readerForListOf(String.class).readValue(tagsJson);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E bundle tags 无法解析");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return Map.of();
            }
            return objectMapper.readerForMapOf(Object.class).readValue(json);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E bundle 摘要无法解析");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E bundle 摘要无法序列化");
        }
    }

    record StaticCheckResult(String status, Map<String, Object> summary) {
    }
}
