package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Resolves one optional WP8 dataset binding for a UI/E2E step and interpolates placeholders into the bounded
 * aggregate summaries that runners consume.
 */
public class UiE2eStepDataBindingSupport {

    public static final String DATA_BINDING_INVALID = "UI_E2E_TEST_DATA_BINDING_INVALID";
    public static final String TEMPLATE_UNRESOLVED = "UI_E2E_TEST_DATA_TEMPLATE_UNRESOLVED";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_.-]{0,127})\\s*}}");

    private final ObjectMapper objectMapper;
    private final TestDataCrossWpReferenceService testDataCrossWpReferenceService;

    public UiE2eStepDataBindingSupport(
            ObjectMapper objectMapper,
            TestDataCrossWpReferenceService testDataCrossWpReferenceService
    ) {
        this.objectMapper = objectMapper;
        this.testDataCrossWpReferenceService = testDataCrossWpReferenceService;
    }

    /**
     * Resolves every step against WP8 before runtime validation so runner readiness and actual execution use the same
     * injected view of the scene.
     */
    public List<UiE2eSceneStep> resolveSceneSteps(List<UiE2eSceneStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<UiE2eSceneStep> resolved = new ArrayList<>();
        for (UiE2eSceneStep step : steps) {
            resolved.add(resolveSceneStep(step));
        }
        return List.copyOf(resolved);
    }

    public boolean containsTemplatePlaceholder(UiE2eSceneStep step) {
        if (step == null) {
            return false;
        }
        return containsPlaceholder(readMap(step.actionSummaryJson()))
                || containsPlaceholder(readMap(step.locatorStrategyJson()))
                || containsPlaceholder(readMap(step.assertionSummaryJson()))
                || containsPlaceholder(readMap(step.waitPolicyJson()));
    }

    public boolean containsTemplatePlaceholder(Map<String, Object> value) {
        return containsPlaceholder(value);
    }

    private UiE2eSceneStep resolveSceneStep(UiE2eSceneStep step) {
        if (step == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DATA_BINDING_INVALID);
        }
        Map<String, Object> actionSummary = readMap(step.actionSummaryJson());
        Map<String, Object> locatorStrategy = readMap(step.locatorStrategyJson());
        Map<String, Object> assertionSummary = readMap(step.assertionSummaryJson());
        Map<String, Object> waitPolicy = readMap(step.waitPolicyJson());
        Map<String, Object> dataBinding = readMap(step.dataBindingJson());
        if (dataBinding.isEmpty()) {
            if (containsPlaceholder(actionSummary)
                    || containsPlaceholder(locatorStrategy)
                    || containsPlaceholder(assertionSummary)
                    || containsPlaceholder(waitPolicy)) {
                throw new BusinessException(ErrorCode.INVALID_STATE, TEMPLATE_UNRESOLVED);
            }
            return step;
        }
        if (testDataCrossWpReferenceService == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DATA_BINDING_INVALID);
        }
        TestDataCrossWpReferenceService.UiE2eStepDataBindingResolution resolution =
                testDataCrossWpReferenceService.resolveUiE2eStepDataBinding(step.projectId(), dataBinding);
        Map<String, Object> templateContext = templateContext(resolution);
        return new UiE2eSceneStep(
                step.id(),
                step.sceneId(),
                step.projectId(),
                step.stepOrder(),
                step.stepType(),
                writeJson(resolveValue(actionSummary, templateContext)),
                writeJson(resolveValue(locatorStrategy, templateContext)),
                writeJson(resolveValue(assertionSummary, templateContext)),
                writeJson(resolveValue(waitPolicy, templateContext)),
                step.dataBindingJson(),
                step.createdBy(),
                step.updatedBy(),
                step.createdAt(),
                step.updatedAt()
        );
    }

    private Map<String, Object> templateContext(TestDataCrossWpReferenceService.UiE2eStepDataBindingResolution resolution) {
        LinkedHashMap<String, Object> record = new LinkedHashMap<>();
        record.put("dataSetId", resolution.dataSetId().toString());
        record.put("dataSetCode", resolution.dataSetCode());
        record.put("dataSetStatus", resolution.dataSetStatus());
        record.put("recordCount", resolution.recordCount());
        record.put("recordKey", resolution.recordKey());
        record.put("recordDigest", resolution.recordDigest());
        record.put("externalRefDigest", resolution.externalRefDigest());
        record.put("maskedSummary", resolution.maskedSummary());
        resolution.maskedSummary().forEach(record::putIfAbsent);

        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put(resolution.bindingAlias(), Map.copyOf(record));
        return Map.copyOf(context);
    }

    private Object resolveValue(Object value, Map<String, Object> templateContext) {
        if (value instanceof Map<?, ?> valueMap) {
            LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
            valueMap.forEach((key, item) -> {
                if (key instanceof String stringKey) {
                    resolved.put(stringKey, resolveValue(item, templateContext));
                }
            });
            return Map.copyOf(resolved);
        }
        if (value instanceof List<?> values) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : values) {
                resolved.add(resolveValue(item, templateContext));
            }
            return List.copyOf(resolved);
        }
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return value;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        matcher.reset();
        if (matcher.matches()) {
            Object resolved = lookup(templateContext, matcher.group(1));
            if (resolved == null) {
                throw new BusinessException(ErrorCode.INVALID_STATE, TEMPLATE_UNRESOLVED);
            }
            return resolved;
        }
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object resolved = lookup(templateContext, matcher.group(1));
            if (resolved == null) {
                throw new BusinessException(ErrorCode.INVALID_STATE, TEMPLATE_UNRESOLVED);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(stringify(resolved)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean containsPlaceholder(Object value) {
        if (value instanceof Map<?, ?> valueMap) {
            return valueMap.values().stream().anyMatch(this::containsPlaceholder);
        }
        if (value instanceof List<?> values) {
            return values.stream().anyMatch(this::containsPlaceholder);
        }
        return value instanceof String text && PLACEHOLDER_PATTERN.matcher(text).find();
    }

    private Object lookup(Map<String, Object> templateContext, String expression) {
        if (!StringUtils.hasText(expression) || templateContext == null || templateContext.isEmpty()) {
            return null;
        }
        String[] segments = expression.split("\\.");
        Object current = templateContext;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map) || (!map.containsKey(segment))) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof UUID) {
            return value.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, TEMPLATE_UNRESOLVED);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DATA_BINDING_INVALID);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DATA_BINDING_INVALID);
        }
    }
}
