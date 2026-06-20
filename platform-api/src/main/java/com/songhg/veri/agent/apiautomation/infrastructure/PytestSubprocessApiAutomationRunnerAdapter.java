package com.songhg.veri.agent.apiautomation.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Explicit WP6 runner adapter that rebuilds the generated Pytest/httpx bundle in a temporary directory and executes it
 * either as a local subprocess or inside a Docker sandbox. The adapter never persists generated source, stdout, stderr,
 * response bodies or secret values.
 */
public class PytestSubprocessApiAutomationRunnerAdapter implements ApiAutomationRunnerPort {

    private static final String STATIC_CHECK_PASSED = "PASSED";
    private static final Pattern SAFE_PATH_SEGMENT_PATTERN = Pattern.compile("\\{[^}/]+}");
    private static final Pattern RUNNER_SECRET_HEADER_PATTERN = Pattern.compile("^X-VA-WP6-Secret-[1-9][0-9]*$");
    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final int OUTPUT_READ_MAX_BYTES = 8_192;
    private static final int PYTEST_EXTRA_TIMEOUT_SECONDS = 5;
    private static final String SECRET_HEADER_MAPPING_ENV = "WP6_RUNNER_SECRET_HEADERS_JSON";
    private static final String SECRET_VALUE_ENV_PREFIX = "WP6_RUNNER_SECRET_VALUE_";
    private static final String CONTAINER_WORKSPACE = "/workspace";
    private static final String RUNNER_MODE_EXTERNAL = "EXTERNAL";
    private static final String RUNNER_ADAPTER_PYTEST_SUBPROCESS = "PYTEST_SUBPROCESS";
    private static final String RUNNER_ADAPTER_PYTEST_DOCKER_SANDBOX = "PYTEST_DOCKER_SANDBOX";

    private final ObjectMapper objectMapper;
    private final CommandExecutor commandExecutor;
    private final List<String> pytestCommand;
    private final ExecutionBackend executionBackend;
    private final List<String> sandboxCommand;
    private final String sandboxImage;
    private final String sandboxNetwork;

    public PytestSubprocessApiAutomationRunnerAdapter(String pytestCommand) {
        this(
                new ObjectMapper(),
                new ProcessBuilderCommandExecutor(),
                splitCommand(pytestCommand),
                ExecutionBackend.HOST_SUBPROCESS,
                List.of("docker"),
                "",
                "bridge"
        );
    }

    public PytestSubprocessApiAutomationRunnerAdapter(
            String pytestCommand,
            String sandboxCommand,
            String sandboxImage,
            String sandboxNetwork
    ) {
        this(
                new ObjectMapper(),
                new ProcessBuilderCommandExecutor(),
                splitCommand(pytestCommand),
                ExecutionBackend.DOCKER_SANDBOX,
                splitCommand(sandboxCommand),
                sandboxImage,
                sandboxNetwork
        );
    }

    PytestSubprocessApiAutomationRunnerAdapter(
            ObjectMapper objectMapper,
            CommandExecutor commandExecutor,
            List<String> pytestCommand
    ) {
        this(
                objectMapper,
                commandExecutor,
                pytestCommand,
                ExecutionBackend.HOST_SUBPROCESS,
                List.of("docker"),
                "",
                "bridge"
        );
    }

    PytestSubprocessApiAutomationRunnerAdapter(
            ObjectMapper objectMapper,
            CommandExecutor commandExecutor,
            List<String> pytestCommand,
            ExecutionBackend executionBackend,
            List<String> sandboxCommand,
            String sandboxImage,
            String sandboxNetwork
    ) {
        this.objectMapper = objectMapper;
        this.commandExecutor = commandExecutor;
        this.pytestCommand = pytestCommand == null || pytestCommand.isEmpty()
                ? List.of("python3", "-m", "pytest")
                : List.copyOf(pytestCommand);
        this.executionBackend = executionBackend == null ? ExecutionBackend.HOST_SUBPROCESS : executionBackend;
        this.sandboxCommand = sandboxCommand == null || sandboxCommand.isEmpty()
                ? List.of("docker")
                : List.copyOf(sandboxCommand);
        this.sandboxImage = nullToEmpty(sandboxImage).trim();
        this.sandboxNetwork = StringUtils.hasText(sandboxNetwork) ? sandboxNetwork.trim() : "bridge";
    }

