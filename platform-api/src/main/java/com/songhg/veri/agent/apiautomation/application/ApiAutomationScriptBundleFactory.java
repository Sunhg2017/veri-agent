package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Builds reviewable pytest bundle metadata while keeping generated source reduced to digests and aggregate evidence.
 */
final class ApiAutomationScriptBundleFactory {

    static final String STATIC_CHECK_PASSED = "PASSED";
    static final String STATIC_CHECK_FAILED = "SCRIPT_STATIC_CHECK_FAILED";

    private static final String SCRIPT_TEMPLATE_VERSION = "wp6-pytest-httpx-v1";
    private static final String RUNNER_SECRET_HEADER_PATTERN_TEXT = "^X-VA-WP6-Secret-[1-9][0-9]*$";
    private static final String PYTEST_SECRET_HEADER_MAPPING_ENV = "WP6_RUNNER_SECRET_HEADERS_JSON";
    private static final String PYTEST_SECRET_VALUE_ENV_PREFIX = "WP6_RUNNER_SECRET_VALUE_";
    private static final List<Pattern> FORBIDDEN_SCRIPT_PATTERNS = List.of(
            Pattern.compile("(?m)^\\s*(import|from)\\s+(subprocess|socket|ftplib|paramiko|telnetlib|pickle|marshal)\\b"),
            Pattern.compile("\\b(os\\.system|eval|exec|__import__)\\s*\\(")
    );

    private final ObjectMapper objectMapper;

    ApiAutomationScriptBundleFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds a reviewable script bundle from persisted case drafts. Source text is used only for digest and static
     * check computation; persisted metadata contains no raw request, response, baseUrl, or secret values.
     */
    ApiAutomationScriptBundle createScriptBundle(
            ApiAutomationGenerationTask task,
            List<ApiAutomationCase> cases,
            String actor,
            Instant now
    ) {
        List<ScriptFile> files = scriptFiles(cases);
        Map<String, Object> fileTreeSummary = fileTreeSummary(task, cases, files);
        Map<String, Object> dependencySummary = dependencySummary();
        StaticCheckResult staticCheck = staticCheck(files);
        String bundleDigest = SensitiveTextSanitizer.sha256Hex(writeJson(Map.of(
                "templateVersion", SCRIPT_TEMPLATE_VERSION,
                "taskId", task.id().toString(),
                "fileTreeSummary", fileTreeSummary,
                "dependencySummary", dependencySummary
        )));
        return new ApiAutomationScriptBundle(
                UUID.randomUUID(),
                task.projectId(),
                task.id(),
                "DRAFT",
                bundleDigest,
                files.size(),
                writeJson(fileTreeSummary),
                writeJson(dependencySummary),
                staticCheck.status(),
                writeJson(staticCheck.summary()),
                null,
                null,
                null,
                null,
                null,
                null,
                actor,
                actor,
                now,
                now
        );
    }

    private List<ScriptFile> scriptFiles(List<ApiAutomationCase> cases) {
        List<ApiAutomationCase> sortedCases = cases.stream()
                .sorted((left, right) -> {
                    int pathCompare = left.path().compareTo(right.path());
                    if (pathCompare != 0) {
                        return pathCompare;
                    }
                    int coverageCompare = left.coverageType().compareTo(right.coverageType());
                    return coverageCompare != 0 ? coverageCompare : left.id().compareTo(right.id());
                })
                .toList();
        return List.of(
                new ScriptFile("pyproject.toml", "PYPROJECT", pyprojectToml(sortedCases.size())),
                new ScriptFile("tests/__init__.py", "PYTHON_PACKAGE", ""),
                new ScriptFile("tests/conftest.py", "PYTEST_FIXTURE", conftestPy()),
                new ScriptFile("tests/helpers.py", "ASSERTION_HELPER", helpersPy()),
                new ScriptFile("tests/test_generated_api.py", "PYTEST_CASES", generatedTestPy(sortedCases)),
                new ScriptFile("README.md", "RUNBOOK", bundleReadme(sortedCases.size()))
        );
    }

