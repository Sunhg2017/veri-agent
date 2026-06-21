package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Fans one UI/E2E run request out to multiple browser-specific runner calls while respecting the configured local
 * concurrency ceiling.
 */
final class UiE2eRunAttemptExecutor {

    private final UiE2eRunnerPort runnerPort;
    private final UiE2eRunnerExecutionPool executionPool;

    UiE2eRunAttemptExecutor(
            UiE2eRunnerPort runnerPort,
            UiE2eRunnerExecutionPool executionPool
    ) {
        this.runnerPort = runnerPort;
        this.executionPool = executionPool;
    }

    List<UiE2eRunAttemptAggregator.BrowserAttempt> execute(
            UUID runId,
            UUID sceneId,
            UUID bundleId,
            String projectId,
            String baseUrl,
            String accountLeaseRef,
            java.util.Map<String, Object> accountSummary,
            UiE2eRunExecutionOptions options,
            UUID baselineRunId
    ) {
        List<String> browsers = options == null ? List.of("CHROMIUM") : options.browserTypes();
        try {
            List<Callable<UiE2eRunAttemptAggregator.BrowserAttempt>> tasks = browsers.stream()
                    .map(browserType -> (Callable<UiE2eRunAttemptAggregator.BrowserAttempt>) () ->
                            new UiE2eRunAttemptAggregator.BrowserAttempt(
                                    browserType,
                                    runnerPort.run(new UiE2eRunnerPort.RunnerRunRequest(
                                            runId,
                                            sceneId,
                                            bundleId,
                                            projectId,
                                            baseUrl,
                                            accountLeaseRef,
                                            accountSummary,
                                            List.of(browserType),
                                            options != null && options.visualRegressionEnabled(),
                                            baselineRunId,
                                            options == null ? null : options.visualMismatchThreshold()
                                    ))
                            ))
                    .toList();
            List<Future<UiE2eRunAttemptAggregator.BrowserAttempt>> futures = executionPool.invokeAll(tasks);
            List<UiE2eRunAttemptAggregator.BrowserAttempt> attempts = new ArrayList<>(futures.size());
            for (Future<UiE2eRunAttemptAggregator.BrowserAttempt> future : futures) {
                attempts.add(future.get());
            }
            return List.copyOf(attempts);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ui-e2e browser attempts interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("ui-e2e browser attempt failed", cause);
        }
    }
}