    @Override
    public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
        if (bundle == null) {
            return new RunnerValidation(false, "RUNNER_FAILED", "script bundle is required");
        }
        if (!"APPROVED".equals(bundle.status())) {
            return new RunnerValidation(false, "RUNNER_BUNDLE_NOT_APPROVED", "script bundle is not approved");
        }
        if (!STATIC_CHECK_PASSED.equals(bundle.staticCheckStatus())) {
            return new RunnerValidation(false, "SCRIPT_STATIC_CHECK_FAILED", "script static check is not passed");
        }
        if (bundle.fileCount() <= 0 || !StringUtils.hasText(bundle.bundleDigest())) {
            return new RunnerValidation(false, "RUNNER_FAILED", "script bundle metadata is incomplete");
        }
        return new RunnerValidation(true, null, null);
    }

    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        if (request == null || request.cases() == null || request.cases().isEmpty()) {
            return new RunnerRunResult("FAILED", RUNNER_MODE_EXTERNAL, "RUNNER_FAILED", "runner request has no cases", List.of());
        }
        Instant startedAt = Instant.now();
        if (hasUnsafeCaseMetadata(request.cases())) {
            return failedRun("RUNNER_FAILED", "pytest runner received unsafe case metadata", request.cases(), startedAt);
        }
        if (executionBackend == ExecutionBackend.DOCKER_SANDBOX && !StringUtils.hasText(sandboxImage)) {
            return failedRun("RUNNER_FAILED", "docker sandbox runner image is required", request.cases(), startedAt);
        }
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("wp6-pytest-runner-");
            writeBundle(workspace, request.cases());
            relaxWorkspacePermissions(workspace);
            Path junitXml = workspace.resolve("runner-results.xml");
            CommandExecutionPlan executionPlan = commandExecutionPlan(workspace, request.baseUrl(), junitXml, request.secrets());
            ProcessResult result = commandExecutor.execute(executionPlan, workspace, request.timeoutSeconds());
            List<RunnerCaseResult> caseResults = parseResults(junitXml, request.cases(), result, startedAt);
            return aggregate(caseResults, result);
        } catch (IllegalArgumentException exception) {
            return failedRun("RUNNER_FAILED", "pytest runner received unsafe runtime input", request.cases(), startedAt);
        } catch (IOException exception) {
            return failedRun("RUNNER_FAILED", "pytest runner failed to prepare workspace", request.cases(), startedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedRun("RUNNER_CANCELED", "pytest runner interrupted", request.cases(), startedAt);
        } finally {
            deleteQuietly(workspace);
        }
    }

    @Override
    public RunnerCancelResult cancel(UUID runId) {
            return new RunnerCancelResult(false, "RUNNER_CANCELED", runnerLabel() + " runner is synchronous; cancel is best effort only");
    }

    private CommandExecutionPlan commandExecutionPlan(
            Path workspace,
            String baseUrl,
            Path junitXml,
            List<RunnerSecret> secrets
    ) {
        Map<String, String> runnerEnvironment = environment(secrets);
        return switch (executionBackend) {
            case HOST_SUBPROCESS -> new CommandExecutionPlan(
                    hostCommand(baseUrl, junitXml),
                    runnerEnvironment,
                    null,
                    RUNNER_ADAPTER_PYTEST_SUBPROCESS,
                    List.of()
            );
            case DOCKER_SANDBOX -> dockerSandboxPlan(workspace, baseUrl, junitXml, runnerEnvironment);
        };
    }

    private List<String> hostCommand(String baseUrl, Path junitXml) {
        List<String> command = new ArrayList<>(pytestCommand);
        command.add("-q");
        command.add("--disable-warnings");
        command.add("--junitxml");
        command.add(junitXml.getFileName().toString());
        command.add("--base-url");
        command.add(baseUrl == null ? "" : baseUrl);
        command.add("tests/test_generated_api.py");
        return command;
    }

    private CommandExecutionPlan dockerSandboxPlan(
            Path workspace,
            String baseUrl,
            Path junitXml,
            Map<String, String> runnerEnvironment
    ) {
        String containerName = sandboxContainerName();
        List<String> command = new ArrayList<>(sandboxCommand);
        command.add("run");
        command.add("--rm");
        command.add("--name");
        command.add(containerName);
        command.add("--network");
        command.add(sandboxNetwork);
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp");
        command.add("--tmpfs");
        command.add("/run");
        command.add("--cap-drop");
        command.add("ALL");
        command.add("--security-opt");
        command.add("no-new-privileges");
        command.add("--pids-limit");
        command.add("256");
        command.add("--memory");
        command.add("256m");
        command.add("--cpus");
        command.add("1");
        command.add("--user");
        command.add("65534:65534");
        command.add("-v");
        command.add(workspace.toAbsolutePath() + ":" + CONTAINER_WORKSPACE + ":rw");
        command.add("-w");
        command.add(CONTAINER_WORKSPACE);
        runnerEnvironment.forEach((key, value) -> {
            command.add("-e");
            command.add(key + "=" + value);
        });
        command.add(sandboxImage);
        command.addAll(hostCommand(baseUrl, junitXml));
        return new CommandExecutionPlan(
                command,
                Map.of(),
                containerName,
                RUNNER_ADAPTER_PYTEST_DOCKER_SANDBOX,
                sandboxCommand
        );
    }

    /**
     * Secrets are passed through controlled WP6 environment variables only. The generated Pytest fixture rejects any
     * header outside the X-VA-WP6-Secret-N family and the service layer stores only secret digests.
     */
    private Map<String, String> environment(List<RunnerSecret> secrets) {
        Map<String, String> environment = new LinkedHashMap<>();
        if (secrets == null || secrets.isEmpty()) {
            environment.put(SECRET_HEADER_MAPPING_ENV, "[]");
            return environment;
        }
        List<Map<String, String>> mappings = new ArrayList<>();
        int index = 1;
        for (RunnerSecret secret : secrets) {
            if (secret == null
                    || !RUNNER_SECRET_HEADER_PATTERN.matcher(nullToEmpty(secret.headerName())).matches()
                    || !StringUtils.hasText(secret.value())
                    || secret.value().contains("\r")
                    || secret.value().contains("\n")) {
                throw new IllegalArgumentException("runner secret header is unsafe");
            }
            String valueEnv = SECRET_VALUE_ENV_PREFIX + index;
            mappings.add(Map.of(
                    "headerName", secret.headerName(),
                    "valueEnv", valueEnv,
                    "secretRefDigest", nullToEmpty(secret.secretRefDigest())
            ));
            environment.put(valueEnv, secret.value());
            index++;
        }
        environment.put(SECRET_HEADER_MAPPING_ENV, writeJson(mappings));
        return environment;
    }

    private void writeBundle(Path workspace, List<ApiAutomationCase> cases) throws IOException {
        Path testsDir = workspace.resolve("tests");
        Files.createDirectories(testsDir);
        Files.writeString(testsDir.resolve("__init__.py"), "", StandardCharsets.UTF_8);
        Files.writeString(testsDir.resolve("conftest.py"), conftestPy(), StandardCharsets.UTF_8);
        Files.writeString(testsDir.resolve("helpers.py"), helpersPy(), StandardCharsets.UTF_8);
        Files.writeString(testsDir.resolve("test_generated_api.py"), generatedTestPy(cases), StandardCharsets.UTF_8);
    }

    private String conftestPy() {
        return """
                import json
                import os
                import re

                import pytest


                _RUNNER_HEADER_PATTERN = re.compile(r"^X-VA-WP6-Secret-[1-9][0-9]*$")
                _RUNNER_HEADER_MAPPING_ENV = "WP6_RUNNER_SECRET_HEADERS_JSON"
                _RUNNER_VALUE_ENV_PREFIX = "WP6_RUNNER_SECRET_VALUE_"


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
                """;
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
        for (ApiAutomationCase automationCase : sortedCases(cases)) {
            String method = normalizedMethod(automationCase.httpMethod());
            if (!SUPPORTED_METHODS.contains(method)) {
                method = "GET";
            }
            builder.append("    {\n")
                    .append("        \"case_id\": ").append(pythonString(automationCase.id().toString())).append(",\n")
                    .append("        \"title\": ").append(pythonString(automationCase.title())).append(",\n")
                    .append("        \"method\": ").append(pythonString(method)).append(",\n")
                    .append("        \"path\": ").append(pythonString(executablePath(automationCase.path()))).append(",\n")
                    .append("        \"expected_status\": ").append(automationCase.expectedStatus()).append(",\n")
                    .append("    },\n");
        }
        builder.append("""
                ]


                @pytest.mark.parametrize("case", CASES, ids=[item["case_id"] for item in CASES])
                def test_generated_api_contract(base_url, default_headers, case):
                    url = f"{base_url}{case['path']}"
                    with httpx.Client(headers=default_headers, timeout=10.0, follow_redirects=False) as client:
                        response = client.request(case["method"], url)
                    assert_status(response, case["expected_status"])
                    assert_response_bounded(response)
                """);
        return builder.toString();
    }

    private List<ApiAutomationCase> sortedCases(List<ApiAutomationCase> cases) {
        return cases.stream()
                .sorted(Comparator.comparing(ApiAutomationCase::id))
                .toList();
    }

    private boolean hasUnsafeCaseMetadata(List<ApiAutomationCase> cases) {
        return cases.stream()
                .anyMatch(automationCase -> automationCase == null
                        || !SUPPORTED_METHODS.contains(normalizedMethod(automationCase.httpMethod()))
                        || !safeCasePath(automationCase.path()));
    }

    private String executablePath(String path) {
        return SAFE_PATH_SEGMENT_PATTERN.matcher(path.trim()).replaceAll("1");
    }

    private boolean safeCasePath(String path) {
        String trimmed = nullToEmpty(path).trim();
        return trimmed.startsWith("/")
                && !trimmed.contains("://")
                && !trimmed.contains("?")
                && !trimmed.contains("#")
                && !trimmed.contains("\r")
                && !trimmed.contains("\n");
    }

    private List<RunnerCaseResult> parseResults(
        Path junitXml,
        List<ApiAutomationCase> cases,
        ProcessResult processResult,
        Instant startedAt
    ) {
        boolean junitEvidenceAvailable = Files.exists(junitXml);
        Map<UUID, RunnerCaseResult> parsed = junitEvidenceAvailable
                ? parseJunitXml(junitXml, cases, processResult)
                : Map.of();
        List<RunnerCaseResult> results = new ArrayList<>();
        for (ApiAutomationCase automationCase : cases) {
            RunnerCaseResult result = parsed.get(automationCase.id());
            results.add(result == null
                    ? fallbackCaseResult(automationCase, processResult, durationMillis(startedAt))
                    : result);
        }
        return results;
    }

    private Map<UUID, RunnerCaseResult> parseJunitXml(
            Path junitXml,
            List<ApiAutomationCase> cases,
            ProcessResult processResult
    ) {
        try (InputStream inputStream = Files.newInputStream(junitXml)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(inputStream);
            NodeList testcases = document.getElementsByTagName("testcase");
            Map<UUID, RunnerCaseResult> results = new LinkedHashMap<>();
            Map<String, ApiAutomationCase> caseById = new LinkedHashMap<>();
            for (ApiAutomationCase automationCase : cases) {
                caseById.put(automationCase.id().toString(), automationCase);
            }
            for (int i = 0; i < testcases.getLength(); i++) {
                Element testcase = (Element) testcases.item(i);
                String caseId = testcaseCaseId(testcase.getAttribute("name"));
                ApiAutomationCase automationCase = caseById.get(caseId);
                if (automationCase == null) {
                    continue;
                }
                boolean failed = testcase.getElementsByTagName("failure").getLength() > 0;
                boolean errored = testcase.getElementsByTagName("error").getLength() > 0;
                boolean skipped = testcase.getElementsByTagName("skipped").getLength() > 0;
                String status = failed ? "FAILED" : errored ? "ERROR" : skipped ? "SKIPPED" : "PASSED";
                results.put(automationCase.id(), new RunnerCaseResult(
                        automationCase.id(),
                        status,
                        durationMillis(testcase.getAttribute("time")),
                        assertionSummary(automationCase.expectedStatus(), status, processResult),
                        errorCode(status, processResult.timedOut()),
                        errorSummary(status, processResult)
                ));
            }
            return results;
        } catch (IOException | RuntimeException | SAXException | javax.xml.parsers.ParserConfigurationException exception) {
            return Map.of();
        }
    }

    private String testcaseCaseId(String name) {
        Matcher matcher = Pattern.compile("\\[([0-9a-fA-F\\-]{36})]").matcher(nullToEmpty(name));
        return matcher.find() ? matcher.group(1) : nullToEmpty(name);
    }

    private RunnerCaseResult fallbackCaseResult(
            ApiAutomationCase automationCase,
            ProcessResult processResult,
            int durationMs
    ) {
        String status = processResult.timedOut() ? "TIMEOUT" : "ERROR";
        return new RunnerCaseResult(
                automationCase.id(),
                status,
                durationMs,
                assertionSummary(automationCase.expectedStatus(), status, processResult),
                errorCode(status, processResult.timedOut()),
                errorSummary(status, processResult)
        );
    }

    private RunnerRunResult aggregate(List<RunnerCaseResult> results, ProcessResult processResult) {
        boolean timeout = processResult.timedOut() || results.stream().anyMatch(result -> "TIMEOUT".equals(result.status()));
        boolean failed = results.stream().anyMatch(result -> "FAILED".equals(result.status()) || "ERROR".equals(result.status()));
        if (timeout) {
            return new RunnerRunResult("TIMEOUT", RUNNER_MODE_EXTERNAL, "RUNNER_TIMEOUT", runnerLabel() + " runner timed out", results);
        }
        if (failed || processResult.exitCode() != 0) {
            return new RunnerRunResult("FAILED", RUNNER_MODE_EXTERNAL, "RUNNER_FAILED", runnerLabel() + " runner found failed cases", results);
        }
        return new RunnerRunResult("PASSED", RUNNER_MODE_EXTERNAL, null, null, results);
    }

    private RunnerRunResult failedRun(
            String errorCode,
            String errorSummary,
            List<ApiAutomationCase> cases,
            Instant startedAt
    ) {
        int durationMs = durationMillis(startedAt);
        List<RunnerCaseResult> results = cases == null ? List.of() : cases.stream()
                .map(automationCase -> new RunnerCaseResult(
                        automationCase.id(),
                        "ERROR",
                        durationMs,
                        assertionSummary(automationCase.expectedStatus(), "ERROR", new ProcessResult(1, false, "", "")),
                        errorCode,
                        errorSummary
                ))
                .toList();
        return new RunnerRunResult("FAILED", RUNNER_MODE_EXTERNAL, errorCode, errorSummary, results);
    }

    private String assertionSummary(Integer expectedStatus, String status, ProcessResult processResult) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aggregateOnly", true);
        summary.put("rawRequestResponseStored", false);
        summary.put("secretValuesStored", false);
        summary.put("runnerAdapter", executionBackend == ExecutionBackend.DOCKER_SANDBOX
                ? RUNNER_ADAPTER_PYTEST_DOCKER_SANDBOX
                : RUNNER_ADAPTER_PYTEST_SUBPROCESS);
        summary.put("sandboxed", executionBackend == ExecutionBackend.DOCKER_SANDBOX);
        summary.put("assertions", List.of("PYTEST_HTTPX"));
        summary.put("expectedStatus", expectedStatus);
        summary.put("status", status);
        summary.put("exitCode", processResult.exitCode());
        summary.put("timedOut", processResult.timedOut());
        summary.put("stdoutBytes", utf8Bytes(processResult.stdout()));
        summary.put("stderrBytes", utf8Bytes(processResult.stderr()));
        return writeJson(summary);
    }

    private String errorCode(String status, boolean timedOut) {
        if (timedOut || "TIMEOUT".equals(status)) {
            return "RUNNER_TIMEOUT";
        }
        return switch (status) {
            case "PASSED", "SKIPPED" -> null;
            case "FAILED" -> "ASSERTION_FAILED";
            default -> "RUNNER_FAILED";
        };
    }

    private String errorSummary(String status, ProcessResult processResult) {
        if ("PASSED".equals(status) || "SKIPPED".equals(status)) {
            return null;
        }
        if (processResult.timedOut() || "TIMEOUT".equals(status)) {
            return runnerLabel() + " timed out";
        }
        return runnerLabel() + " case did not pass";
    }

    private int utf8Bytes(String value) {
        long bytes = nullToEmpty(value).getBytes(StandardCharsets.UTF_8).length;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    private int durationMillis(String seconds) {
        try {
            double value = Double.parseDouble(nullToEmpty(seconds));
            long millis = Math.round(value * 1000);
            return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) millis);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int durationMillis(Instant startedAt) {
        long millis = Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private String normalizedMethod(String method) {
        return StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String pythonString(String value) {
        return writeJson(nullToEmpty(value));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize runner json", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void relaxWorkspacePermissions(Path workspace) {
        if (executionBackend != ExecutionBackend.DOCKER_SANDBOX || workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (var stream = Files.walk(workspace)) {
            stream.forEach(this::relaxPathPermissions);
        } catch (IOException ignored) {
            // Best-effort only; docker sandbox execution will fail closed if the workspace stays inaccessible.
        }
    }

    private void relaxPathPermissions(Path path) {
        if (path == null) {
            return;
        }
        boolean directory = Files.isDirectory(path);
        path.toFile().setReadable(true, false);
        path.toFile().setWritable(true, false);
        path.toFile().setExecutable(directory, false);
    }

    private String sandboxContainerName() {
        return "wp6-pytest-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String runnerLabel() {
        return executionBackend == ExecutionBackend.DOCKER_SANDBOX
                ? "pytest docker sandbox"
                : "pytest subprocess";
    }

    private static List<String> splitCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return List.of("python3", "-m", "pytest");
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|'([^']*)'|\\S+").matcher(command);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                values.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                values.add(matcher.group(2));
            } else {
                values.add(matcher.group());
            }
        }
        return values.isEmpty() ? List.of("python3", "-m", "pytest") : values;
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Best-effort cleanup only; runner results are already reduced before this point.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    interface CommandExecutor {

        ProcessResult execute(
                CommandExecutionPlan plan,
                Path workingDirectory,
                int timeoutSeconds
        ) throws IOException, InterruptedException;
    }

    record CommandExecutionPlan(
            List<String> command,
            Map<String, String> environment,
            String sandboxContainerName,
            String runnerAdapter,
            List<String> sandboxCleanupCommand
    ) {
    }

    record ProcessResult(
            int exitCode,
            boolean timedOut,
            String stdout,
            String stderr
    ) {
    }

    private static final class ProcessBuilderCommandExecutor implements CommandExecutor {

        @Override
        public ProcessResult execute(
                CommandExecutionPlan plan,
                Path workingDirectory,
                int timeoutSeconds
        ) throws IOException, InterruptedException {
            Path stdoutFile = workingDirectory.resolve("pytest-stdout.log");
            Path stderrFile = workingDirectory.resolve("pytest-stderr.log");
            ProcessBuilder builder = new ProcessBuilder(plan.command())
                    .directory(workingDirectory.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile());
            builder.environment().putAll(plan.environment());
            Process process = builder.start();
            try {
                boolean completed = process.waitFor(Math.max(1, timeoutSeconds) + PYTEST_EXTRA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    process.waitFor(1, TimeUnit.SECONDS);
                    cleanupSandbox(plan);
                    return new ProcessResult(124, true, readLog(stdoutFile), readLog(stderrFile));
                }
                return new ProcessResult(process.exitValue(), false, readLog(stdoutFile), readLog(stderrFile));
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                cleanupSandbox(plan);
                throw exception;
            }
        }

        private String readLog(Path path) throws IOException {
            if (!Files.exists(path)) {
                return "";
            }
            try (InputStream inputStream = Files.newInputStream(path)) {
                return new String(inputStream.readNBytes(OUTPUT_READ_MAX_BYTES), StandardCharsets.UTF_8);
            }
        }

        private void cleanupSandbox(CommandExecutionPlan plan) {
            if (plan == null
                    || !StringUtils.hasText(plan.sandboxContainerName())
                    || plan.sandboxCleanupCommand() == null
                    || plan.sandboxCleanupCommand().isEmpty()) {
                return;
            }
            try {
                List<String> cleanupCommand = new ArrayList<>(plan.sandboxCleanupCommand());
                cleanupCommand.add("rm");
                cleanupCommand.add("-f");
                cleanupCommand.add(plan.sandboxContainerName());
                Process cleanup = new ProcessBuilder(cleanupCommand)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                cleanup.waitFor(5, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    enum ExecutionBackend {
        HOST_SUBPROCESS,
        DOCKER_SANDBOX
    }
}