    private String pyprojectToml(int caseCount) {
        return """
                [project]
                name = "wp6-api-automation-bundle"
                version = "0.1.0"
                description = "Generated WP6 API automation bundle metadata"
                requires-python = ">=3.11"
                dependencies = [
                    "pytest>=8,<9",
                    "httpx>=0.27,<1"
                ]

                [tool.wp6]
                template_version = "%s"
                case_count = %d
                """.formatted(SCRIPT_TEMPLATE_VERSION, caseCount);
    }

    private String conftestPy() {
        return """
                import json
                import os
                import re

                import pytest


                _RUNNER_HEADER_PATTERN = re.compile(r"%s")
                _RUNNER_HEADER_MAPPING_ENV = "%s"
                _RUNNER_VALUE_ENV_PREFIX = "%s"


                def pytest_addoption(parser):
                    parser.addoption("--base-url", action="store", default="http://127.0.0.1:8080")


                @pytest.fixture()
                def base_url(pytestconfig):
                    return pytestconfig.getoption("--base-url").rstrip("/")


                def _runner_secret_headers():
                    raw_mapping = os.environ.get(_RUNNER_HEADER_MAPPING_ENV, "[]").strip()
                    if not raw_mapping:
                        return {}
                    try:
                        mappings = json.loads(raw_mapping)
                    except json.JSONDecodeError as exc:
                        raise pytest.UsageError("invalid WP6 runner header mapping") from exc
                    if not isinstance(mappings, list):
                        raise pytest.UsageError("invalid WP6 runner header mapping")

                    headers = {}
                    for index, item in enumerate(mappings, start=1):
                        if not isinstance(item, dict):
                            raise pytest.UsageError("invalid WP6 runner header mapping")
                        header_name = str(item.get("headerName", "")).strip()
                        value_env = str(item.get("valueEnv", "")).strip()
                        if not _RUNNER_HEADER_PATTERN.match(header_name):
                            raise pytest.UsageError("invalid WP6 runner header name")
                        if value_env != f"{_RUNNER_VALUE_ENV_PREFIX}{index}":
                            raise pytest.UsageError("invalid WP6 runner value env")
                        header_value = os.environ.get(value_env, "")
                        if "\\r" in header_value or "\\n" in header_value:
                            raise pytest.UsageError("invalid WP6 runner header value")
                        if header_value:
                            headers[header_name] = header_value
                    return headers


                @pytest.fixture()
                def default_headers():
                    return _runner_secret_headers()
                """.formatted(
                RUNNER_SECRET_HEADER_PATTERN_TEXT,
                PYTEST_SECRET_HEADER_MAPPING_ENV,
                PYTEST_SECRET_VALUE_ENV_PREFIX
        );
    }

    private String helpersPy() {
        return """
                def assert_status(response, expected_status):
                    assert response.status_code == expected_status


                def assert_response_bounded(response):
                    assert len(response.content) <= 1048576
                """;
    }

    private String generatedTestPy(List<ApiAutomationCase> cases) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                import httpx
                import pytest

                from tests.helpers import assert_response_bounded, assert_status


                CASES = [
                """);
        for (ApiAutomationCase automationCase : cases) {
            builder.append("    {\n")
                    .append("        \"case_id\": ").append(pythonString(automationCase.id().toString())).append(",\n")
                    .append("        \"title\": ").append(pythonString(automationCase.title())).append(",\n")
                    .append("        \"method\": ").append(pythonString(automationCase.httpMethod())).append(",\n")
                    .append("        \"path\": ").append(pythonString(automationCase.path())).append(",\n")
                    .append("        \"coverage_type\": ").append(pythonString(automationCase.coverageType())).append(",\n")
                    .append("        \"expected_status\": ").append(automationCase.expectedStatus()).append(",\n")
                    .append("    },\n");
        }
        builder.append("""
                ]


