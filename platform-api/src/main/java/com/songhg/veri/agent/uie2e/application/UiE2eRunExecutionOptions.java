package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Normalizes optional browser-matrix and visual-regression switches so the rest of the run pipeline can stay
 * deterministic and backward compatible with legacy single-browser requests.
 */
public record UiE2eRunExecutionOptions(
        List<String> browserTypes,
        boolean visualRegressionEnabled,
        UUID baselineRunId,
        Double visualMismatchThreshold
) {

    private static final Set<String> SUPPORTED_BROWSERS = Set.of("CHROMIUM", "FIREFOX", "WEBKIT");
    private static final String DEFAULT_BROWSER = "CHROMIUM";

    public static UiE2eRunExecutionOptions from(CreateUiE2eRunCommand command) {
        if (command == null) {
            return new UiE2eRunExecutionOptions(List.of(DEFAULT_BROWSER), false, null, null);
        }
        List<String> browsers = normalizeBrowsers(command.browsers());
        boolean visualRegressionEnabled = Boolean.TRUE.equals(command.visualRegressionEnabled());
        Double threshold = normalizeThreshold(command.visualMismatchThreshold());
        UUID baselineRunId = command.baselineRunId();
        return new UiE2eRunExecutionOptions(browsers, visualRegressionEnabled, baselineRunId, threshold);
    }

    public String requestSummaryKey() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", browserTypes));
        builder.append('|').append(visualRegressionEnabled);
        builder.append('|').append(baselineRunId == null ? "" : baselineRunId);
        builder.append('|').append(visualMismatchThreshold == null ? "" : SensitiveTextSanitizer.boundedText(
                String.format(Locale.ROOT, "%.4f", visualMismatchThreshold),
                16
        ));
        return builder.toString();
    }

    private static List<String> normalizeBrowsers(List<String> browsers) {
        if (browsers == null || browsers.isEmpty()) {
            return List.of(DEFAULT_BROWSER);
        }
        List<String> normalized = new ArrayList<>();
        for (String browser : browsers) {
            String safe = normalizeBrowser(browser);
            if (safe != null) {
                normalized.add(safe);
            }
        }
        if (normalized.isEmpty()) {
            return List.of(DEFAULT_BROWSER);
        }
        return List.copyOf(new LinkedHashSet<>(normalized));
    }

    private static String normalizeBrowser(String browser) {
        if (!StringUtils.hasText(browser)) {
            return null;
        }
        String normalized = SensitiveTextSanitizer.boundedText(browser.trim().toUpperCase(Locale.ROOT), 32);
        if (!SUPPORTED_BROWSERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BROWSER_NOT_SUPPORTED");
        }
        return normalized;
    }

    private static Double normalizeThreshold(Double threshold) {
        if (threshold == null) {
            return null;
        }
        if (Double.isNaN(threshold) || Double.isInfinite(threshold) || threshold < 0D || threshold > 1D) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_VISUAL_THRESHOLD_INVALID");
        }
        return threshold;
    }
}
