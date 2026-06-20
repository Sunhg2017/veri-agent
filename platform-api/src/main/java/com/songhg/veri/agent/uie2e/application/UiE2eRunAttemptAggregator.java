package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.application.port.UiE2eArtifactStorage;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eArtifactManifest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.util.StringUtils;

/**
 * Aggregates one or more browser-specific runner attempts into the single WP7 run snapshot exposed to the control
 * plane, and computes optional screenshot diff evidence against a baseline run.
 */
final class UiE2eRunAttemptAggregator {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> ACTIVE_STATUSES = Set.of("QUEUED", "RUNNING");
    private static final String VISUAL_FAILURE_CODE = "UI_E2E_VISUAL_REGRESSION_FAILED";

    private final UiE2eRepository repository;
    private final UiE2eArtifactStorage artifactStorage;
    private final UiE2eProperties properties;
    private final ObjectMapper objectMapper;

    UiE2eRunAttemptAggregator(
            UiE2eRepository repository,
            UiE2eArtifactStorage artifactStorage,
            UiE2eProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.artifactStorage = artifactStorage;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    UiE2eRunnerPort.RunnerRunResult aggregate(
            UUID runId,
            UUID baselineRunId,
            UiE2eRunExecutionOptions options,
            List<BrowserAttempt> attempts
    ) {
        if (attempts == null || attempts.isEmpty()) {
            return new UiE2eRunnerPort.RunnerRunResult(
                    "BLOCKED",
                    "DISABLED",
                    "EXECUTION_RUNNER_NOT_READY",
                    "runner attempt list is empty",
                    List.of(),
                    List.of(),
                    Map.of(
                            "aggregateOnly", true,
                            "browserTypes", options == null ? List.of() : options.browserTypes(),
                            "parallelExecutionEnabled", false
                    )
            );
        }
        ComparisonAggregate comparisonAggregate = compareAgainstBaseline(runId, baselineRunId, options, attempts);
        List<UiE2eRunnerPort.RunnerStepResult> stepResults = aggregateSteps(attempts);
        List<UiE2eRunnerPort.RunnerArtifactManifest> artifacts = aggregateArtifacts(
                baselineRunId,
                attempts,
                comparisonAggregate
        );
        Map<String, Object> executionSummary = aggregateExecutionSummary(
                baselineRunId,
                options,
                attempts,
                stepResults,
                artifacts,
                comparisonAggregate
        );
        String status = aggregateStatus(attempts, comparisonAggregate);
        String failureCode = aggregateFailureCode(attempts, comparisonAggregate);
        String failureSummary = aggregateFailureSummary(attempts, comparisonAggregate);
        String runnerMode = aggregateRunnerMode(attempts);
        return new UiE2eRunnerPort.RunnerRunResult(
                status,
                runnerMode,
                failureCode,
                failureSummary,
                stepResults,
                artifacts,
                executionSummary
        );
    }

    private List<UiE2eRunnerPort.RunnerStepResult> aggregateSteps(List<BrowserAttempt> attempts) {
        Map<Integer, List<PerBrowserStep>> grouped = new LinkedHashMap<>();
        for (BrowserAttempt attempt : attempts) {
            if (attempt == null || attempt.result() == null || attempt.result().stepResults() == null) {
                continue;
            }
            for (UiE2eRunnerPort.RunnerStepResult item : attempt.result().stepResults()) {
                if (item == null) {
                    continue;
                }
                grouped.computeIfAbsent(Math.max(item.stepOrder(), 1), ignored -> new ArrayList<>())
                        .add(new PerBrowserStep(attempt.browserType(), item));
            }
        }
        List<UiE2eRunnerPort.RunnerStepResult> results = new ArrayList<>();
        for (Map.Entry<Integer, List<PerBrowserStep>> entry : grouped.entrySet()) {
            List<PerBrowserStep> items = entry.getValue();
            if (items.isEmpty()) {
                continue;
            }
            UiE2eRunnerPort.RunnerStepResult exemplar = items.getFirst().result();
            Map<String, Object> summary = new LinkedHashMap<>(safeMap(exemplar.summary()));
            summary.put("browserCount", items.size());
            summary.put("browserResults", items.stream().map(this::browserStepSummary).toList());
            summary.put("aggregateOnly", true);
            results.add(new UiE2eRunnerPort.RunnerStepResult(
                    exemplar.sceneStepId(),
                    entry.getKey(),
                    aggregateStepStatus(items),
                    items.stream().map(PerBrowserStep::durationMs).max(Integer::compareTo).orElse(0),
                    aggregateFailureBucket(items),
                    aggregateStepErrorCode(items),
                    Map.copyOf(summary)
            ));
        }
        return results.stream()
                .sorted(Comparator.comparingInt(UiE2eRunnerPort.RunnerStepResult::stepOrder))
                .toList();
    }

    private List<UiE2eRunnerPort.RunnerArtifactManifest> aggregateArtifacts(
            UUID baselineRunId,
            List<BrowserAttempt> attempts,
            ComparisonAggregate comparisonAggregate
    ) {
        List<UiE2eRunnerPort.RunnerArtifactManifest> artifacts = new ArrayList<>();
        for (BrowserAttempt attempt : attempts) {
            if (attempt == null || attempt.result() == null || attempt.result().artifacts() == null) {
                continue;
            }
            for (UiE2eRunnerPort.RunnerArtifactManifest artifact : attempt.result().artifacts()) {
                if (artifact == null) {
                    continue;
                }
                Map<String, Object> flags = new LinkedHashMap<>(safeMap(artifact.redactionFlags()));
                flags.put("browserType", attempt.browserType());
                if ("SCREENSHOT".equalsIgnoreCase(artifact.artifactType())) {
                    flags.put("visualRole", "ACTUAL");
                }
                artifacts.add(new UiE2eRunnerPort.RunnerArtifactManifest(
                        artifact.artifactType(),
                        artifact.storageRef(),
                        artifact.artifactDigest(),
                        artifact.sizeBytes(),
                        Map.copyOf(flags),
                        artifact.captureStatus()
                ));
            }
        }
        for (VisualComparison comparison : comparisonAggregate.comparisons()) {
            if (comparison.baselineArtifact() != null) {
                Map<String, Object> flags = new LinkedHashMap<>(readMap(comparison.baselineArtifact().redactionFlagsJson()));
                flags.put("browserType", comparison.browserType());
                flags.put("visualRole", "BASELINE");
                flags.put("visualBaselineRunId", baselineRunId == null ? null : baselineRunId.toString());
                artifacts.add(new UiE2eRunnerPort.RunnerArtifactManifest(
                        "SCREENSHOT",
                        comparison.baselineArtifact().storageRef(),
                        comparison.baselineArtifact().artifactDigest(),
                        comparison.baselineArtifact().sizeBytes(),
                        Map.copyOf(flags),
                        comparison.baselineArtifact().captureStatus()
                ));
            }
            if (comparison.diffArtifact() != null) {
                artifacts.add(comparison.diffArtifact());
            }
        }
        return artifacts.stream().limit(properties.effectiveMaxArtifactCount()).toList();
    }

    private Map<String, Object> aggregateExecutionSummary(
            UUID baselineRunId,
            UiE2eRunExecutionOptions options,
            List<BrowserAttempt> attempts,
            List<UiE2eRunnerPort.RunnerStepResult> stepResults,
            List<UiE2eRunnerPort.RunnerArtifactManifest> artifacts,
            ComparisonAggregate comparisonAggregate
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aggregateOnly", true);
        summary.put("browserTypes", attempts.stream().map(BrowserAttempt::browserType).distinct().toList());
        summary.put("browserCount", attempts.size());
        summary.put("parallelExecutionEnabled", attempts.size() > 1);
        summary.put("browserRuns", attempts.stream().map(this::browserRunSummary).toList());
        summary.put("browserStatusCounts", counts(attempts.stream().map(attempt -> attempt.result().status()).toList()));
        summary.put("visualRegressionEnabled", options != null && options.visualRegressionEnabled());
        if (baselineRunId != null) {
            summary.put("visualBaselineRunId", baselineRunId.toString());
        }
        if (options != null && options.visualMismatchThreshold() != null) {
            summary.put("visualMismatchThreshold", options.visualMismatchThreshold());
        }
        summary.put("visualComparedBrowsers", comparisonAggregate.comparedBrowserTypes());
        summary.put("visualMismatchBrowsers", comparisonAggregate.mismatchBrowserTypes());
        summary.put("visualMismatchExceeded", comparisonAggregate.thresholdExceeded());
        summary.put("visualComparisonCount", comparisonAggregate.comparisonCount());
        summary.put("visualMismatchCount", comparisonAggregate.mismatchCount());
        summary.put("visualDiffArtifactCount", comparisonAggregate.diffArtifactCount());
        summary.put("stepResultCount", stepResults.size());
        summary.put("artifactManifestCount", artifacts.size());
        return Map.copyOf(summary);
    }

    private ComparisonAggregate compareAgainstBaseline(
            UUID runId,
            UUID baselineRunId,
            UiE2eRunExecutionOptions options,
            List<BrowserAttempt> attempts
    ) {
        if (runId == null || artifactStorage == null || options == null || !options.visualRegressionEnabled() || baselineRunId == null) {
            return ComparisonAggregate.empty();
        }
        Map<String, UiE2eArtifactManifest> baselineByBrowser = baselineScreenshotByBrowser(baselineRunId);
        List<VisualComparison> comparisons = new ArrayList<>();
        for (BrowserAttempt attempt : attempts) {
            if (attempt == null || attempt.result() == null) {
                continue;
            }
            UiE2eRunnerPort.RunnerArtifactManifest actual = actualScreenshot(attempt.result().artifacts());
            UiE2eArtifactManifest baseline = baselineByBrowser.get(attempt.browserType());
            if (actual == null || baseline == null) {
                comparisons.add(new VisualComparison(attempt.browserType(), baseline, null, null, false, 0D, false, "baselineOrActualScreenshotMissing"));
                continue;
            }
            comparisons.add(compare(runId, attempt.browserType(), actual, baseline, options.visualMismatchThreshold()));
        }
        return new ComparisonAggregate(comparisons);
    }

    private VisualComparison compare(
            UUID runId,
            String browserType,
            UiE2eRunnerPort.RunnerArtifactManifest actual,
            UiE2eArtifactManifest baseline,
            Double threshold
    ) {
        Path tempFile = null;
        try {
            UiE2eArtifactStorage.StoredArtifactContent actualContent = artifactStorage.read(actual.storageRef());
            UiE2eArtifactStorage.StoredArtifactContent baselineContent = artifactStorage.read(baseline.storageRef());
            BufferedImage actualImage = ImageIO.read(new ByteArrayInputStream(actualContent.content()));
            BufferedImage baselineImage = ImageIO.read(new ByteArrayInputStream(baselineContent.content()));
            if (actualImage == null || baselineImage == null) {
                return new VisualComparison(browserType, baseline, null, null, false, 0D, false, "imageDecodeFailed");
            }
            BufferedImage diffImage = new BufferedImage(
                    Math.max(actualImage.getWidth(), baselineImage.getWidth()),
                    Math.max(actualImage.getHeight(), baselineImage.getHeight()),
                    BufferedImage.TYPE_INT_ARGB
            );
            long total = (long) diffImage.getWidth() * diffImage.getHeight();
            long diffCount = 0L;
            for (int y = 0; y < diffImage.getHeight(); y++) {
                for (int x = 0; x < diffImage.getWidth(); x++) {
                    int actualRgb = pixel(actualImage, x, y);
                    int baselineRgb = pixel(baselineImage, x, y);
                    boolean matched = actualRgb == baselineRgb;
                    if (!matched) {
                        diffCount++;
                        diffImage.setRGB(x, y, 0xffff00ff);
                    } else {
                        diffImage.setRGB(x, y, fade(actualRgb));
                    }
                }
            }
            double mismatchRatio = total == 0L ? 0D : (double) diffCount / (double) total;
            boolean passed = threshold == null ? diffCount == 0L : mismatchRatio <= threshold;
            tempFile = Files.createTempFile("wp7-visual-diff-", ".png");
            ImageIO.write(diffImage, "png", tempFile.toFile());
            UiE2eArtifactStorage.StoredArtifact stored = artifactStorage.store(
                    runId,
                    UUID.randomUUID(),
                    "SCREENSHOT",
                    tempFile
            );
            Map<String, Object> flags = new LinkedHashMap<>();
            flags.put("browserType", browserType);
            flags.put("visualRole", "DIFF");
            flags.put("visualBaselineDigest", baseline.artifactDigest());
            flags.put("visualActualDigest", actual.artifactDigest());
            flags.put("visualCompared", true);
            flags.put("visualPassed", passed);
            flags.put("visualMismatchRatio", mismatchRatio);
            return new VisualComparison(
                    browserType,
                    baseline,
                    new UiE2eRunnerPort.RunnerArtifactManifest(
                            "SCREENSHOT",
                            stored.storageRef(),
                            SensitiveTextSanitizer.sha256Hex(stored.storageRef() + ":" + mismatchRatio),
                            stored.sizeBytes(),
                            Map.copyOf(flags),
                            "CAPTURED"
                    ),
                    mismatchRatio,
                    true,
                    mismatchRatio,
                    !passed,
                    null
            );
        } catch (IOException exception) {
            return new VisualComparison(browserType, baseline, null, null, false, 0D, false, "visualDiffUnavailable");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup only.
                }
            }
        }
    }

    private Map<String, UiE2eArtifactManifest> baselineScreenshotByBrowser(UUID baselineRunId) {
        Map<String, UiE2eArtifactManifest> result = new LinkedHashMap<>();
        for (UiE2eArtifactManifest artifact : repository.artifacts(baselineRunId)) {
            if (artifact == null || !"SCREENSHOT".equalsIgnoreCase(artifact.artifactType())) {
                continue;
            }
            Map<String, Object> flags = readMap(artifact.redactionFlagsJson());
            String visualRole = stringValue(flags.get("visualRole"));
            if ("DIFF".equalsIgnoreCase(visualRole) || "BASELINE".equalsIgnoreCase(visualRole)) {
                continue;
            }
            String browserType = stringValue(flags.get("browserType"));
            if (!StringUtils.hasText(browserType)) {
                browserType = "CHROMIUM";
            }
            result.putIfAbsent(browserType.trim().toUpperCase(Locale.ROOT), artifact);
        }
        return result;
    }

    private UiE2eRunnerPort.RunnerArtifactManifest actualScreenshot(List<UiE2eRunnerPort.RunnerArtifactManifest> artifacts) {
        if (artifacts == null) {
            return null;
        }
        return artifacts.stream()
                .filter(Objects::nonNull)
                .filter(artifact -> "SCREENSHOT".equalsIgnoreCase(artifact.artifactType()))
                .filter(artifact -> "CAPTURED".equalsIgnoreCase(artifact.captureStatus()))
                .filter(artifact -> StringUtils.hasText(artifact.storageRef()))
                .findFirst()
                .orElse(null);
    }

    private String aggregateStatus(List<BrowserAttempt> attempts, ComparisonAggregate comparisonAggregate) {
        if (comparisonAggregate.thresholdExceeded()) {
            return "FAILED";
        }
        Set<String> statuses = attempts.stream()
                .map(BrowserAttempt::result)
                .filter(Objects::nonNull)
                .map(UiE2eRunnerPort.RunnerRunResult::status)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (statuses.stream().anyMatch(ACTIVE_STATUSES::contains)) {
            return statuses.contains("RUNNING") ? "RUNNING" : "QUEUED";
        }
        if (statuses.contains("FAILED")) {
            return "FAILED";
        }
        if (statuses.contains("TIMEOUT")) {
            return "TIMEOUT";
        }
        if (statuses.contains("BLOCKED")) {
            return "BLOCKED";
        }
        if (statuses.contains("CANCELED")) {
            return "CANCELED";
        }
        return "SUCCEEDED";
    }

    private String aggregateFailureCode(List<BrowserAttempt> attempts, ComparisonAggregate comparisonAggregate) {
        if (comparisonAggregate.thresholdExceeded()) {
            return VISUAL_FAILURE_CODE;
        }
        return attempts.stream()
                .map(BrowserAttempt::result)
                .filter(Objects::nonNull)
                .map(UiE2eRunnerPort.RunnerRunResult::failureCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String aggregateFailureSummary(List<BrowserAttempt> attempts, ComparisonAggregate comparisonAggregate) {
        if (comparisonAggregate.thresholdExceeded()) {
            return "visual regression mismatch exceeded threshold on " + String.join(", ", comparisonAggregate.mismatchBrowserTypes());
        }
        return attempts.stream()
                .map(BrowserAttempt::result)
                .filter(Objects::nonNull)
                .filter(result -> StringUtils.hasText(result.failureSummary()))
                .map(result -> String.format(Locale.ROOT, "%s: %s", firstBrowserType(attempts, result), result.failureSummary()))
                .findFirst()
                .orElse(null);
    }

    private String aggregateRunnerMode(List<BrowserAttempt> attempts) {
        return attempts.stream()
                .map(BrowserAttempt::result)
                .filter(Objects::nonNull)
                .map(UiE2eRunnerPort.RunnerRunResult::runnerMode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("DISABLED");
    }

    private String aggregateStepStatus(List<PerBrowserStep> items) {
        Set<String> statuses = items.stream()
                .map(PerBrowserStep::status)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (statuses.stream().anyMatch(ACTIVE_STATUSES::contains)) {
            return statuses.contains("RUNNING") ? "RUNNING" : "PENDING";
        }
        if (statuses.contains("FAILED")) {
            return "FAILED";
        }
        if (statuses.contains("TIMEOUT")) {
            return "TIMEOUT";
        }
        if (statuses.contains("BLOCKED")) {
            return "BLOCKED";
        }
        if (statuses.contains("CANCELED")) {
            return "CANCELED";
        }
        if (statuses.contains("SKIPPED")) {
            return "SKIPPED";
        }
        return "SUCCEEDED";
    }

    private String aggregateFailureBucket(List<PerBrowserStep> items) {
        return items.stream()
                .map(PerBrowserStep::failureBucket)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String aggregateStepErrorCode(List<PerBrowserStep> items) {
        return items.stream()
                .map(PerBrowserStep::errorCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> browserRunSummary(BrowserAttempt attempt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("browserType", attempt.browserType());
        summary.put("status", attempt.result().status());
        if (StringUtils.hasText(attempt.result().failureCode())) {
            summary.put("failureCode", attempt.result().failureCode());
        }
        summary.put("stepResultCount", attempt.result().stepResults() == null ? 0 : attempt.result().stepResults().size());
        summary.put("artifactCount", attempt.result().artifacts() == null ? 0 : attempt.result().artifacts().size());
        return Map.copyOf(summary);
    }

    private Map<String, Object> browserStepSummary(PerBrowserStep step) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("browserType", step.browserType());
        summary.put("status", step.status());
        summary.put("durationMs", step.durationMs());
        if (StringUtils.hasText(step.failureBucket())) {
            summary.put("failureBucket", step.failureBucket());
        }
        if (StringUtils.hasText(step.errorCode())) {
            summary.put("errorCode", step.errorCode());
        }
        summary.put("summary", safeMap(step.result().summary()));
        return Map.copyOf(summary);
    }

    private Map<String, Integer> counts(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            counts.merge(normalized, 1, Integer::sum);
        }
        return counts;
    }

    private String firstBrowserType(List<BrowserAttempt> attempts, UiE2eRunnerPort.RunnerRunResult result) {
        return attempts.stream()
                .filter(attempt -> attempt.result() == result)
                .map(BrowserAttempt::browserType)
                .findFirst()
                .orElse("UNKNOWN");
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private static int pixel(BufferedImage image, int x, int y) {
        if (image == null || x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return 0;
        }
        return image.getRGB(x, y);
    }

    private static int fade(int rgb) {
        int alpha = (rgb >>> 24) & 0xff;
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        int faded = ((red + green + blue) / 3);
        return (alpha << 24) | (faded << 16) | (faded << 8) | faded;
    }

    record BrowserAttempt(String browserType, UiE2eRunnerPort.RunnerRunResult result) {
    }

    private record PerBrowserStep(String browserType, UiE2eRunnerPort.RunnerStepResult result) {

        String status() {
            return result.status() == null ? "BLOCKED" : result.status().trim().toUpperCase(Locale.ROOT);
        }

        int durationMs() {
            return Math.max(0, result.durationMs());
        }

        String failureBucket() {
            return result.failureBucket();
        }

        String errorCode() {
            return result.errorCode();
        }
    }

    private record VisualComparison(
            String browserType,
            UiE2eArtifactManifest baselineArtifact,
            UiE2eRunnerPort.RunnerArtifactManifest diffArtifact,
            Double mismatchRatioValue,
            boolean compared,
            double mismatchRatio,
            boolean mismatch,
            String errorCode
    ) {
    }

    private record ComparisonAggregate(List<VisualComparison> comparisons) {

        static ComparisonAggregate empty() {
            return new ComparisonAggregate(List.of());
        }

        boolean thresholdExceeded() {
            return comparisons.stream().anyMatch(VisualComparison::mismatch);
        }

        long comparisonCount() {
            return comparisons.stream().filter(VisualComparison::compared).count();
        }

        int mismatchCount() {
            return (int) comparisons.stream().filter(VisualComparison::mismatch).count();
        }

        int diffArtifactCount() {
            return (int) comparisons.stream().filter(item -> item.diffArtifact() != null).count();
        }

        List<String> comparedBrowserTypes() {
            return comparisons.stream()
                    .filter(VisualComparison::compared)
                    .map(VisualComparison::browserType)
                    .toList();
        }

        List<String> mismatchBrowserTypes() {
            return comparisons.stream()
                    .filter(VisualComparison::mismatch)
                    .map(VisualComparison::browserType)
                    .toList();
        }
    }
}