                @pytest.mark.parametrize("case", CASES, ids=[item["title"] for item in CASES])
                def test_generated_api_contract(base_url, default_headers, case):
                    url = f"{base_url}{case['path']}"
                    with httpx.Client(headers=default_headers, timeout=10.0) as client:
                        response = client.request(case["method"], url)
                    assert_status(response, case["expected_status"])
                    assert_response_bounded(response)
                """);
        return builder.toString();
    }

    private String bundleReadme(int caseCount) {
        return """
                # WP6 API Automation Bundle

                Template: %s
                Cases: %d

                Runtime base URL and headers are supplied by the controlled runner. The bundle metadata does not store
                raw request bodies, response bodies, tokens, passwords or environment variable values.
                """.formatted(SCRIPT_TEMPLATE_VERSION, caseCount);
    }

    private Map<String, Object> fileTreeSummary(
            ApiAutomationGenerationTask task,
            List<ApiAutomationCase> cases,
            List<ScriptFile> files
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateVersion", SCRIPT_TEMPLATE_VERSION);
        summary.put("taskId", task.id().toString());
        summary.put("caseCount", cases.size());
        summary.put("caseIdsDigest", SensitiveTextSanitizer.sha256Hex(cases.stream()
                .map(value -> value.id().toString())
                .sorted()
                .collect(Collectors.joining(","))));
        summary.put("files", files.stream().map(file -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", file.path());
            item.put("kind", file.kind());
            item.put("digest", SensitiveTextSanitizer.sha256Hex(file.content()));
            item.put("lineCount", lineCount(file.content()));
            item.put("pythonFile", file.path().endsWith(".py"));
            return item;
        }).toList());
        summary.put("rawSourceStored", false);
        summary.put("secretValuesStored", false);
        summary.put("runtimeInputs", runtimeInputsSummary());
        summary.put("pytestRunnerContractReady", true);
        summary.put("aggregateOnly", true);
        return summary;
    }

    private Map<String, Object> dependencySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runtime", "python>=3.11");
        summary.put("packageManager", "pip");
        summary.put("dependencies", List.of(
                Map.of("name", "pytest", "versionRange", ">=8,<9"),
                Map.of("name", "httpx", "versionRange", ">=0.27,<1")
        ));
        summary.put("networkAccessDuringStaticCheck", false);
        summary.put("secretValuesStored", false);
        summary.put("runnerContract", Map.of(
                "runtime", "PYTEST_HTTPX",
                "secretHeaderMapping", "ENV_JSON_TO_CONTROLLED_HEADERS",
                "mappingEnv", PYTEST_SECRET_HEADER_MAPPING_ENV,
                "valueEnvPrefix", PYTEST_SECRET_VALUE_ENV_PREFIX,
                "headerNamePattern", RUNNER_SECRET_HEADER_PATTERN_TEXT,
                "secretValuesStored", false
        ));
        summary.put("aggregateOnly", true);
        return summary;
    }

    private Map<String, Object> runtimeInputsSummary() {
        Map<String, Object> baseUrl = new LinkedHashMap<>();
        baseUrl.put("source", "pytest option --base-url");
        baseUrl.put("rawValueStored", false);

        Map<String, Object> secretHeaders = new LinkedHashMap<>();
        secretHeaders.put("mappingEnv", PYTEST_SECRET_HEADER_MAPPING_ENV);
        secretHeaders.put("valueEnvPrefix", PYTEST_SECRET_VALUE_ENV_PREFIX);
        secretHeaders.put("headerNamePattern", RUNNER_SECRET_HEADER_PATTERN_TEXT);
        secretHeaders.put("secretRefStored", false);
        secretHeaders.put("secretValuesStored", false);
        secretHeaders.put("allowedHeaderFamily", ApiAutomationRunSecretResolver.RUNNER_SECRET_HEADER_PREFIX + "N");

        Map<String, Object> runtimeInputs = new LinkedHashMap<>();
        runtimeInputs.put("baseUrl", baseUrl);
        runtimeInputs.put("secretHeaders", secretHeaders);
        runtimeInputs.put("rawRequestResponseStored", false);
        return runtimeInputs;
    }

    /**
     * Static checks run without executing Python or touching the network, then persist only aggregate evidence.
     */
    private StaticCheckResult staticCheck(List<ScriptFile> files) {
        List<String> violations = new ArrayList<>();
        int pythonFileCount = 0;
        boolean runtimeSecretHeaderMappingPresent = files.stream().anyMatch(this::hasRuntimeSecretHeaderMapping);
        for (ScriptFile file : files) {
            if (!file.path().endsWith(".py")) {
                continue;
            }
            pythonFileCount++;
            if (!balancedTemplateDelimiters(file.content())) {
                violations.add(file.path() + ":PYTHON_TEMPLATE_SYNTAX");
            }
            for (Pattern pattern : FORBIDDEN_SCRIPT_PATTERNS) {
                if (pattern.matcher(file.content()).find()) {
                    violations.add(file.path() + ":FORBIDDEN_IMPORT_OR_CALL");
                    break;
                }
            }
            if (SensitiveTextSanitizer.containsSensitiveText(file.content())) {
                violations.add(file.path() + ":HARDCODED_SECRET_PATTERN");
            }
        }
        if (!runtimeSecretHeaderMappingPresent) {
            violations.add("tests/conftest.py:RUNTIME_SECRET_HEADER_MAPPING_MISSING");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateVersion", SCRIPT_TEMPLATE_VERSION);
        summary.put("pythonSyntax", violations.stream().noneMatch(value -> value.endsWith("PYTHON_TEMPLATE_SYNTAX"))
                ? "PASSED"
                : "FAILED");
        summary.put("forbiddenImports", violations.stream()
                .filter(value -> value.endsWith("FORBIDDEN_IMPORT_OR_CALL"))
                .count());
        summary.put("secretPatternHits", violations.stream()
                .filter(value -> value.endsWith("HARDCODED_SECRET_PATTERN"))
                .count());
        summary.put("runtimeSecretHeaderMapping", runtimeSecretHeaderMappingPresent ? "PASSED" : "FAILED");
        summary.put("pythonFileCount", pythonFileCount);
        summary.put("violations", violations);
        summary.put("networkAccessDuringStaticCheck", false);
        summary.put("aggregateOnly", true);
        return new StaticCheckResult(violations.isEmpty() ? STATIC_CHECK_PASSED : STATIC_CHECK_FAILED, summary);
    }

    private boolean hasRuntimeSecretHeaderMapping(ScriptFile file) {
        return "tests/conftest.py".equals(file.path())
                && file.content().contains(PYTEST_SECRET_HEADER_MAPPING_ENV)
                && file.content().contains(PYTEST_SECRET_VALUE_ENV_PREFIX)
                && file.content().contains(RUNNER_SECRET_HEADER_PATTERN_TEXT);
    }

    private boolean balancedTemplateDelimiters(String content) {
        int round = 0;
        int square = 0;
        int curly = 0;
        for (int index = 0; index < content.length(); index++) {
            char value = content.charAt(index);
            if (value == '(') {
                round++;
            } else if (value == ')') {
                round--;
            } else if (value == '[') {
                square++;
            } else if (value == ']') {
                square--;
            } else if (value == '{') {
                curly++;
            } else if (value == '}') {
                curly--;
            }
            if (round < 0 || square < 0 || curly < 0) {
                return false;
            }
        }
        return round == 0 && square == 0 && curly == 0;
    }

    private int lineCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return content.split("\\R", -1).length;
    }

    private String pythonString(String value) {
        String escaped = nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "接口自动化摘要序列化失败");
        }
    }

    private record ScriptFile(
            String path,
            String kind,
            String content
    ) {
    }

    private record StaticCheckResult(
            String status,
            Map<String, Object> summary
    ) {
    }
}
