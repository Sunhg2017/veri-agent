package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.application.command.ImportUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneImportResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneImportStepResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Parses external Selenium IDE / Playwright codegen snippets into WP7 scene drafts so operators can review and
 * normalize imported steps before persisting or executing them.
 */
@Service
public class UiE2eSceneImportService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of("SELENIUM_IDE", "PLAYWRIGHT_CODEGEN");
    private static final Pattern PAGE_GOTO = Pattern.compile("page\\.goto\\((['\"])(.+?)\\1");
    private static final Pattern PAGE_FILL = Pattern.compile("page\\.(?:locator\\((['\"])(.+?)\\1\\)|getByLabel\\((['\"])(.+?)\\3\\)|getByPlaceholder\\((['\"])(.+?)\\5\\)|getByRole\\((['\"])textbox\\7\\s*,\\s*\\{\\s*name\\s*:\\s*(['\"])(.+?)\\8\\s*\\}\\)|getByTestId\\((['\"])(.+?)\\10\\))\\.fill\\((.+?)\\)");
    private static final Pattern PAGE_CLICK = Pattern.compile("page\\.(?:locator\\((['\"])(.+?)\\1\\)|getByLabel\\((['\"])(.+?)\\3\\)|getByText\\((['\"])(.+?)\\5\\)|getByRole\\((['\"])(button|link)\\7\\s*,\\s*\\{\\s*name\\s*:\\s*(['\"])(.+?)\\9\\s*\\}\\)|getByTestId\\((['\"])(.+?)\\11\\))\\.click\\(");
    private static final Pattern EXPECT_URL = Pattern.compile("expect\\(page\\)\\.toHaveURL\\((?:/.*?/(?:[gimuy]*)|(['\"])(.+?)\\1)\\)");
    private static final Pattern EXPECT_TEXT = Pattern.compile("expect\\(page\\.(?:getByText|getByRole|getByLabel|getByPlaceholder|getByTestId|locator)[^\\n]*?\\)\\.to(?:Contain)?HaveText\\((['\"])(.+?)\\1\\)");
    private static final Pattern WAIT_FOR_TIMEOUT = Pattern.compile("page\\.waitForTimeout\\((\\d+)\\)");
    private static final Pattern WAIT_FOR_URL = Pattern.compile("page\\.waitForURL\\((?:/.*?/(?:[gimuy]*)|(['\"])(.+?)\\1)");
    private static final Pattern VALUE_PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}|process\\.env\\.[A-Z0-9_]+", Pattern.CASE_INSENSITIVE);

    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eProperties properties;
    private final ObjectMapper objectMapper;

    public UiE2eSceneImportService(
            UiE2ePlatformContextClient contextClient,
            UiE2eProperties properties,
            ObjectMapper objectMapper
    ) {
        this.contextClient = contextClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Converts external recorder output into a governed WP7 draft so teams can review step semantics, keep
     * credentials lease-based, and avoid storing raw scripts or bypassing scene lifecycle controls.
     */
    public UiE2eSceneImportResponse importScene(ImportUiE2eSceneCommand command) {
        assertEnabled();
        String projectId = contextClient.projectContext(command.projectId()).resourceId();
        String sourceType = normalizeSourceType(command.sourceType());
        String content = boundedContent(command.content());
        ImportResult importResult = "SELENIUM_IDE".equals(sourceType)
                ? importSeleniumIde(content)
                : importPlaywrightCodegen(content);
        if (importResult.steps().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入内容未解析出可用步骤");
        }
        String code = resolveCode(command.codeHint(), importResult.defaultCode(), importResult.sourceLabel());
        String name = resolveName(command.nameHint(), importResult.defaultName(), importResult.sourceLabel());
        List<String> tags = normalizeTags(command.tags(), importResult.defaultTags());
        Map<String, Object> sourceSummary = sourceSummary(projectId, sourceType, content, importResult, tags);
        List<UiE2eSceneImportStepResponse> steps = toResponse(importResult.steps());
        UiE2eSceneImportResponse response = new UiE2eSceneImportResponse(
                projectId,
                boundedNullable(command.applicationId(), 64),
                boundedNullable(command.environmentId(), 64),
                code,
                name,
                "DRAFT",
                importResult.riskLevel(),
                tags,
                sourceSummary,
                steps,
                List.copyOf(importResult.warnings()),
                importSummary(sourceType, importResult, steps.size())
        );
        auditImport(response);
        return response;
    }

    private ImportResult importSeleniumIde(String content) {
        Map<String, Object> root = readMap(content);
        Object testsNode = root.get("tests");
        if (!(testsNode instanceof List<?> tests) || tests.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Selenium IDE 导出缺少 tests");
        }
        Object firstTest = tests.get(0);
        if (!(firstTest instanceof Map<?, ?> firstTestMap)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Selenium IDE tests 结构非法");
        }
        Object commandsNode = firstTestMap.get("commands");
        if (!(commandsNode instanceof List<?> commands) || commands.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Selenium IDE 导出缺少 commands");
        }
        List<ImportedStep> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        LoginDraft loginDraft = new LoginDraft();
        int unsupportedCount = 0;
        for (Object item : commands) {
            if (!(item instanceof Map<?, ?> command)) {
                warnings.add("忽略非对象命令节点");
                continue;
            }
            String rawCommand = text(command.get("command")).toLowerCase(Locale.ROOT);
            String target = text(command.get("target"));
            String value = text(command.get("value"));
            switch (rawCommand) {
                case "open" -> steps.add(importedNavigateStep(target));
                case "type", "sendkeys" -> consumeSeleniumType(target, value, loginDraft, steps, warnings);
                case "click", "clickat" -> consumeSeleniumClick(target, loginDraft, steps, warnings);
                case "asserttext", "verifytext", "assertelementpresent", "verifyelementpresent" ->
                        steps.add(importedAssertStep(target, value, rawCommand));
                case "pause" -> steps.add(importedWaitStep(value));
                case "waitfortext", "waitforelementvisible", "waitforelementpresent" ->
                        steps.add(importedWaitForElementStep(target, value, rawCommand));
                default -> {
                    unsupportedCount++;
                    warnings.add("未直接映射 Selenium IDE 命令: " + rawCommand);
                }
            }
        }
        flushLoginDraft(loginDraft, steps, warnings);
        List<String> defaultTags = new ArrayList<>();
        defaultTags.add("imported");
        defaultTags.add("selenium-ide");
        return new ImportResult(
                slug(text(firstTestMap.get("name")), "selenium-ide-scene"),
                StringUtils.hasText(text(firstTestMap.get("name"))) ? text(firstTestMap.get("name")) : "Selenium IDE 导入场景",
                List.copyOf(steps),
                List.copyOf(warnings),
                defaultTags,
                unsupportedCount > 0 ? "HIGH" : "MEDIUM",
                "Selenium IDE"
        );
    }

    private void consumeSeleniumType(
            String target,
            String value,
            LoginDraft loginDraft,
            List<ImportedStep> steps,
            List<String> warnings
    ) {
        String selector = normalizeSeleniumLocator(target);
        if (!StringUtils.hasText(selector)) {
            warnings.add("忽略缺少 target 的 type 命令");
            return;
        }
        String normalizedValue = normalizeFilledValue(value);
        if (isPasswordValue(value) || isPasswordSelector(selector)) {
            loginDraft.passwordSelector = selector;
            loginDraft.passwordValue = normalizedValue;
            return;
        }
        if (isUsernameValue(value) || isUsernameSelector(selector)) {
            loginDraft.usernameSelector = selector;
            loginDraft.usernameValue = normalizedValue;
            return;
        }
        flushLoginDraft(loginDraft, steps, warnings);
        steps.add(importedFormFillStep(selector, normalizedValue, "selenium-type"));
    }

    private void consumeSeleniumClick(
            String target,
            LoginDraft loginDraft,
            List<ImportedStep> steps,
            List<String> warnings
    ) {
        String selector = normalizeSeleniumLocator(target);
        if (loginDraft.ready()) {
            loginDraft.submitAction = "click";
            loginDraft.submitSelector = selector;
            flushLoginDraft(loginDraft, steps, warnings);
            return;
        }
        if (!StringUtils.hasText(selector)) {
            warnings.add("忽略缺少 target 的 click 命令");
            return;
        }
        steps.add(importedClickStep(selector, "selenium-click"));
    }

    private void flushLoginDraft(LoginDraft loginDraft, List<ImportedStep> steps, List<String> warnings) {
        if (!loginDraft.ready()) {
            loginDraft.reset();
            return;
        }
        if (!StringUtils.hasText(loginDraft.usernameSelector) || !StringUtils.hasText(loginDraft.passwordSelector)) {
            warnings.add("登录步骤仅部分识别，已按普通表单填充处理");
            if (StringUtils.hasText(loginDraft.usernameSelector)) {
                steps.add(importedFormFillStep(loginDraft.usernameSelector, loginDraft.usernameValue, "login-fragment"));
            }
            if (StringUtils.hasText(loginDraft.passwordSelector)) {
                steps.add(importedFormFillStep(loginDraft.passwordSelector, loginDraft.passwordValue, "login-fragment"));
            }
            loginDraft.reset();
            return;
        }
        LinkedHashMap<String, Object> actionSummary = new LinkedHashMap<>();
        actionSummary.put("principalField", loginDraft.usernameSelector);
        actionSummary.put("credentialField", loginDraft.passwordSelector);
        actionSummary.put("submitAction", StringUtils.hasText(loginDraft.submitAction()) ? loginDraft.submitAction() : "click");
        if (StringUtils.hasText(loginDraft.submitSelector)) {
            actionSummary.put("submitSelector", loginDraft.submitSelector);
        }
        if (StringUtils.hasText(loginDraft.usernameValue)) {
            actionSummary.put("samplePrincipal", SensitiveTextSanitizer.sanitizedEvidenceText(loginDraft.usernameValue, 64));
        }
        LinkedHashMap<String, Object> locatorStrategy = new LinkedHashMap<>();
        locatorStrategy.put("preferred", "selector");
        locatorStrategy.put("usernameSelector", loginDraft.usernameSelector);
        locatorStrategy.put("passwordSelector", loginDraft.passwordSelector);
        if (StringUtils.hasText(loginDraft.submitSelector)) {
            locatorStrategy.put("submitSelector", loginDraft.submitSelector);
        }
        steps.add(new ImportedStep(
                "LOGIN",
                Map.copyOf(actionSummary),
                Map.copyOf(locatorStrategy),
                Map.of(),
                Map.of("timeoutSeconds", 5)
        ));
        loginDraft.reset();
    }

    private ImportResult importPlaywrightCodegen(String content) {
        String[] lines = content.split("\\r?\\n");
        List<ImportedStep> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> defaultTags = new ArrayList<>();
        defaultTags.add("imported");
        defaultTags.add("playwright-codegen");
        LoginDraft loginDraft = new LoginDraft();
        int unsupportedCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("//")) {
                continue;
            }
            if (matchGoto(trimmed, steps)) {
                flushLoginDraft(loginDraft, steps, warnings);
                continue;
            }
            if (matchFill(trimmed, loginDraft, steps, warnings)) {
                continue;
            }
            if (matchClick(trimmed, loginDraft, steps, warnings)) {
                continue;
            }
            if (matchExpectUrl(trimmed, steps)) {
                flushLoginDraft(loginDraft, steps, warnings);
                continue;
            }
            if (matchExpectText(trimmed, steps)) {
                flushLoginDraft(loginDraft, steps, warnings);
                continue;
            }
            if (matchWait(trimmed, steps)) {
                flushLoginDraft(loginDraft, steps, warnings);
                continue;
            }
            if (trimmed.contains("await page.") || trimmed.contains("expect(")) {
                unsupportedCount++;
                warnings.add("未直接映射 Playwright 语句: " + SensitiveTextSanitizer.boundedWithEllipsis(trimmed, 120));
            }
        }
        flushLoginDraft(loginDraft, steps, warnings);
        return new ImportResult(
                slug(extractTestTitle(content), "playwright-codegen-scene"),
                StringUtils.hasText(extractTestTitle(content)) ? extractTestTitle(content) : "Playwright codegen 导入场景",
                List.copyOf(steps),
                List.copyOf(warnings),
                defaultTags,
                unsupportedCount > 0 ? "HIGH" : "MEDIUM",
                "Playwright codegen"
        );
    }

    private boolean matchGoto(String line, List<ImportedStep> steps) {
        Matcher matcher = PAGE_GOTO.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        steps.add(importedNavigateStep(matcher.group(2)));
        return true;
    }

    private boolean matchFill(
            String line,
            LoginDraft loginDraft,
            List<ImportedStep> steps,
            List<String> warnings
    ) {
        Matcher matcher = PAGE_FILL.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        String selector = normalizePlaywrightFillLocator(matcher);
        String value = normalizeFilledValue(matcher.group(12));
        if (isPasswordValue(value) || isPasswordSelector(selector)) {
            loginDraft.passwordSelector = selector;
            loginDraft.passwordValue = value;
            return true;
        }
        if (isUsernameValue(value) || isUsernameSelector(selector)) {
            loginDraft.usernameSelector = selector;
            loginDraft.usernameValue = value;
            return true;
        }
        flushLoginDraft(loginDraft, steps, warnings);
        steps.add(importedFormFillStep(selector, value, "playwright-fill"));
        return true;
    }

    private boolean matchClick(
            String line,
            LoginDraft loginDraft,
            List<ImportedStep> steps,
            List<String> warnings
    ) {
        Matcher matcher = PAGE_CLICK.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        String selector = normalizePlaywrightClickLocator(matcher);
        if (loginDraft.ready()) {
            loginDraft.submitAction = "click";
            loginDraft.submitSelector = selector;
            flushLoginDraft(loginDraft, steps, warnings);
            return true;
        }
        steps.add(importedClickStep(selector, "playwright-click"));
        return true;
    }

    private boolean matchExpectUrl(String line, List<ImportedStep> steps) {
        Matcher matcher = EXPECT_URL.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        String expected = matcher.group(2);
        if (!StringUtils.hasText(expected)) {
            expected = extractRegexBody(line);
        }
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        steps.add(new ImportedStep(
                "ASSERT",
                Map.of("mode", "url"),
                Map.of(),
                Map.of(
                        "urlContains", expected,
                        "successSignal", "url contains " + expected
                ),
                Map.of("timeoutSeconds", 5)
        ));
        return true;
    }

    private boolean matchExpectText(String line, List<ImportedStep> steps) {
        Matcher matcher = EXPECT_TEXT.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        String expected = matcher.group(2);
        steps.add(new ImportedStep(
                "ASSERT",
                Map.of("mode", "text"),
                Map.of(),
                Map.of(
                        "expectedText", expected,
                        "successSignal", "text contains " + expected
                ),
                Map.of("timeoutSeconds", 5)
        ));
        return true;
    }

    private boolean matchWait(String line, List<ImportedStep> steps) {
        Matcher timeoutMatcher = WAIT_FOR_TIMEOUT.matcher(line);
        if (timeoutMatcher.find()) {
            steps.add(importedWaitStep(timeoutMatcher.group(1)));
            return true;
        }
        Matcher urlMatcher = WAIT_FOR_URL.matcher(line);
        if (urlMatcher.find()) {
            String expected = urlMatcher.group(2);
            if (!StringUtils.hasText(expected)) {
                expected = extractRegexBody(line);
            }
            steps.add(new ImportedStep(
                    "WAIT",
                    Map.of("mode", "url"),
                    Map.of(),
                    Map.of("urlContains", expected),
                    Map.of("timeoutSeconds", 5)
            ));
            return true;
        }
        return false;
    }

    private ImportedStep importedNavigateStep(String rawTarget) {
        String normalizedTarget = StringUtils.hasText(rawTarget) ? rawTarget.trim() : "/";
        String targetPath = normalizedTarget;
        if (normalizedTarget.startsWith("http://") || normalizedTarget.startsWith("https://")) {
            targetPath = normalizeAbsoluteTargetPath(normalizedTarget);
        } else if (!normalizedTarget.startsWith("/")) {
            targetPath = "/" + normalizedTarget;
        }
        return new ImportedStep(
                "NAVIGATE",
                Map.of("targetPath", targetPath),
                Map.of("preferred", "path"),
                Map.of(),
                Map.of("timeoutSeconds", 5)
        );
    }

    private ImportedStep importedAssertStep(String target, String value, String mode) {
        LinkedHashMap<String, Object> assertionSummary = new LinkedHashMap<>();
        if (StringUtils.hasText(value)) {
            assertionSummary.put("expectedText", value.trim());
            assertionSummary.put("successSignal", "text contains " + value.trim());
        } else if (StringUtils.hasText(target)) {
            assertionSummary.put("successSignal", mode + " " + target.trim());
        }
        return new ImportedStep(
                "ASSERT",
                Map.of("mode", mode),
                StringUtils.hasText(target) ? Map.of("selector", normalizeSeleniumLocator(target), "preferred", "selector") : Map.of(),
                Map.copyOf(assertionSummary),
                Map.of("timeoutSeconds", 5)
        );
    }

    private ImportedStep importedWaitForElementStep(String target, String value, String mode) {
        LinkedHashMap<String, Object> assertionSummary = new LinkedHashMap<>();
        if (StringUtils.hasText(value)) {
            assertionSummary.put("expectedText", value.trim());
        }
        if (StringUtils.hasText(target)) {
            assertionSummary.put("selector", normalizeSeleniumLocator(target));
        }
        return new ImportedStep(
                "WAIT",
                Map.of("mode", mode),
                StringUtils.hasText(target) ? Map.of("selector", normalizeSeleniumLocator(target), "preferred", "selector") : Map.of(),
                Map.copyOf(assertionSummary),
                Map.of("timeoutSeconds", 5)
        );
    }

    private ImportedStep importedWaitStep(String millisecondsText) {
        int timeoutSeconds = 5;
        if (StringUtils.hasText(millisecondsText)) {
            try {
                timeoutSeconds = Math.max(1, (int) Math.ceil(Double.parseDouble(millisecondsText.trim()) / 1000.0));
            } catch (NumberFormatException ignored) {
                timeoutSeconds = 5;
            }
        }
        return new ImportedStep(
                "WAIT",
                Map.of("mode", "delay"),
                Map.of(),
                Map.of(),
                Map.of("timeoutSeconds", timeoutSeconds)
        );
    }

    private ImportedStep importedFormFillStep(String selector, String value, String source) {
        LinkedHashMap<String, Object> actionSummary = new LinkedHashMap<>();
        actionSummary.put("fillValue", value);
        actionSummary.put("source", source);
        LinkedHashMap<String, Object> locatorStrategy = new LinkedHashMap<>();
        locatorStrategy.put("preferred", "selector");
        locatorStrategy.put("selector", selector);
        return new ImportedStep(
                "FORM_FILL",
                Map.copyOf(actionSummary),
                Map.copyOf(locatorStrategy),
                Map.of(),
                Map.of("timeoutSeconds", 5)
        );
    }

    private ImportedStep importedClickStep(String selector, String source) {
        LinkedHashMap<String, Object> actionSummary = new LinkedHashMap<>();
        actionSummary.put("action", "click");
        actionSummary.put("source", source);
        LinkedHashMap<String, Object> locatorStrategy = new LinkedHashMap<>();
        locatorStrategy.put("preferred", "selector");
        locatorStrategy.put("selector", selector);
        return new ImportedStep(
                "CLICK",
                Map.copyOf(actionSummary),
                Map.copyOf(locatorStrategy),
                Map.of(),
                Map.of("timeoutSeconds", 5)
        );
    }

    private List<UiE2eSceneImportStepResponse> toResponse(List<ImportedStep> importedSteps) {
        List<UiE2eSceneImportStepResponse> steps = new ArrayList<>();
        for (int i = 0; i < importedSteps.size(); i++) {
            ImportedStep step = importedSteps.get(i);
            steps.add(new UiE2eSceneImportStepResponse(
                    i + 1,
                    step.stepType(),
                    step.actionSummary(),
                    step.locatorStrategy(),
                    step.assertionSummary(),
                    step.waitPolicy()
            ));
        }
        return List.copyOf(steps);
    }

    private Map<String, Object> sourceSummary(
            String projectId,
            String sourceType,
            String content,
            ImportResult importResult,
            List<String> tags
    ) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("sourceType", sourceType);
        summary.put("importedFrom", importResult.sourceLabel());
        summary.put("projectId", projectId);
        summary.put("importDigest", SensitiveTextSanitizer.sha256Hex(content));
        summary.put("importedStepCount", importResult.steps().size());
        summary.put("warningCount", importResult.warnings().size());
        summary.put("aggregateOnly", true);
        summary.put("rawScriptStored", false);
        if (!tags.isEmpty()) {
            summary.put("tags", tags);
        }
        return Map.copyOf(summary);
    }

    private Map<String, Object> importSummary(String sourceType, ImportResult importResult, int stepCount) {
        return Map.of(
                "sourceType", sourceType,
                "sourceLabel", importResult.sourceLabel(),
                "stepCount", stepCount,
                "warningCount", importResult.warnings().size(),
                "rawScriptStored", false,
                "editableDraft", true
        );
    }

    private void auditImport(UiE2eSceneImportResponse response) {
        contextClient.writeAuditEvent(
                "ui_e2e.scene.imported",
                "UI_E2E_SCENE_IMPORT",
                response.code(),
                response.projectId(),
                "SUCCEEDED",
                Map.of(
                        "sourceType", response.sourceSummary().get("sourceType"),
                        "stepCount", response.steps().size(),
                        "warningCount", response.warnings().size(),
                        "aggregateOnly", true,
                        "rawScriptStored", false
                )
        );
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = boundedText(sourceType, 32).toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scene import sourceType 不支持");
        }
        return normalized;
    }

    private String resolveCode(String codeHint, String defaultCode, String sourceLabel) {
        String preferred = boundedNullable(codeHint, 128);
        if (StringUtils.hasText(preferred)) {
            return slug(preferred, slug(defaultCode, sourceLabel.toLowerCase(Locale.ROOT).replace(' ', '-')));
        }
        return slug(defaultCode, sourceLabel.toLowerCase(Locale.ROOT).replace(' ', '-'));
    }

    private String resolveName(String nameHint, String defaultName, String sourceLabel) {
        if (StringUtils.hasText(nameHint)) {
            return boundedText(nameHint, 128);
        }
        if (StringUtils.hasText(defaultName)) {
            return boundedText(defaultName, 128);
        }
        return boundedText(sourceLabel + " 导入场景", 128);
    }

    private List<String> normalizeTags(List<String> inputTags, List<String> importedTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (inputTags != null) {
            for (String tag : inputTags) {
                if (StringUtils.hasText(tag)) {
                    tags.add(boundedText(tag, 32));
                }
            }
        }
        for (String tag : importedTags) {
            if (StringUtils.hasText(tag)) {
                tags.add(boundedText(tag, 32));
            }
        }
        return List.copyOf(tags);
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入内容不是合法 JSON");
        }
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必填字段不能为空");
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String boundedContent(String content) {
        String normalized = boundedText(content, 200000);
        if (normalized.length() < 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入内容过短");
        }
        return normalized;
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP7 UI/E2E 控制面已关闭");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String slug(String value, String fallback) {
        String basis = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : fallback;
        String normalized = basis
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = fallback;
        }
        normalized = normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
        return normalized;
    }

    private String normalizeFilledValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return VALUE_PLACEHOLDER.matcher(trimmed).find()
                ? "[dynamic]"
                : SensitiveTextSanitizer.sanitizedEvidenceText(trimmed, 96);
    }

    private boolean isUsernameSelector(String selector) {
        String lowered = selector.toLowerCase(Locale.ROOT);
        return lowered.contains("user") || lowered.contains("email") || lowered.contains("login") || lowered.contains("account");
    }

    private boolean isPasswordSelector(String selector) {
        return selector.toLowerCase(Locale.ROOT).contains("pass");
    }

    private boolean isUsernameValue(String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        return lowered.contains("@") || lowered.contains("user") || lowered.contains("admin");
    }

    private boolean isPasswordValue(String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        return lowered.contains("pass") || lowered.contains("pwd") || lowered.contains("secret");
    }

    private String normalizeSeleniumLocator(String locator) {
        if (!StringUtils.hasText(locator)) {
            return "";
        }
        String trimmed = locator.trim();
        if (trimmed.startsWith("id=")) {
            return "#" + trimmed.substring(3);
        }
        if (trimmed.startsWith("css=")) {
            return trimmed.substring(4);
        }
        if (trimmed.startsWith("name=")) {
            return "[name=\"" + trimmed.substring(5) + "\"]";
        }
        if (trimmed.startsWith("link=")) {
            return "text=" + trimmed.substring(5);
        }
        if (trimmed.startsWith("xpath=")) {
            return trimmed;
        }
        return trimmed;
    }

    private String normalizePlaywrightFillLocator(Matcher matcher) {
        if (StringUtils.hasText(matcher.group(4))) {
            return "label=" + matcher.group(4);
        }
        if (StringUtils.hasText(matcher.group(6))) {
            return "placeholder=" + matcher.group(6);
        }
        if (StringUtils.hasText(matcher.group(9))) {
            return "role=textbox[name=\"" + matcher.group(9) + "\"]";
        }
        if (StringUtils.hasText(matcher.group(11))) {
            return "data-testid=" + matcher.group(11);
        }
        return firstText(matcher.group(2));
    }

    private String normalizePlaywrightClickLocator(Matcher matcher) {
        if (StringUtils.hasText(matcher.group(4))) {
            return "label=" + matcher.group(4);
        }
        if (StringUtils.hasText(matcher.group(6))) {
            return "text=" + matcher.group(6);
        }
        if (StringUtils.hasText(matcher.group(10))) {
            return "role=" + matcher.group(8) + "[name=\"" + matcher.group(10) + "\"]";
        }
        if (StringUtils.hasText(matcher.group(12))) {
            return "data-testid=" + matcher.group(12);
        }
        return firstText(matcher.group(2));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractRegexBody(String line) {
        int start = line.indexOf('/');
        int end = line.lastIndexOf('/');
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        return "";
    }

    private String extractTestTitle(String content) {
        Matcher matcher = Pattern.compile("test\\((['\"])(.+?)\\1").matcher(content);
        return matcher.find() ? matcher.group(2) : "";
    }

    private String normalizeAbsoluteTargetPath(String value) {
        try {
            URI uri = new URI(value);
            String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : "/";
            if (StringUtils.hasText(uri.getQuery())) {
                path = path + "?" + uri.getQuery();
            }
            if (StringUtils.hasText(uri.getFragment())) {
                path = path + "#" + uri.getFragment();
            }
            return path;
        } catch (URISyntaxException exception) {
            return "/";
        }
    }

    private record ImportedStep(
            String stepType,
            Map<String, Object> actionSummary,
            Map<String, Object> locatorStrategy,
            Map<String, Object> assertionSummary,
            Map<String, Object> waitPolicy
    ) {
    }

    private record ImportResult(
            String defaultCode,
            String defaultName,
            List<ImportedStep> steps,
            List<String> warnings,
            List<String> defaultTags,
            String riskLevel,
            String sourceLabel
    ) {
    }

    private static final class LoginDraft {
        private String usernameSelector;
        private String passwordSelector;
        private String usernameValue;
        private String passwordValue;
        private String submitAction;
        private String submitSelector;

        boolean ready() {
            return StringUtils.hasText(usernameSelector) || StringUtils.hasText(passwordSelector);
        }

        String submitAction() {
            return submitAction;
        }

        void reset() {
            usernameSelector = null;
            passwordSelector = null;
            usernameValue = null;
            passwordValue = null;
            submitAction = null;
            submitSelector = null;
        }
    }
}
